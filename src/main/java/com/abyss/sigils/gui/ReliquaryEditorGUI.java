package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Configuration hub for a template's Reliquary (strongbox-style event):
 *  - the guardian pack summoned when a reliquary is unsealed
 *  - how many of the placed reliquaries unseal per run (a random min-max range)
 *  - the loot pool dropped when it cracks open, gated by guardians slain
 *
 * Reliquary markers themselves are placed in-world with the wand (Add as
 * Reliquary), so this menu shows the count but doesn't place them.
 */
public final class ReliquaryEditorGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public ReliquaryEditorGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new ReliquaryEditorGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&6&l✦ Reliquary: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // 4 Anchors (info — placed via wand)
        int relicAnchors = template.reliquarySpawnAnchors().size();
        boolean relicUsesEvents = template.reliquaryCenters().isEmpty();
        set(4, icon(Material.TRIAL_KEY,
                "&6Reliquary Anchors: &f" + relicAnchors,
                "&7Where reliquaries spawn (a random subset each run).",
                relicUsesEvents
                        ? "&7Using shared &bevent blocks &7(" + template.eventBlocks().size() + ")."
                        : "&7Using &6" + template.reliquaryCenters().size() + " &7reliquary-specific markers.",
                "&8• Wand → Add Event Block (shared by all events)",
                "&8• Wand → Add as Reliquary (override)",
                "",
                relicAnchors == 0
                        ? "&cNo anchors — place event blocks or reliquaries."
                        : "&aReady."),
            null);

        // 10 Active per run (random min-max range)
        int placed = template.reliquarySpawnAnchors().size();
        set(10, icon(Material.TARGET,
                "&dActive Reliquaries per Run",
                "&7How many placed reliquaries actually",
                "&7unseal each run — a random count in this",
                "&7range, for variety.",
                "",
                "&7Currently: " + rangeLabel(template.reliquaryActiveMin(), template.reliquaryActive(), placed),
                "&80 = always use every placed reliquary",
                "",
                "&eClick &7to set a range (e.g. 1-3)"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fActive reliquaries per run (e.g. 1-3, or 0 = all)",
                        rangeInput(template.reliquaryActiveMin(), template.reliquaryActive()), text -> {
                    applyRange(text, template::setReliquaryActiveMin, template::setReliquaryActive, p);
                    Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                });
            });

        // 12 Guardian mobs
        set(12, icon(Material.ZOMBIE_HEAD,
                "&dGuardian Pack &7(" + template.reliquaryGuards().size() + ")",
                "&7Mobs summoned when a reliquary is unsealed.",
                "&7Slay them ALL to crack it open.",
                "&7They do NOT count toward the boss.",
                "",
                "&eClick &7to manage"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                MobListGUI.openFor(plugin, p, template, template.reliquaryGuards(), "Reliquary Guardians",
                        () -> Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template)));
            });

        // 14 Use trash toggle
        set(14, icon(template.reliquaryUseTrash() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&fUse Trash Mobs: " + (template.reliquaryUseTrash() ? "&aON" : "&cOFF"),
                "&7Also add the dungeon's Default Trash Mobs",
                "&7to the guardian pack (by their counts).",
                "",
                "&eClick &7to toggle"),
            e -> {
                template.setReliquaryUseTrash(!template.reliquaryUseTrash());
                plugin.templates().save(template);
                refresh((Player) e.getWhoClicked());
            });

        // 16 Loot
        set(16, icon(Material.CHEST,
                "&6Crack-Open Loot &7(" + template.reliquaryLoot().size() + ")",
                "&7Items dropped in the cache when the",
                "&7reliquary cracks open. Each item has a",
                "&fmin-guardians &7threshold — more slain",
                "&7unlocks better loot.",
                "",
                "&eClick &7to edit"),
            e -> ReliquaryLootGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 22 Max loot items
        set(22, icon(Material.HOPPER,
                "&eMax Loot Items",
                "&7Most items the cache can roll: &f" + template.reliquaryMaxLootItems(),
                "&7Map tier and Relic Hunter add more on top.",
                "",
                "&eClick &7to set"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fMax loot items in cache",
                        String.valueOf(template.reliquaryMaxLootItems()), text -> {
                    try { template.setReliquaryMaxLootItems(Integer.parseInt(text.trim())); plugin.templates().save(template); }
                    catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a whole number.")); }
                    Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                });
            });

        // 49 Back
        set(49, icon(Material.ARROW, "&7← Back to events"),
            e -> EventsGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }

    /** "All (N)" when the upper bound is 0/over the pool, else "min–max of N". */
    private String rangeLabel(int min, int max, int placed) {
        if (max <= 0 || max >= placed) {
            return (min > 0 && min < placed)
                    ? "&f" + min + "&7–&fAll &7(" + placed + ")"
                    : "&fAll &7(" + placed + ")";
        }
        int lo = Math.min(Math.max(0, min), max);
        return (lo == max ? "&f" + max : "&f" + lo + "&7–&f" + max) + " &7of &f" + placed;
    }

    /** Pre-fill text for the prompt: "min-max", or just the value if they're equal. */
    private String rangeInput(int min, int max) {
        int lo = Math.min(min, max);
        return lo == max ? String.valueOf(max) : lo + "-" + max;
    }

    /** Parse "min-max" or a single number and apply via the two setters. */
    private void applyRange(String text, java.util.function.IntConsumer setMin,
                            java.util.function.IntConsumer setMax, Player p) {
        try {
            if (text.contains("-")) {
                String[] parts = text.split("-", 2);
                int lo = Integer.parseInt(parts[0].trim());
                int hi = Integer.parseInt(parts[1].trim());
                setMin.accept(Math.min(lo, hi));
                setMax.accept(Math.max(lo, hi));
            } else {
                int n = Integer.parseInt(text.trim());
                setMin.accept(n);
                setMax.accept(n);
            }
            plugin.templates().save(template);
        } catch (NumberFormatException ex) {
            p.sendMessage(color("&cFormat: '3' or '1-5' (0 = all)"));
        }
    }
}
