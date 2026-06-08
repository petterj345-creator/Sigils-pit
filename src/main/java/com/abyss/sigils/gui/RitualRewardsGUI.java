package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.RitualReward;
import com.abyss.sigils.integration.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Soul-shop pool editor. Same drag-and-drop feel as {@link RewardsGUI}: drop or
 * shift-click items into the top area to add them, click an entry to set its
 * soul price range, shift-click to remove. MMOItems entries are auto-detected
 * and roll fresh per purchase.
 *
 * Layout (54 slots):
 *   Rows 0–2 (0–26)  — pool drop zone (27 items)
 *   Row 3 (27–35)    — separator
 *   Row 4 (36–44)    — controls: items-per-shop (36), price range (38), back (44)
 *   Row 5 (45–53)    — border + help
 */
public final class RitualRewardsGUI implements Listener {

    private static RitualRewardsGUI INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new RitualRewardsGUI(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        register(plugin);
        Holder holder = new Holder(t);
        Inventory inv = INSTANCE.build(holder, t);
        holder.setInventory(inv);
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;
    private RitualRewardsGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static final class Holder implements InventoryHolder {
        private final DungeonTemplate template;
        private Inventory inv;
        Holder(DungeonTemplate template) { this.template = template; }
        void setInventory(Inventory inv) { this.inv = inv; }
        public DungeonTemplate template() { return template; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int POOL_START = 0;
    private static final int POOL_END_EX = 27;
    private static final int CTRL_ITEMS = 36;
    private static final int CTRL_PRICE = 38;
    private static final int CTRL_CASH = 40;
    private static final int CTRL_BACK = 44;

    private Inventory build(Holder holder, DungeonTemplate t) {
        Inventory inv = Bukkit.createInventory(holder, 54, color("&5&lSoul Shop: &f" + t.name()));

        List<RitualReward> pool = t.ritualRewardPool();
        for (int i = 0; i < Math.min(pool.size(), POOL_END_EX); i++) {
            inv.setItem(i, decorate(t, pool.get(i)));
        }
        for (int i = 27; i < 36; i++) inv.setItem(i, filler());

        inv.setItem(CTRL_ITEMS, icon(Material.HOPPER, "&eItems Rolled per Shop",
                "&7Range: &f" + t.ritualItemsMin() + "-" + t.ritualItemsMax(),
                "&7How many items appear in the shop.",
                "",
                "&eClick &7to set (e.g. 3-5)"));
        inv.setItem(CTRL_PRICE, icon(Material.SOUL_TORCH, "&bDefault Soul Price",
                "&7Range: &f" + t.ritualPriceMin() + "-" + t.ritualPriceMax(),
                "&7Used for items that don't set their own.",
                "",
                "&eClick &7to set (e.g. 10-50)"));
        inv.setItem(CTRL_CASH, icon(t.ritualCashEnabled() ? Material.PAPER : Material.MAP,
                "&6&lCash Reward: " + (t.ritualCashEnabled() ? "&aON" : "&cOFF"),
                "&7A money payout sold as a paper in the shop.",
                "&7Money: &f" + t.ritualCashMoneyMin() + "-" + t.ritualCashMoneyMax(),
                "&7Soul price: &b" + t.ritualCashPriceMin() + "-" + t.ritualCashPriceMax(),
                VaultHook.available() ? "" : "&c(Vault not installed — won't pay out)",
                "",
                "&eLeft-click &7→ toggle on/off",
                "&eRight-click &7→ set money range",
                "&eDrop key &7→ set soul price range"));
        inv.setItem(CTRL_BACK, icon(Material.ARROW, "&7← Back to rituals"));

        for (int i = 45; i < 54; i++) if (inv.getItem(i) == null) inv.setItem(i, filler());
        inv.setItem(49, icon(Material.BOOK, "&e&lHow it works",
                "&7Drop or shift-click items into the",
                "&7top area to add them to the shop.",
                "",
                "&7Left-click an entry → set soul price",
                "&7Shift-click an entry → return + remove",
                "",
                "&dMMOItems items roll fresh per buyer."));
        return inv;
    }

    private ItemStack decorate(DungeonTemplate t, RitualReward r) {
        ItemStack stack = r.itemStack().clone();
        stack.setAmount(Math.max(1, Math.min(64, r.amount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            if (r.inheritsPrice()) {
                lore.add(color("&7Price: &finherits &7(" + t.ritualPriceMin() + "-" + t.ritualPriceMax() + " souls)"));
            } else {
                lore.add(color("&7Price: &b" + r.priceMin() + "-" + r.priceMax() + " souls"));
            }
            lore.add(color("&7Quantity: &f" + r.amount()));
            if (r.isMMOItem()) {
                lore.add(color("&dMMOItems: &f" + r.mmoType() + ":" + r.mmoId()));
                lore.add(color("&8Rolls fresh per buyer"));
            }
            lore.add("");
            lore.add(color("&eLeft-click &7→ set price (or 'inherit')"));
            lore.add(color("&eRight-click &7→ set quantity"));
            lore.add(color("&cShift-click &7→ remove"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String l : lore) list.add(color(l));
                meta.setLore(list);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    private ItemStack filler() {
        ItemStack s = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    private static String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    private static Holder holderOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return (h instanceof Holder rh) ? rh : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        int raw = e.getRawSlot();
        int topSize = top.getSize();

        if (raw >= topSize) {
            // bottom inv — shift-click adds to pool
            if (e.isShiftClick()) {
                ItemStack toAdd = e.getCurrentItem();
                if (toAdd != null && toAdd.getType() != Material.AIR) {
                    e.setCancelled(true);
                    addToPool(holder, toAdd);
                    p.getInventory().setItem(e.getSlot(), null);
                    rebuild(holder);
                }
            }
            return;
        }

        e.setCancelled(true);

        if (raw >= POOL_START && raw < POOL_END_EX) {
            handlePoolClick(e, holder, p, raw);
            return;
        }
        handleControlClick(e, holder, p, raw);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        int topSize = top.getSize();
        boolean touchesTop = false;
        for (int slot : e.getRawSlots()) if (slot < topSize) { touchesTop = true; break; }
        if (!touchesTop) return;

        e.setCancelled(true);
        ItemStack cursor = e.getOldCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            boolean dropInPool = false;
            for (int slot : e.getRawSlots()) if (slot >= POOL_START && slot < POOL_END_EX) { dropInPool = true; break; }
            if (dropInPool) {
                addToPool(holder, cursor.clone());
                e.getView().setCursor(null);
            }
            Bukkit.getScheduler().runTask(plugin, () -> rebuild(holder));
        }
    }

    private void handlePoolClick(InventoryClickEvent e, Holder holder, Player p, int slot) {
        List<RitualReward> pool = holder.template().ritualRewardPool();
        ItemStack cursor = e.getView().getCursor();
        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;

        if (cursorHasItem) {
            addToPool(holder, cursor.clone());
            e.getView().setCursor(null);
            rebuild(holder);
            return;
        }

        if (slot >= pool.size()) return;
        RitualReward entry = pool.get(slot);

        if (e.isShiftClick()) {
            pool.remove(slot);
            plugin.templates().save(holder.template());
            var overflow = p.getInventory().addItem(entry.itemStack().clone());
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            rebuild(holder);
            p.sendMessage(color("&aRemoved &7" + entry.itemStack().getType() + "&a from shop."));
            return;
        }

        // Right-click → set quantity
        if (e.isRightClick()) {
            ChatInput.prompt(plugin, p, "&fQuantity (1-64)", String.valueOf(entry.amount()), text -> {
                try { entry.setAmount(Integer.parseInt(text.trim())); plugin.templates().save(holder.template()); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopen(p, holder);
            });
            return;
        }

        // Left-click → set price range, or 'inherit'
        String current = entry.inheritsPrice() ? "inherit" : (entry.priceMin() + "-" + entry.priceMax());
        ChatInput.prompt(plugin, p, "&fSoul price ('10-50', '25', or 'inherit')", current, text -> {
            String t = text.trim().toLowerCase();
            if (t.equals("inherit") || t.equals("default")) {
                entry.clearPrice();
                plugin.templates().save(holder.template());
            } else {
                try {
                    if (t.contains("-")) {
                        String[] parts = t.split("-", 2);
                        entry.setPriceRange(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    } else {
                        int n = Integer.parseInt(t);
                        entry.setPriceRange(n, n);
                    }
                    plugin.templates().save(holder.template());
                } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '10-50', '25', or 'inherit'")); }
            }
            reopen(p, holder);
        });
    }

    private void handleControlClick(InventoryClickEvent e, Holder holder, Player p, int raw) {
        DungeonTemplate t = holder.template();
        if (raw == CTRL_BACK) {
            RitualEditorGUI.openFor(plugin, p, t);
            return;
        }
        if (raw == CTRL_CASH) {
            if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                    || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
                ChatInput.prompt(plugin, p, "&fCash soul price range (e.g. 50-100)",
                        t.ritualCashPriceMin() + "-" + t.ritualCashPriceMax(), text -> {
                    applyRange(text, t::setRitualCashPriceMin, t::setRitualCashPriceMax, p, t);
                    reopen(p, holder);
                });
            } else if (e.isRightClick()) {
                ChatInput.prompt(plugin, p, "&fCash money range (e.g. 500-2000)",
                        ((int) t.ritualCashMoneyMin()) + "-" + ((int) t.ritualCashMoneyMax()), text -> {
                    applyMoneyRange(text, t, p);
                    reopen(p, holder);
                });
            } else {
                t.setRitualCashEnabled(!t.ritualCashEnabled());
                plugin.templates().save(t);
                reopen(p, holder);
            }
            return;
        }
        if (raw == CTRL_ITEMS) {
            ChatInput.prompt(plugin, p, "&fItems per shop (e.g. 3-5)",
                    t.ritualItemsMin() + "-" + t.ritualItemsMax(), text -> {
                applyRange(text, t::setRitualItemsMin, t::setRitualItemsMax, p, t);
                reopen(p, holder);
            });
            return;
        }
        if (raw == CTRL_PRICE) {
            ChatInput.prompt(plugin, p, "&fSoul price range (e.g. 10-50)",
                    t.ritualPriceMin() + "-" + t.ritualPriceMax(), text -> {
                applyRange(text, t::setRitualPriceMin, t::setRitualPriceMax, p, t);
                reopen(p, holder);
            });
        }
    }

    private void applyRange(String text, java.util.function.IntConsumer setMin,
                            java.util.function.IntConsumer setMax, Player p, DungeonTemplate t) {
        try {
            if (text.contains("-")) {
                String[] parts = text.split("-", 2);
                setMin.accept(Integer.parseInt(parts[0].trim()));
                setMax.accept(Integer.parseInt(parts[1].trim()));
            } else {
                int n = Integer.parseInt(text.trim());
                setMin.accept(n);
                setMax.accept(n);
            }
            plugin.templates().save(t);
        } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '5' or '3-5'")); }
    }

    private void applyMoneyRange(String text, DungeonTemplate t, Player p) {
        try {
            if (text.contains("-")) {
                String[] parts = text.split("-", 2);
                t.setRitualCashMoneyMin(Double.parseDouble(parts[0].trim()));
                t.setRitualCashMoneyMax(Double.parseDouble(parts[1].trim()));
            } else {
                double v = Double.parseDouble(text.trim());
                t.setRitualCashMoneyMin(v);
                t.setRitualCashMoneyMax(v);
            }
            plugin.templates().save(t);
        } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '500' or '500-2000'")); }
    }

    private void addToPool(Holder holder, ItemStack item) {
        ItemStack clean = item.clone();
        clean.setAmount(1);
        // -1/-1 = inherit the template's global price range.
        RitualReward entry = new RitualReward(clean, -1, -1);
        if (plugin.mmoItemsHook() != null && plugin.mmoItemsHook().isMMOItem(clean)) {
            entry.setMMOItem(plugin.mmoItemsHook().mmoType(clean), plugin.mmoItemsHook().mmoId(clean));
        }
        holder.template().ritualRewardPool().add(entry);
        plugin.templates().save(holder.template());
    }

    private void rebuild(Holder holder) {
        Inventory fresh = build(holder, holder.template());
        Inventory existing = holder.getInventory();
        if (existing == null) return;
        for (int i = 0; i < fresh.getSize(); i++) existing.setItem(i, fresh.getItem(i));
    }

    private void reopen(Player p, Holder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, holder.template()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;
        ItemStack cursor = e.getView().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            var overflow = p.getInventory().addItem(cursor);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            e.getView().setCursor(null);
        }
    }
}
