package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.skills.SkillType;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Runs The Sundering (PoE Expedition-style) event inside a dungeon instance.
 *
 * On session start (if the entry map carries {@link MapMod#SUNDERING} and the
 * template has an event-trash pool) we bury a hoard at one shared event anchor:
 * a field of buried monster packs studded with {@link Remnant} monoliths, plus a
 * central Detonator. Every player is handed a stack of {@link SeismicCharge}s.
 *
 * Players plant charges on the ground — each must be within {@code chain-range}
 * of the site centre or another charge, so a limited supply only reaches part of
 * the field. Striking the Detonator unearths every buried pack and Remnant inside
 * any charge's blast radius: the packs spawn live mobs (from the shared event-
 * trash pool), and triggered Remnants buff all of them while multiplying the
 * Shards they drop and widening the dealer's stock. Slay everything and a vendor
 * opens in the crater where you spend those Shards. Greedy chains = a harder
 * fight for a bigger payout.
 *
 * Per-session runtime state lives here and is dropped when the session ends.
 */
public final class SunderingManager implements Listener {

    private final AbyssPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();        // session id -> state
    private final Map<Location, UUID> vendors = new HashMap<>();    // vendor block -> session id
    private final Random rng = new Random();

    public SunderingManager(AbyssPlugin plugin) { this.plugin = plugin; }

    /**
     * A buried Remnant's twin effect: it buffs every unearthed mob (attribute
     * multipliers, applied next tick like tier scaling) AND it sweetens the
     * payout (a Shard multiplier on its mobs + extra cache items). Reaching more
     * Remnants with your blast chain is the core greed decision.
     */
    enum Remnant {
        //        display     colour  banner                 glow          hp   dmg  spd  shardMult lootBonus
        BULWARK ("Bulwark",  "&7&l", Material.WHITE_BANNER,  Color.WHITE,  2.2, 1.0, 1.0, 1.6, 1),
        SAVAGE  ("Savage",   "&c&l", Material.RED_BANNER,    Color.RED,    1.0, 2.0, 1.0, 1.6, 1),
        FRENZIED("Frenzied", "&e&l", Material.YELLOW_BANNER, Color.YELLOW, 1.0, 1.2, 1.6, 1.5, 1),
        WARLORD ("Warlord",  "&5&l", Material.PURPLE_BANNER, Color.PURPLE, 1.8, 1.6, 1.2, 2.2, 2);

        final String display;
        final String colour;
        final Material banner;
        final Color glow;
        final double hpMult;
        final double dmgMult;
        final double spdMult;
        final double shardMult;
        final int lootBonus;

        Remnant(String display, String colour, Material banner, Color glow, double hpMult,
                double dmgMult, double spdMult, double shardMult, int lootBonus) {
            this.display = display;
            this.colour = colour;
            this.banner = banner;
            this.glow = glow;
            this.hpMult = hpMult;
            this.dmgMult = dmgMult;
            this.spdMult = spdMult;
            this.shardMult = shardMult;
            this.lootBonus = lootBonus;
        }
    }

    static final class RemnantNode {
        final Location loc;
        final Remnant type;
        UUID displayId;   // glowing banner BlockDisplay
        UUID textId;      // floating name above it
        boolean triggered = false;
        RemnantNode(Location loc, Remnant type) { this.loc = loc; this.type = type; }
    }

    static final class State {
        final DungeonSession session;
        final DungeonTemplate template;
        Location centre;
        final List<Location> buried = new ArrayList<>();         // buried pack positions
        final List<RemnantNode> remnants = new ArrayList<>();
        final List<Location> charges = new ArrayList<>();        // planted charge positions
        final List<UUID> chargeMarkers = new ArrayList<>();      // visual markers to clean up
        final List<UUID> buriedMarkers = new ArrayList<>();      // glowing banner per buried pack
        UUID detonatorDisplayId;     // glowing banner BlockDisplay
        UUID detonatorTextId;        // floating name above it
        UUID detonatorInteractionId; // invisible click hitbox (right-click to detonate)
        boolean detonated = false;
        final Set<UUID> mobs = new HashSet<>();                  // live unearthed mobs
        // Shards are physical items now (SunderingShard) — earned into and spent
        // from the player's inventory, not a per-session balance.
        final Map<UUID, List<VendorOffer>> vendorOffers = new HashMap<>(); // per-player rolled wares
        UUID vendorLabelId;                                      // floating label over the vendor crate
        double shardMult = 1.0;                                  // product of triggered Remnant mults
        int lootBonus = 0;                                       // sum of triggered Remnant loot bonuses
        BukkitTask watchdog;
        State(DungeonSession s, DungeonTemplate t) { this.session = s; this.template = t; }
    }

    /** One purchasable item at the Sundered Hoard vendor, priced in Shards. */
    static final class VendorOffer {
        final MaelstromLoot source;
        final int count;
        final int price;
        boolean bought = false;
        VendorOffer(MaelstromLoot source, int count, int price) {
            this.source = source;
            this.count = count;
            this.price = price;
        }
    }

    // ============================================================
    // Config
    // ============================================================

    // Most tuning is per-template (edited in the Sundering editor); only the
    // Shard-vendor economy knobs stay global in config.yml.
    private int cfgVendorPriceMin(){ return plugin.getConfig().getInt("sundering.vendor-price-min", 8); }
    private int cfgVendorPriceMax(){ return plugin.getConfig().getInt("sundering.vendor-price-max", 40); }
    private int cfgVendorReroll()  { return plugin.getConfig().getInt("sundering.vendor-reroll-cost", 12); }

    /** A template can run The Sundering if it has any event-trash to unearth. */
    public boolean canRun(DungeonTemplate t) {
        return t != null && !t.eventTrashMobs().isEmpty();
    }

    // ============================================================
    // Setup
    // ============================================================

    /** Bury a hoard for a session if it should run a Sundering. No-op otherwise. */
    public void init(DungeonSession session, DungeonTemplate template) {
        if (!session.hasMod(MapMod.SUNDERING.id())) return;
        if (!canRun(template)) return;

        // Claim a single shared event anchor as the dig-site centre.
        List<Location> chosen = com.abyss.sigils.util.RandomPick.someInRange(
                session.unclaimedAnchors(template.eventBlocks()), 1, 1, rng);
        if (chosen.isEmpty()) return;
        session.claimAnchors(chosen);

        World w = session.world();
        Location raw = chosen.get(0);
        Location centre = new Location(w, raw.getX(), raw.getY(), raw.getZ());

        State state = new State(session, template);
        state.centre = centre;

        // Bury the field: one guaranteed pack at the centre so a single charge
        // near the Detonator always hits something, plus scattered packs/Remnants.
        // Each buried pack gets a glowing orange banner planted in the ground so
        // players can see where to chain their charges.
        state.buried.add(centre.clone());
        int packs = Math.max(1, template.sunderingBuriedPacks());
        for (int i = 1; i < packs; i++) {
            Location spot = scatter(w, centre, template.sunderingFieldRadius());
            if (spot != null) state.buried.add(spot);
        }
        for (Location b : state.buried) {
            state.buriedMarkers.add(spawnBanner(b, Material.ORANGE_BANNER, Color.ORANGE));
        }
        int remnants = Math.max(0, template.sunderingRemnants());
        for (int i = 0; i < remnants; i++) {
            Location spot = scatter(w, centre, template.sunderingFieldRadius());
            if (spot == null) continue;
            RemnantNode node = new RemnantNode(spot, Remnant.values()[rng.nextInt(Remnant.values().length)]);
            spawnRemnantMarker(node);
            state.remnants.add(node);
        }

        spawnDetonator(state);
        states.put(session.id(), state);

        for (UUID id : session.players()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null) continue;
            giveCharges(p, session, template);
            p.sendMessage(Text.color("&c&l✦ &7This map carries &cThe Sundering&7. Find the buried "
                    + "field, plant your &cSeismic Charges &7and strike the &cDetonator&7."));
        }
    }

    /** Hand a player their charge supply (base + Excavator skill ranks). */
    private void giveCharges(Player p, DungeonSession session, DungeonTemplate t) {
        int bonus = SkillType.EXCAVATOR.flatAt(plugin.skills().rank(p.getUniqueId(), SkillType.EXCAVATOR));
        int amount = Math.max(1, t.sunderingCharges() + bonus);
        var overflow = p.getInventory().addItem(SeismicCharge.create(session.id(), amount));
        for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
    }

    private void spawnDetonator(State state) {
        state.detonatorDisplayId = spawnBanner(state.centre, Material.RED_BANNER, Color.RED);
        state.detonatorInteractionId = spawnInteraction(state.centre);

        World w = state.centre.getWorld();
        Location textLoc = state.centre.clone().add(0, 2.3, 0);
        TextDisplay text = w.spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(180, 45, 0, 0));
            td.setPersistent(false);
        });
        state.detonatorTextId = text.getUniqueId();
        updateDetonatorText(state);
    }

    private void updateDetonatorText(State state) {
        if (state.detonatorTextId == null) return;
        Entity e = plugin.getServer().getEntity(state.detonatorTextId);
        if (!(e instanceof TextDisplay td)) return;
        td.setText(state.detonated
                ? "§c§l✦ Detonated ✦\n§7Slay what you unearthed"
                : "§c§l✦ Detonator ✦\n§7Plant charges, then right-click\n§7Charges placed: §f" + state.charges.size());
    }

    private void spawnRemnantMarker(RemnantNode node) {
        node.displayId = spawnBanner(node.loc, node.type.banner, node.type.glow);

        World w = node.loc.getWorld();
        Location textLoc = node.loc.clone().add(0, 2.3, 0);
        TextDisplay text = w.spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(160, 20, 20, 20));
            td.setPersistent(false);
            td.setText(Text.color(node.type.colour + "❖ Remnant: " + node.type.display
                    + "\n&7Within a blast = buffed mobs, more loot"));
        });
        node.textId = text.getUniqueId();
    }

    /**
     * Plant a glowing banner block-display in the ground at a marker. A
     * BlockDisplay renders from the block corner, so we drop to the block grid —
     * the banner stands in the floor block rather than floating. Returns its id.
     */
    private UUID spawnBanner(Location at, Material banner, Color glow) {
        World w = at.getWorld();
        Location loc = new Location(w, at.getBlockX(), at.getBlockY(), at.getBlockZ());
        BlockDisplay d = w.spawn(loc, BlockDisplay.class, bd -> {
            bd.setBlock(banner.createBlockData());
            bd.setGlowing(true);
            bd.setGlowColorOverride(glow);
            bd.setBrightness(new Display.Brightness(15, 15));
            bd.setPersistent(false);
        });
        return d.getUniqueId();
    }

    /** An invisible right-click hitbox over a marker (used for the Detonator). */
    private UUID spawnInteraction(Location at) {
        World w = at.getWorld();
        Interaction in = w.spawn(at.clone(), Interaction.class, i -> {
            i.setInteractionWidth(1.4f);
            i.setInteractionHeight(2.2f);
            i.setResponsive(true);
            i.setPersistent(false);
        });
        return in.getUniqueId();
    }

    // ============================================================
    // Planting charges
    // ============================================================

    @EventHandler
    public void onPlant(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack used = e.getItem();
        if (!SeismicCharge.isCharge(used)) return;

        Player p = e.getPlayer();
        e.setCancelled(true); // never let TNT actually place / interact

        DungeonSession session = plugin.dungeonManager().sessionOf(p);
        if (session == null || !SeismicCharge.isChargeFor(used, session.id())) {
            p.sendMessage(Text.color("&cThis charge is spent — it belongs to another dungeon."));
            return;
        }
        State state = states.get(session.id());
        if (state == null) return;
        if (state.detonated) {
            p.sendMessage(Text.color("&7The Sundering has already been detonated."));
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Plant on top of the clicked block (the spot a mob would stand on).
        Location spot = clicked.getLocation().add(0.5, 1, 0.5);
        if (!withinChain(state, spot)) {
            p.sendMessage(Text.color("&cToo far from the chain — plant within "
                    + state.template.sunderingChainRange() + " blocks of the Detonator or another charge."));
            return;
        }

        consumeOne(p, used);
        state.charges.add(spot);
        spawnChargeMarker(state, spot);
        previewBlast(spot, state.template.sunderingBlastRadius());
        updateDetonatorText(state);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
        p.sendMessage(Text.color("&cCharge planted &7(&f" + state.charges.size() + "&7 in the chain)."));
    }

    /** A charge is valid if it's within chain-range of the centre or any planted charge. */
    private boolean withinChain(State state, Location spot) {
        double range = state.template.sunderingChainRange();
        if (horizDist(spot, state.centre) <= range) return true;
        for (Location c : state.charges) if (horizDist(spot, c) <= range) return true;
        return false;
    }

    private void consumeOne(Player p, ItemStack used) {
        // Charges are only ever used from the main hand (gated in onPlant).
        if (used.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else used.setAmount(used.getAmount() - 1);
        p.updateInventory();
    }

    private void spawnChargeMarker(State state, Location spot) {
        World w = spot.getWorld();
        if (w == null) return;
        TextDisplay text = w.spawn(spot.clone().add(0, 0.4, 0), TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(160, 45, 0, 0));
            td.setPersistent(false);
            td.setText(Text.color("&c&l✦"));
        });
        state.chargeMarkers.add(text.getUniqueId());
    }

    /** One-shot ring showing a planted charge's blast radius. */
    private void previewBlast(Location centre, double r) {
        World w = centre.getWorld();
        if (w == null) return;
        int points = 32;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 / points) * i;
            Location p = centre.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
            w.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0);
        }
    }

    // ============================================================
    // Detonation
    // ============================================================

    @EventHandler
    public void onInteractDetonator(PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        DungeonSession session = plugin.dungeonManager().sessionOf(p);
        if (session == null) return;
        State state = states.get(session.id());
        if (state == null) return;
        if (!e.getRightClicked().getUniqueId().equals(state.detonatorInteractionId)) return;
        e.setCancelled(true);

        if (state.detonated) {
            p.sendMessage(Text.color("&7Already detonated — slay what you unearthed."));
            return;
        }
        if (state.charges.isEmpty()) {
            p.sendMessage(Text.color("&cPlant at least one Seismic Charge first."));
            return;
        }
        detonate(state);
    }

    /** Keep the Sundering's banner / interaction markers indestructible. */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        UUID id = e.getEntity().getUniqueId();
        for (State state : states.values()) {
            if (isOurMarker(state, id)) { e.setCancelled(true); return; }
        }
    }

    private boolean isOurMarker(State state, UUID id) {
        if (id.equals(state.detonatorDisplayId) || id.equals(state.detonatorInteractionId)) return true;
        if (state.buriedMarkers.contains(id)) return true;
        for (RemnantNode n : state.remnants) if (id.equals(n.displayId)) return true;
        return false;
    }

    private void detonate(State state) {
        state.detonated = true;
        double blast = state.template.sunderingBlastRadius();

        // Which Remnants did the chain reach? Accumulate their buffs/payout.
        List<Remnant> triggered = new ArrayList<>();
        state.shardMult = 1.0;
        state.lootBonus = 0;
        for (RemnantNode n : state.remnants) {
            boolean hit = false;
            for (Location c : state.charges) if (n.loc.distance(c) <= blast) { hit = true; break; }
            n.triggered = hit;
            if (hit) {
                triggered.add(n.type);
                state.shardMult *= n.type.shardMult;
                state.lootBonus += n.type.lootBonus;
            }
        }

        // Which buried packs did the chain reach? Unearth them into live mobs.
        World w = state.centre.getWorld();
        int unearthed = 0;
        for (Location b : state.buried) {
            boolean hit = false;
            for (Location c : state.charges) if (b.distance(c) <= blast) { hit = true; break; }
            if (!hit) continue;
            if (w != null) w.spawnParticle(Particle.EXPLOSION_EMITTER, b.clone().add(0, 0.5, 0), 1);
            int min = state.template.sunderingMobsPerPackMin();
            int max = state.template.sunderingMobsPerPackMax();
            int count = min + (max > min ? rng.nextInt(max - min + 1) : 0);
            for (int i = 0; i < count && state.mobs.size() < state.template.sunderingMaxAlive(); i++) {
                List<MobEntry> pick = state.template.rollEventTrash(1);
                if (pick.isEmpty()) break;
                MobEntry me = pick.get(0);
                Location loc = safeSpawn(b, state.centre);
                plugin.dungeonManager().spawnMythic(me.mythicId(), loc, me.level()).ifPresent(ent -> {
                    ent.setPersistent(true);
                    if (ent instanceof LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                        le.setFallDistance(0);
                        applyRemnantBuffs(le, triggered);
                    }
                    state.mobs.add(ent.getUniqueId());
                });
                unearthed++;
            }
        }

        // The Detonator, Remnant + buried-pack banners have done their job — clear them.
        removeEntity(state.detonatorDisplayId);
        removeEntity(state.detonatorTextId);
        removeEntity(state.detonatorInteractionId);
        state.detonatorDisplayId = null;
        state.detonatorTextId = null;
        state.detonatorInteractionId = null;
        for (RemnantNode n : state.remnants) { removeEntity(n.displayId); removeEntity(n.textId); }
        for (UUID m : state.buriedMarkers) removeEntity(m);
        state.buriedMarkers.clear();
        for (UUID m : state.chargeMarkers) removeEntity(m);
        state.chargeMarkers.clear();

        if (unearthed == 0) {
            broadcast(state, "&c&l✦ &7The charges blast open empty ground — nothing was buried in range.");
            playSound(state, Sound.ENTITY_GENERIC_EXPLODE, 0.8f);
            return;
        }

        String tail = triggered.isEmpty()
                ? "&7No Remnants in the blast — a clean, modest haul."
                : "&7" + triggered.size() + " Remnant(s) triggered — &cdeadlier mobs&7, &6×"
                        + fmt(state.shardMult) + " Shards&7.";
        broadcast(state, "&c&l✦ THE EARTH SUNDERS! &7" + unearthed + " unearthed. " + tail);
        playSound(state, Sound.ENTITY_GENERIC_EXPLODE, 1f);
        startWatchdog(state);
    }

    /** Buff one unearthed mob by every triggered Remnant (next tick, like tier scaling). */
    private void applyRemnantBuffs(LivingEntity le, List<Remnant> triggered) {
        if (triggered.isEmpty()) return;
        double hp = 1, dmg = 1, spd = 1;
        for (Remnant r : triggered) { hp *= r.hpMult; dmg *= r.dmgMult; spd *= r.spdMult; }
        final double fhp = hp, fdmg = dmg, fspd = spd;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (le.isDead() || !le.isValid()) return;
            scaleAttribute(le, "max_health", fhp, true);
            scaleAttribute(le, "attack_damage", fdmg, false);
            scaleAttribute(le, "movement_speed", fspd, false);
        });
    }

    /** Multiply a mob's attribute base value; optionally re-heal it to full. */
    private void scaleAttribute(LivingEntity le, String key, double mult, boolean heal) {
        if (mult == 1.0) return;
        try {
            AttributeInstance attr = le.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key)));
            if (attr == null) return;
            attr.setBaseValue(attr.getBaseValue() * mult);
            if (heal) le.setHealth(attr.getValue());
        } catch (Throwable ignored) {}
    }

    /** How often (ticks) the watchdog reconciles vanished unearthed mobs. */
    private static final long WATCHDOG_INTERVAL = 20L;

    /**
     * Safety net: an unearthed mob can leave the world WITHOUT a death event
     * (falls into the void, culled by the entity cap). Without this its UUID
     * never leaves {@code mobs}, the set never empties, and the cache never
     * drops. This sweep drops any tracked mob whose entity is gone and places
     * the cache once none remain.
     */
    private void startWatchdog(State state) {
        if (state.watchdog != null) return;
        state.watchdog = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (states.get(state.session.id()) != state) {
                if (state.watchdog != null) { state.watchdog.cancel(); state.watchdog = null; }
                return;
            }
            if (!state.detonated || state.mobs.isEmpty()) return;
            state.mobs.removeIf(id -> {
                Entity e = plugin.getServer().getEntity(id);
                return e == null || e.isDead();
            });
            if (state.mobs.isEmpty()) onCleared(state);
        }, WATCHDOG_INTERVAL, WATCHDOG_INTERVAL);
    }

    // ============================================================
    // Mob deaths — award Shards, drop the cache when cleared
    // ============================================================

    /**
     * Called from {@link DungeonManager#handleMythicMobDeath}. If the dead mob
     * was unearthed by a Sundering, awards Shards to its killer and returns true
     * (so the caller skips the boss kill-counter for it).
     */
    public boolean handleMobDeath(DungeonSession session, UUID mobId, Player killer) {
        State state = states.get(session.id());
        if (state == null) return false;
        if (!state.mobs.remove(mobId)) return false;

        if (killer != null) {
            int shards = (int) Math.round(state.template.sunderingShardsPerKill() * state.shardMult);
            int rank = plugin.skills().rank(killer.getUniqueId(), SkillType.PROSPECTOR);
            if (rank > 0) shards = (int) Math.round(shards * SkillType.PROSPECTOR.multiplierAt(rank));
            if (shards > 0) {
                SunderingShard.give(killer, shards);
                killer.sendMessage(Text.color("&6+ " + shards + " Shards &7(carried: &6"
                        + SunderingShard.count(killer) + "&7)"));
            }
        }

        if (state.mobs.isEmpty()) onCleared(state);
        return true;
    }

    private void onCleared(State state) {
        if (state.watchdog != null) { state.watchdog.cancel(); state.watchdog = null; }
        World w = state.centre.getWorld();
        if (w == null) return;
        // Place the crate ON TOP of the floor (the centre block, where players
        // stand) — not in place of the floor block, which left it sunk in the
        // ground and hard to spot.
        Block crate = w.getBlockAt(state.centre.getBlockX(), state.centre.getBlockY(), state.centre.getBlockZ());
        crate.setType(Material.BARREL);
        vendors.put(crate.getLocation(), state.session.id());

        // A bright floating beacon label so the crate is easy to find.
        Location textLoc = crate.getLocation().add(0.5, 1.4, 0.5);
        TextDisplay label = w.spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(200, 60, 10, 0));
            td.setGlowing(true);
            td.setGlowColorOverride(Color.ORANGE);
            td.setPersistent(false);
            td.setText(Text.color("&6&l✦ Sundered Hoard ✦\n&eRight-click to spend Shards"));
        });
        state.vendorLabelId = label.getUniqueId();

        w.spawnParticle(Particle.EXPLOSION_EMITTER, state.centre.clone().add(0, 1, 0), 1);
        broadcast(state, "&c&l✦ &7The dust settles. &eA hoard dealer sets up in the crater — "
                + "right-click the &6glowing crate &eto spend your Shards.");
        playSound(state, Sound.BLOCK_ENDER_CHEST_OPEN, 1f);
    }

    // ============================================================
    // The Sundered Hoard vendor (spend Shards on rolled wares)
    // ============================================================

    /** Identifies the vendor inventory and remembers whose session it serves. */
    public static final class VendorHolder implements InventoryHolder {
        private final UUID sessionId;
        private final Map<Integer, Integer> slotToOffer = new HashMap<>();
        private Inventory inv;
        VendorHolder(UUID sessionId) { this.sessionId = sessionId; }
        void setInventory(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int[] VENDOR_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int VENDOR_BALANCE_SLOT = 49;
    private static final int VENDOR_REROLL_SLOT = 51;

    /** Right-click the dealer's crate to open the vendor. */
    @EventHandler
    public void onVendorInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.BARREL) return;
        UUID sid = vendors.get(b.getLocation());
        if (sid == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        State state = states.get(sid);
        if (state == null) { p.sendMessage(Text.color("&cThis hoard's dungeon is no longer active.")); return; }
        if (!state.session.players().contains(p.getUniqueId())) {
            p.sendMessage(Text.color("&cThis hoard isn't for you."));
            return;
        }
        openVendor(p, state);
    }

    private void openVendor(Player p, State state) {
        List<VendorOffer> offers = state.vendorOffers.computeIfAbsent(
                p.getUniqueId(), k -> rollOffers(state, p.getUniqueId()));
        VendorHolder holder = new VendorHolder(state.session.id());
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color("&c&l✦ Sundered Hoard"));
        holder.setInventory(inv);

        for (int i = 0; i < 9; i++) inv.setItem(i, filler());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, filler()); inv.setItem(r * 9 + 8, filler()); }

        int balance = SunderingShard.count(p);
        int slotIdx = 0;
        for (int i = 0; i < offers.size() && slotIdx < VENDOR_SLOTS.length; i++) {
            int slot = VENDOR_SLOTS[slotIdx++];
            inv.setItem(slot, decorateOffer(offers.get(i), balance));
            holder.slotToOffer.put(slot, i);
        }

        inv.setItem(VENDOR_BALANCE_SLOT, icon(Material.AMETHYST_SHARD,
                "&6Your Shards: &f" + balance,
                "&7Earned by slaying what you unearthed.",
                "",
                "&8Physical currency — yours to keep."));

        int rerollCost = cfgVendorReroll();
        if (rerollCost > 0) {
            boolean can = balance >= rerollCost;
            inv.setItem(VENDOR_REROLL_SLOT, icon(can ? Material.ENDER_EYE : Material.ENDER_PEARL,
                    "&d&l↻ Haggle (Reroll)",
                    "&7Replace the wares with a fresh roll.",
                    "&7Cost: &6" + rerollCost + " Shards",
                    "",
                    can ? "&eClick to reroll" : "&cNot enough Shards"));
        }
        p.openInventory(inv);
    }

    /**
     * Roll a player's vendor stock from the template's own Sundering loot pool
     * (configured in the editor, separate from the maelstrom/reliquary pools).
     * Rarer items (lower chance%) cost more Shards; triggered Remnants and Greater
     * Spoils widen the stock.
     */
    private List<VendorOffer> rollOffers(State state, UUID player) {
        DungeonTemplate t = state.template;
        List<MaelstromLoot> pool = new ArrayList<>(t.sunderingLoot());
        pool.removeIf(l -> l.itemStack() == null);
        Collections.shuffle(pool, rng);

        int count = t.sunderingVendorOffers() + state.lootBonus
                + SkillType.GREATER_SPOILS.flatAt(plugin.skills().rank(player, SkillType.GREATER_SPOILS));
        count = Math.min(Math.max(0, count), pool.size());

        List<VendorOffer> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MaelstromLoot l = pool.get(i);
            int amount = l.minCount() + (l.maxCount() > l.minCount()
                    ? rng.nextInt(l.maxCount() - l.minCount() + 1) : 0);
            offers.add(new VendorOffer(l, amount, priceFor(l)));
        }
        return offers;
    }

    /** Shard price for an item — lerps min..max by rarity (rarer = pricier), with jitter. */
    private int priceFor(MaelstromLoot l) {
        int min = cfgVendorPriceMin(), max = cfgVendorPriceMax();
        if (max < min) { int t = min; min = max; max = t; }
        double rarity = 1.0 - Math.max(0, Math.min(100, l.chancePercent())) / 100.0; // 0 common .. 1 rare
        int base = (int) Math.round(min + (max - min) * rarity);
        int jitter = (int) Math.round((max - min) * 0.15);
        int price = base + (jitter > 0 ? rng.nextInt(jitter * 2 + 1) - jitter : 0);
        return Math.max(1, price);
    }

    private ItemStack decorateOffer(VendorOffer offer, int balance) {
        ItemStack stack = offer.source.resolve(plugin, offer.count);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(Text.color("&7Price: &6" + offer.price + " Shards"));
            if (offer.count > 1) lore.add(Text.color("&7Quantity: &f" + offer.count));
            if (offer.bought) {
                lore.add(Text.color("&a✔ Bought"));
            } else {
                lore.add(Text.color(balance >= offer.price ? "&eClick to buy" : "&cNot enough Shards"));
            }
            if (offer.source.isMMOItem()) lore.add(Text.color("&8Rolls fresh on purchase"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onVendorClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof VendorHolder holder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        State state = states.get(holder.sessionId);
        if (state == null) {
            p.sendMessage(Text.color("&cThe dealer has packed up — the dungeon is gone."));
            p.closeInventory();
            return;
        }

        int raw = e.getRawSlot();
        if (raw == VENDOR_REROLL_SLOT) {
            int cost = cfgVendorReroll();
            if (cost <= 0) return;
            if (!SunderingShard.take(p, cost)) { notEnough(p, cost); return; }
            state.vendorOffers.put(p.getUniqueId(), rollOffers(state, p.getUniqueId()));
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
            p.sendMessage(Text.color("&dHaggled for &6" + cost + " Shards&d. &7Balance: &6" + SunderingShard.count(p)));
            reopen(p, state);
            return;
        }

        Integer offerIdx = holder.slotToOffer.get(raw);
        if (offerIdx == null) return;
        List<VendorOffer> offers = state.vendorOffers.get(p.getUniqueId());
        if (offers == null || offerIdx < 0 || offerIdx >= offers.size()) return;
        VendorOffer offer = offers.get(offerIdx);

        if (offer.bought) { p.sendMessage(Text.color("&7You already bought that.")); return; }
        if (!SunderingShard.take(p, offer.price)) { notEnough(p, offer.price); return; }
        offer.bought = true;
        give(p, offer.source.resolve(plugin, offer.count));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        p.sendMessage(Text.color("&aBought for &6" + offer.price + " Shards&a. &7Balance: &6" + SunderingShard.count(p)));
        reopen(p, state);
    }

    // ----- vendor helpers -----

    private void give(Player p, ItemStack item) {
        var overflow = p.getInventory().addItem(item);
        for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
    }

    private void notEnough(Player p, int cost) {
        p.sendMessage(Text.color("&cNot enough Shards. &7Need &6" + cost + "&7."));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private void reopen(Player p, State state) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (states.containsValue(state)) openVendor(p, state); });
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String l : lore) list.add(Text.color(l));
                meta.setLore(list);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    private ItemStack filler() {
        ItemStack s = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    // ============================================================
    // Cleanup
    // ============================================================

    public void cleanup(DungeonSession session) {
        State state = states.remove(session.id());
        if (state != null) {
            if (state.watchdog != null) state.watchdog.cancel();
            for (UUID id : new HashSet<>(state.mobs)) removeEntity(id);
            removeEntity(state.detonatorDisplayId);
            removeEntity(state.detonatorTextId);
            removeEntity(state.detonatorInteractionId);
            for (RemnantNode n : state.remnants) { removeEntity(n.displayId); removeEntity(n.textId); }
            for (UUID m : state.buriedMarkers) removeEntity(m);
            for (UUID m : state.chargeMarkers) removeEntity(m);
            removeEntity(state.vendorLabelId);
            // Sweep any unplanted charges still on the players in this session.
            for (UUID id : session.players()) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null) removeCharges(p);
            }
        }
        vendors.entrySet().removeIf(en -> en.getValue().equals(session.id()));
    }

    /** Strip any Seismic Charges from a player's inventory (e.g. on dungeon leave). */
    public void removeCharges(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (SeismicCharge.isCharge(contents[i])) { p.getInventory().setItem(i, null); changed = true; }
        }
        if (changed) p.updateInventory();
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** A scattered, standable spot within {@code radius} of the centre, or null. */
    private Location scatter(World w, Location centre, double radius) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = radius * (0.25 + rng.nextDouble() * 0.75);
        int x = centre.getBlockX() + (int) Math.round(Math.cos(ang) * dist);
        int z = centre.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
        int baseY = centre.getBlockY();
        for (int dy = 0; dy <= 5; dy++) {
            if (standable(w, x, baseY + dy, z)) return new Location(w, x + 0.5, baseY + dy, z + 0.5);
            if (standable(w, x, baseY - dy, z)) return new Location(w, x + 0.5, baseY - dy, z + 0.5);
        }
        return null;
    }

    /** Snap a candidate spawn to a standable spot near the target; falls back to centre. */
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

    /** Horizontal (X/Z) distance — lenient on Y so charges chain across slopes. */
    private double horizDist(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
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

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.1f", d);
    }
}
