package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.AbyssCurrency;
import com.abyss.sigils.dungeon.DungeonMap;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.MapMod;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Admin hub (/abyss admin): browse every giveable item — sigils, books, maps,
 * ritual currency, dust — and click to give it to yourself.
 *
 * Implemented as one Holder with a {@link Page} + page number. Navigation
 * creates a fresh instance so each screen gets its own title. List pages
 * (sigils/maps) paginate; small lists fit one page.
 */
public final class AdminGUI extends EditorGUI.Holder {

    public enum Page { HUB, SIGILS, BOOKS, MAPS, CURRENCY }

    /** Interior slots (4 rows of 7) used for paginated list entries. */
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int BACK = 45, PREV = 48, NEXT = 50;

    private final AbyssPlugin plugin;
    private final Page page;
    private final int pageNum;

    public AdminGUI(AbyssPlugin plugin, Page page, int pageNum) {
        this.plugin = plugin;
        this.page = page;
        this.pageNum = pageNum;
    }

    public static void openHub(AbyssPlugin plugin, Player p) {
        new AdminGUI(plugin, Page.HUB, 0).open(p);
    }

    @Override protected String title() {
        return switch (page) {
            case HUB      -> color("&5&l✦ Abyss Admin");
            case SIGILS   -> color("&5Give: Sigils");
            case BOOKS    -> color("&5Give: Books");
            case MAPS     -> color("&5Give: Maps");
            case CURRENCY -> color("&5Give: Currency");
        };
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();
        if (page == Page.HUB) { buildHub(); return; }
        buildList(collectEntries());
    }

    // ----- hub -----

    private void buildHub() {
        set(20, icon(Material.AMETHYST_SHARD, "&d&lSigils",
                "&7Every sigil definition.",
                "&7Left-click → give T1, right-click → max tier.",
                "", "&eClick to open"),
            e -> nav(e, Page.SIGILS));

        set(21, icon(Material.ENCHANTED_BOOK, "&b&lBooks of Sigils",
                "&7Give a Book of Sigils at any tier.",
                "", "&eClick to open"),
            e -> nav(e, Page.BOOKS));

        set(22, icon(Material.PAPER, "&5&lAbyss Maps",
                "&7Give a map for any template.",
                "", "&eClick to open"),
            e -> nav(e, Page.MAPS));

        set(23, icon(Material.ECHO_SHARD, "&5&lMap Currency",
                "&7Give map-modifier currency",
                "&7(apply to a map to roll mods).",
                "", "&eClick to open"),
            e -> nav(e, Page.CURRENCY));

        set(24, icon(Material.GLOWSTONE_DUST, "&f&lSigil Dust",
                "&7Crafting/upgrade material.",
                "", "&eLeft-click → 16, shift → 64"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                int n = e.isShiftClick() ? 64 : 16;
                give(p, SigilItem.createDust(n));
                done(p, n + "x Sigil Dust");
            });
    }

    private void nav(InventoryClickEvent e, Page to) {
        new AdminGUI(plugin, to, 0).open((Player) e.getWhoClicked());
    }

    // ----- list pages -----

    /** A single giveable entry: its display icon + what happens on click. */
    private record Entry(ItemStack icon, Consumer<InventoryClickEvent> onClick) {}

    private List<Entry> collectEntries() {
        return switch (page) {
            case SIGILS   -> sigilEntries();
            case BOOKS    -> bookEntries();
            case MAPS     -> mapEntries();
            case CURRENCY -> currencyEntries();
            case HUB      -> List.of();
        };
    }

    private void buildList(List<Entry> entries) {
        int per = SLOTS.length;
        int totalPages = Math.max(1, (entries.size() + per - 1) / per);
        int clamped = Math.min(Math.max(0, pageNum), totalPages - 1);
        int start = clamped * per;

        for (int i = 0; i < per && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            set(SLOTS[i], entry.icon(), entry.onClick());
        }

        set(BACK, icon(Material.ARROW, "&7← Back to admin hub"),
            e -> openHub(plugin, (Player) e.getWhoClicked()));

        if (clamped > 0) {
            set(PREV, icon(Material.SPECTRAL_ARROW, "&e← Previous page",
                    "&7Page " + (clamped) + " of " + totalPages),
                e -> new AdminGUI(plugin, page, clamped - 1).open((Player) e.getWhoClicked()));
        }
        if (clamped < totalPages - 1) {
            set(NEXT, icon(Material.SPECTRAL_ARROW, "&eNext page →",
                    "&7Page " + (clamped + 2) + " of " + totalPages),
                e -> new AdminGUI(plugin, page, clamped + 1).open((Player) e.getWhoClicked()));
        }
    }

    private List<Entry> sigilEntries() {
        List<Entry> out = new ArrayList<>();
        for (SigilDefinition def : plugin.sigils().all()) {
            ItemStack ic = icon(def.material(), "&f" + def.display(),
                    "&7Id: &f" + def.id(),
                    "&7Rank: &f" + def.rank(),
                    "&7Max tier: &f" + def.maxTier(),
                    "",
                    "&eLeft-click &7→ give T1",
                    "&eRight-click &7→ give T" + def.maxTier());
            out.add(new Entry(ic, e -> {
                Player p = (Player) e.getWhoClicked();
                int tier = e.isRightClick() ? def.maxTier() : 1;
                give(p, SigilItem.toItem(new SigilInstance(def.id(), tier)));
                done(p, "T" + tier + " " + def.display());
            }));
        }
        return out;
    }

    private List<Entry> bookEntries() {
        List<Entry> out = new ArrayList<>();
        int max = plugin.bookTiers().maxTier();
        for (int tier = 1; tier <= max; tier++) {
            final int t = tier;
            ItemStack book = SigilItem.createBook(t);
            ItemStack ic = book.clone();
            decorateGiveLore(ic, "&eClick &7→ give this book");
            out.add(new Entry(ic, e -> {
                Player p = (Player) e.getWhoClicked();
                give(p, SigilItem.createBook(t));
                done(p, "Book of Sigils (T" + t + ")");
            }));
        }
        return out;
    }

    private List<Entry> mapEntries() {
        List<Entry> out = new ArrayList<>();
        for (DungeonTemplate tpl : plugin.templates().all()) {
            ItemStack map = DungeonMap.create(tpl);
            ItemStack ic = map.clone();
            decorateGiveLore(ic, "&eLeft-click &7→ give 1, &eshift &7→ 8");
            out.add(new Entry(ic, e -> {
                Player p = (Player) e.getWhoClicked();
                int n = e.isShiftClick() ? 8 : 1;
                ItemStack give = DungeonMap.create(tpl);
                give.setAmount(n);
                give(p, give);
                done(p, n + "x Map (" + tpl.name() + ")");
            }));
        }
        return out;
    }

    private List<Entry> currencyEntries() {
        List<Entry> out = new ArrayList<>();
        for (MapMod mod : MapMod.values()) {
            ItemStack cur = AbyssCurrency.create(mod);
            ItemStack ic = cur.clone();
            decorateGiveLore(ic, "&eLeft-click &7→ give 1, &eshift &7→ 16");
            out.add(new Entry(ic, e -> {
                Player p = (Player) e.getWhoClicked();
                int n = e.isShiftClick() ? 16 : 1;
                ItemStack give = AbyssCurrency.create(mod);
                give.setAmount(n);
                give(p, give);
                done(p, n + "x " + mod.id() + " currency");
            }));
        }
        return out;
    }

    // ----- helpers -----

    private void give(Player p, ItemStack item) {
        var overflow = p.getInventory().addItem(item);
        for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
    }

    private void done(Player p, String what) {
        p.sendMessage(color("&aGave yourself &f" + what + "&a."));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
    }

    /** Append a give hint to an item's existing lore. */
    private void decorateGiveLore(ItemStack stack, String hint) {
        var meta = stack.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(color(hint));
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }
}
