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
        // Place many altars in the editor, but only spawn a random subset each
        // run so the map feels different every play. 0 = use all placed altars.
        List<Location> chosen = RandomPick.some(
                template.ritualAltars(), template.ritualAltarsActive(), rng);
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
            case IDLE   -> "§5§l✦ Altar of Souls ✦\n§7Right-click to begin the ritual";
            case ACTIVE -> "§c§l⚔ Ritual Active ⚔\n§7Slay the summoned souls!";
            case DONE   -> "§a§l✔ Ritual Cleared";
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
            p.sendMessage(Text.color("&cA ritual is already active. Clear it first."));
            return;
        }
        if (altar.status == Status.DONE) {
            p.sendMessage(Text.color("&7This altar is already spent."));
            return;
        }
        startRitual(state, altar);
    }

    private void startRitual(State state, Altar altar) {
        int idx = state.altars.indexOf(altar);
        altar.status = Status.ACTIVE;
        state.activeAltar = idx;

        DungeonTemplate t = state.template;
        int spawned = 0, failed = 0;
        for (MobEntry entry : t.ritualMobs()) {
            for (int i = 0; i < entry.count(); i++) {
                Location loc = altar.loc.clone().add(
                        rng.nextDouble() * 4 - 2, 0, rng.nextDouble() * 4 - 2);
                var ent = plugin.dungeonManager().spawnMythic(entry.mythicId(), loc, entry.level());
                if (ent.isPresent()) { state.activeMobs.add(ent.get().getUniqueId()); spawned++; }
                else failed++;
            }
        }
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
            broadcast(state, "&cThe ritual fizzled — no souls answered. Try again.");
            return;
        }

        updateAltarText(altar, false);
        broadcast(state, "&5&l✦ &dA ritual has begun! &7Slay the summoned souls.");
        playSoundToAll(state, Sound.BLOCK_BEACON_ACTIVATE, 1f);
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
                session.addSouls(killer.getUniqueId(), souls);
                killer.sendMessage(Text.color("&b+ " + souls + " souls &7(total: &b"
                        + session.soulsOf(killer.getUniqueId()) + "&7)"));
            }
        }

        if (state.activeMobs.isEmpty() && state.activeAltar != -1) {
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
            broadcast(state, "&6&l✦ All rituals cleared! &eRight-click an altar to open the soul shop.");
            playSoundToAll(state, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        } else {
            broadcast(state, "&aRitual cleared! &7(" + state.completed + "/" + state.altars.size()
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
