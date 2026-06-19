package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.skills.SkillType;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Runs the Reliquary (PoE strongbox-style) event inside a dungeon instance.
 *
 * On session start (if the entry map carries {@link MapMod#RELIQUARY} and the
 * template has a reliquary configured) we spawn a sealed reliquary marker per
 * chosen center. A player right-clicks an idle reliquary to unseal it: a
 * guardian pack spawns and the reliquary locks. Slay EVERY guardian to crack it
 * open, dropping a loot cache whose contents scale with how many guardians were
 * slain and the map tier.
 *
 * Per-session runtime state lives here and is dropped when the session ends.
 */
public final class ReliquaryManager implements Listener {

    private final AbyssPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();        // session id -> state
    private final Map<Location, Cache> caches = new HashMap<>();    // chest block -> loot cache
    private final Map<UUID, Location> viewing = new HashMap<>();    // viewer -> cache block they have open
    private final Random rng = new Random();

    public ReliquaryManager(AbyssPlugin plugin) { this.plugin = plugin; }

    enum Status { IDLE, GUARDED, DONE }

    static final class Vault {
        final Location center;
        UUID standId;
        UUID textId;
        Status status = Status.IDLE;
        int guardsTotal = 0;
        int slain = 0;
        /** True while the guard pack is being spawned — blocks a premature crack. */
        boolean spawning = false;
        final Set<UUID> guards = new HashSet<>();
        Vault(Location center) { this.center = center; }
    }

    static final class State {
        final DungeonSession session;
        final DungeonTemplate template;
        final List<Vault> vaults = new ArrayList<>();
        org.bukkit.scheduler.BukkitTask watchdog; // prunes vanished guards so a vault can't stall
        State(DungeonSession s, DungeonTemplate t) { this.session = s; this.template = t; }
    }

    /** A dropped loot cache: rolled per-player, scaled by guardians slain + tier. */
    static final class Cache {
        final UUID sessionId;
        final int slain;
        final Map<UUID, Inventory> rolled = new HashMap<>();
        Cache(UUID sessionId, int slain) { this.sessionId = sessionId; this.slain = slain; }
    }

    // ============================================================
    // Setup
    // ============================================================

    /** Spawn reliquary markers for a session if it should run one. No-op otherwise. */
    public void init(DungeonSession session, DungeonTemplate template) {
        if (!session.hasMod(MapMod.RELIQUARY.id())) return;
        if (!template.hasReliquary()) return;

        State state = new State(session, template);
        World w = session.world();

        // Unseal a random subset of the reliquary's anchors each run so the map
        // feels different every play. Anchors are the type-specific markers if any
        // were placed, otherwise the shared event-block pool. The admin sets a
        // min-max range; we roll a count in it (0 max = use all anchors).
        List<Location> chosen = com.abyss.sigils.util.RandomPick.someInRange(
                session.unclaimedAnchors(template.reliquarySpawnAnchors()), template.reliquaryActiveMin(),
                template.reliquaryActive(), rng);
        session.claimAnchors(chosen);

        for (Location raw : chosen) {
            Location loc = new Location(w, raw.getX(), raw.getY(), raw.getZ());
            Vault vault = new Vault(loc);
            spawnMarker(vault);
            state.vaults.add(vault);
        }
        if (state.vaults.isEmpty()) return;
        states.put(session.id(), state);

        for (UUID id : session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(Text.color("&6&l✦ &eThis map carries "
                    + (state.vaults.size() == 1 ? "a Reliquary" : state.vaults.size() + " Reliquaries")
                    + ". &7Find " + (state.vaults.size() == 1 ? "it" : "them")
                    + " and slay the guardians to crack " + (state.vaults.size() == 1 ? "it" : "them") + " open."));
        }
    }

    private void spawnMarker(Vault vault) {
        Location standLoc = vault.center.clone();
        World w = standLoc.getWorld();

        ArmorStand stand = w.spawn(standLoc, ArmorStand.class, as -> {
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setBasePlate(true);
            as.setArms(true);
            as.setCustomNameVisible(false);
            as.setPersistent(false);
            as.setGlowing(true);
        });
        vault.standId = stand.getUniqueId();

        Location textLoc = vault.center.clone().add(0, 2.3, 0);
        TextDisplay text = w.spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(180, 45, 30, 0));
            td.setPersistent(false);
        });
        vault.textId = text.getUniqueId();
        updateText(vault);
    }

    private void updateText(Vault vault) {
        if (vault.textId == null) return;
        Entity e = plugin.getServer().getEntity(vault.textId);
        if (!(e instanceof TextDisplay td)) return;
        String label = switch (vault.status) {
            case IDLE    -> "§6§l✦ Reliquary ✦\n§7Right-click to unseal";
            case GUARDED -> "§c§l⚔ Guarded ⚔\n§7Slain: §f" + vault.slain + "§7/§f" + vault.guardsTotal;
            case DONE    -> "§8§lThe reliquary lies cracked open";
        };
        td.setText(label);
    }

    // ============================================================
    // Interaction — unsealing a reliquary
    // ============================================================

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        State state = states.get(sessionIdOf(p));
        if (state == null) return;
        Vault vault = vaultByEntity(state, e.getRightClicked().getUniqueId());
        if (vault == null) return;
        e.setCancelled(true);
        if (vault.status == Status.IDLE) {
            open(state, vault, p);
        } else if (vault.status == Status.GUARDED) {
            p.sendMessage(Text.color("&7This reliquary is still guarded — slay them all first."));
        } else {
            p.sendMessage(Text.color("&7This reliquary has already been cracked open."));
        }
    }

    /** Keep players from rotating / equipping the reliquary stands. */
    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        State state = states.get(sessionIdOf(e.getPlayer()));
        if (state == null) return;
        if (vaultByEntity(state, e.getRightClicked().getUniqueId()) != null) e.setCancelled(true);
    }

    /** Keep the reliquary stands indestructible. */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        for (State state : states.values()) {
            if (vaultByEntity(state, e.getEntity().getUniqueId()) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void open(State state, Vault vault, Player opener) {
        DungeonTemplate t = state.template;
        // Targeted guardians (flattened by their count) plus N random picks from
        // the shared event-trash pool. Each trash pick is a single mob.
        List<MobEntry> toSpawn = new ArrayList<>();
        for (MobEntry entry : t.reliquaryGuards())
            for (int i = 0; i < Math.max(1, entry.count()); i++) toSpawn.add(entry);
        toSpawn.addAll(t.rollEventTrash(t.reliquaryTrashCount()));
        if (toSpawn.isEmpty()) {
            broadcast(state, "&cThe reliquary is sealed shut — no guardians are configured.");
            return;
        }

        vault.status = Status.GUARDED;
        vault.slain = 0;
        // While spawning, handleMobDeath must NOT crack the reliquary even if the
        // guard set momentarily empties (player oneshots an early guardian before
        // the rest spawn) — that would orphan the remaining spawns. Cleared below.
        vault.spawning = true;
        vault.guards.clear();
        updateText(vault);
        broadcast(state, "&6&l✦ &eA Reliquary breaks its seal! &7Slay its guardians to crack it open.");
        playSound(state, Sound.BLOCK_BEACON_ACTIVATE, 0.7f);

        int spawned = 0, failed = 0;
        for (MobEntry entry : toSpawn) {
            Location loc = safeSpawn(ringSpot(vault.center), vault.center);
            var ent = plugin.dungeonManager().spawnMythic(entry.mythicId(), loc, entry.level());
            if (ent.isPresent()) {
                Entity mob = ent.get();
                // Keep guardians from despawning — a guardian that vanished
                // without a death event would leave the reliquary forever
                // guarded (uncrackable).
                mob.setPersistent(true);
                if (mob instanceof LivingEntity le) { le.setRemoveWhenFarAway(false); le.setFallDistance(0); }
                vault.guards.add(mob.getUniqueId());
                spawned++;
            } else failed++;
        }
        if (failed > 0) {
            plugin.getLogger().warning("Reliquary: " + failed + " guardian(s) failed to spawn (spawned "
                    + spawned + "). Check the MythicMob ids.");
        }

        vault.guardsTotal = spawned;
        vault.spawning = false;

        // No guardians spawned at all — don't soft-lock the reliquary; reseal it
        // so it can be retried.
        if (spawned == 0) {
            vault.status = Status.IDLE;
            updateText(vault);
            broadcast(state, "&cThe reliquary's guardians never stirred. Try again.");
            return;
        }
        updateText(vault);

        // The opener may have oneshot every guardian before the last one finished
        // spawning; handleMobDeath deferred while spawning, so crack now if empty.
        if (vault.guards.isEmpty()) crack(state, vault);

        startWatchdog(state);
    }

    /** How often (ticks) the watchdog reconciles tracked guardians. */
    private static final long WATCHDOG_INTERVAL = 20L;

    /**
     * Safety net against a reliquary stalling forever. A guardian can leave the
     * world WITHOUT firing a MythicMobDeathEvent — e.g. it spawns on a bad spot
     * and falls into the void, or the server's entity cap culls it when the
     * dungeon is full. Without a death event its UUID is never removed from the
     * vault's guard set, so the vault stays GUARDED forever and never cracks.
     * This periodic sweep drops any tracked guardian whose entity no longer
     * exists and cracks the vault once none remain. Runs for the whole session;
     * idles while no vault is guarded.
     */
    private void startWatchdog(State state) {
        if (state.watchdog != null) return; // already running for this session
        state.watchdog = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (states.get(state.session.id()) != state) { // session gone
                if (state.watchdog != null) { state.watchdog.cancel(); state.watchdog = null; }
                return;
            }
            for (Vault v : state.vaults) {
                if (v.status != Status.GUARDED || v.spawning) continue;
                v.guards.removeIf(id -> {
                    Entity e = plugin.getServer().getEntity(id);
                    return e == null || e.isDead();
                });
                if (v.guards.isEmpty()) crack(state, v);
            }
        }, WATCHDOG_INTERVAL, WATCHDOG_INTERVAL);
    }

    /** A random spot in a small ring around the reliquary for a guardian to spawn. */
    private Location ringSpot(Location center) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = 1.5 + rng.nextDouble() * 2.0;
        return center.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
    }

    /**
     * Snap a candidate spawn to a standable spot near the target, searching a
     * small vertical window. Falls back to the reliquary center, which is always
     * valid. Stops guardians spawning inside walls / over holes.
     */
    private Location safeSpawn(Location target, Location fallback) {
        World w = target.getWorld();
        if (w == null) return fallback.clone();
        int x = target.getBlockX(), z = target.getBlockZ(), baseY = target.getBlockY();
        for (int dy = 0; dy <= 4; dy++) {
            if (standable(w, x, baseY + dy, z)) return new Location(w, x + 0.5, baseY + dy, z + 0.5);
            if (standable(w, x, baseY - dy, z)) return new Location(w, x + 0.5, baseY - dy, z + 0.5);
        }
        return fallback.clone();
    }

    private boolean standable(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).isPassable()
                && w.getBlockAt(x, y + 1, z).isPassable()
                && w.getBlockAt(x, y - 1, z).getType().isSolid();
    }

    // ============================================================
    // Guardian deaths — crack the reliquary once all are slain
    // ============================================================

    /**
     * Called from {@link DungeonManager#handleMythicMobDeath}. If the dead mob is
     * a reliquary guardian, counts it and returns true so the caller skips the
     * boss kill-counter for it (guardians don't advance the dungeon).
     */
    public boolean handleMobDeath(DungeonSession session, UUID mobId) {
        State state = states.get(session.id());
        if (state == null) return false;
        for (Vault vault : state.vaults) {
            if (vault.guards.remove(mobId)) {
                vault.slain++;
                if (vault.status == Status.GUARDED) updateText(vault);
                if (vault.guards.isEmpty() && !vault.spawning && vault.status == Status.GUARDED) {
                    crack(state, vault);
                }
                return true;
            }
        }
        return false;
    }

    private void crack(State state, Vault vault) {
        if (vault.status == Status.DONE) return;
        vault.status = Status.DONE;

        // Close the marker visually.
        removeEntity(vault.standId);
        removeEntity(vault.textId);
        vault.standId = null;
        vault.textId = null;

        World w = vault.center.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, vault.center.clone().add(0, 1, 0), 1);
        }

        placeCache(state, vault);
        broadcast(state, "&6&l✦ &eThe Reliquary cracks open! &7" + vault.slain
                + " guardian(s) slain — &eclaim the hoard within.");
        playSound(state, Sound.BLOCK_ENDER_CHEST_OPEN, 1f);
    }

    private void placeCache(State state, Vault vault) {
        World w = vault.center.getWorld();
        if (w == null) return;
        // Center is stored "stand on top", so the floor block is one below.
        Block floor = w.getBlockAt(vault.center.getBlockX(), vault.center.getBlockY() - 1, vault.center.getBlockZ());
        floor.setType(Material.CHEST);
        caches.put(floor.getLocation(), new Cache(state.session.id(), vault.slain));
    }

    // ============================================================
    // Loot cache claiming
    // ============================================================

    @EventHandler
    public void onCacheInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CHEST) return;
        Cache cache = caches.get(b.getLocation());
        if (cache == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        DungeonSession session = plugin.dungeonManager().sessionById(cache.sessionId);
        if (session == null) { p.sendMessage(Text.color("&cThis hoard's dungeon is no longer active.")); return; }
        if (!session.players().contains(p.getUniqueId())) {
            p.sendMessage(Text.color("&cThis hoard isn't for you."));
            return;
        }

        Inventory inv = cache.rolled.get(p.getUniqueId());
        if (inv == null) {
            DungeonTemplate t = plugin.templates().get(session.templateName());
            if (t == null) { p.sendMessage(Text.color("&cTemplate missing.")); return; }
            inv = rollCache(t, cache.slain, session.tier(), p.getUniqueId());
            cache.rolled.put(p.getUniqueId(), inv);
            boolean empty = true;
            for (ItemStack s : inv.getContents()) if (s != null && s.getType() != Material.AIR) { empty = false; break; }
            if (empty) p.sendMessage(Text.color("&7The reliquary holds nothing for you this time."));
        }

        viewing.put(p.getUniqueId(), b.getLocation());
        p.openInventory(inv);
    }

    private Inventory rollCache(DungeonTemplate t, int slain, int tier, UUID player) {
        List<MaelstromLoot> candidates = new ArrayList<>();
        // Own reliquary loot + shared default-event pool, filtered to this map's tier.
        for (MaelstromLoot l : t.eventLootFor(t.reliquaryLoot(), tier)) {
            if (slain < l.minKills()) continue;                  // gated by guardians slain
            if (rng.nextDouble() * 100.0 < l.chancePercent()) candidates.add(l);
        }
        Collections.shuffle(candidates, rng);
        // Tome of Mastery — Greater Spoils (general) + Relic Hunter (reliquary)
        // let the cache roll more items; higher map tier adds more on top.
        int bonus = SkillType.GREATER_SPOILS.flatAt(plugin.skills().rank(player, SkillType.GREATER_SPOILS))
                + SkillType.RELIC_HUNTER.flatAt(plugin.skills().rank(player, SkillType.RELIC_HUNTER))
                + Math.max(0, tier);
        int cap = Math.min(candidates.size(), Math.max(0, t.reliquaryMaxLootItems() + bonus));
        Inventory inv = Bukkit.createInventory(null, 27, Text.color("&6&lReliquary Hoard"));
        int slot = 4;
        for (int i = 0; i < cap; i++) {
            MaelstromLoot l = candidates.get(i);
            int count = l.minCount() + (l.maxCount() > l.minCount()
                    ? rng.nextInt(l.maxCount() - l.minCount() + 1) : 0);
            inv.setItem(Math.min(slot, 22), l.resolve(plugin, count));
            slot += 2;
        }
        return inv;
    }

    /** Block players from inserting items into a reliquary hoard. */
    @EventHandler
    public void onCacheClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!viewing.containsKey(p.getUniqueId())) return;
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCacheClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Location loc = viewing.remove(p.getUniqueId());
        if (loc == null) return;
        Cache cache = caches.get(loc);
        if (cache == null) return;
        Inventory inv = cache.rolled.get(p.getUniqueId());
        if (inv == null) return;
        boolean empty = true;
        for (ItemStack s : inv.getContents()) if (s != null && s.getType() != Material.AIR) { empty = false; break; }
        if (empty) cache.rolled.remove(p.getUniqueId());
    }

    // ============================================================
    // Cleanup
    // ============================================================

    public void cleanup(DungeonSession session) {
        State state = states.remove(session.id());
        if (state != null) {
            if (state.watchdog != null) { state.watchdog.cancel(); state.watchdog = null; }
            for (Vault vault : state.vaults) {
                for (UUID id : new HashSet<>(vault.guards)) removeEntity(id);
                removeEntity(vault.standId);
                removeEntity(vault.textId);
            }
        }
        caches.entrySet().removeIf(en -> en.getValue().sessionId.equals(session.id()));
    }

    /** Number of live reliquary guardians summoned in this session (0 if none). */
    public int activeGuardCount(DungeonSession session) {
        State state = states.get(session.id());
        if (state == null) return 0;
        int n = 0;
        for (Vault v : state.vaults) n += v.guards.size();
        return n;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private UUID sessionIdOf(Player p) {
        DungeonSession s = plugin.dungeonManager().sessionOf(p);
        return s == null ? null : s.id();
    }

    private Vault vaultByEntity(State state, UUID entityId) {
        for (Vault v : state.vaults) if (entityId.equals(v.standId)) return v;
        return null;
    }

    private void removeEntity(UUID id) {
        if (id == null) return;
        Entity e = plugin.getServer().getEntity(id);
        if (e != null) e.remove();
    }

    private void broadcast(State state, String msg) {
        for (UUID id : state.session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(Text.color(msg));
        }
    }

    private void playSound(State state, Sound sound, float pitch) {
        for (UUID id : state.session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, 1f, pitch);
        }
    }
}
