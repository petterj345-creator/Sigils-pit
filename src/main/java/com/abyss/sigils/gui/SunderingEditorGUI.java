package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Configuration hub for a template's Sundering (Expedition-style buried-hoard
 * event). The dig site spawns on the shared event anchors and its mobs come
 * from the shared Event Trash pool, so this menu tunes the field shape, the
 * charge economy, and the Shard payout — plus the vendor's loot pool.
 */
public final class SunderingEditorGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public SunderingEditorGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new SunderingEditorGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&c&l✦ The Sundering: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // 4 Info — where the site spawns + where its mobs come from
        int anchors = template.eventBlocks().size();
        set(4, icon(Material.TNT,
                "&c&l✦ The Sundering",
                "&7A buried hoard manifests on a map carrying",
                "&7the &cSundering Catalyst&7.",
                "",
                "&7Site anchors: &f" + anchors + " &7(shared event blocks)",
                "&7Mobs: &fshared Event Trash pool &7(" + template.eventTrashMobs().size() + ")",
                "&7Vendor wares: &f" + template.sunderingLoot().size(),
                "",
                anchors == 0 ? "&cNo event blocks — place some with the wand."
                             : (template.eventTrashMobs().isEmpty()
                                ? "&cNo event trash — add some in Events." : "&aReady.")),
            null);

        // 10 Charges per player
        set(10, icon(Material.TNT,
                "&eCharges per Player",
                "&7Seismic Charges handed to each player: &f" + template.sunderingCharges(),
                "&7(before the Excavator skill adds more)",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fCharges per player",
                    template.sunderingCharges(), template::setSunderingCharges));

        // 11 Blast radius
        set(11, icon(Material.FIRE_CHARGE,
                "&eBlast Radius",
                "&7Blocks each charge unearths/triggers: &f" + template.sunderingBlastRadius(),
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fBlast radius (blocks)",
                    template.sunderingBlastRadius(), template::setSunderingBlastRadius));

        // 12 Chain range
        set(12, icon(Material.CHAIN,
                "&eChain Range",
                "&7Max gap when chaining charges: &f" + template.sunderingChainRange(),
                "&7Each charge must be within this of the",
                "&7Detonator or another charge.",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fChain range (blocks)",
                    template.sunderingChainRange(), template::setSunderingChainRange));

        // 13 Field radius
        set(13, icon(Material.TARGET,
                "&eField Radius",
                "&7How far the field scatters: &f" + template.sunderingFieldRadius(),
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fField radius (blocks)",
                    template.sunderingFieldRadius(), template::setSunderingFieldRadius));

        // 14 Buried packs
        set(14, icon(Material.ZOMBIE_HEAD,
                "&eBuried Packs",
                "&7Buried mob packs in the field: &f" + template.sunderingBuriedPacks(),
                "&7One is always at the centre.",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fBuried packs",
                    template.sunderingBuriedPacks(), template::setSunderingBuriedPacks));

        // 15 Remnants
        set(15, icon(Material.WHITE_BANNER,
                "&eRemnants",
                "&7Remnant monoliths scattered in: &f" + template.sunderingRemnants(),
                "&7Reaching them with a blast buffs the mobs",
                "&7but pays more Shards + richer wares.",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fRemnant count",
                    template.sunderingRemnants(), template::setSunderingRemnants));

        // 16 Mobs per pack (range)
        set(16, icon(Material.SPAWNER,
                "&eMobs per Pack",
                "&7Mobs spawned per unearthed pack: &f"
                        + template.sunderingMobsPerPackMin() + "-" + template.sunderingMobsPerPackMax(),
                "",
                "&eClick &7to set a range (e.g. 2-4)"),
            e -> promptRange((Player) e.getWhoClicked()));

        // 19 Shards per kill
        set(19, icon(Material.AMETHYST_SHARD,
                "&eShards per Kill",
                "&7Base Shards an unearthed kill grants: &f" + template.sunderingShardsPerKill(),
                "&7(× triggered Remnants, × Prospector skill)",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fShards per kill",
                    template.sunderingShardsPerKill(), template::setSunderingShardsPerKill));

        // 20 Vendor offers
        set(20, icon(Material.EMERALD,
                "&eVendor Wares",
                "&7Items the Shard vendor stocks: &f" + template.sunderingVendorOffers(),
                "&7(triggered Remnants + Greater Spoils add more)",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fVendor wares stocked",
                    template.sunderingVendorOffers(), template::setSunderingVendorOffers));

        // 21 Max alive
        set(21, icon(Material.OBSIDIAN,
                "&8Max Mobs Alive",
                "&7Cap on mobs a single detonation spawns: &f" + template.sunderingMaxAlive(),
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fMax mobs per detonation",
                    template.sunderingMaxAlive(), template::setSunderingMaxAlive));

        // 25 Vendor loot pool
        set(25, icon(Material.CHEST,
                "&6Vendor Loot &7(" + template.sunderingLoot().size() + ")",
                "&7The Shard vendor's own stock — separate",
                "&7from the maelstrom/reliquary pools.",
                "",
                "&eClick &7to edit"),
            e -> SunderingLootGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 49 Back
        set(49, icon(Material.ARROW, "&7← Back to events"),
            e -> EventsGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }

    /** Prompt for the mobs-per-pack "min-max" range (or a single number). */
    private void promptRange(Player p) {
        String prefill = template.sunderingMobsPerPackMin() + "-" + template.sunderingMobsPerPackMax();
        ChatInput.prompt(plugin, p, "&fMobs per pack (e.g. 2-4)", prefill, text -> {
            try {
                if (text.contains("-")) {
                    String[] parts = text.split("-", 2);
                    int lo = Integer.parseInt(parts[0].trim());
                    int hi = Integer.parseInt(parts[1].trim());
                    template.setSunderingMobsPerPackMin(Math.min(lo, hi));
                    template.setSunderingMobsPerPackMax(Math.max(lo, hi));
                } else {
                    int n = Integer.parseInt(text.trim());
                    template.setSunderingMobsPerPackMin(n);
                    template.setSunderingMobsPerPackMax(n);
                }
                plugin.templates().save(template);
            } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '3' or '2-4'")); }
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
        });
    }

    /** Prompt for a whole number, apply via the setter, save, and reopen. */
    private void promptInt(Player p, String prompt, int current, java.util.function.IntConsumer setter) {
        ChatInput.prompt(plugin, p, prompt, String.valueOf(current), text -> {
            try { setter.accept(Integer.parseInt(text.trim())); plugin.templates().save(template); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a whole number.")); }
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
        });
    }
}
