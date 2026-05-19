package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

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
        File mobsDir = new File(plugin.getServer().getWorldContainer(),
                "plugins" + File.separator + "MythicMobs" + File.separator + "Mobs");
        if (!mobsDir.exists()) {
            mobsDir = new File("plugins" + File.separator + "MythicMobs" + File.separator + "Mobs");
        }
        if (!mobsDir.exists() || !mobsDir.isDirectory()) {
            plugin.getLogger().warning("MythicMobs Mobs/ folder not found at " + mobsDir.getAbsolutePath());
            return null;
        }
        return findRecursive(mobsDir, internalName);
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
            if (!f.getName().endsWith(".yml") && !f.getName().endsWith(".yaml")) continue;
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                if (cfg.contains(key)) return f;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Append a single sigil drop line to a mob's Drops: list. Returns true on success.
     */
    public boolean appendSigilDrop(String mobInternalName, String sigilId, int tier,
                                   int amount, double chance) {
        File file = findMobFile(mobInternalName);
        if (file == null) {
            plugin.getLogger().warning("Couldn't find MythicMob '" + mobInternalName + "' in any file.");
            return false;
        }

        // Build the drop string. Match MythicMobs Drops syntax:
        //   "abyss_sigil{id=wrath;tier=2} 1 0.25"
        String dropLine = "abyss_sigil{id=" + sigilId + ";tier=" + tier + "} "
                + amount + " " + chance;

        // We hand-edit the YAML file as text so we don't touch ANYTHING else in it
        // (some Mythic configs contain special MM-only constructs that Bukkit's
        // YamlConfiguration may reformat in a lossy way).
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath()));
            int insertIndex = findInsertIndex(lines, mobInternalName);
            if (insertIndex < 0) {
                plugin.getLogger().warning("Couldn't locate mob entry '" + mobInternalName + "' in file.");
                return false;
            }
            // Insert with 4 leading spaces of indentation under Drops:
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
