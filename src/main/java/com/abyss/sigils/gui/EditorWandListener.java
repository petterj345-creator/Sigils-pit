package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.util.Text;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes wand clicks and gives/takes the wand as players enter/leave editor worlds.
 *
 * "Template world" detection: a world whose name starts with "abyss_tpl_".
 * If a player enters such a world they get the wand in their first available slot.
 * On leaving, all wands are removed from their inventory.
 */
public final class EditorWandListener implements Listener {

    private final AbyssPlugin plugin;
    public EditorWandListener(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Place a wand in the player's inventory if they don't already have one. */
    public void giveWand(Player p) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (EditorWand.isWand(s)) return; // already has one
        }
        p.getInventory().addItem(EditorWand.create());
    }

    /** Remove every wand from a player's inventory. */
    public void removeWands(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (EditorWand.isWand(s)) p.getInventory().setItem(i, null);
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (EditorWand.isWand(off)) p.getInventory().setItemInOffHand(null);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        String world = p.getWorld().getName();
        boolean inEditor = world.startsWith("abyss_tpl_") && p.hasPermission("abyss.admin");
        if (inEditor) giveWand(p);
        else removeWands(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Strip wands so they don't persist in saved inventory data outside editor worlds
        removeWands(e.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack in = p.getInventory().getItemInMainHand();
        if (!EditorWand.isWand(in)) return;
        if (!p.hasPermission("abyss.admin")) return;
        if (!p.getWorld().getName().startsWith("abyss_tpl_")) return;

        DungeonTemplate tpl = plugin.templates().get(
                p.getWorld().getName().substring("abyss_tpl_".length()));
        if (tpl == null) return;

        Action a = e.getAction();

        if (a == Action.RIGHT_CLICK_AIR) {
            e.setCancelled(true);
            TemplateEditorGUI.openFor(plugin, p, tpl);
            return;
        }
        if (a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            Block b = e.getClickedBlock();
            if (b == null) return;
            WandBlockMenu.openFor(plugin, p, tpl, b);
            return;
        }
        if (a == Action.LEFT_CLICK_BLOCK && p.isSneaking()) {
            e.setCancelled(true);
            Block b = e.getClickedBlock();
            if (b == null) return;
            int removed = WandBlockMenu.removeMarkersAt(tpl, b);
            if (removed > 0) {
                plugin.templates().save(tpl);
                p.sendMessage(Text.color("&aRemoved " + removed + " marker(s) at this block."));
            } else {
                p.sendMessage(Text.color("&7No markers here."));
            }
            return;
        }
        if (a == Action.LEFT_CLICK_BLOCK || a == Action.LEFT_CLICK_AIR) {
            // Suppress damage swing
            e.setCancelled(true);
        }
    }
}
