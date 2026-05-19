package com.abyss.sigils.socket;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-click the Book of Sigils (in main or off hand) to open the socket GUI.
 */
public final class BookListener implements Listener {

    private final AbyssPlugin plugin;
    public BookListener(AbyssPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return; // only main-hand, avoid double-fire
        Player p = e.getPlayer();
        if (!SigilItem.isBook(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        plugin.socketGUI().openFor(p);
    }
}
