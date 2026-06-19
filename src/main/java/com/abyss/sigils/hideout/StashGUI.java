package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.AbyssCurrency;
import com.abyss.sigils.dungeon.DungeonMap;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.skills.SkillBookItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * The Abyss Stash GUI — a freely-editable storage screen that only accepts Abyss
 * items (sigils, dust, books, maps, currency, tomes). Contents persist server-side
 * via {@link HideoutStashStore}; the placed chest block is just an opener.
 *
 * Enforcement is twofold: clicks/drags that would insert a non-Abyss item are
 * cancelled up-front, and a sweep on close returns anything non-Abyss that slipped
 * through — so junk can never actually be stored.
 */
public final class StashGUI implements Listener {

    private static StashGUI INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new StashGUI(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void openFor(AbyssPlugin plugin, Player p) {
        register(plugin);
        UUID owner = p.getUniqueId();
        int size = plugin.hideoutStash().size();
        Holder holder = new Holder(owner);
        Inventory inv = Bukkit.createInventory(holder, size, color("&6&l✦ Abyss Stash"));
        holder.setInventory(inv);
        ItemStack[] stored = plugin.hideoutStash().get(owner);
        for (int i = 0; i < size && i < stored.length; i++) inv.setItem(i, stored[i]);
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;
    private StashGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static final class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inv;
        Holder(UUID owner) { this.owner = owner; }
        void setInventory(Inventory inv) { this.inv = inv; }
        public UUID owner() { return owner; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static Holder holderOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return (h instanceof Holder sh) ? sh : null;
    }

    /** Anything that belongs in the Abyss ecosystem may be stored. */
    private boolean isAbyss(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return false;
        return SigilItem.isSigil(it) || SigilItem.isDust(it) || SigilItem.isBook(it)
                || DungeonMap.isMap(it) || AbyssCurrency.isCurrency(it)
                || SkillBookItem.isSkillBook(it);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (holderOf(top) == null) return;

        int topSize = top.getSize();
        int raw = e.getRawSlot();

        // Shift-click from the player's inventory into the stash.
        if (e.isShiftClick() && raw >= topSize) {
            if (!isAbyss(e.getCurrentItem())) { e.setCancelled(true); deny(p); }
            return;
        }
        // Clicks inside the stash that would deposit a non-Abyss item.
        if (raw < topSize) {
            if (e.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hot = p.getInventory().getItem(e.getHotbarButton());
                if (hot != null && hot.getType() != Material.AIR && !isAbyss(hot)) { e.setCancelled(true); deny(p); }
                return;
            }
            if (e.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack off = p.getInventory().getItemInOffHand();
                if (off.getType() != Material.AIR && !isAbyss(off)) { e.setCancelled(true); deny(p); }
                return;
            }
            ItemStack cursor = e.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR && !isAbyss(cursor)) {
                e.setCancelled(true); deny(p);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (holderOf(top) == null) return;
        if (isAbyss(e.getOldCursor())) return;
        for (int slot : e.getRawSlots()) {
            if (slot < top.getSize()) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        ItemStack[] contents = top.getContents();
        ItemStack[] keep = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (isAbyss(it)) {
                keep[i] = it;
            } else {
                // Safety net: bounce non-Abyss items back to the player.
                for (ItemStack o : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), o);
                }
            }
        }
        plugin.hideoutStash().set(holder.owner(), keep);
        plugin.hideoutStash().save();
    }

    private long lastDeny;
    private void deny(Player p) {
        long now = System.currentTimeMillis();
        if (now - lastDeny < 1500) return;   // don't spam on rapid shift-clicks
        lastDeny = now;
        p.sendMessage(color("&cOnly Abyss items can be stored in the stash."));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
