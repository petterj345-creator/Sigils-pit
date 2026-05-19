package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages visual markers shown to admins while editing a template.
 *
 *  - PLAYER spawn → green wool block + "Player Spawn" floating text
 *  - BOSS spawn   → red wool block + "Boss Spawn" floating text
 *  - SPAWN POINTS → blue wool blocks + "Spawn Point #N" floating text
 *
 * Markers are BlockDisplay + TextDisplay entities, tagged with our PDC key so
 * they can be (a) cleaned up on world unload, (b) stripped from cloned instance
 * worlds before the world is loaded.
 *
 * They are spawned only while at least one admin is in the template world, and
 * removed when the last admin leaves.
 */
public final class EditorMarkers implements Listener {

    public static final NamespacedKey KEY_MARKER = new NamespacedKey(AbyssPlugin.get(), "editor_marker");

    private final AbyssPlugin plugin;
    /** Set of worlds we've populated markers for. */
    private final Set<String> markedWorlds = new HashSet<>();

    public EditorMarkers(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Spawn markers in the template world for the given template. Idempotent — clears + respawns. */
    public void refreshFor(DungeonTemplate t) {
        World w = Bukkit.getWorld(t.worldName());
        if (w == null) return;
        clearFor(w);
        markedWorlds.add(w.getName());

        // Player spawn
        if (t.playerSpawn() != null) {
            Location l = boundTo(t.playerSpawn(), w);
            spawnMarker(l, Material.LIME_WOOL, "§a§lPlayer Spawn");
        }
        // Boss spawn
        if (t.bossSpawn() != null) {
            Location l = boundTo(t.bossSpawn(), w);
            spawnMarker(l, Material.RED_WOOL, "§c§lBoss Spawn");
        }
        // Spawn points
        List<SpawnPoint> sps = t.spawnPoints();
        for (int i = 0; i < sps.size(); i++) {
            Location l = sps.get(i).boundTo(w);
            int mobCount = sps.get(i).mobs().size();
            spawnMarker(l, Material.LIGHT_BLUE_WOOL,
                    "§b§lSpawn Point #" + (i + 1)
                    + (mobCount > 0 ? " §7(" + mobCount + " mob entries)" : " §8(empty)"));
        }
    }

    /** Spawn one block-display + text-display pair at a location. */
    private void spawnMarker(Location loc, Material wool, String label) {
        // Block sits at the location with a 1×1×1 cube offset by 0.5 to center it
        Location blockLoc = loc.clone();
        // Most spawn locations have y = block.y + 1 (we stand on top of a block),
        // so the *visual* should be the block under the player's feet — drop y by 1.
        blockLoc.setY(blockLoc.getY() - 1.0);
        blockLoc.setX(Math.floor(blockLoc.getX()));
        blockLoc.setZ(Math.floor(blockLoc.getZ()));
        blockLoc.setY(Math.floor(blockLoc.getY()));
        BlockData data = wool.createBlockData();

        blockLoc.getWorld().spawn(blockLoc, BlockDisplay.class, e -> {
            e.setBlock(data);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setGlowing(true);
            e.setGlowColorOverride(switch (wool) {
                case LIME_WOOL       -> Color.LIME;
                case RED_WOOL        -> Color.RED;
                case LIGHT_BLUE_WOOL -> Color.AQUA;
                default              -> Color.WHITE;
            });
            // CRITICAL: don't persist these to disk. If they were persistent, the
            // template world would save them, the dungeon clone would inherit them,
            // and players running the dungeon would see "Boss Spawn"/"Player Spawn"
            // wool blocks and floating text in the actual instance. By marking the
            // entity non-persistent it gets discarded on chunk unload and is
            // never written to region files.
            e.setPersistent(false);
            e.getPersistentDataContainer().set(KEY_MARKER, PersistentDataType.BYTE, (byte) 1);
        });

        // Text floats 1.5 blocks above the visual block
        Location textLoc = blockLoc.clone().add(0.5, 1.7, 0.5);
        textLoc.getWorld().spawn(textLoc, TextDisplay.class, e -> {
            e.setText(label);
            e.setBillboard(Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setSeeThrough(true);
            e.setDefaultBackground(false);
            e.setBackgroundColor(Color.fromARGB(180, 0, 0, 0));
            // Same reason as the block display above — never persist to disk.
            e.setPersistent(false);
            e.getPersistentDataContainer().set(KEY_MARKER, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Remove all marker entities from a world. */
    public void clearFor(World w) {
        if (w == null) return;
        for (Entity e : w.getEntities()) {
            if (e.getPersistentDataContainer().has(KEY_MARKER, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        markedWorlds.remove(w.getName());
    }

    /** True if any admin is still in this template world. */
    private boolean anyAdminIn(World w) {
        for (Player p : w.getPlayers()) {
            if (p.hasPermission("abyss.admin")) return true;
        }
        return false;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        // Entering a template world → spawn markers if not already shown
        if (p.getWorld().getName().startsWith("abyss_tpl_") && p.hasPermission("abyss.admin")) {
            String name = p.getWorld().getName().substring("abyss_tpl_".length());
            DungeonTemplate t = plugin.templates().get(name);
            if (t != null && !markedWorlds.contains(p.getWorld().getName())) {
                refreshFor(t);
            }
        }
        // Leaving a template world → clear if no admin is left
        World from = e.getFrom();
        if (from.getName().startsWith("abyss_tpl_") && !anyAdminIn(from)) {
            clearFor(from);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        World w = e.getPlayer().getWorld();
        if (w.getName().startsWith("abyss_tpl_")) {
            // Run on next tick — the player is technically still counted in w.getPlayers() until removal
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!anyAdminIn(w)) clearFor(w);
            });
        }
    }

    /**
     * Strip all marker entities from a freshly-cloned instance world.
     * Called by DungeonManager right after the clone is loaded.
     */
    public void stripFromInstance(World w) { clearFor(w); }

    /**
     * Strip stale marker entities as chunks load.
     *
     * - In instance worlds (abyss_inst_*) markers should never appear, so we
     *   kill anything tagged as a marker.
     * - In template worlds (abyss_tpl_*) we also kill them on chunk load,
     *   because markers persisted to disk from older plugin versions need to
     *   be wiped — {@link #refreshFor} will re-spawn the current ones (non-
     *   persistently now) for any admin who's in the world.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        String name = e.getWorld().getName();
        boolean instance = name.startsWith("abyss_inst_");
        boolean template = name.startsWith("abyss_tpl_");
        if (!instance && !template) return;
        Chunk c = e.getChunk();
        for (Entity entity : c.getEntities()) {
            if (entity.getPersistentDataContainer().has(KEY_MARKER, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    private Location boundTo(Location l, World w) {
        Location bound = l.clone();
        bound.setWorld(w);
        return bound;
    }
}
