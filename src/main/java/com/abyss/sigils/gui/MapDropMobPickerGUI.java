package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.integration.MobPackIndex;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Browse all MythicMobs to pick which one should drop an Abyss Map for a
 * specific template. Mirrors {@link MythicDropsGUI} (which targets sigil
 * drops); the only differences are the title, the destination GUI on click,
 * and the template binding.
 */
public final class MapDropMobPickerGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;
    private String search = "";
    private int page = 0;

    private static final int PAGE_SIZE = 28;
    private static final int SEARCH_SLOT = 4;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int CLEAR_SLOT = 53;
    private static final int BACK_SLOT = 45;

    public MapDropMobPickerGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new MapDropMobPickerGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return search.isEmpty()
                ? color("&5&lPick mob → Map drop &7(" + template.name() + ")")
                : color("&5Search: &f" + search);
    }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();
        List<MythicMob> mobs = filtered();
        if (mobs.isEmpty()) {
            set(22, icon(Material.BARRIER, "&cNo MythicMobs match",
                    "&7Make sure MythicMobs is installed",
                    "&7and the search isn't too narrow."),
                null);
        }

        int totalPages = Math.max(1, (mobs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        set(SEARCH_SLOT, icon(Material.WRITABLE_BOOK,
                search.isEmpty() ? "&fSearch..." : "&fSearch: &e" + search,
                "&7" + mobs.size() + " mob(s) match",
                "",
                "&eClick &7to type a query"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fSearch mobs",
                        search.isEmpty() ? "" : search,
                        text -> {
                            search = text == null ? "" : text.trim();
                            page = 0;
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                        });
            });

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mobs.size());
        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            final MythicMob m = mobs.get(i);
            String pack = MobPackIndex.packOf(m.getInternalName());
            ItemStack ic = icon(Material.ZOMBIE_HEAD,
                    "&f" + MobPickerGUI.niceName(m),
                    "&7Internal ID: &8" + m.getInternalName(),
                    pack != null ? "&dPack: &7" + pack : "&8(no pack)",
                    "",
                    "&eClick &7to configure map drop");
            set(slot, ic, e -> MobMapDropGUI.openFor(plugin, (Player) e.getWhoClicked(), m, template));
            slot++;
        }

        set(BACK_SLOT, icon(Material.ARROW, "&7← Back to template editor"),
            e -> TemplateEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        if (page > 0) {
            set(PREV_SLOT, icon(Material.SPECTRAL_ARROW, "&7← Page " + page),
                e -> { page--; refresh((Player) e.getWhoClicked()); });
        }
        if (page < totalPages - 1) {
            set(NEXT_SLOT, icon(Material.SPECTRAL_ARROW, "&7Page " + (page + 2) + " →"),
                e -> { page++; refresh((Player) e.getWhoClicked()); });
        }
        if (!search.isEmpty()) {
            set(CLEAR_SLOT, icon(Material.BARRIER, "&cClear Search"),
                e -> { search = ""; page = 0; refresh((Player) e.getWhoClicked()); });
        }
    }

    private List<MythicMob> filtered() {
        List<MythicMob> out = new ArrayList<>();
        try {
            for (MythicMob m : MythicBukkit.inst().getMobManager().getMobTypes()) {
                if (search.isEmpty()) { out.add(m); continue; }
                String q = search.toLowerCase(Locale.ROOT);
                if (m.getInternalName().toLowerCase(Locale.ROOT).contains(q)
                        || MobPickerGUI.niceName(m).toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(m);
                }
            }
        } catch (Throwable ignored) {}
        // Pack mobs first (grouped by pack), then everything else; alphabetical within each group.
        out.sort((a, b) -> MobPackIndex.comparePackThenName(a.getInternalName(), b.getInternalName()));
        return out;
    }
}
