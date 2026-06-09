package com.abyss.sigils.skills;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Drives the Tome of Mastery item:
 *  - right-click (main hand) opens the skill tree GUI,
 *  - it NEVER drops on death (removed from the drop list), and is restored on
 *    respawn if the player should have one,
 *  - new players are given one on join (configurable).
 */
public final class SkillBookListener implements Listener {

    private final AbyssPlugin plugin;
    public SkillBookListener(AbyssPlugin plugin) { this.plugin = plugin; }

    private boolean giveOnJoin() {
        return plugin.getConfig().getBoolean("skillbook.give-on-join", true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!SkillBookItem.isSkillBook(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        SkillBookGUI.openFor(plugin, p);
    }

    /** Strip the skill book from death drops so it can never be lost. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(SkillBookItem::isSkillBook);
    }

    /** Hand the book back after respawn if the player no longer has one. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!giveOnJoin()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> giveIfMissing(p));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!giveOnJoin()) return;
        giveIfMissing(e.getPlayer());
    }

    private void giveIfMissing(Player p) {
        if (!p.isOnline()) return;
        if (SkillBookItem.has(p)) return;
        var overflow = p.getInventory().addItem(SkillBookItem.create());
        for (var o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
    }
}
