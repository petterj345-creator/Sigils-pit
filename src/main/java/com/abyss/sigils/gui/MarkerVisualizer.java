package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Renders client-side-only fake blocks at template markers so an admin can
 * actually SEE where they've placed things while editing.
 *
 * Uses {@code Player.sendBlockChange(...)} — the blocks aren't real on the
 * server, so they:
 *  - don't get copied when the template world is cloned into an instance
 *  - don't appear to players playing the dungeon
 *  - can be re-rendered every few seconds to recover from chunk reloads
 *
 * Markers used:
 *   Player spawn  → lime stained glass
 *   Boss spawn    → red stained glass
 *   Spawn point   → blue stained glass + magenta glass pane "beam" above
 *   Upgrade altar location (only shown if set in template)
 *
 * The visualizer runs a per-player tick task that refreshes the fake blocks
 * every 2 seconds (handles re-rendering after the player moves chunks).
 */
public final class MarkerVisualizer implements Listener {

    private final AbyssPlugin plugin;

    /** Per-player tick task that re-sends block changes. */
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    /** Track which locations we've rendered per player, so we can clear them later. */
    private final Map<UUID, Set<Location>> renderedBlocks = new HashMap<>();

    public MarkerVisualizer(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Start showing markers for a player in their current template world. */
    public void startFor(Player p, DungeonTemplate t) {
        stopFor(p); // cancel any old session
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> renderFor(p, t), 1L, 40L); // every 2 seconds
        tasks.put(p.getUniqueId(), task);
    }

    /** Stop showing markers + clear any rendered fake blocks for a player. */
    public void stopFor(Player p) {
        BukkitTask task = tasks.remove(p.getUniqueId());
        if (task != null) task.cancel();
        Set<Location> rendered = renderedBlocks.remove(p.getUniqueId());
        if (rendered != null && p.isOnline()) {
            for (Location l : rendered) {
                // Restore real block by sending the actual block back to this player
                try { p.sendBlockChange(l, l.getBlock().getBlockData()); }
                catch (Throwable ignored) {}
            }
        }
    }

    private void renderFor(Player p, DungeonTemplate t) {
        if (!p.isOnline() || !p.getWorld().getName().equals(t.worldName())) {
            stopFor(p);
            return;
        }
        Set<Location> newRendered = new HashSet<>();
        World w = p.getWorld();

        // Player spawn — lime glass under foot
        if (t.playerSpawn() != null) {
            Location l = bindBelowFeet(t.playerSpawn(), w);
            renderFake(p, l, Material.LIME_STAINED_GLASS, newRendered);
            renderFake(p, l.clone().add(0, 1, 0), Material.LIME_STAINED_GLASS_PANE, newRendered);
        }

        // Boss spawn — red glass + pane beam
        if (t.bossSpawn() != null) {
            Location l = bindBelowFeet(t.bossSpawn(), w);
            renderFake(p, l, Material.RED_STAINED_GLASS, newRendered);
            renderFake(p, l.clone().add(0, 1, 0), Material.RED_STAINED_GLASS_PANE, newRendered);
            renderFake(p, l.clone().add(0, 2, 0), Material.RED_STAINED_GLASS_PANE, newRendered);
        }

        // Spawn points — blue glass + magenta pane beam
        int i = 1;
        for (SpawnPoint sp : t.spawnPoints()) {
            Location loc = bindBelowFeet(sp.location(), w);
            renderFake(p, loc, Material.BLUE_STAINED_GLASS, newRendered);
            renderFake(p, loc.clone().add(0, 1, 0), Material.MAGENTA_STAINED_GLASS_PANE, newRendered);
            renderFake(p, loc.clone().add(0, 2, 0), Material.MAGENTA_STAINED_GLASS_PANE, newRendered);
            i++;
        }

        // Clear any old markers we rendered last tick but aren't rendering now
        Set<Location> oldRendered = renderedBlocks.getOrDefault(p.getUniqueId(), Collections.emptySet());
        for (Location oldLoc : oldRendered) {
            if (!newRendered.contains(oldLoc)) {
                try { p.sendBlockChange(oldLoc, oldLoc.getBlock().getBlockData()); }
                catch (Throwable ignored) {}
            }
        }
        renderedBlocks.put(p.getUniqueId(), newRendered);
    }

    private void renderFake(Player p, Location loc, Material mat, Set<Location> tracker) {
        try {
            BlockData data = mat.createBlockData();
            p.sendBlockChange(loc, data);
            tracker.add(loc);
        } catch (Throwable ignored) {}
    }

    /** Turn a "stand here" location (block + 0.5,1.0,0.5) into the block beneath their feet. */
    private static Location bindBelowFeet(Location l, World w) {
        Location bound = new Location(w,
                Math.floor(l.getX()),
                Math.floor(l.getY()) - 1,
                Math.floor(l.getZ()));
        return bound;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        // Stop markers when leaving an editor world
        Player p = e.getPlayer();
        if (!p.getWorld().getName().startsWith("abyss_tpl_")) {
            stopFor(p);
            return;
        }
        // Auto-start markers when entering an editor world
        if (p.hasPermission("abyss.admin")) {
            DungeonTemplate t = plugin.templates().get(
                    p.getWorld().getName().substring("abyss_tpl_".length()));
            if (t != null) startFor(p, t);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { stopFor(e.getPlayer()); }
}
