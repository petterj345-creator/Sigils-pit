package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Configuration hub for a template's Altar of Souls ritual:
 *  - the mobs each ritual summons
 *  - how many souls each mob grants
 *  - the soul shop's item pool + roll counts + price range
 *
 * Altar locations themselves are placed in-world with the wand (Set as Ritual
 * Altar), so this menu shows the count but doesn't place them.
 */
public final class RitualEditorGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public RitualEditorGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new RitualEditorGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&5&l✦ Rituals: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // 4 Altars (info — placed via wand)
        set(4, icon(Material.PURPLE_WOOL,
                "&5Ritual Altars: &f" + template.ritualAltars().size(),
                "&7Armor stands players activate.",
                "&7Place them in-world with the wand:",
                "&8• Right-click a block → Add as Ritual Altar",
                "",
                template.ritualAltars().isEmpty()
                        ? "&cNone placed yet — maps with the ritual"
                        : "&aReady.",
                template.ritualAltars().isEmpty()
                        ? "&cmodifier won't spawn any altars."
                        : "&7"),
            null);

        // 20 Ritual mobs
        set(20, icon(Material.ZOMBIE_HEAD,
                "&dRitual Mobs &7(" + template.ritualMobs().size() + ")",
                "&7Mobs summoned when a ritual starts.",
                "&7They do NOT count toward the boss.",
                "",
                "&eClick &7to manage"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                MobListGUI.openFor(plugin, p, template, template.ritualMobs(), "Ritual Mobs",
                        () -> Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template)));
            });

        // 22 Souls per mob
        set(22, icon(Material.ECHO_SHARD,
                "&bSouls per Mob",
                "&7How many souls each mob grants",
                "&7when killed during a ritual.",
                "&7Configured: &f" + template.ritualMobSouls().size(),
                "",
                "&eClick &7to manage"),
            e -> RitualSoulsGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 24 Shop rewards
        set(24, icon(Material.CHEST,
                "&6Soul Shop Rewards &7(" + template.ritualRewardPool().size() + ")",
                "&7Items players can buy with souls.",
                "&7Drag items in from your inventory.",
                "",
                "&eClick &7to edit"),
            e -> RitualRewardsGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 30 Items per shop (range)
        set(30, icon(Material.HOPPER,
                "&eItems Rolled per Shop",
                "&7Range: &f" + template.ritualItemsMin() + "-" + template.ritualItemsMax(),
                "&7How many items appear in the shop.",
                "",
                "&eClick &7to set (e.g. 3-5)"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fItems per shop (e.g. 3-5)",
                        template.ritualItemsMin() + "-" + template.ritualItemsMax(), text -> {
                    applyRange(text, template::setRitualItemsMin, template::setRitualItemsMax, p);
                    Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                });
            });

        // 32 Soul price range
        set(32, icon(Material.SOUL_TORCH,
                "&bDefault Soul Price",
                "&7Range: &f" + template.ritualPriceMin() + "-" + template.ritualPriceMax(),
                "&7Price rolled for items that don't",
                "&7set their own price.",
                "",
                "&eClick &7to set (e.g. 10-50)"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fSoul price range (e.g. 10-50)",
                        template.ritualPriceMin() + "-" + template.ritualPriceMax(), text -> {
                    applyRange(text, template::setRitualPriceMin, template::setRitualPriceMax, p);
                    Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                });
            });

        // 49 Back
        set(49, icon(Material.ARROW, "&7← Back to editor"),
            e -> TemplateEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }

    /** Parse "min-max" or a single number and apply via the two setters. */
    private void applyRange(String text, java.util.function.IntConsumer setMin,
                            java.util.function.IntConsumer setMax, Player p) {
        try {
            if (text.contains("-")) {
                String[] parts = text.split("-", 2);
                int lo = Integer.parseInt(parts[0].trim());
                int hi = Integer.parseInt(parts[1].trim());
                setMin.accept(lo);
                setMax.accept(hi);
            } else {
                int n = Integer.parseInt(text.trim());
                setMin.accept(n);
                setMax.accept(n);
            }
            plugin.templates().save(template);
        } catch (NumberFormatException ex) {
            p.sendMessage(color("&cFormat: '5' or '3-5'"));
        }
    }
}
