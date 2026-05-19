package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lightweight text-input GUI. Opens an anvil with a paper item the player can
 * "rename" to type. On confirm (clicking the right output slot), the typed text
 * is captured and the supplied callback fires.
 *
 *  AnvilInput.open(plugin, player, "Enter mob ID", "AbyssZombie", text -> { ... });
 *
 * The callback is invoked on the main thread. If the player closes without
 * confirming, no callback fires.
 */
public final class AnvilInput implements Listener {

    private static AnvilInput INSTANCE;
    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new AnvilInput();
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void open(AbyssPlugin plugin, Player p, String title, String initial, Consumer<String> onConfirm) {
        if (INSTANCE == null) register(plugin);
        INSTANCE.openInternal(plugin, p, title, initial, onConfirm);
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    private static class Session {
        final Consumer<String> onConfirm;
        final Inventory inv;
        String current;
        boolean confirmed = false;
        Session(Inventory inv, String initial, Consumer<String> cb) {
            this.inv = inv; this.current = initial; this.onConfirm = cb;
        }
    }

    private void openInternal(AbyssPlugin plugin, Player p, String title, String initial, Consumer<String> onConfirm) {
        // We use Bukkit's createInventory(InventoryType.ANVIL) — Paper exposes the anvil's repair-text via Anvil#getRenameText
        Inventory inv = Bukkit.createInventory(p, org.bukkit.event.inventory.InventoryType.ANVIL, Text.color(title));

        ItemStack input = new ItemStack(Material.PAPER);
        ItemMeta meta = input.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(initial == null ? "" : initial);
            input.setItemMeta(meta);
        }
        inv.setItem(0, input);

        sessions.put(p.getUniqueId(), new Session(inv, initial, onConfirm));
        p.openInventory(inv);
    }

    /** When the anvil "computes" the rename, capture the text and put a confirm item in slot 2. */
    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getInventory().getHolder() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || !s.inv.equals(e.getInventory())) return;

        AnvilInventory anvil = e.getInventory();
        String typed = anvil.getRenameText();
        if (typed == null) typed = s.current;
        s.current = typed;

        // Build a confirm "result" item (green wool) so the slot 2 click is satisfying
        ItemStack confirm = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = confirm.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&a&lConfirm: &f" + typed));
            confirm.setItemMeta(meta);
        }
        e.setResult(confirm);
        // Force zero cost so any player can click the result
        Bukkit.getScheduler().runTask(getPlugin(), () -> {
            try { anvil.setRepairCost(0); } catch (Throwable ignored) {}
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || !e.getView().getTopInventory().equals(s.inv)) return;

        // Cancel ALL top-inventory clicks (lock the input/output items in place)
        // and any shift-clicks from below.
        if (e.getClickedInventory() == s.inv) {
            e.setCancelled(true);
        } else if (e.isShiftClick()) {
            e.setCancelled(true);
            return;
        }

        // Slot 2 = anvil output. A click there means "confirm".
        if (e.getRawSlot() == 2) {
            if (s.current == null || s.current.isBlank()) {
                p.sendMessage(Text.color("&cType something first."));
                return;
            }
            s.confirmed = true;
            String text = s.current;
            Consumer<String> cb = s.onConfirm;
            sessions.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                p.closeInventory();
                try { cb.accept(text); } catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        if (!s.confirmed) sessions.remove(p.getUniqueId());
    }

    private AbyssPlugin getPlugin() { return AbyssPlugin.get(); }
}
