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

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        // Upgrade block first (takes priority when player is inside a dungeon)
        if (handleUpgrade(e, b)) return;
        // Return portal (placed when boss dies) — sends player out
        if (handleReturnPortal(e, b)) return;

        if (!isPortalBlock(b)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        List<Player> party;
        if (p.isSneaking()) {
            party = p.getWorld().getNearbyEntitiesByType(Player.class, p.getLocation(), 8)
                    .stream().toList();
        } else {
            party = List.of(p);
        }

        // If the player is holding an Abyss Map, enter THAT template and consume one.
        // Look in the hand actually used (main or off) to interact with the portal.
        org.bukkit.inventory.ItemStack heldMap = mapInHand(e);
        if (heldMap != null) {
            DungeonTemplate mapTemplate = DungeonMap.templateOf(plugin, heldMap);
            if (mapTemplate == null) {
                p.sendMessage(Text.color("&cThis Abyss Map references a dungeon that no longer exists."));
                return;
            }
            // Consume one map BEFORE starting — if start() fails, we'll restore it.
            int beforeAmount = heldMap.getAmount();
            heldMap.setAmount(beforeAmount - 1);
            p.sendMessage(Text.color("&5&lThe Abyss &7is opening... &7(" + mapTemplate.name() + ")"));
            plugin.dungeonManager().start(party, mapTemplate);
            return;
        }

        // No map — legacy behaviour: random playable template.
        p.sendMessage(Text.color("&5&lThe Abyss &7is opening..."));
        plugin.dungeonManager().start(party);
    }

    /**
     * Return the Abyss Map ItemStack the player used to click the portal, if
     * any. Checks main hand first, then off hand. We modify the returned
     * ItemStack directly to decrement its count.
     */
    private org.bukkit.inventory.ItemStack mapInHand(PlayerInteractEvent e) {
        org.bukkit.inventory.ItemStack main = e.getPlayer().getInventory().getItemInMainHand();
        if (DungeonMap.isMap(main)) return main;
        org.bukkit.inventory.ItemStack off = e.getPlayer().getInventory().getItemInOffHand();
        if (DungeonMap.isMap(off)) return off;
        return null;
    }

    private boolean isPortalBlock(Block b) {
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
