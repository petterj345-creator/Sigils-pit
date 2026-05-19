package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Keeps existing Abyss Map items in sync with the current state of their
 * referenced template. The map item is just a snapshot — once it's in a
 * player's inventory, its name/lore are static metadata. If an admin tweaks
 * the template after handing out maps, this listener re-stamps the lore so
 * the player sees the current info.
 *
 * Hook points:
 *  - {@link PlayerJoinEvent}: refresh the player's whole inventory on join.
 *  - {@link InventoryOpenEvent}: refresh any maps in the inventory they're
 *    opening (chests too, so dropped maps in storage update).
 *  - {@link #refreshAllOnline}: callable from {@link com.abyss.sigils.dungeon.DungeonTemplateRegistry}
 *    after a template is saved/created/deleted so changes propagate live.
 *
 * Refresh is cheap: at most we re-encode meta on items that are already maps
 * (identified by {@link DungeonMap#isMap}). Non-map items are skipped.
 */
public final class MapRefreshListener implements Listener {

    private final AbyssPlugin plugin;
    public MapRefreshListener(AbyssPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        refreshInventory(e.getPlayer().getInventory());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        // Refresh BOTH the inventory being opened (could be a chest with maps)
        // AND the viewer's own inventory (so their map names update when they
        // open any container, not just on join).
        refreshInventory(e.getInventory());
        if (e.getPlayer() instanceof Player p) {
            refreshInventory(p.getInventory());
        }
    }

    /** Call after a template change to push fresh metadata to every online player. */
    public void refreshAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshInventory(p.getInventory());
        }
    }

    /**
     * Walk an inventory and re-decorate every map item we find. Skips non-map
     * items quickly. We refresh in-place — no item replacement, no slot
     * shuffling.
     */
    private void refreshInventory(Inventory inv) {
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!DungeonMap.isMap(it)) continue;
            String tplName = DungeonMap.templateNameOf(it);
            if (tplName == null) continue;
            DungeonTemplate tpl = plugin.templates().get(tplName);
            DungeonMap.decorate(it, tpl); // tpl == null → broken-map decoration
            changed = true;
        }
        if (changed && inv instanceof PlayerInventory pinv) {
            // Push the visual update to the client immediately.
            for (org.bukkit.entity.HumanEntity h : pinv.getViewers()) {
                if (h instanceof Player p) p.updateInventory();
            }
            // The owner may not be in viewers() of their own inventory, so update too
            if (pinv.getHolder() instanceof Player p) p.updateInventory();
        }
    }
}
