package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Helper for writing drops into MythicMobs YAML files.
 *
 * Each MythicMob has an internal name (e.g. "AbyssOverlord"). The MythicMobs
 * plugin keeps mob YAML files under plugins/MythicMobs/Mobs/. There may be many
 * files; mob definitions are keys at the top level of those files.
 *
 * Algorithm:
 *  1. Walk plugins/MythicMobs/Mobs/ recursively for *.yml.
 *  2. For each file, load as YamlConfiguration; if it has a top-level key
 *     matching the mob internal name, that's the file.
 *  3. Read its `Drops:` list (if any), append our new drop line, save.
 *  4. Issue `/mm reload` so MythicMobs picks up changes.
 *
 * Each drop line written looks like:
 *     abyss_sigil{id=&lt;id&gt;;tier=&lt;tier&gt;} &lt;amount&gt; &lt;chance&gt;
 *
 * We never delete other drops — only append.
 */
public final class MythicDropWriter {

    private final AbyssPlugin plugin;
    public MythicDropWriter(AbyssPlugin plugin) { this.plugin = plugin; }

    /** All MythicMobs currently loaded. */
    public Collection<MythicMob> allMythicMobs() {
        try { return MythicBukkit.inst().getMobManager().getMobTypes(); }
        catch (Throwable t) { return Collections.emptyList(); }
    }

    /** Find which file on disk contains the given mob. Returns null if not found. */
    public File findMobFile(String internalName) {
        // Use MythicMobs' own data folder — guaranteed correct on any server setup.
        org.bukkit.plugin.Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mm == null) {
            plugin.getLogger().warning("MythicMobs plugin not loaded.");
            return null;
        }
        File root = mm.getDataFolder();
        if (root == null || !root.isDirectory()) {
            plugin.getLogger().warning("MythicMobs data folder not found.");
            return null;
        }

        // Mobs can live in plugins/MythicMobs/Mobs/ OR inside packs at
        // plugins/MythicMobs/Packs/<pack>/Mobs/. Filenames/casing vary and Linux
        // is case-sensitive, so search every "Mobs" directory first (top-level
        // and inside Packs), then fall back to scanning the WHOLE MythicMobs
        // data folder so the mob is found no matter where it's defined.
        List<File> roots = new ArrayList<>();
        collectMobsDirs(root, roots); // preferred: dedicated Mobs/ folders
        roots.add(root);              // fallback: anywhere under the data folder

        for (File r : roots) {
            File found = findRecursive(r, internalName);
            if (found != null) return found;
        }
        plugin.getLogger().warning("No file under " + root.getAbsolutePath()
                + " defines MythicMob '" + internalName + "'.");
        return null;
    }

    /** Collect every directory named "Mobs" (case-insensitive) under {@code dir}. */
    private void collectMobsDirs(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (!f.isDirectory()) continue;
            if (f.getName().equalsIgnoreCase("Mobs")) out.add(f);
            collectMobsDirs(f, out); // descend so Packs/<pack>/Mobs is found too
        }
    }

    private File findRecursive(File dir, String key) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isDirectory()) {
                File r = findRecursive(f, key);
                if (r != null) return r;
                continue;
            }
            String lower = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) continue;
            if (fileDefinesMob(f, key)) return f;
        }
        return null;
    }

    /**
     * True if the file defines a top-level mob with the given internal name.
     *
     * We scan the raw text rather than parsing as YAML: MythicMobs mob files
     * frequently aren't valid strict-YAML to Bukkit's SnakeYAML (skill syntax
     * like {@code ~onAttack}, unquoted colons in values, etc.), in which case
     * {@code YamlConfiguration.loadConfiguration} silently returns an empty
     * config and the mob looks "missing". A top-level mob key is a line with no
     * leading whitespace whose text before the first ':' equals the name.
     */
    private boolean fileDefinesMob(File f, String key) {
        try {
            for (String raw : Files.readAllLines(f.toPath())) {
                if (raw.isEmpty() || Character.isWhitespace(raw.charAt(0))) continue; // not top-level
                if (raw.stripLeading().startsWith("#")) continue;                     // comment
                int colon = raw.indexOf(':');
                if (colon <= 0) continue;
                String name = raw.substring(0, colon).strip();
                // tolerate quoted keys: "AbyssOverlord": or 'AbyssOverlord':
                if (name.length() >= 2
                        && (name.charAt(0) == '"' || name.charAt(0) == '\'')
                        && name.charAt(name.length() - 1) == name.charAt(0)) {
                    name = name.substring(1, name.length() - 1);
                }
                if (name.equals(key)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Append a single sigil drop line to a mob's Drops: list. Returns true on success.
     */
    public boolean appendSigilDrop(String mobInternalName, String sigilId, int tier,
                                   int amount, double chance) {
        return appendDrop(mobInternalName,
                "abyss_sigil{id=" + sigilId + ";tier=" + tier + "} " + amount + " " + chance);
    }

    /**
     * Append an Abyss Map drop line to a mob's Drops: list. The map is for the
     * given template; whoever kills the mob has a `chance` (0..1) of getting
     * one. Returns true on success.
     */
    public boolean appendMapDrop(String mobInternalName, String templateName,
                                 int amount, double chance) {
        return appendDrop(mobInternalName,
                "abyss_map{template=" + templateName + "} " + amount + " " + chance);
    }

    /** Shared logic for appending any drop line under a mob's Drops: list. */
    private boolean appendDrop(String mobInternalName, String dropLine) {
        File file = findMobFile(mobInternalName);
        if (file == null) {
            plugin.getLogger().warning("Couldn't find MythicMob '" + mobInternalName + "' in any file.");
            return false;
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath()));
            int insertIndex = findInsertIndex(lines, mobInternalName);
            if (insertIndex < 0) {
                plugin.getLogger().warning("Couldn't locate mob entry '" + mobInternalName + "' in file.");
                return false;
            }
            String indented = "    - " + dropLine;
            lines.add(insertIndex, indented);
            Files.write(file.toPath(), lines);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds where to insert a new drop line.
     *
     *  - If the mob has an existing `Drops:` block, insert as the first child entry.
     *  - If it doesn't, append `  Drops:` after the mob key, then a `    - ` child.
     *
     * Returns the line index to insert AT (existing line at that index gets pushed down).
     * Returns -1 if the mob couldn't be found.
     */
    private int findInsertIndex(List<String> lines, String mobName) {
        int mobStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).stripTrailing();
            if (trimmed.startsWith(mobName + ":") || trimmed.equals(mobName + ":")) {
                mobStart = i; break;
            }
        }
        if (mobStart < 0) return -1;

        // Look for Drops: under this mob
        for (int i = mobStart + 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            String stripped = raw.stripLeading();
            // End of mob block when we hit a non-indented line that isn't blank
            if (!raw.isBlank() && !Character.isWhitespace(raw.charAt(0))) break;
            if (stripped.startsWith("Drops:")) {
                // Insert right after this line
                return i + 1;
            }
        }
        // No existing Drops block — create one at the end of the mob block.
        // We append AFTER the last non-blank indented line.
        int insert = mobStart + 1;
        for (int i = mobStart + 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (!raw.isBlank() && !Character.isWhitespace(raw.charAt(0))) break;
            if (!raw.isBlank()) insert = i + 1;
        }
        lines.add(insert, "  Drops:");
        return insert + 1;
    }

    /** Triggers a MythicMobs reload (without a server restart). */
    public void reloadMythic() {
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm reload"));
    }
}
