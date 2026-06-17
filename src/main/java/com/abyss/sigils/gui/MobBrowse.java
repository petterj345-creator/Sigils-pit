package com.abyss.sigils.gui;

import com.abyss.sigils.integration.MobPackIndex;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Shared view model for the MythicMob browser GUIs.
 *
 * The browsers ({@link MobPickerGUI}, {@link MapDropMobPickerGUI},
 * {@link MythicDropsGUI}) all show the same thing: at the root, the MythicMobs
 * packs as clickable folders first, then the loose (non-pack) mobs; clicking a
 * folder drills into that pack; a search matches across every mob regardless of
 * pack. This class computes the entries for the current view so each GUI only
 * has to render and wire up clicks.
 *
 * An entry is either a {@link String} (a pack name → a folder) or a
 * {@link MythicMob} (a pickable mob).
 */
final class MobBrowse {

    private MobBrowse() {}

    /** Material used for pack folder icons. */
    static final Material FOLDER_MATERIAL = Material.CHEST;

    /**
     * Entries to show for the current view.
     *
     * - Searching: flat list of every matching mob (packs-first ordering), so a
     *   search always finds mobs inside folders too.
     * - Root (no pack): pack folders first, then loose mobs.
     * - Inside a pack: that pack's mobs, alphabetical.
     */
    static List<Object> entries(String search, String pack) {
        List<MythicMob> all = allMobs();

        if (search != null && !search.isEmpty()) {
            String q = search.toLowerCase(Locale.ROOT);
            List<MythicMob> hits = new ArrayList<>();
            for (MythicMob m : all) {
                if (m.getInternalName().toLowerCase(Locale.ROOT).contains(q)
                        || MobPickerGUI.niceName(m).toLowerCase(Locale.ROOT).contains(q)) {
                    hits.add(m);
                }
            }
            hits.sort((a, b) -> MobPackIndex.comparePackThenName(a.getInternalName(), b.getInternalName()));
            return new ArrayList<>(hits);
        }

        if (pack == null || pack.isEmpty()) {
            List<Object> out = new ArrayList<>();
            TreeSet<String> packs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (MythicMob m : all) {
                String p = MobPackIndex.packOf(m.getInternalName());
                if (p != null) packs.add(p);
            }
            out.addAll(packs); // folders first
            List<MythicMob> loose = new ArrayList<>();
            for (MythicMob m : all) {
                if (MobPackIndex.packOf(m.getInternalName()) == null) loose.add(m);
            }
            loose.sort((a, b) -> a.getInternalName().compareToIgnoreCase(b.getInternalName()));
            out.addAll(loose);
            return out;
        }

        List<MythicMob> inPack = new ArrayList<>();
        for (MythicMob m : all) {
            if (pack.equalsIgnoreCase(MobPackIndex.packOf(m.getInternalName()))) inPack.add(m);
        }
        inPack.sort((a, b) -> a.getInternalName().compareToIgnoreCase(b.getInternalName()));
        return new ArrayList<>(inPack);
    }

    /** How many mobs live in the given pack (for folder lore). */
    static int packMobCount(String pack) {
        int n = 0;
        for (MythicMob m : allMobs()) {
            if (pack.equalsIgnoreCase(MobPackIndex.packOf(m.getInternalName()))) n++;
        }
        return n;
    }

    /** All loaded MythicMobs, or an empty list if MythicMobs isn't available. */
    static List<MythicMob> allMobs() {
        try { return new ArrayList<>(MythicBukkit.inst().getMobManager().getMobTypes()); }
        catch (Throwable t) { return new ArrayList<>(); }
    }
}
