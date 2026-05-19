package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDraft;
import com.abyss.sigils.sigils.SigilRank;
import com.abyss.sigils.sigils.SigilStat;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Admin GUI for creating or editing a SigilDraft.
 *
 * Layout (54 slots):
 *   10 ID                 11 Display name        12 Material (drag-drop)   13 Model data
 *   19 Rank (toggle)      20 Main stat
 *   28..32 Tier values 1..5  (paper icons, click to set)
 *   33 Add tier slot      34 Remove tier slot
 *   37..43 Substat pool toggles (click to add/remove)
 *   49 Save & Reload      50 Cancel
 *
 * Material picking: drag any item from your inventory into slot 12 — that
 * item's type becomes the sigil's icon material.
 */
public final class SigilCreatorGUI implements Listener {

    private static SigilCreatorGUI INSTANCE;
    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new SigilCreatorGUI(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void openFor(AbyssPlugin plugin, Player p, SigilDraft draft) {
        register(plugin);
        Inventory inv = INSTANCE.build(draft);
        INSTANCE.viewers.put(p.getUniqueId(), new Session(draft, inv));
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;
    private final Map<UUID, Session> viewers = new HashMap<>();

    private SigilCreatorGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    private static class Session {
        final SigilDraft draft;
        final Inventory inv;
        Session(SigilDraft draft, Inventory inv) { this.draft = draft; this.inv = inv; }
    }

    private static final int SLOT_ID    = 10;
    private static final int SLOT_NAME  = 11;
    private static final int SLOT_MAT   = 12;
    private static final int SLOT_MODEL = 13;
    private static final int SLOT_RANK  = 19;
    private static final int SLOT_STAT  = 20;
    private static final int[] SLOT_TIERS = {28, 29, 30, 31, 32};
    private static final int SLOT_TIER_ADD = 33;
    private static final int SLOT_TIER_DEL = 34;
    private static final int[] SLOT_SUBS = {37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_SAVE  = 49;
    private static final int SLOT_CANCEL = 50;

    private Inventory build(SigilDraft d) {
        Inventory inv = Bukkit.createInventory(null, 54, Text.color("&5&lSigil Creator: &f" + d.id()));

        // Border filler
        for (int i = 0; i < 54; i++) inv.setItem(i, filler());

        inv.setItem(SLOT_ID, icon(Material.NAME_TAG, "&fID: &7" + d.id(),
                "&7Internal identifier, used in YAML.",
                "&7Lowercase letters, digits, underscores.",
                "",
                "&eClick &7to change"));

        inv.setItem(SLOT_NAME, icon(Material.WRITABLE_BOOK, "&fDisplay Name",
                "&7Currently: &r" + Text.color(d.display()),
                "&7Supports &color codes.",
                "",
                "&eClick &7to change"));

        inv.setItem(SLOT_MAT, icon(d.material(), "&fIcon: &7" + d.material().name(),
                "&7The item type used as the sigil's icon.",
                "",
                "&eDrag an item here &7to set"));

        inv.setItem(SLOT_MODEL, icon(Material.PAINTING, "&fModel Data: &7" + d.modelData(),
                "&7Custom model data (resource pack support).",
                "",
                "&eClick &7to change"));

        inv.setItem(SLOT_RANK, icon(d.rank() == SigilRank.MAJOR ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                "&fRank: " + (d.rank() == SigilRank.MAJOR ? "&6MAJOR" : "&7minor"),
                d.rank() == SigilRank.MAJOR
                        ? "&7Fits in big sockets. Can have unique stats."
                        : "&7Fits in small sockets. Common stats only.",
                "",
                "&eClick &7to toggle"));

        inv.setItem(SLOT_STAT, icon(Material.NETHER_STAR, "&fMain Stat",
                "&7Currently: &f" + d.stat().name(),
                "",
                "&eClick &7to change"));

        // Tier values
        for (int i = 0; i < SLOT_TIERS.length; i++) {
            if (i < d.tierValues().size()) {
                double val = d.tierValues().get(i);
                inv.setItem(SLOT_TIERS[i], icon(Material.PAPER, "&fT" + (i + 1) + ": &e" + fmt(val),
                        "&7Value at tier " + (i + 1),
                        "",
                        "&eClick &7to change"));
            } else {
                inv.setItem(SLOT_TIERS[i], icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "&8(no tier " + (i + 1) + ")",
                        "&7Click 'add tier' to extend."));
            }
        }
        inv.setItem(SLOT_TIER_ADD, icon(Material.EMERALD, "&aAdd Tier",
                "&7Current max tier: &f" + d.tierValues().size()));
        inv.setItem(SLOT_TIER_DEL, icon(Material.REDSTONE, "&cRemove Last Tier",
                "&7Must keep at least 1."));

        // Substat pool — present up to 7 buttons for stats valid for this rank.
        List<SigilStat> available = stream(SigilStat.values())
                .filter(s -> s.allowedFor(d.rank()))
                .filter(s -> s != d.stat()) // don't list the main stat
                .toList();
        for (int i = 0; i < SLOT_SUBS.length; i++) {
            if (i < available.size()) {
                SigilStat s = available.get(i);
                boolean selected = d.substatPool().contains(s);
                inv.setItem(SLOT_SUBS[i], icon(
                        selected ? Material.LIME_DYE : Material.GRAY_DYE,
                        (selected ? "&a✓ " : "&7") + s.name(),
                        "&8" + s.category(),
                        "",
                        "&eClick &7to " + (selected ? "remove" : "add") + " from pool"));
            } else {
                inv.setItem(SLOT_SUBS[i], filler());
            }
        }

        // Status / save / cancel
        String err = d.validationError();
        inv.setItem(SLOT_SAVE, icon(err == null ? Material.LIME_WOOL : Material.RED_WOOL,
                err == null ? "&a&lSave & Reload" : "&c&lCan't save",
                err == null ? "&7Writes to sigils.yml and reloads." : "&c" + err));
        inv.setItem(SLOT_CANCEL, icon(Material.BARRIER, "&cCancel",
                "&7Closes without saving."));

        return inv;
    }

    private static <T> java.util.stream.Stream<T> stream(T[] arr) { return java.util.Arrays.stream(arr); }

    private static String fmt(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String l : lore) list.add(Text.color(l));
                meta.setLore(list);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    private ItemStack filler() {
        ItemStack s = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = viewers.get(p.getUniqueId());
        if (s == null || !e.getView().getTopInventory().equals(s.inv)) return;

        int slot = e.getRawSlot();

        // Material drop: drag any item into SLOT_MAT
        if (slot == SLOT_MAT && e.getView().getCursor() != null
                && e.getView().getCursor().getType() != Material.AIR) {
            e.setCancelled(true);
            s.draft.setMaterial(e.getView().getCursor().getType());
            // Keep the player's item — don't consume it
            redraw(p, s);
            return;
        }

        if (e.getClickedInventory() != s.inv) {
            if (e.isShiftClick()) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        switch (slot) {
            case SLOT_ID -> AnvilInput.open(plugin, p, "&fSigil ID", s.draft.id(), text -> {
                s.draft.setId(text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", ""));
                reopenAfterAnvil(p, s);
            });
            case SLOT_NAME -> AnvilInput.open(plugin, p, "&fDisplay Name", s.draft.display(), text -> {
                s.draft.setDisplay(text);
                reopenAfterAnvil(p, s);
            });
            case SLOT_MAT -> p.sendMessage(Text.color("&7Drag an item from your inventory into this slot."));
            case SLOT_MODEL -> AnvilInput.open(plugin, p, "&fModel Data", String.valueOf(s.draft.modelData()), text -> {
                try { s.draft.setModelData(Integer.parseInt(text)); } catch (NumberFormatException ex) {}
                reopenAfterAnvil(p, s);
            });
            case SLOT_RANK -> {
                s.draft.setRank(s.draft.rank() == SigilRank.MAJOR ? SigilRank.MINOR : SigilRank.MAJOR);
                redraw(p, s);
            }
            case SLOT_STAT -> StatPickerGUI.openFor(plugin, p, s.draft.rank(),
                    chosen -> { s.draft.setStat(chosen); openFor(plugin, p, s.draft); });
            case SLOT_TIER_ADD -> { s.draft.addTier(); redraw(p, s); }
            case SLOT_TIER_DEL -> { s.draft.removeTier(); redraw(p, s); }
            case SLOT_SAVE -> {
                if (plugin.sigils().saveDraft(s.draft)) {
                    p.sendMessage(Text.color("&aSigil &f" + s.draft.id() + "&a saved & registry reloaded."));
                    viewers.remove(p.getUniqueId());
                    p.closeInventory();
                } else {
                    p.sendMessage(Text.color("&cCouldn't save — check console."));
                }
            }
            case SLOT_CANCEL -> {
                viewers.remove(p.getUniqueId());
                p.closeInventory();
            }
            default -> {
                // Tier value clicks
                for (int i = 0; i < SLOT_TIERS.length; i++) {
                    if (slot == SLOT_TIERS[i] && i < s.draft.tierValues().size()) {
                        int tierIndex = i;
                        AnvilInput.open(plugin, p,
                                "&fValue for T" + (tierIndex + 1),
                                fmt(s.draft.tierValues().get(tierIndex)),
                                text -> {
                                    try { s.draft.setTierValue(tierIndex, Double.parseDouble(text)); }
                                    catch (NumberFormatException ex) { p.sendMessage(Text.color("&cMust be a number.")); }
                                    reopenAfterAnvil(p, s);
                                });
                        return;
                    }
                }
                // Substat toggles
                for (int i = 0; i < SLOT_SUBS.length; i++) {
                    if (slot == SLOT_SUBS[i]) {
                        List<SigilStat> available = stream(SigilStat.values())
                                .filter(st -> st.allowedFor(s.draft.rank()))
                                .filter(st -> st != s.draft.stat())
                                .toList();
                        if (i < available.size()) {
                            s.draft.toggleSubstat(available.get(i));
                            redraw(p, s);
                        }
                        return;
                    }
                }
            }
        }
    }

    private void redraw(Player p, Session s) {
        Inventory fresh = build(s.draft);
        for (int i = 0; i < fresh.getSize(); i++) s.inv.setItem(i, fresh.getItem(i));
    }

    private void reopenAfterAnvil(Player p, Session s) {
        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, s.draft));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        viewers.remove(p.getUniqueId());
    }
}
