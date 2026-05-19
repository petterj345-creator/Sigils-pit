package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.MobEntry;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable, paginated picker for MythicMobs.
 *
 * Layout (54 slots):
 *   slot 4   - search icon (click to open anvil for query)
 *   slots 10..43 - up to 28 mob slots per page (4 rows of 7, inset from border)
 *   slot 45  - back / cancel
 *   slot 48  - prev page
 *   slot 50  - next page
 *   slot 53  - clear search / show all
 *
 * When a mob is picked, opens a small {@link MobConfigGUI} so the admin can
 * pick count + level. The final result (a {@link MobEntry}) is delivered to
 * the caller's {@code onPicked} callback.
 *
 * Usage:
 *   MobPickerGUI.openFor(plugin, player, entry -> {
 *       // entry is the configured MobEntry, or null if cancelled
 *   });
 */
public final class MobPickerGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final Consumer<MobEntry> onPicked;
    private final Runnable onCancel;
    private String search = "";
    private int page = 0;

    private static final int PAGE_SIZE = 28; // 4 rows of 7 (slots 10..43, skipping borders)
    private static final int SEARCH_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int CLEAR_SLOT = 53;

    public MobPickerGUI(AbyssPlugin plugin, Consumer<MobEntry> onPicked, Runnable onCancel) {
        this.plugin = plugin;
        this.onPicked = onPicked;
        this.onCancel = onCancel;
    }

    public static void openFor(AbyssPlugin plugin, Player p, Consumer<MobEntry> onPicked) {
        new MobPickerGUI(plugin, onPicked, null).open(p);
    }

    public static void openFor(AbyssPlugin plugin, Player p, Consumer<MobEntry> onPicked, Runnable onCancel) {
        new MobPickerGUI(plugin, onPicked, onCancel).open(p);
    }

    @Override protected String title() {
        if (search.isEmpty()) return color("&5Pick MythicMob &7(page " + (page + 1) + ")");
        return color("&5Search: &f" + search);
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        List<MythicMob> filtered = filteredMobs();
        int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        // Top-row search icon
        set(SEARCH_SLOT, icon(Material.WRITABLE_BOOK,
                search.isEmpty() ? "&fSearch..." : "&fSearch: &e" + search,
                "&7" + filtered.size() + " mob(s) match",
                "",
                "&eClick &7to type a query"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                AnvilInput.open(plugin, p, "&fSearch mobs",
                        search.isEmpty() ? "" : search,
                        text -> {
                            search = text == null ? "" : text.trim();
                            page = 0;
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                        });
            });

        // Mob grid
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot % 9 == 8) slot += 2; // skip right border
            if (slot >= 44) break;
            final MythicMob mob = filtered.get(i);
            String displayName = niceName(mob);
            ItemStack ic = icon(Material.ZOMBIE_HEAD,
                    "&f" + displayName,
                    "&8" + mob.getInternalName(),
                    "",
                    "&eClick &7to configure count/level");
            set(slot, ic, e -> {
                Player p = (Player) e.getWhoClicked();
                final String capturedSearch = this.search;
                final int capturedPage = this.page;
                new MobConfigGUI(plugin, mob, onPicked, onCancel, () -> {
                    MobPickerGUI again = new MobPickerGUI(plugin, onPicked, onCancel);
                    again.search = capturedSearch;
                    again.page = capturedPage;
                    again.open(p);
                }).open(p);
            });
            slot++;
        }

        if (filtered.isEmpty()) {
            set(22, icon(Material.BARRIER, "&cNo mobs match",
                    "&7Try a different search."), null);
        }

        // Bottom navigation
        set(BACK_SLOT, icon(Material.ARROW, "&7← Cancel"), e -> {
            Player p = (Player) e.getWhoClicked();
            p.closeInventory();
            if (onCancel != null) onCancel.run();
        });

        if (page > 0) {
            set(PREV_SLOT, icon(Material.SPECTRAL_ARROW, "&7← Page " + page),
                e -> { page--; refresh((Player) e.getWhoClicked()); });
        }
        if (page < totalPages - 1) {
            set(NEXT_SLOT, icon(Material.SPECTRAL_ARROW, "&7Page " + (page + 2) + " →"),
                e -> { page++; refresh((Player) e.getWhoClicked()); });
        }
        if (!search.isEmpty()) {
            set(CLEAR_SLOT, icon(Material.BARRIER, "&cClear Search",
                    "&7Show all mobs again."),
                e -> {
                    search = "";
                    page = 0;
                    refresh((Player) e.getWhoClicked());
                });
        }
    }

    private List<MythicMob> filteredMobs() {
        List<MythicMob> out = new ArrayList<>();
        try {
            for (MythicMob m : MythicBukkit.inst().getMobManager().getMobTypes()) {
                if (search.isEmpty()) { out.add(m); continue; }
                String q = search.toLowerCase(Locale.ROOT);
                String id = m.getInternalName().toLowerCase(Locale.ROOT);
                String name = niceName(m).toLowerCase(Locale.ROOT);
                if (id.contains(q) || name.contains(q)) out.add(m);
            }
        } catch (Throwable ignored) {}
        // Sort by internal name for stable ordering
        out.sort((a, b) -> a.getInternalName().compareToIgnoreCase(b.getInternalName()));
        return out;
    }

    /** Robust display-name extraction across Mythic API versions. */
    static String niceName(MythicMob m) {
        try {
            Object dn = m.getDisplayName();
            if (dn == null) return m.getInternalName();
            try {
                java.lang.reflect.Method get = dn.getClass().getMethod("get");
                Object res = get.invoke(dn);
                return res == null ? m.getInternalName()
                        : org.bukkit.ChatColor.stripColor(res.toString());
            } catch (Throwable t2) {
                return org.bukkit.ChatColor.stripColor(dn.toString());
            }
        } catch (Throwable t) {
            return m.getInternalName();
        }
    }
}
