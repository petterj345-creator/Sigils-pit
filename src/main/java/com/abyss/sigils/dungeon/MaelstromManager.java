package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Runs the Maelstrom (breach-style) event inside a dungeon instance.
 *
 * On session start (if the entry map carries {@link MapMod#MAELSTROM} and the
 * template has a maelstrom configured) we spawn a rift marker per chosen
 * center. A player right-clicks an idle rift to tear it open: a churning circle
 * spawns mobs at a tickrate (capped so it stays clearable) for a fixed
 * duration, then collapses — deleting any mobs it still owns and dropping a
 * loot cache whose contents scale with how many were killed during the rift.
 *
 * Per-session runtime state lives here and is dropped when the session ends.
 */
public final class MaelstromManager implements Listener {

    private final AbyssPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();        // session id -> state
    private final Map<Location, Cache> caches = new HashMap<>();    // chest block -> loot cache
    private final Map<UUID, Location> viewing = new HashMap<>();    // viewer -> cache block they have open
    private final Random rng = new Random();

    public MaelstromManager(AbyssPlugin plugin) { this.plugin = plugin; }

    enum Status { IDLE, ACTIVE, DONE }

    static final class Rift {
        final Location center;
        UUID standId;
        UUID textId;
        Status status = Status.IDLE;
        int kills = 0;
        int elapsedTicks = 0;
        final Set<UUID> mobs = new HashSet<>();
        BukkitTask task;
        Rift(Location center) { this.center = center; }
    }

    static final class State {
        final DungeonSession session;
        final DungeonTemplate template;
        final List<Rift> rifts = new ArrayList<>();
        State(DungeonSession s, DungeonTemplate t) { this.session = s; this.template = t; }
    }

    /** A dropped loot cache: rolled per-player, gated by the rift's total kills. */
    static final class Cache {
        final UUID sessionId;
        final int kills;
        final Map<UUID, Inventory> rolled = new HashMap<>();
        Cache(UUID sessionId, int kills) { this.sessionId = sessionId; this.kills = kills; }
    }

    // ============================================================
    // Setup
    // ============================================================

    /** Spawn rift markers for a session if it should run a maelstrom. No-op otherwise. */
    public void init(DungeonSession session, DungeonTemplate template) {
        if (!session.hasMod(MapMod.MAELSTROM.id())) return;
        if (!template.hasMaelstrom()) return;

        State state = new State(session, template);
        World w = session.world();

        // Place many rifts in the editor, open a random subset each run so the
        // map feels different every play. 0 = use all placed rifts.
        List<Location> all = template.maelstromCenters();
        int active = template.maelstromActive();
        List<Location> chosen;
        if (active <= 0 || active >= all.size()) {
            chosen = new ArrayList<>(all);
        } else {
            List<Location> copy = new ArrayList<>(all);
            Collections.shuffle(copy, rng);
            chosen = new ArrayList<>(copy.subList(0, active));
        }

        for (Location raw : chosen) {
            Location loc = new Location(w, raw.getX(), raw.getY(), raw.getZ());
            Rift rift = new Rift(loc);
            spawnMarker(rift);
            state.rifts.add(rift);
        }
        if (state.rifts.isEmpty()) return;
        states.put(session.id(), state);

        for (UUID id : session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(Text.color("&3&l✦ &bThis map carries a Maelstrom. "
                    + "&7Find the &3" + state.rifts.size() + " &7rift" + (state.rifts.size() == 1 ? "" : "s")
                    + " &7and tear " + (state.rifts.size() == 1 ? "it" : "them") + " open."));
        }
    }

    private void spawnMarker(Rift rift) {
        Location standLoc = rift.center.clone();
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
        rift.standId = stand.getUniqueId();

        Location textLoc = rift.center.clone().add(0, 2.3, 0);
        TextDisplay text = w.spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(180, 0, 30, 45));
            td.setPersistent(false);
        });
        rift.textId = text.getUniqueId();
        updateText(rift);
    }

    private void updateText(Rift rift) {
        if (rift.textId == null) return;
        Entity e = plugin.getServer().getEntity(rift.textId);
        if (!(e instanceof TextDisplay td)) return;
        String label = switch (rift.status) {
            case IDLE   -> "§3§l✦ Maelstrom ✦\n§7Right-click to tear it open";
            case ACTIVE -> "§b§l⚡ Maelstrom ⚡\n§7Kills: §f" + rift.kills;
            case DONE   -> "§8§lThe rift has collapsed";
        };
        td.setText(label);
    }

    // ============================================================
    // Interaction — opening a rift
    // ============================================================

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        State state = states.get(sessionIdOf(p));
        if (state == null) return;
        Rift rift = riftByEntity(state, e.getRightClicked().getUniqueId());
        if (rift == null) return;
        e.setCancelled(true);
        if (rift.status == Status.IDLE) {
            open(state, rift);
        } else if (rift.status == Status.ACTIVE) {
            p.sendMessage(Text.color("&7This maelstrom is already churning."));
        } else {
            p.sendMessage(Text.color("&7This rift has already collapsed."));
        }
    }

    /** Keep players from rotating / equipping the rift stands. */
    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        State state = states.get(sessionIdOf(e.getPlayer()));
        if (state == null) return;
        if (riftByEntity(state, e.getRightClicked().getUniqueId()) != null) e.setCancelled(true);
    }

    /** Keep the rift stands indestructible. */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        for (State state : states.values()) {
            if (riftByEntity(state, e.getEntity().getUniqueId()) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void open(State state, Rift rift) {
        DungeonTemplate t = state.template;
        List<MobEntry> pool = new ArrayList<>(t.maelstromMobs());
        if (t.maelstromUseTrash()) pool.addAll(t.defaultTrashMobs());
        if (pool.isEmpty()) {
            broadcast(state, "&cThe rift sputters — no mobs are configured.");
            return;
        }

        rift.status = Status.ACTIVE;
        rift.elapsedTicks = 0;
        updateText(rift);
        broadcast(state, "&3&l✦ &bA Maelstrom tears open! &7Slay everything that pours out.");
        playSound(state, Sound.BLOCK_BEACON_ACTIVATE, 0.6f);

        final int interval = Math.max(1, t.maelstromSpawnIntervalTicks());
        final int durationTicks = t.maelstromDurationSeconds() * 20;
        final int perPulse = t.maelstromMobsPerPulse();
        final int maxAlive = t.maelstromMaxAlive();
        final double radius = t.maelstromRadius();

        rift.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            drawRing(rift, radius);
            for (int i = 0; i < perPulse && rift.mobs.size() < maxAlive; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double dist = radius * (0.5 + rng.nextDouble() * 0.5);
                Location ring = rift.center.clone().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
                // Snap to a safe, standable spot so mobs don't spawn inside a
                // wall (suffocate) or over a hole (fall) and die instantly.
                Location loc = safeSpawn(ring, rift.center);
                MobEntry me = pool.get(rng.nextInt(pool.size()));
                plugin.dungeonManager().spawnMythic(me.mythicId(), loc, me.level()).ifPresent(ent -> {
                    ent.setPersistent(true);
                    if (ent instanceof LivingEntity le) { le.setRemoveWhenFarAway(false); le.setFallDistance(0); }
                    rift.mobs.add(ent.getUniqueId());
                });
            }
            rift.elapsedTicks += interval;
            if (rift.elapsedTicks >= durationTicks) collapse(state, rift);
        }, 0L, interval);
    }

    /**
     * Snap a candidate spawn to a standable spot (feet + head clear, solid
     * ground under) near the target, searching a small vertical window. Falls
     * back to the rift center, which is always valid. Stops rift mobs spawning
     * inside walls/over holes and dying the instant they appear.
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

    private void drawRing(Rift rift, double radius) {
        World w = rift.center.getWorld();
        if (w == null) return;
        int points = 48;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 / points) * i;
            Location p = rift.center.clone().add(Math.cos(a) * radius, 0.3, Math.sin(a) * radius);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0);
        }
        w.spawnParticle(Particle.PORTAL, rift.center.clone().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.4);
    }

    // ============================================================
    // Mob deaths — count kills toward the rift
    // ============================================================

    /**
     * Called from {@link DungeonManager#handleMythicMobDeath}. If the dead mob
     * belongs to an active rift, counts it and returns true so the caller skips
     * the boss kill-counter for it (rift mobs don't advance the dungeon).
     */
    public boolean handleMobDeath(DungeonSession session, UUID mobId) {
        State state = states.get(session.id());
        if (state == null) return false;
        for (Rift rift : state.rifts) {
            if (rift.mobs.remove(mobId)) {
                rift.kills++;
                if (rift.status == Status.ACTIVE) updateText(rift);
                return true;
            }
        }
        return false;
    }

    private void collapse(State state, Rift rift) {
        if (rift.status == Status.DONE) return;
        rift.status = Status.DONE;
        if (rift.task != null) { rift.task.cancel(); rift.task = null; }

        // Delete the mobs the rift still owns — just like the real mechanic.
        for (UUID id : new HashSet<>(rift.mobs)) removeEntity(id);
        rift.mobs.clear();

        // Close the rift visually.
        removeEntity(rift.standId);
        removeEntity(rift.textId);
        rift.standId = null;
        rift.textId = null;

        World w = rift.center.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, rift.center.clone().add(0, 1, 0), 1);
        }

        placeCache(state, rift);
        broadcast(state, "&3&l✦ &bThe Maelstrom collapses! &7" + rift.kills
                + " slain — &eclaim the cache it left behind.");
        playSound(state, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f);
    }

    private void placeCache(State state, Rift rift) {
        World w = rift.center.getWorld();
        if (w == null) return;
        // Rift center is stored "stand on top", so the floor block is one below.
        Block floor = w.getBlockAt(rift.center.getBlockX(), rift.center.getBlockY() - 1, rift.center.getBlockZ());
        floor.setType(Material.CHEST);
        caches.put(floor.getLocation(), new Cache(state.session.id(), rift.kills));
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
        if (session == null) { p.sendMessage(Text.color("&cThis cache's dungeon is no longer active.")); return; }
        if (!session.players().contains(p.getUniqueId())) {
            p.sendMessage(Text.color("&cThis cache isn't for you."));
            return;
        }

        Inventory inv = cache.rolled.get(p.getUniqueId());
        if (inv == null) {
            DungeonTemplate t = plugin.templates().get(session.templateName());
            if (t == null) { p.sendMessage(Text.color("&cTemplate missing.")); return; }
            inv = rollCache(t, cache.kills);
            cache.rolled.put(p.getUniqueId(), inv);
            boolean empty = true;
            for (ItemStack s : inv.getContents()) if (s != null && s.getType() != Material.AIR) { empty = false; break; }
            if (empty) p.sendMessage(Text.color("&7The eye of the storm holds nothing for you this time."));
        }

        viewing.put(p.getUniqueId(), b.getLocation());
        p.openInventory(inv);
    }

    private Inventory rollCache(DungeonTemplate t, int kills) {
        List<MaelstromLoot> candidates = new ArrayList<>();
        for (MaelstromLoot l : t.maelstromLoot()) {
            if (l.itemStack() == null) continue;
            if (kills < l.minKills()) continue;                 // gated by kills
            if (rng.nextDouble() * 100.0 < l.chancePercent()) candidates.add(l);
        }
        Collections.shuffle(candidates, rng);
        int cap = Math.min(candidates.size(), Math.max(0, t.maelstromMaxLootItems()));
        Inventory inv = Bukkit.createInventory(null, 27, Text.color("&3&lMaelstrom Cache"));
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

    /** Block players from inserting items into a maelstrom cache. */
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
            for (Rift rift : state.rifts) {
                if (rift.task != null) rift.task.cancel();
                for (UUID id : new HashSet<>(rift.mobs)) removeEntity(id);
                removeEntity(rift.standId);
                removeEntity(rift.textId);
            }
        }
        caches.entrySet().removeIf(en -> en.getValue().sessionId.equals(session.id()));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private UUID sessionIdOf(Player p) {
        DungeonSession s = plugin.dungeonManager().sessionOf(p);
        return s == null ? null : s.id();
    }

    private Rift riftByEntity(State state, UUID entityId) {
        for (Rift r : state.rifts) if (entityId.equals(r.standId)) return r;
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
