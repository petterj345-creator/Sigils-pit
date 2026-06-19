package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Wires the two hideout fixtures into the world:
 *   - placing an {@link HideoutItems#PORTAL}/{@link HideoutItems#STASH} item in
 *     your own hideout registers its location (one of each, enforced),
 *   - breaking a fixture clears it and returns the item (stash contents are
 *     server-side, so nothing is lost),
 *   - right-clicking a portal opens Abyss entry; a stash opens its GUI.
 */
public final class HideoutFixtureListener implements Listener {

    private final AbyssPlugin plugin;
    public HideoutFixtureListener(AbyssPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String type = HideoutItems.typeOf(e.getItemInHand());
        if (type == null) return;

        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        UUID owner = HideoutManager.ownerOf(b.getWorld());
        if (owner == null || !owner.equals(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(Text.color("&cYou can only place that in your own hideout."));
            return;
        }

        HideoutFixtures fx = plugin.hideoutFixtures();
        if (HideoutItems.PORTAL.equals(type)) {
            if (existingValid(fx.portal(owner), b.getWorld(), Material.END_PORTAL_FRAME)) {
                e.setCancelled(true);
                p.sendMessage(Text.color("&7You already have an Abyss Portal placed. Break it first."));
                return;
            }
            fx.setPortal(owner, b);
            p.sendMessage(Text.color("&5&l✦ &7Abyss Portal placed. Right-click it to enter The Abyss."));
        } else if (HideoutItems.STASH.equals(type)) {
            if (existingValid(fx.stash(owner), b.getWorld(), Material.CHEST)) {
                e.setCancelled(true);
                p.sendMessage(Text.color("&7You already have an Abyss Stash placed. Break it first."));
                return;
            }
            fx.setStash(owner, b);
            p.sendMessage(Text.color("&6&l✦ &7Abyss Stash placed. Right-click it to open your storage."));
        }
    }

    /** True if a stored fixture coord still holds the expected block (else it's stale). */
    private boolean existingValid(int[] c, World w, Material mat) {
        return c != null && w.getBlockAt(c[0], c[1], c[2]).getType() == mat;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        UUID owner = HideoutManager.ownerOf(b.getWorld());
        if (owner == null) return;
        // Only the owner can dismantle a fixture. This also guards against the
        // protection listener (HIGHEST) cancelling a visitor's break only AFTER
        // this NORMAL-priority handler would otherwise have run.
        if (!owner.equals(e.getPlayer().getUniqueId())) return;

        HideoutFixtures fx = plugin.hideoutFixtures();
        if (fx.isPortal(b)) {
            fx.clearPortal(owner);
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), HideoutItems.portal());
        } else if (fx.isStash(b)) {
            fx.clearStash(owner);
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), HideoutItems.stash());
            // Stash contents persist server-side; breaking the block never loses them.
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        HideoutFixtures fx = plugin.hideoutFixtures();
        if (fx.isPortal(b)) {
            e.setCancelled(true);
            plugin.portalEntryGUI().openFor(e.getPlayer());
        } else if (fx.isStash(b)) {
            e.setCancelled(true);
            UUID owner = HideoutManager.ownerOf(b.getWorld());
            if (owner != null && owner.equals(e.getPlayer().getUniqueId())) {
                StashGUI.openFor(plugin, e.getPlayer());
            } else {
                e.getPlayer().sendMessage(Text.color("&7This isn't your stash."));
            }
        }
    }
}
