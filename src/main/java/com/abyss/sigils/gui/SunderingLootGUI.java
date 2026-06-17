package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.MaelstromLoot;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Sundered Hoard vendor stock editor. Drag/drop items into the top area to add
 * them to the pool; click an entry to edit its <b>rarity</b> (its chance%, which
 * the vendor uses to price it — rarer = pricier) and its count range.
 *
 * Structurally identical to {@link ReliquaryLootGUI} but bound to the template's
 * own {@code sunderingLoot()} pool, and routing back to {@link EventsGUI}. Unlike
 * the cache events there is no min-kills threshold — the vendor sells whatever it
 * stocks; rarity only sets the price.
 */
public final class SunderingLootGUI implements Listener {

    private static SunderingLootGUI INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new SunderingLootGUI(plugin);
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

    private SunderingLootGUI(AbyssPlugin plugin) { this.plugin = plugin; }

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
    private static final int BACK_SLOT = 44;

    private Inventory build(Holder holder, DungeonTemplate t) {
        Inventory inv = Bukkit.createInventory(holder, 54, color("&c&lSundering Loot: &f" + t.name()));

        List<MaelstromLoot> pool = t.sunderingLoot();
        for (int i = 0; i < Math.min(pool.size(), POOL_END_EX); i++) {
            inv.setItem(i, decorate(pool.get(i)));
        }

        for (int i = 27; i < 36; i++) inv.setItem(i, filler());

        inv.setItem(BACK_SLOT, icon(Material.ARROW, "&7← Back to The Sundering"));

        for (int i = 45; i < 54; i++) if (inv.getItem(i) == null) inv.setItem(i, filler());
        inv.setItem(49, icon(Material.BOOK, "&e&lHow it works",
                "&7Drop or click items into the top area",
                "&7to add them to the &cSundered Hoard &7vendor.",
                "",
                "&7Left-click → rarity (sets the Shard price)",
                "&7Right-click → count range",
                "&7Shift-click → return + remove",
                "",
                "&8Rarer items (lower chance%) cost more Shards.",
                "&8Players spend Shards earned in the event here."));
        return inv;
    }

    private ItemStack decorate(MaelstromLoot r) {
        ItemStack stack = r.itemStack().clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&7Rarity (chance): &f" + r.chancePercent() + "%"));
            lore.add(color("&7Count: &f" + r.minCount() + "-" + r.maxCount()));
            if (r.isMMOItem()) {
                lore.add(color("&dMMOItems: &f" + r.mmoType() + ":" + r.mmoId()));
                lore.add(color("&8Rolls fresh on purchase"));
            }
            lore.add("");
            lore.add(color("&eLeft-click &7→ change rarity / price"));
            lore.add(color("&eRight-click &7→ change count range"));
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
        if (raw == BACK_SLOT) {
            SunderingEditorGUI.openFor(plugin, p, holder.template());
        }
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
        List<MaelstromLoot> pool = holder.template().sunderingLoot();
        ItemStack cursor = e.getView().getCursor();
        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;

        if (cursorHasItem) {
            addToPool(holder, cursor.clone());
            e.getView().setCursor(null);
            rebuild(holder);
            return;
        }

        if (slot >= pool.size()) return;
        MaelstromLoot entry = pool.get(slot);

        if (e.isShiftClick()) {
            pool.remove(slot);
            plugin.templates().save(holder.template());
            ItemStack original = entry.itemStack().clone();
            var overflow = p.getInventory().addItem(original);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            rebuild(holder);
            p.sendMessage(color("&aRemoved &7" + entry.itemStack().getType() + "&a from the vendor stock."));
            return;
        }
        if (e.isRightClick()) {
            ChatInput.prompt(plugin, p, "&fCount range (e.g. 1-3)",
                    entry.minCount() + "-" + entry.maxCount(), text -> {
                try {
                    if (text.contains("-")) {
                        String[] parts = text.split("-", 2);
                        entry.setCountRange(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    } else {
                        int n = Integer.parseInt(text.trim());
                        entry.setCountRange(n, n);
                    }
                    plugin.templates().save(holder.template());
                } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '3' or '1-5'")); }
                reopenAfterInput(p, holder);
            });
            return;
        }
        // Left-click → rarity (chance %), which sets the Shard price.
        ChatInput.prompt(plugin, p, "&fRarity (chance %)", String.valueOf(entry.chancePercent()), text -> {
            try { entry.setChancePercent(Double.parseDouble(text)); plugin.templates().save(holder.template()); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterInput(p, holder);
        });
    }

    private void addToPool(Holder holder, ItemStack item) {
        ItemStack clean = item.clone();
        clean.setAmount(1);
        MaelstromLoot entry = new MaelstromLoot(clean, 50, 1, 1, 0);
        if (plugin.mmoItemsHook() != null && plugin.mmoItemsHook().isMMOItem(clean)) {
            entry.setMMOItem(plugin.mmoItemsHook().mmoType(clean),
                             plugin.mmoItemsHook().mmoId(clean));
        }
        holder.template().sunderingLoot().add(entry);
        plugin.templates().save(holder.template());
    }

    private void rebuild(Holder holder) {
        Inventory fresh = build(holder, holder.template());
        Inventory existing = holder.getInventory();
        if (existing == null) return;
        for (int i = 0; i < fresh.getSize(); i++) existing.setItem(i, fresh.getItem(i));
    }

    private void reopenAfterInput(Player p, Holder holder) {
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
