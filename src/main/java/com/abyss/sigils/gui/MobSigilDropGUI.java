package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * For one specific MythicMob: configure a new sigil drop.
 *
 * Layout:
 *   Row 0 — selected sigil + tier/amount/chance controls
 *   Rows 1–4 — list of all available sigils (click to select)
 *   Row 5 — Save (writes drop to mob's YAML + reloads Mythic) / Cancel
 *
 * Default settings: tier=1, amount=1, chance=0.1 (10%). All adjustable.
 */
public final class MobSigilDropGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final MythicMob mob;
    private SigilDefinition selected;
    private int tier = 1;
    private int amount = 1;
    private double chance = 0.10;

    public MobSigilDropGUI(AbyssPlugin plugin, MythicMob mob) {
        this.plugin = plugin;
        this.mob = mob;
    }

    public static void openFor(AbyssPlugin plugin, Player p, MythicMob m) {
        new MobSigilDropGUI(plugin, m).open(p);
    }

    @Override protected String title() {
        return color("&5Drop on " + mob.getInternalName());
    }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // Selected preview + controls
        if (selected == null) {
            set(4, icon(Material.PAPER, "&fNo sigil selected",
                    "&7Pick one from the list below."), null);
        } else {
            ItemStack preview = SigilItem.toItem(new SigilInstance(selected.id(), tier));
            set(4, preview, null);
        }

        if (selected != null) {
            set(1, icon(Material.EXPERIENCE_BOTTLE, "&fTier: &e" + tier,
                    "&7Max for this sigil: &f" + selected.maxTier(),
                    "",
                    "&eLeft-click &7+1   &eRight-click &7-1"),
                e -> {
                    if (e.isRightClick()) tier = Math.max(1, tier - 1);
                    else tier = Math.min(selected.maxTier(), tier + 1);
                    refresh((Player) e.getWhoClicked());
                });
            set(2, icon(Material.GHAST_TEAR, "&fAmount: &e" + amount,
                    "&7How many items drop on success.",
                    "",
                    "&eLeft-click &7+1   &eRight-click &7-1"),
                e -> {
                    if (e.isRightClick()) amount = Math.max(1, amount - 1);
                    else amount = Math.min(64, amount + 1);
                    refresh((Player) e.getWhoClicked());
                });
            set(6, icon(Material.GOLD_NUGGET, "&fChance: &e" + (chance * 100) + "%",
                    "&7Probability the drop fires.",
                    "",
                    "&eLeft-click &7type new chance",
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
                        AnvilInput.open(plugin, p, "&fChance (0.0 to 1.0)",
                                String.valueOf(chance), text -> {
                            try {
                                double c = Double.parseDouble(text);
                                chance = Math.max(0, Math.min(1, c));
                            } catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number 0..1")); }
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                        });
                    }
                });

            // Save button
            set(49, icon(Material.LIME_WOOL, "&a&lAdd Drop",
                    "&7Writes to MythicMobs YAML and reloads.",
                    "",
                    "&7Line: &fabyss_sigil{id=" + selected.id() + ";tier=" + tier + "} "
                            + amount + " " + chance),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    boolean ok = plugin.mythicDropWriter().appendSigilDrop(
                            mob.getInternalName(), selected.id(), tier, amount, chance);
                    if (ok) {
                        p.sendMessage(color("&aDrop added to &f" + mob.getInternalName()
                                + "&a. Reloading Mythic..."));
                        plugin.mythicDropWriter().reloadMythic();
                        p.closeInventory();
                    } else {
                        p.sendMessage(color("&cFailed to write drop. Check console."));
                    }
                });
        }

        set(53, icon(Material.BARRIER, "&cCancel", "&7Close without saving."),
            e -> ((Player) e.getWhoClicked()).closeInventory());

        // List of sigils to choose from
        List<SigilDefinition> defs = new ArrayList<>(plugin.sigils().all());
        int slot = 10;
        int max = Math.min(defs.size(), 28);
        for (int i = 0; i < max; i++) {
            if (slot % 9 == 8) slot += 2;
            SigilDefinition def = defs.get(i);
            ItemStack stack = SigilItem.toItem(new SigilInstance(def.id(), 1));
            if (stack != null) {
                ItemStack chosenMarker = stack.clone();
                if (selected != null && selected.id().equals(def.id())) {
                    chosenMarker.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
                }
                set(slot, chosenMarker, e -> {
                    selected = def;
                    tier = Math.min(tier, def.maxTier());
                    refresh((Player) e.getWhoClicked());
                });
            }
            slot++;
        }
    }

    // (no extra helpers)
}
