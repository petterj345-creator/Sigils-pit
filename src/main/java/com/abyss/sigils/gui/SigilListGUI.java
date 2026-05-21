package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilDraft;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilRank;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists all sigils as their actual ItemStacks. Click to edit, shift-click to delete,
 * "Create new" button creates a fresh draft.
 *
 * For simplicity it shows up to ~28 sigils. If you need more, we'll add pagination.
 */
public final class SigilListGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;

    public SigilListGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new SigilListGUI(plugin).open(p);
    }

    @Override protected String title() { return color("&5&lAll Sigils"); }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        List<SigilDefinition> all = new ArrayList<>(plugin.sigils().all());
        int slot = 10;
        int max = Math.min(all.size(), 28);
        for (int i = 0; i < max; i++) {
            if (slot % 9 == 8) slot += 2;
            SigilDefinition def = all.get(i);
            // Show its real ItemStack at T1 + an admin overlay lore
            SigilInstance inst = new SigilInstance(def.id(), 1);
            ItemStack stack = SigilItem.toItem(inst);
            if (stack != null) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(color("&7ID: &f" + def.id()));
                    lore.add(color("&7Rank: " + switch (def.rank()) {
                        case GRAND -> "&5&lGRAND";
                        case MAJOR -> "&6MAJOR";
                        case MINOR -> "&7minor";
                    }));
                    lore.add(color("&7Max tier: &f" + def.maxTier()));
                    lore.add("");
                    lore.add(color("&eClick &7to edit"));
                    lore.add(color("&cShift-click &7to delete"));
                    meta.setLore(lore);
                    stack.setItemMeta(meta);
                }
            }
            set(slot, stack, e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isShiftClick()) {
                    plugin.sigils().delete(def.id());
                    p.sendMessage(color("&aDeleted sigil &f" + def.id() + "&a."));
                    refresh(p);
                } else {
                    SigilCreatorGUI.openFor(plugin, p, SigilDraft.from(def));
                }
            });
            slot++;
        }

        set(49, icon(Material.EMERALD_BLOCK, "&a&lCreate New Sigil",
                "&7Opens the sigil creator with a fresh draft."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                String id = "new_" + System.currentTimeMillis() % 100000;
                SigilCreatorGUI.openFor(plugin, p, new SigilDraft(id));
            });
    }
}
