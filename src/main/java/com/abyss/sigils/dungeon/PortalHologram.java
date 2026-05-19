package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Floating "⛧ Abyss Portal" label above the configured portal block.
 *
 * Design:
 *  - On plugin enable we read portal.world/x/y/z from config and spawn a
 *    {@link TextDisplay} 1.6 blocks above the block.
 *  - The display is non-persistent — it lives only while chunks are loaded.
 *    Bukkit/Paper would otherwise save it forever and we'd accumulate duplicates
 *    every config reload. We re-spawn it whenever the portal chunk loads.
 *  - We tag it with a PDC key so we can find and kill stale instances after
 *    plugin reload (and from old plugin versions that may have left one).
 *  - {@link #refreshFromConfig()} is callable from {@code /abyss reload} so
 *    moving the portal in config takes effect immediately.
 *
 * If the portal block isn't currently in a loaded chunk we wait for the
 * matching ChunkLoadEvent before spawning, so this works after server restarts
 * regardless of spawn-chunks settings.
 */
public final class PortalHologram implements Listener {

    /** PDC tag so we can recognise our own holograms and clean stale ones. */
    public static final NamespacedKey KEY_PORTAL_HOLO =
            new NamespacedKey("abyss", "portal_hologram");

    private final AbyssPlugin plugin;
    public PortalHologram(AbyssPlugin plugin) { this.plugin = plugin; }

    /**
     * Called on plugin enable AND any time the portal config changes. Wipes
     * any existing hologram (in any loaded world) and re-spawns at the new
     * configured location if its chunk is loaded.
     */
    public void refreshFromConfig() {
        // Always clear out old holograms across all loaded worlds first —
        // this catches config moves, stale entities from previous reloads,
        // and any duplicates that snuck in.
        for (World w : Bukkit.getWorlds()) removeHologramsIn(w);

        Location loc = portalLocation();
        if (loc == null) {
            plugin.getLogger().info("Portal hologram: no portal.world/x/y/z configured, skipping.");
            return;
        }
        // Only spawn now if the chunk is loaded; otherwise the onChunkLoad
        // handler below will pick it up when the chunk first loads.
        if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            spawnAt(loc);
        }
    }

    /**
     * Read the configured portal location into a Bukkit Location, or null if
     * the world doesn't exist or required fields are missing.
     */
    private Location portalLocation() {
        String worldName = plugin.getConfig().getString("portal.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        if (!plugin.getConfig().isSet("portal.x")) return null;
        double x = plugin.getConfig().getInt("portal.x") + 0.5;
        double y = plugin.getConfig().getInt("portal.y") + 1.6; // float above block
        double z = plugin.getConfig().getInt("portal.z") + 0.5;
        return new Location(world, x, y, z);
    }

    /** Actually spawn the floating label. */
    private void spawnAt(Location loc) {
        loc.getWorld().spawn(loc, TextDisplay.class, e -> {
            e.setText(Text.color("&5&l⛧ &d&lAbyss Portal &5&l⛧"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setSeeThrough(true);
            e.setDefaultBackground(false);
            e.setBackgroundColor(Color.fromARGB(180, 20, 0, 30));
            // Non-persistent so chunk unload kills it cleanly — we'll respawn
            // it on the next chunk load. Without this, every reload would
            // accumulate ghost copies.
            e.setPersistent(false);
            e.getPersistentDataContainer().set(KEY_PORTAL_HOLO, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Strip any of our hologram entities from a world. */
    private void removeHologramsIn(World w) {
        for (Entity ent : w.getEntities()) {
            if (ent instanceof TextDisplay
                    && ent.getPersistentDataContainer().has(KEY_PORTAL_HOLO, PersistentDataType.BYTE)) {
                ent.remove();
            }
        }
    }

    /**
     * When the chunk containing the configured portal loads, make sure the
     * hologram exists in it. Handles server start, world loading, and players
     * loading the chunk for the first time after a restart.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Location loc = portalLocation();
        if (loc == null) return;
        if (!loc.getWorld().equals(e.getWorld())) return;
        if (e.getChunk().getX() != (loc.getBlockX() >> 4)) return;
        if (e.getChunk().getZ() != (loc.getBlockZ() >> 4)) return;
        // Check if a hologram is already present in this chunk (defensive).
        for (Entity ent : e.getChunk().getEntities()) {
            if (ent instanceof TextDisplay
                    && ent.getPersistentDataContainer().has(KEY_PORTAL_HOLO, PersistentDataType.BYTE)) {
                return; // already there
            }
        }
        spawnAt(loc);
    }

    /** Handles the portal world being loaded after our plugin enabled. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        Location loc = portalLocation();
        if (loc == null) return;
        if (!loc.getWorld().equals(e.getWorld())) return;
        if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            removeHologramsIn(e.getWorld());
            spawnAt(loc);
        }
    }
}
