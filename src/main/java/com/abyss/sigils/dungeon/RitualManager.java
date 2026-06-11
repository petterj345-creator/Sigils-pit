package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.RandomPick;
import com.abyss.sigils.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.*;

/**
 * Runs the Altar of Souls ritual inside a dungeon instance.
 *
 * On session start (if the entry map carries the {@link MapMod#RITUAL} mod and
 * the template has a ritual configured) we spawn one armor stand + floating
 * text per configured altar. A player right-clicks an idle altar to start its
 * ritual, which summons the template's ritual mobs (these do NOT count toward
 * the boss kill counter). Killing them grants souls. Once every altar is
 * cleared, right-clicking any altar opens the soul shop.
 *
 * Per-session runtime state (altar entities, active mobs, rolled shop offers)
 * lives here and is dropped when the session ends; soul balances live on the
 * {@link DungeonSession} so the death handler can reach them.
 */
public final class RitualManager implements Listener {

    private final AbyssPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>(); // session id -> state
    private final Random rng = new Random();

    public RitualManager(AbyssPlugin plugin) { this.plugin = plugin; }

    enum Status { IDLE, ACTIVE, DONE }

    static final class Altar {
        final Location loc;
        UUID standId;
        UUID textId;
        Status status = Status.IDLE;
        Altar(Location loc) { this.loc = loc; }
    }

    /** One rolled shop offer for a player. Either an item ({@code source}) or cash ({@code cashAmount} &gt; 0). */
    public static final class Offer {
        public final RitualReward source;   // null for cash offers
        public final int price;             // soul cost
        public final int cashAmount;        // money paid out; 0 for item offers
        public boolean bought = false;
        Offer(RitualReward source, int price) { this.source = source; this.price = price; this.cashAmount = 0; }
        Offer(int cashAmount, int price) { this.source = null; this.price = price; this.cashAmount = cashAmount; }
        public boolean isCash() { return cashAmount > 0; }
    }

    static final class State {
        final DungeonSession session;
        final DungeonTemplate template;
        final List<Altar> altars = new ArrayList<>();
        final Set<UUID> activeMobs = new HashSet<>();
        int activeAltar = -1;
        int completed = 0;
        boolean shopUnlocked = false;
        boolean spawning = false; // staggered ritual spawn in progress; blocks premature clear
        final Map<UUID, List<Offer>> offers = new HashMap<>();
        State(DungeonSession s, DungeonTemplate t) { this.session = s; this.template = t; }
    }

    // ============================================================
    // Setup
    // ============================================================

    /** Spawn altars for a session if it should run a ritual. No-op otherwise. */
    public void init(DungeonSession session, DungeonTemplate template) {
        if (!session.hasMod(MapMod.RITUAL.id())) return;
        if (!template.hasRitual()) return;

        State state = new State(session, template);
        World w = session.world();
        // Spawn on a random subset of the ritual's anchors each run so the map
        // feels different every play. Anchors are the type-specific altars if any
        // were placed, otherwise the shared event-block pool. The admin sets a
        // min-max range; we roll a count in it (0 max = use all anchors).
        List<Location> chosen = RandomPick.someInRange(
                template.ritualSpawnAnchors(), template.ritualAltarsActiveMin(),
                template.ritualAltarsActive(), rng);
        for (Location raw : chosen) {
            Location loc = new Location(w, raw.getX(), raw.getY(), raw.getZ());
            Altar altar = new Altar(loc);
            spawnAltarEntities(altar);
            state.altars.add(altar);
        }
        if (state.altars.isEmpty()) return;
        states.put(session.id(), state);

        for (UUID id : session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(Text.color("&5&l✦ &dThis map carries the Altar of Souls. "
                    + "&7Find the &5" + state.altars.size() + " &7altars."));
        }
    }

    private void spawnAltarEntities(Altar altar) {
        // The stored location is "stand on top" of a block (block.y + 1), i.e.
        // exactly where a player would stand — so the armor stand spawns there.
        Location standLoc = altar.loc.clone();
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
        altar.standId = stand.getUniqueId();

        // Float the label above the stand's head (~2 blocks tall).
        Location textLoc = altar.loc.clone().add(0, 2.3, 0);
        TextDisplay text = w.spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(180, 30, 0, 40));
            td.setPersistent(false);
        });
        altar.textId = text.getUniqueId();
        updateAltarText(altar, false);
    }

    private void updateAltarText(Altar altar, boolean shopUnlocked) {
        if (altar.textId == null) return;
        Entity e = plugin.getServer().getEntity(altar.textId);
        if (!(e instanceof TextDisplay td)) return;
        String label;
        if (shopUnlocked) {
            label = "§6§l✦ Soul Shop ✦\n§7Right-click to open";
        } else label = switch (altar.status) {
            case IDLE   -> "§5§l✦ Altar of Souls ✦\n§7Right-click to awaken the altar";
            case ACTIVE -> "§c§l⚔ Altar Awakened ⚔\n§7Slay the summoned souls!";
            case DONE   -> "§a§l✔ Altar Cleared";
        };
        td.setText(label);
    }

    private void refreshAllAltarText(State state) {
        for (Altar a : state.altars) updateAltarText(a, state.shopUnlocked);
    }

    // ============================================================
    // Interaction
    // ============================================================

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent e) {
        // Right-clicking fires once per hand — only act on the main hand.
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        State state = states.get(sessionIdOf(p));
        if (state == null) return;
        Altar altar = altarByEntity(state, e.getRightClicked().getUniqueId());
        if (altar == null) return;
        e.setCancelled(true);
        handleAltarClick(p, state, altar);
    }

    /**
     * Stop players from putting armor onto / rotating the altar stands. We only
     * CANCEL here — the actual click is handled in {@link #onInteract} so the
     * ritual never starts twice from one right-click.
     */
    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        State state = states.get(sessionIdOf(e.getPlayer()));
        if (state == null) return;
        if (altarByEntity(state, e.getRightClicked().getUniqueId()) != null) {
            e.setCancelled(true);
        }
    }

    /** Keep the altar stands indestructible. */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        for (State state : states.values()) {
            if (altarByEntity(state, e.getEntity().getUniqueId()) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void handleAltarClick(Player p, State state, Altar altar) {
        if (state.shopUnlocked) {
            com.abyss.sigils.gui.RitualShopGUI.openFor(plugin, p, state.session);
            return;
        }
        if (state.activeAltar != -1) {
            p.sendMessage(Text.color("&cAn altar is already active. Clear it first."));
            return;
        }
        if (altar.status == Status.DONE) {
            p.sendMessage(Text.color("&7This altar is already spent."));
            return;
        }
        startRitual(state, altar);
    }

    /** Ticks between each ritual mob spawn. A short stagger so a player can't
     *  AoE/oneshot the whole batch in the single tick they appear (which raced
     *  the clear bookkeeping), and so the summon reads as a cascade. */
    private static final long SPAWN_INTERVAL_TICKS = 4L;

    private void startRitual(State state, Altar altar) {
        int idx = state.altars.indexOf(altar);
        altar.status = Status.ACTIVE;
        state.activeAltar = idx;

        // Flatten the configured mobs into one slot per individual spawn so we
        // can drip them out across ticks rather than all at once.
        List<MobEntry> queue = new ArrayList<>();
        for (MobEntry entry : state.template.ritualMobs())
            for (int i = 0; i < entry.count(); i++) queue.add(entry);

        if (queue.isEmpty()) {
            altar.status = Status.IDLE;
            state.activeAltar = -1;
            updateAltarText(altar, false);
            broadcast(state, "&cThe altar fizzled — no souls answered. Try again.");
            return;
        }

        updateAltarText(altar, false);
        broadcast(state, "&5&l✦ &dThe altar awakens! &7Slay the summoned souls.");
        playSoundToAll(state, Sound.BLOCK_BEACON_ACTIVATE, 1f);

        // While spawning, handleMobDeath must NOT finish the ritual even if
        // activeMobs momentarily empties (player oneshots an early spawn before
        // later ones land) — that would orphan the remaining spawns. The guard
        // is cleared in onSpawnComplete, which also does the final clear check.
        state.spawning = true;
        spawnRitualMobs(state, altar, queue);
    }

    /** Spawn the queued ritual mobs one per {@link #SPAWN_INTERVAL_TICKS}. */
    private void spawnRitualMobs(State state, Altar altar, List<MobEntry> queue) {
        final int[] index = {0};
        final int[] spawned = {0};
        final int[] failed = {0};
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                // The session ended (or the altar was reset) mid-spawn — stop.
                if (states.get(state.session.id()) != state || altar.status != Status.ACTIVE) {
                    state.spawning = false;
                    cancel();
                    return;
                }
                MobEntry entry = queue.get(index[0]++);
                // Spawn on a SAFE spot near the altar. The old code jittered the
                // altar X/Z but kept its Y, so mobs could spawn inside a wall and
                // die to suffocation within a second — which looked like "the
                // mobs don't spawn / something kills them instantly".
                Location loc = safeSpawn(altar.loc);
                var ent = plugin.dungeonManager().spawnMythic(entry.mythicId(), loc, entry.level());
                if (ent.isPresent()) {
                    Entity mob = ent.get();
                    // Keep ritual mobs from despawning. If one vanished without a
                    // death event, activeMobs would never drain — the altar would
                    // stay ACTIVE and the trash spawner stayed paused, locking
                    // the whole dungeon.
                    mob.setPersistent(true);
                    if (mob instanceof org.bukkit.entity.LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                        le.setFallDistance(0);
                    }
                    state.activeMobs.add(mob.getUniqueId());
                    spawned[0]++;
                } else failed[0]++;

                if (index[0] >= queue.size()) {
                    cancel();
                    onSpawnComplete(state, altar, spawned[0], failed[0]);
                }
            }
        }.runTaskTimer(plugin, 0L, SPAWN_INTERVAL_TICKS);
    }

    private void onSpawnComplete(State state, Altar altar, int spawned, int failed) {
        state.spawning = false;
        if (failed > 0) {
            plugin.getLogger().warning("Ritual: " + failed + " mob(s) failed to spawn (spawned "
                    + spawned + "). Check the MythicMob ids and that nothing caps entity spawns.");
        }

        // If nothing spawned, don't soft-lock the ritual (it would never clear
        // and trash would stay paused). Revert the altar so it can be retried.
        if (spawned == 0) {
            altar.status = Status.IDLE;
            state.activeAltar = -1;
            updateAltarText(altar, false);
            broadcast(state, "&cThe altar fizzled — no souls answered. Try again.");
            return;
        }

        // The player may have cleared every spawned mob before the last one
        // landed; handleMobDeath deferred finishing while spawning, so do it now.
        if (state.activeMobs.isEmpty() && state.activeAltar != -1) {
            finishRitual(state);
        }
    }

    // ============================================================
    // Mob deaths
    // ============================================================

    /**
     * Called from {@link DungeonManager#handleMythicMobDeath}. If the dead mob
     * belongs to an active ritual, awards souls to the killer and returns true
     * (so the caller skips the boss kill-counter for it).
     */
    public boolean handleMobDeath(DungeonSession session, UUID mobId, String mythicId, Player killer) {
        State state = states.get(session.id());
        if (state == null) return false;
        if (!state.activeMobs.remove(mobId)) return false;

        if (killer != null) {
            int souls = state.template.rollSoulsFor(mythicId);
            if (souls > 0) {
                // Tome of Mastery — Soul Harvest grants more souls per kill.
                int rank = plugin.skills().rank(killer.getUniqueId(),
                        com.abyss.sigils.skills.SkillType.SOUL_HARVEST);
                if (rank > 0) {
                    souls = (int) Math.round(souls
                            * com.abyss.sigils.skills.SkillType.SOUL_HARVEST.multiplierAt(rank));
                }
                session.addSouls(killer.getUniqueId(), souls);
                killer.sendMessage(Text.color("&b+ " + souls + " souls &7(total: &b"
                        + session.soulsOf(killer.getUniqueId()) + "&7)"));
            }
        }

        if (state.activeMobs.isEmpty() && !state.spawning && state.activeAltar != -1) {
            finishRitual(state);
        }
        return true;
    }

    private void finishRitual(State state) {
        Altar altar = state.altars.get(state.activeAltar);
        altar.status = Status.DONE;
        state.activeAltar = -1;
        state.completed++;
        updateAltarText(altar, false);

        if (state.completed >= state.altars.size()) {
            state.shopUnlocked = true;
            refreshAllAltarText(state);
            broadcast(state, "&6&l✦ All altars cleared! &eRight-click an altar to open the soul shop.");
            playSoundToAll(state, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        } else {
            broadcast(state, "&aAltar cleared! &7(" + state.completed + "/" + state.altars.size()
                    + ") &7Activate the next altar.");
            playSoundToAll(state, Sound.BLOCK_BEACON_DEACTIVATE, 1f);
        }
    }

    // ============================================================
    // Shop offers
    // ============================================================

    /** Get (rolling once) the shop offers for a player in this session. */
    public List<Offer> offersFor(DungeonSession session, Player p) {
        State state = states.get(session.id());
        if (state == null) return List.of();
        return state.offers.computeIfAbsent(p.getUniqueId(), k -> rollOffers(state));
    }

    /** True if a ritual is currently in progress in this session. */
    public boolean isRitualActive(DungeonSession session) {
        State state = states.get(session.id());
        return state != null && state.activeAltar != -1;
    }

    /**
     * Number of live ritual mobs summoned in this session (0 if none). The trash
     * spawner adds this on top of its concurrent-mob cap (the cap is raised by
     * the live ritual count for the ritual's duration) so trash keeps its full
     * budget and the summoned mobs always have room.
     */
    public int activeMobCount(DungeonSession session) {
        State state = states.get(session.id());
        return state == null ? 0 : state.activeMobs.size();
    }

    /** Force a fresh roll of a player's shop offers (used by the reroll button). */
    public void reroll(DungeonSession session, Player p) {
        State state = states.get(session.id());
        if (state == null) return;
        state.offers.put(p.getUniqueId(), rollOffers(state));
    }

    private List<Offer> rollOffers(State state) {
        DungeonTemplate t = state.template;
        List<RitualReward> pool = new ArrayList<>(t.ritualRewardPool());
        Collections.shuffle(pool, rng);

        int min = t.ritualItemsMin();
        int max = t.ritualItemsMax();
        int count = min + (max > min ? rng.nextInt(max - min + 1) : 0);
        count = Math.min(count, pool.size());

        List<Offer> offers = new ArrayList<>(count + 1);

        // Optional cash offer first.
        if (t.ritualCashEnabled() && t.ritualCashMoneyMax() > 0) {
            int money = rollInt((int) Math.round(t.ritualCashMoneyMin()), (int) Math.round(t.ritualCashMoneyMax()));
            int price = rollInt(t.ritualCashPriceMin(), t.ritualCashPriceMax());
            if (money > 0) offers.add(new Offer(money, Math.max(0, price)));
        }

        for (int i = 0; i < count; i++) {
            RitualReward r = pool.get(i);
            int lo = r.inheritsPrice() ? t.ritualPriceMin() : r.priceMin();
            int hi = r.inheritsPrice() ? t.ritualPriceMax() : r.priceMax();
            offers.add(new Offer(r, Math.max(0, rollInt(lo, hi))));
        }
        return offers;
    }

    private int rollInt(int lo, int hi) {
        if (hi <= lo) return lo;
        return lo + rng.nextInt(hi - lo + 1);
    }

    // ============================================================
    // Cleanup
    // ============================================================

    public void cleanup(DungeonSession session) {
        State state = states.remove(session.id());
        if (state == null) return;
        for (Altar a : state.altars) {
            removeEntity(a.standId);
            removeEntity(a.textId);
        }
    }

    private void removeEntity(UUID id) {
        if (id == null) return;
        Entity e = plugin.getServer().getEntity(id);
        if (e != null) e.remove();
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * A safe spawn point near the altar: jitter horizontally, then snap to a
     * spot where the mob won't suffocate or fall — feet and head clear, solid
     * ground underneath. Falls back to the altar itself, which is always valid
     * (an admin stood on it to place it). This stops ritual mobs spawning
     * inside walls/over holes and dying instantly.
     */
    private Location safeSpawn(Location altar) {
        World w = altar.getWorld();
        if (w == null) return altar.clone();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = (int) Math.floor(altar.getX() + rng.nextDouble() * 4 - 2);
            int z = (int) Math.floor(altar.getZ() + rng.nextDouble() * 4 - 2);
            int baseY = altar.getBlockY();
            for (int dy = 0; dy <= 3; dy++) {
                if (standable(w, x, baseY + dy, z)) return new Location(w, x + 0.5, baseY + dy, z + 0.5);
                if (standable(w, x, baseY - dy, z)) return new Location(w, x + 0.5, baseY - dy, z + 0.5);
            }
        }
        return altar.clone();
    }

    /** Feet + head passable and solid ground below — i.e. a mob can stand here. */
    private boolean standable(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).isPassable()
                && w.getBlockAt(x, y + 1, z).isPassable()
                && w.getBlockAt(x, y - 1, z).getType().isSolid();
    }

    private UUID sessionIdOf(Player p) {
        DungeonSession s = plugin.dungeonManager().sessionOf(p);
        return s == null ? null : s.id();
    }

    private Altar altarByEntity(State state, UUID entityId) {
        for (Altar a : state.altars) {
            if (entityId.equals(a.standId)) return a;
        }
        return null;
    }

    private void broadcast(State state, String msg) {
        for (UUID id : state.session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(Text.color(msg));
        }
    }

    private void playSoundToAll(State state, Sound sound, float pitch) {
        for (UUID id : state.session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, 1f, pitch);
        }
    }
}
