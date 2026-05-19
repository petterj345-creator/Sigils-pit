package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
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
import java.util.function.Consumer;

/**
 * Lightweight base for all chest-style editor GUIs. Tracks which player has
 * which holder open, dispatches clicks by slot to per-slot handlers, and
 * cancels all clicks by default so the inventory can't be tampered with.
 *
 * One static EditorGUI is registered as the global listener; individual GUIs
 * subclass {@link Holder} which carries the per-instance state.
 */
public final class EditorGUI implements Listener {

    private static EditorGUI INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new EditorGUI();
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    /** A GUI screen. Subclasses define slot layout + click behaviour. */
    public static abstract class Holder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inv;
        protected final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();

        protected abstract String title();
        protected abstract int size();
        /** Populate slots. Called by open() and refresh(). */
        protected abstract void build(Player viewer);

        public final void open(Player p) {
            clickHandlers.clear();
            if (inv == null) inv = Bukkit.createInventory(this, size(), title());
            else inv.clear();
            build(p);
            p.openInventory(inv);
        }

        public final void refresh(Player p) {
            clickHandlers.clear();
            if (inv != null) inv.clear();
            build(p);
        }

        @Override public Inventory getInventory() { return inv; }

        // ----- helpers for subclasses -----

        protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
            inv.setItem(slot, item);
            if (onClick != null) clickHandlers.put(slot, onClick);
        }

        protected ItemStack icon(Material mat, String name, String... lore) {
            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(name));
                if (lore.length > 0) {
                    List<String> list = new ArrayList<>();
                    for (String l : lore) list.add(color(l));
                    meta.setLore(list);
                }
                stack.setItemMeta(meta);
            }
            return stack;
        }

        protected ItemStack filler() {
            ItemStack s = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = s.getItemMeta();
            if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
            return s;
        }

        protected void fillBorder() {
            int size = size();
            int rows = size / 9;
            for (int i = 0; i < 9; i++) inv.setItem(i, filler());
            for (int i = (rows - 1) * 9; i < size; i++) inv.setItem(i, filler());
            for (int r = 1; r < rows - 1; r++) {
                inv.setItem(r * 9, filler());
                inv.setItem(r * 9 + 8, filler());
            }
        }

        protected static String color(String s) {
            return s == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;

        // Top inventory click — always cancel and dispatch by slot
        if (e.getClickedInventory() == top) {
            e.setCancelled(true);
            Consumer<InventoryClickEvent> handler = h.clickHandlers.get(e.getSlot());
            if (handler != null) handler.accept(e);
            return;
        }
        // Bottom inventory click while an editor is open — block shift-clicks into the top
        if (e.isShiftClick()) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // No-op — subclasses can override via Holder if they need close logic.
        // We intentionally don't persist on close; setters persist immediately.
    }
}
