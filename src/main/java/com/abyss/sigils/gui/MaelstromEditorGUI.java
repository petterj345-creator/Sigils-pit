package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Configuration hub for a template's Maelstrom (breach-style rift event):
 *  - which mobs the rift spawns (or "use trash" to fill from the dungeon list)
 *  - timing: duration, spawn interval, mobs per pulse, circle radius, max alive
 *  - the loot pool dropped on collapse, gated by kill thresholds
 *
 * Rift centers themselves are placed in-world with the wand (Add as Maelstrom),
 * so this menu shows the count but doesn't place them.
 */
public final class MaelstromEditorGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public MaelstromEditorGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new MaelstromEditorGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&3&l✦ Maelstrom: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // 4 Rifts (info — placed via wand)
        set(4, icon(Material.HEART_OF_THE_SEA,
                "&3Rift Centers: &f" + template.maelstromCenters().size(),
                "&7Where a Maelstrom can tear open.",
                "&7Place them in-world with the wand:",
                "&8• Right-click a block → Add as Maelstrom",
                "",
                template.maelstromCenters().isEmpty()
                        ? "&cNone placed yet — maps with the maelstrom"
                        : "&aReady.",
                template.maelstromCenters().isEmpty()
                        ? "&cmodifier won't open any rifts."
                        : "&7"),
            null);

        // 10 Active per run (random min-max range)
        int placed = template.maelstromCenters().size();
        int amin = Math.min(template.maelstromActiveMin(), template.maelstromActive());
        int amax = template.maelstromActive();
        String activeLabel = (amax <= 0 || amax >= placed)
                ? "&fAll &7(" + placed + ")"
                : (amin == amax ? "&f" + amax : "&f" + amin + "&7–&f" + amax) + " &7of &f" + placed;
        String activePrefill = amin == amax ? String.valueOf(amax) : amin + "-" + amax;
        set(10, icon(Material.TARGET,
                "&dActive Rifts per Run",
                "&7How many placed rifts actually open each",
                "&7run — a random count in this range.",
                "",
                "&7Currently: " + activeLabel,
                "&80 = always use every placed rift",
                "",
                "&eClick &7to set a range (e.g. 1-3)"),
            e -> promptActiveRange((Player) e.getWhoClicked(), activePrefill));

        // 12 Mobs
        set(12, icon(Material.ZOMBIE_HEAD,
                "&dMaelstrom Mobs &7(" + template.maelstromMobs().size() + ")",
                "&7Specific mobs the rift spews.",
                "&7Leave empty and enable &fUse Trash &7to just",
                "&7fill with the dungeon's trash mobs.",
                "",
                "&eClick &7to manage"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                MobListGUI.openFor(plugin, p, template, template.maelstromMobs(), "Maelstrom Mobs",
                        () -> Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template)));
            });

        // 14 Use trash toggle
        set(14, icon(template.maelstromUseTrash() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&fUse Trash Mobs: " + (template.maelstromUseTrash() ? "&aON" : "&cOFF"),
                "&7Also pull from the dungeon's Default Trash",
                "&7Mobs — fast filler at a clearable rate,",
                "&7just like the normal dungeon spawner.",
                "",
                "&eClick &7to toggle"),
            e -> {
                template.setMaelstromUseTrash(!template.maelstromUseTrash());
                plugin.templates().save(template);
                refresh((Player) e.getWhoClicked());
            });

        // 16 Max alive
        set(16, icon(Material.OBSIDIAN,
                "&8Max Mobs Alive",
                "&7Cap on rift mobs at once: &f" + template.maelstromMaxAlive(),
                "&7Keeps the rift clearable instead of",
                "&7burying players. Spawns pause at the cap.",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fMax rift mobs alive",
                    template.maelstromMaxAlive(), template::setMaelstromMaxAlive));

        // 20 Duration
        set(20, icon(Material.CLOCK,
                "&eDuration",
                "&7Seconds the rift spawns before it",
                "&7collapses: &f" + template.maelstromDurationSeconds() + "s",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fDuration in seconds",
                    template.maelstromDurationSeconds(), template::setMaelstromDurationSeconds));

        // 22 Spawn interval
        set(22, icon(Material.REPEATER,
                "&eSpawn Interval",
                "&7Ticks between spawn pulses: &f" + template.maelstromSpawnIntervalTicks(),
                "&7Lower = faster spawning (20 ticks = 1s).",
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fSpawn interval in ticks",
                    template.maelstromSpawnIntervalTicks(), template::setMaelstromSpawnIntervalTicks));

        // 24 Mobs per pulse
        set(24, icon(Material.SPAWNER,
                "&eMobs per Pulse",
                "&7How many spawn each pulse: &f" + template.maelstromMobsPerPulse(),
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fMobs per pulse",
                    template.maelstromMobsPerPulse(), template::setMaelstromMobsPerPulse));

        // 30 Radius
        set(30, icon(Material.SCAFFOLDING,
                "&eCircle Radius",
                "&7Spawn-circle radius in blocks: &f" + template.maelstromRadius(),
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fCircle radius (blocks)",
                    template.maelstromRadius(), template::setMaelstromRadius));

        // 32 Loot
        set(32, icon(Material.CHEST,
                "&6Collapse Loot &7(" + template.maelstromLoot().size() + ")",
                "&7Items dropped in the cache when the rift",
                "&7collapses. Each item has a &fmin-kills",
                "&7threshold — more kills unlocks better loot.",
                "",
                "&eClick &7to edit"),
            e -> MaelstromLootGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 34 Max loot items
        set(34, icon(Material.HOPPER,
                "&eMax Loot Items",
                "&7Most items the cache can roll: &f" + template.maelstromMaxLootItems(),
                "",
                "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "&fMax loot items in cache",
                    template.maelstromMaxLootItems(), template::setMaelstromMaxLootItems));

        // 49 Back
        set(49, icon(Material.ARROW, "&7← Back to editor"),
            e -> TemplateEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }

    /** Prompt for a "min-max" range (or single number) for active rifts per run. */
    private void promptActiveRange(Player p, String prefill) {
        ChatInput.prompt(plugin, p, "&fActive rifts per run (e.g. 1-3, or 0 = all)", prefill, text -> {
            try {
                if (text.contains("-")) {
                    String[] parts = text.split("-", 2);
                    int lo = Integer.parseInt(parts[0].trim());
                    int hi = Integer.parseInt(parts[1].trim());
                    template.setMaelstromActiveMin(Math.min(lo, hi));
                    template.setMaelstromActive(Math.max(lo, hi));
                } else {
                    int n = Integer.parseInt(text.trim());
                    template.setMaelstromActiveMin(n);
                    template.setMaelstromActive(n);
                }
                plugin.templates().save(template);
            } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '3' or '1-5' (0 = all)")); }
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
