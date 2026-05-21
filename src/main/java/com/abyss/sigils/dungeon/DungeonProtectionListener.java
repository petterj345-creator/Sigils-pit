package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Locks down building inside dungeon PLAY instances.
 *
 * Worlds are named:
 *   abyss_inst_*  → a live, cloned dungeon instance players run (PROTECTED)
 *   abyss_tpl_*   → the editor template world admins build in (NOT protected)
 *
 * Rule the user asked for: nobody — including ops — may break or place blocks
 * inside a play instance. The editor world is left fully editable so admins
 * can still design dungeons there.
 *
 * We fire at HIGHEST priority (not MONITOR) so we actually cancel the event
 * before other plugins act on it, and we don't use ignoreCancelled so we still
 * enforce protection even if something else un-cancelled it.
 *
 * NOTE: the plugin itself places the forge/chest/return-portal blocks via the
 * Bukkit API (block.setType / world edits), which do NOT fire BlockBreak/Place
 * player events — so this protection never blocks the plugin's own placements.
 */
public final class DungeonProtectionListener implements Listener {

    private final AbyssPlugin plugin;
    public DungeonProtectionListener(AbyssPlugin plugin) { this.plugin = plugin; }

    /** True only for live play instances (abyss_inst_*), not editor worlds. */
    private boolean isPlayInstance(World w) {
        return w != null && w.getName().startsWith("abyss_inst_");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (isPlayInstance(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        if (isPlayInstance(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (isPlayInstance(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent e) {
        if (isPlayInstance(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }
}
