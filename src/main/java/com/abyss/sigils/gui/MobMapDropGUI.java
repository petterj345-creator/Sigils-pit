package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonMap;
import com.abyss.sigils.dungeon.DungeonTemplate;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Configure an Abyss-Map drop on a specific MythicMob for a specific template.
 *
 * Same UX as {@link MobSigilDropGUI}: tweak amount + chance, then save. The
 * Save button writes a Drops: line into the mob's MythicMobs YAML file and
 * reloads Mythic. The resulting drop line looks like:
 *     abyss_map{template=&lt;name&gt;} &lt;amount&gt; &lt;chance&gt;
 */
public final class MobMapDropGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final MythicMob mob;
    private final DungeonTemplate template;
    private int amount = 1;
    private double chance = 0.10;

    public MobMapDropGUI(AbyssPlugin plugin, MythicMob mob, DungeonTemplate template) {
        this.plugin = plugin;
        this.mob = mob;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, MythicMob m, DungeonTemplate t) {
        new MobMapDropGUI(plugin, m, t).open(p);
    }

    @Override protected String title() {
        return color("&5Map drop: " + mob.getInternalName());
    }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // Preview: the actual map item for this template
        set(4, DungeonMap.create(template), null);

        set(20, icon(Material.GHAST_TEAR, "&fAmount: &e" + amount,
                "&7How many maps drop on a successful roll.",
                "",
                "&eLeft-click &7+1   &eRight-click &7-1"),
            e -> {
                if (e.isRightClick()) amount = Math.max(1, amount - 1);
                else amount = Math.min(64, amount + 1);
                refresh((Player) e.getWhoClicked());
            });

        set(24, icon(Material.GOLD_NUGGET, "&fChance: &e" + (chance * 100) + "%",
                "&7Probability the drop fires.",
                "",
                "&eLeft-click &7type a new chance",
                "&eRight-click &7+5%   &eDrop key &7-5%"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    chance = Math.min(1.0, chance + 0.05);
                    refresh(p);
                } else if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                        || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
                    chance = Math.max(0.0, chance - 0.05);
                    refresh(p);
                } else {
                    ChatInput.prompt(plugin, p, "&fChance (0.0 to 1.0)",
                            String.valueOf(chance), text -> {
                        try {
                            double c = Double.parseDouble(text);
                            chance = Math.max(0, Math.min(1, c));
                        } catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number 0..1")); }
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                    });
                }
            });

        // Save — writes Drops line + reloads Mythic
        set(49, icon(Material.LIME_WOOL, "&a&lAdd Map Drop",
                "&7Writes to MythicMobs YAML and reloads.",
                "",
                "&7Line: &fabyss_map{template=" + template.name() + "} "
                        + amount + " " + chance),
            e -> {
                Player p = (Player) e.getWhoClicked();
                boolean ok = plugin.mythicDropWriter().appendMapDrop(
                        mob.getInternalName(), template.name(), amount, chance);
                if (ok) {
                    p.sendMessage(color("&aMap drop added to &f" + mob.getInternalName()
                            + "&a. Reloading Mythic..."));
                    plugin.mythicDropWriter().reloadMythic();
                    p.closeInventory();
                } else {
                    p.sendMessage(color("&cFailed to write drop. Check console."));
                }
            });

        set(45, icon(Material.ARROW, "&7← Back"),
            e -> MapDropMobPickerGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        set(53, icon(Material.BARRIER, "&cCancel", "&7Close without saving."),
            e -> ((Player) e.getWhoClicked()).closeInventory());
    }
}
