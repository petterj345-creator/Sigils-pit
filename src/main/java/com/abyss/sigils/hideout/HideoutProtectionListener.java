package com.abyss.sigils.hideout;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.UUID;

/**
 * Building rules inside hideouts (abyss_hideout_&lt;owner-uuid&gt;):
 *   - the OWNER may freely build (place/break/buckets),
 *   - everyone else (visitors) may not touch a single block.
 *
 * Fires at HIGHEST (not MONITOR) so we cancel before other plugins act, and we
 * don't use ignoreCancelled so protection holds even if something un-cancels.
 *
 * The plugin's own block edits go through the Bukkit block API, which doesn't
 * fire these player events, so this never blocks the plugin itself.
 */
public final class HideoutProtectionListener implements Listener {

    /** A protected hideout — a player's world, but NOT the admin editor template. */
    private boolean isProtected(World w) {
        return HideoutManager.isHideoutWorld(w) && !w.getName().equals(HideoutTemplate.WORLD);
    }

    /** True when {@code p} is allowed to edit blocks in {@code w} (owner only). */
    private boolean canBuild(Player p, World w) {
        UUID owner = HideoutManager.ownerOf(w);
        return owner != null && owner.equals(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (isProtected(e.getBlock().getWorld())
                && !canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        if (isProtected(e.getBlock().getWorld())
                && !canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (isProtected(e.getBlock().getWorld())
                && !canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (isProtected(e.getBlock().getWorld())
                && !canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }
}
