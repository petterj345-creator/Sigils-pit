package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.MobEntry;
import com.abyss.sigils.integration.MobPackIndex;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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
    private String pack = null; // current pack folder, or null at the root
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
        if (!search.isEmpty()) return color("&5Search: &f" + search);
        if (pack != null) return color("&5Pack: &f" + pack + " &7(page " + (page + 1) + ")");
        return color("&5Pick MythicMob &7(page " + (page + 1) + ")");
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        List<Object> entries = MobBrowse.entries(search, pack);
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        // Top-row search icon
        set(SEARCH_SLOT, icon(Material.WRITABLE_BOOK,
                search.isEmpty() ? "&fSearch..." : "&fSearch: &e" + search,
                "&7" + entries.size() + " result(s)",
                "",
                "&eClick &7to type a query"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                ChatInput.prompt(plugin, p, "&fSearch mobs",
                        search.isEmpty() ? "" : search,
                        text -> {
                            search = text == null ? "" : text.trim();
                            pack = null; // search spans every pack
                            page = 0;
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                        });
            });

        // Grid of folders + mobs
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot % 9 == 8) slot += 2; // skip right border
            if (slot >= 44) break;
            Object entry = entries.get(i);
            if (entry instanceof String) {
                final String packName = (String) entry;
                set(slot, icon(MobBrowse.FOLDER_MATERIAL,
                        "&e&l" + packName,
                        "&7" + MobBrowse.packMobCount(packName) + " mob(s)",
                        "",
                        "&eClick &7to open this pack"),
                    e -> { pack = packName; page = 0; refresh((Player) e.getWhoClicked()); });
            } else {
                final MythicMob mob = (MythicMob) entry;
                set(slot, mobIcon(mob), e -> {
                    Player p = (Player) e.getWhoClicked();
                    final String capturedSearch = this.search;
                    final String capturedPack = this.pack;
                    final int capturedPage = this.page;
                    new MobConfigGUI(plugin, mob, onPicked, onCancel, () -> {
                        MobPickerGUI again = new MobPickerGUI(plugin, onPicked, onCancel);
                        again.search = capturedSearch;
                        again.pack = capturedPack;
                        again.page = capturedPage;
                        again.open(p);
                    }).open(p);
                });
            }
            slot++;
        }

        if (entries.isEmpty()) {
            set(22, icon(Material.BARRIER, "&cNothing here",
                    "&7Try a different search."), null);
        }

        // Bottom navigation
        if (pack != null && search.isEmpty()) {
            set(BACK_SLOT, icon(Material.ARROW, "&7← All packs"),
                e -> { pack = null; page = 0; refresh((Player) e.getWhoClicked()); });
        } else {
            set(BACK_SLOT, icon(Material.ARROW, "&7← Cancel"), e -> {
                Player p = (Player) e.getWhoClicked();
                p.closeInventory();
                if (onCancel != null) onCancel.run();
            });
        }

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
                    "&7Show all packs again."),
                e -> {
                    search = "";
                    page = 0;
                    refresh((Player) e.getWhoClicked());
                });
        }
    }

    private ItemStack mobIcon(MythicMob mob) {
        String mobPack = MobPackIndex.packOf(mob.getInternalName());
        return icon(Material.ZOMBIE_HEAD,
                "&f" + niceName(mob),
                "&8" + mob.getInternalName(),
                mobPack != null ? "&dPack: &7" + mobPack : "&8(no pack)",
                "",
                "&eClick &7to configure count/level");
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
