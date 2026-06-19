package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Personal, persistent player hideouts — a small bounded world each player can
 * build in (PoE-style). Unlike dungeon instances (abyss_inst_*, cloned fresh
 * every run and deleted on exit), a hideout is:
 *
 *   - created ONCE on first entry (a fresh flat world bounded by a world border),
 *   - SAVED and unloaded when the last occupant leaves (edits persist to disk),
 *   - NEVER deleted automatically — the folder is the player's permanent hideout.
 *
 * Worlds are named {@code abyss_hideout_<owner-uuid>} so ownership is encoded in
 * the world name and survives restarts with no extra bookkeeping.
 *
 * Only the owner may build inside their hideout (enforced by
 * {@link HideoutProtectionListener}); visitors can look but not touch.
 */
public final class HideoutManager implements Listener {

    public static final String PREFIX = "abyss_hideout_";

    private final AbyssPlugin plugin;
    /** Where each player was standing before they entered a hideout, so leave() returns them home. */
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public HideoutManager(AbyssPlugin plugin) { this.plugin = plugin; }

    private int borderSize() {
        return Math.max(8, plugin.getConfig().getInt("hideout.border-size", 48));
    }

    public static boolean isHideoutWorld(World w) {
        return w != null && w.getName().startsWith(PREFIX);
    }

    public boolean isInHideout(Player p) {
        return isHideoutWorld(p.getWorld());
    }

    /** The owning player's UUID for a hideout world, or null if the name doesn't parse. */
    public static UUID ownerOf(World w) {
        if (!isHideoutWorld(w)) return null;
        try { return UUID.fromString(w.getName().substring(PREFIX.length())); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static String worldName(UUID owner) { return PREFIX + owner; }

    // ============================================================
    // Entering / visiting
    // ============================================================

    /** Send a player to their own hideout, creating it on first use. */
    public void enterOwn(Player p) {
        goTo(p, p.getUniqueId(), true);
    }

    /**
     * Visit another player's hideout. The target must already have a hideout on
     * disk — we never create someone else's hideout for them.
     */
    public void visit(Player p, UUID owner, String ownerName) {
        if (owner.equals(p.getUniqueId())) { enterOwn(p); return; }
        if (Bukkit.getWorld(worldName(owner)) == null && !folderExists(owner)) {
            p.sendMessage(Text.color("&7" + ownerName + " doesn't have a hideout yet."));
            return;
        }
        goTo(p, owner, false);
        p.sendMessage(Text.color("&dYou step into &f" + ownerName + "&d's hideout. &7(You can't build here.)"));
    }

    private void goTo(Player p, UUID owner, boolean own) {
        if (plugin.dungeonManager().sessionOf(p) != null) {
            p.sendMessage(Text.color("&cLeave The Abyss before heading to a hideout."));
            return;
        }
        boolean firstTime = own && !folderExists(owner);
        World w = loadOrCreate(owner, own);
        if (w == null) {
            p.sendMessage(Text.color("&cCouldn't open that hideout."));
            return;
        }
        // Remember where they came from — but only if they're not already in a
        // hideout, so leaving always returns to the overworld, not another hideout.
        if (!isInHideout(p)) returnLocations.put(p.getUniqueId(), p.getLocation());

        Location spawn = w.getSpawnLocation().clone().add(0.5, 0, 0.5);
        p.teleport(spawn);
        if (own) p.sendMessage(Text.color("&5&l✦ &7Welcome to your hideout. &8/abyss leave &7to head out."));
        if (firstTime) {
            // Starter kit: the two placeable fixtures so they can set up the
            // portal and stash wherever they like.
            p.getInventory().addItem(HideoutItems.portal(), HideoutItems.stash());
            p.sendMessage(Text.color("&7Place your &5Abyss Portal &7and &6Abyss Stash &7to get started."));
        }
    }

    /**
     * Load the hideout world for {@code owner}, creating a fresh bounded world if
     * it doesn't exist on disk yet (only when {@code allowCreate}).
     */
    private World loadOrCreate(UUID owner, boolean allowCreate) {
        String name = worldName(owner);
        World existing = Bukkit.getWorld(name);
        if (existing != null) { applySettings(existing, false); return existing; }

        boolean firstTime = !folderExists(owner);
        if (firstTime && !allowCreate) return null;

        // First time: clone the designed starter template if one exists, so the
        // player begins with the admin-built layout (and its border/spawn).
        boolean clonedFromTemplate = false;
        if (firstTime && plugin.hideoutTemplate().worldExists()) {
            clonedFromTemplate = cloneTemplateInto(name);
        }

        WorldCreator wc = new WorldCreator(name);
        wc.environment(World.Environment.NORMAL);
        wc.type(WorldType.FLAT);
        wc.generateStructures(false);
        World w = Bukkit.createWorld(wc);
        if (w == null) return null;

        // Only pick a default spawn for a freshly-generated flat world; a clone
        // already carries the template's spawn (overridden below if configured).
        if (firstTime && !clonedFromTemplate) {
            int y = w.getHighestBlockYAt(0, 0) + 1;
            w.setSpawnLocation(0, y, 0);
        }
        applySettings(w, firstTime);
        return w;
    }

    /** Copy the template world's folder into a fresh hideout folder. Returns success. */
    private boolean cloneTemplateInto(String newName) {
        World tpl = Bukkit.getWorld(HideoutTemplate.WORLD);
        if (tpl != null) tpl.save();   // flush latest edits before copying
        File src = new File(Bukkit.getWorldContainer(), HideoutTemplate.WORLD);
        File dst = new File(Bukkit.getWorldContainer(), newName);
        if (!src.isDirectory()) return false;
        try {
            if (dst.exists()) deleteRecursive(dst.toPath());
            copyDir(src.toPath(), dst.toPath());
            new File(dst, "uid.dat").delete();
            new File(dst, "session.lock").delete();
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to clone hideout template: " + e.getMessage());
            return false;
        }
    }

    /** Game rules, border, and spawn. Re-applied on every load so edits take effect. */
    private void applySettings(World w, boolean firstTime) {
        w.setAutoSave(true);                 // flush edits to disk
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setTime(6000);

        HideoutTemplate tpl = plugin.hideoutTemplate();
        WorldBorder border = w.getWorldBorder();
        if (tpl.hasBorder()) {
            border.setCenter(tpl.borderCenterX(), tpl.borderCenterZ());
            border.setSize(tpl.borderSize());
        } else {
            border.setCenter(0.5, 0.5);
            border.setSize(borderSize());
        }
        // Keep each hideout's spawn aligned with the configured template spawn.
        if (tpl.hasSpawn()) {
            Location s = tpl.spawnIn(w);
            if (s != null) w.setSpawnLocation(s);
        }
    }

    private static void copyDir(java.nio.file.Path src, java.nio.file.Path dst) throws IOException {
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(src)) {
            stream.forEach(p -> {
                try {
                    java.nio.file.Path rel = src.relativize(p);
                    String n = rel.getFileName() == null ? "" : rel.getFileName().toString();
                    if (n.equals("session.lock") || n.equals("uid.dat")) return;
                    java.nio.file.Path target = dst.resolve(rel);
                    if (java.nio.file.Files.isDirectory(p)) java.nio.file.Files.createDirectories(target);
                    else {
                        java.nio.file.Files.createDirectories(target.getParent());
                        java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) { throw new RuntimeException(ex); }
            });
        }
    }

    private static void deleteRecursive(java.nio.file.Path p) throws IOException {
        if (!java.nio.file.Files.exists(p)) return;
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(p)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try { java.nio.file.Files.delete(f); } catch (IOException ignored) {}
            });
        }
    }

    private boolean folderExists(UUID owner) {
        File dir = new File(Bukkit.getWorldContainer(), worldName(owner));
        return dir.isDirectory();
    }

    // ============================================================
    // Leaving
    // ============================================================

    /** Teleport a player out of a hideout, back where they came from. */
    public void leave(Player p) {
        if (!isInHideout(p)) { p.sendMessage(Text.color("&7You're not in a hideout.")); return; }
        String worldName = p.getWorld().getName();
        Location back = returnLocations.remove(p.getUniqueId());
        if (back == null || back.getWorld() == null || isHideoutWorld(back.getWorld())) {
            back = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        p.teleport(back);
        p.sendMessage(Text.color("&7You leave the hideout."));
        scheduleUnloadIfEmpty(worldName);
    }

    /** If a player logs out inside a hideout, unload it once it's empty. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (isInHideout(e.getPlayer())) {
            returnLocations.remove(e.getPlayer().getUniqueId());
            scheduleUnloadIfEmpty(e.getPlayer().getWorld().getName());
        }
    }

    /**
     * Save and unload a hideout world a short while after it appears empty.
     * Hideouts are persistent, so we unload with save = true and never delete
     * the folder.
     */
    private void scheduleUnloadIfEmpty(String worldName) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            if (w == null || !w.getPlayers().isEmpty()) return;
            w.save();
            Bukkit.unloadWorld(w, true);
        }, 60L);
    }

    /** Save every loaded hideout on shutdown so the latest edits aren't lost. */
    public void saveAll() {
        for (World w : Bukkit.getWorlds()) {
            if (isHideoutWorld(w)) {
                w.save();
                Bukkit.unloadWorld(w, true);
            }
        }
    }
}
