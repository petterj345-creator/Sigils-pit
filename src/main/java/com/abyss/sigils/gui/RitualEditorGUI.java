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

        // 13 Altars active per run (random subset)
        int placed = template.ritualAltars().size();
        int active = template.ritualAltarsActive();
        String activeLabel = (active <= 0 || active >= placed)
                ? "&fAll &7(" + placed + ")"
                : "&f" + active + " &7of &f" + placed;
        set(13, icon(Material.TARGET,
                "&dActive Altars per Run",
                "&7How many of the placed altars actually",
                "&7spawn each run — picked at random, so the",
                "&7map feels different every time you play.",
                "",
                "&7Currently: " + activeLabel,
                "&80 = always use every placed altar",
                "",
                "&eClick &7to set"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fActive altars per run (0 = all)",
                        String.valueOf(template.ritualAltarsActive()), text -> {
                    try { template.setRitualAltarsActive(Integer.parseInt(text.trim())); plugin.templates().save(template); }
                    catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                    Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                });
            });

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

        // 34 Reroll cost
        set(34, icon(Material.ENDER_EYE,
                "&dShop Reroll Cost",
                "&7Souls to reroll the shop: &f"
                        + (template.ritualRerollCost() <= 0 ? "&cdisabled" : template.ritualRerollCost()),
                "&7Set 0 to disable rerolling.",
                "",
                "&eClick &7to set"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fReroll cost in souls (0 = disabled)",
                        String.valueOf(template.ritualRerollCost()), text -> {
                    try { template.setRitualRerollCost(Integer.parseInt(text.trim())); plugin.templates().save(template); }
                    catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                    Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                });
            });

        // 28 Reserve (layaway) settings
        set(28, icon(Material.ENDER_CHEST,
                "&dReserve Settings",
                "&7Players can reserve shop items for later.",
                "&7Max reserved: &f" + template.ritualReserveLimit(),
                "&7Deposit: &f" + template.ritualReserveDepositPct() + "%",
                "&7Discount: &f" + template.ritualReserveDiscountPct() + "%",
                "",
                "&eLeft-click &7→ set max",
                "&eRight-click &7→ set deposit %",
                "&eDrop key &7→ set discount %"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                        || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
                    ChatInput.prompt(plugin, p, "&fReserve discount %",
                            String.valueOf(template.ritualReserveDiscountPct()), text -> {
                        applyInt(text, template::setRitualReserveDiscountPct, p);
                        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                    });
                } else if (e.isRightClick()) {
                    ChatInput.prompt(plugin, p, "&fReserve deposit %",
                            String.valueOf(template.ritualReserveDepositPct()), text -> {
                        applyInt(text, template::setRitualReserveDepositPct, p);
                        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                    });
                } else {
                    ChatInput.prompt(plugin, p, "&fMax reserved items (0 = disable)",
                            String.valueOf(template.ritualReserveLimit()), text -> {
                        applyInt(text, template::setRitualReserveLimit, p);
                        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                    });
                }
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

    /** Parse a single number and apply it. */
    private void applyInt(String text, java.util.function.IntConsumer setter, Player p) {
        try {
            setter.accept(Integer.parseInt(text.trim()));
            plugin.templates().save(template);
        } catch (NumberFormatException ex) {
            p.sendMessage(color("&cMust be a number."));
        }
    }
}

