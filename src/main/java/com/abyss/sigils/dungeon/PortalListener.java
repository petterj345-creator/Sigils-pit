package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

/**
 * Right-click the configured portal block to enter The Abyss.
 *
 * If the player is sneaking when interacting, we treat their nearby party
 * members (within 8 blocks) as the party. Otherwise, solo entry.
 */
public final class PortalListener implements Listener {

    private final AbyssPlugin plugin;
    public PortalListener(AbyssPlugin plugin) { this.plugin = plugin; }

    /**
     * Anti-spam debounce. Right-click events on a block can fire twice per
     * physical click on some Paper builds (once for main hand, once for off
     * hand), and players can also click rapidly. We dedupe by tracking the
     * last click timestamp per player and ignoring anything within 500ms.
     */
    private final java.util.Map<java.util.UUID, Long> lastPortalClick = new java.util.HashMap<>();

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        // Only respond to the main hand to avoid the double-fire (main + off).
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        // Upgrade block first (takes priority when player is inside a dungeon)
        if (handleUpgrade(e, b)) return;
        // Return portal (placed when boss dies) — sends player out
        if (handleReturnPortal(e, b)) return;

        if (!isPortalBlock(b)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();

        // Debounce: 500ms between portal clicks. Without this, even with the
        // GUI in front, very fast/double-click patterns could spawn the GUI
        // twice — and players reported "boss bars all over the place" which
        // was the direct-start path before this GUI existed.
        long now = System.currentTimeMillis();
        Long last = lastPortalClick.get(p.getUniqueId());
        if (last != null && now - last < 500) return;
        lastPortalClick.put(p.getUniqueId(), now);

        // Hand off to the confirmation GUI. The GUI handles map slot, broken
        // maps, party-with-sneak, and the actual start() call.
        plugin.portalEntryGUI().openFor(p);
    }

    private boolean isPortalBlock(Block b) {
        // No portal configured → nothing matches. Without this check, getInt
        // returns 0 for unset keys and players could trigger entry by clicking
        // any matching block at world coord (0,0,0).
        if (!plugin.getConfig().isSet("portal.x")) return false;
        Material mat = Material.matchMaterial(plugin.getConfig().getString("portal.block-type", "END_PORTAL_FRAME"));
        if (mat == null || b.getType() != mat) return false;
        String w = plugin.getConfig().getString("portal.world", "world");
        int x = plugin.getConfig().getInt("portal.x");
        int y = plugin.getConfig().getInt("portal.y");
        int z = plugin.getConfig().getInt("portal.z");
        return b.getWorld().getName().equals(w)
                && b.getX() == x && b.getY() == y && b.getZ() == z;
    }

    private boolean handleUpgrade(PlayerInteractEvent e, Block b) {
        Player p = e.getPlayer();
        DungeonSession s = plugin.dungeonManager().sessionOf(p);
        if (s == null || s.upgradeBlock() == null) return false;
        Location up = s.upgradeBlock();
        if (!b.getLocation().equals(up)) return false;
        e.setCancelled(true);
        plugin.upgradeGUI().open(p);
        return true;
    }

    /**
     * Right-clicking the return-portal block (placed at boss death) sends the
     * player back to the overworld portal location, same as /abyss leave.
     */
    private boolean handleReturnPortal(PlayerInteractEvent e, Block b) {
        Player p = e.getPlayer();
        DungeonSession s = plugin.dungeonManager().sessionOf(p);
        if (s == null || s.returnPortalBlock() == null) return false;
        if (!b.getLocation().equals(s.returnPortalBlock())) return false;
        e.setCancelled(true);
        p.sendMessage(Text.color("&7Returning to the overworld..."));
        plugin.dungeonManager().leave(p);
        return true;
    }
}
