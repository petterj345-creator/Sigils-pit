package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Browse all loaded MythicMobs. Click one to open the per-mob drop editor.
 *
 * 4 rows of clickable mobs (28 slots, max). If you have more than 28 mobs,
 * we'll need pagination — easy to add later.
 */
public final class MythicDropsGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    public MythicDropsGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new MythicDropsGUI(plugin).open(p);
    }

    @Override protected String title() { return color("&5&lMythicMobs → Sigil Drops"); }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();
        List<MythicMob> mobs = new ArrayList<>(plugin.mythicDropWriter().allMythicMobs());
        if (mobs.isEmpty()) {
            set(22, icon(Material.BARRIER, "&cNo MythicMobs loaded",
                    "&7Make sure MythicMobs is installed",
                    "&7and at least one mob is defined."),
                null);
            return;
        }
        int slot = 10;
        int max = Math.min(mobs.size(), 28);
        for (int i = 0; i < max; i++) {
            if (slot % 9 == 8) slot += 2;
            MythicMob m = mobs.get(i);
            String displayName;
            try {
                Object dn = m.getDisplayName();
                if (dn == null) { displayName = m.getInternalName(); }
                else {
                    // Try .get(), fall back to toString
                    try {
                        java.lang.reflect.Method get = dn.getClass().getMethod("get");
                        Object res = get.invoke(dn);
                        displayName = res == null ? m.getInternalName()
                                : org.bukkit.ChatColor.stripColor(res.toString());
                    } catch (Throwable t2) {
                        displayName = org.bukkit.ChatColor.stripColor(dn.toString());
                    }
                }
            } catch (Throwable t) {
                displayName = m.getInternalName();
            }
            ItemStack ic = icon(Material.ZOMBIE_HEAD,
                    "&f" + displayName,
                    "&7Internal ID: &8" + m.getInternalName(),
                    "",
                    "&eClick &7to edit sigil drops");
            set(slot, ic, e -> MobSigilDropGUI.openFor(plugin, (Player) e.getWhoClicked(), m));
            slot++;
        }
        if (mobs.size() > 28) {
            set(49, icon(Material.PAPER, "&7Showing first 28 of " + mobs.size(),
                    "&8Pagination not yet implemented."), null);
        }
    }
}
