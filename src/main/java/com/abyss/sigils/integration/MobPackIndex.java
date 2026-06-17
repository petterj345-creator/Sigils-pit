package com.abyss.sigils.integration;

import org.bukkit.Bukkit;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps each MythicMob internal name to the pack folder it lives in, if any.
 *
 * MythicMobs lets mobs live either at the top level
 * ({@code plugins/MythicMobs/Mobs/}) or inside a pack
 * ({@code plugins/MythicMobs/Packs/<pack>/...}). The {@link io.lumine.mythic.api.mobs.MythicMob}
 * API doesn't expose which file/pack a mob came from, so we recover it by
 * scanning the {@code Packs/} directory on disk: any mob defined under a pack
 * is recorded with that pack's name.
 *
 * Used by the mob-browsing GUIs to list pack mobs (grouped by pack) before
 * everything else. Results are cached with a short TTL so paging/searching
 * doesn't re-scan disk on every click, while a {@code /mm reload} is still
 * reflected within a few seconds.
 */
public final class MobPackIndex {

    private MobPackIndex() {}

    /** Cache TTL: long enough to cover a browsing session, short enough to refresh after a reload. */
    private static final long TTL_MS = 10_000L;

    private static volatile Map<String, String> cache = null;
    private static volatile long builtAt = 0L;

    /**
     * Pack name for {@code internalName}, or {@code null} if the mob is not in a
     * pack (or MythicMobs / its data folder can't be found). Case-insensitive.
     */
    public static String packOf(String internalName) {
        if (internalName == null) return null;
        return index().get(internalName.toLowerCase(Locale.ROOT));
    }

    /** Force the next lookup to re-scan disk (e.g. after editing mob files). */
    public static void invalidate() {
        cache = null;
    }

    /**
     * Browsing order: mobs that live in a pack first (grouped by pack name),
     * then everything else; alphabetical by internal name within each group.
     * Both arguments are mob internal names.
     */
    public static int comparePackThenName(String a, String b) {
        String pa = packOf(a);
        String pb = packOf(b);
        if ((pa != null) != (pb != null)) return pa != null ? -1 : 1; // pack mobs first
        if (pa != null && !pa.equalsIgnoreCase(pb)) return pa.compareToIgnoreCase(pb); // group by pack
        return a.compareToIgnoreCase(b);
    }

    private static Map<String, String> index() {
        Map<String, String> c = cache;
        if (c != null && (System.currentTimeMillis() - builtAt) < TTL_MS) return c;
        synchronized (MobPackIndex.class) {
            c = cache;
            if (c != null && (System.currentTimeMillis() - builtAt) < TTL_MS) return c;
            c = build();
            cache = c;
            builtAt = System.currentTimeMillis();
            return c;
        }
    }

    private static Map<String, String> build() {
        Map<String, String> out = new HashMap<>();
        try {
            org.bukkit.plugin.Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (mm == null) return out;
            File root = mm.getDataFolder();
            if (root == null || !root.isDirectory()) return out;
            File packs = firstChild(root, "Packs");
            if (packs == null) return out;
            File[] packDirs = packs.listFiles(File::isDirectory);
            if (packDirs == null) return out;
            for (File pack : packDirs) {
                collectMobs(pack, pack.getName(), out);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Child directory of {@code dir} named {@code name} (case-insensitive), or null. */
    private static File firstChild(File dir, String name) {
        File[] kids = dir.listFiles(File::isDirectory);
        if (kids == null) return null;
        for (File f : kids) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    /** Record every top-level mob key in every .yml under {@code dir} as belonging to {@code pack}. */
    private static void collectMobs(File dir, String pack, Map<String, String> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) { collectMobs(f, pack, out); continue; }
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) continue;
            collectTopLevelKeys(f, pack, out);
        }
    }

    /**
     * Add each top-level YAML key in {@code f} to the map (lower-cased) → pack.
     *
     * We scan raw text rather than parsing: MythicMobs mob files are frequently
     * not strict YAML, so SnakeYAML would silently drop them. A top-level key is
     * a line with no leading whitespace whose text before the first ':' is the
     * mob name (quotes tolerated).
     */
    private static void collectTopLevelKeys(File f, String pack, Map<String, String> out) {
        try {
            for (String raw : Files.readAllLines(f.toPath())) {
                if (raw.isEmpty() || Character.isWhitespace(raw.charAt(0))) continue; // not top-level
                if (raw.stripLeading().startsWith("#")) continue;                     // comment
                int colon = raw.indexOf(':');
                if (colon <= 0) continue;
                String name = raw.substring(0, colon).strip();
                if (name.length() >= 2
                        && (name.charAt(0) == '"' || name.charAt(0) == '\'')
                        && name.charAt(name.length() - 1) == name.charAt(0)) {
                    name = name.substring(1, name.length() - 1);
                }
                if (!name.isEmpty()) out.putIfAbsent(name.toLowerCase(Locale.ROOT), pack);
            }
        } catch (Throwable ignored) {}
    }
}
