package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.RewardEntry;
import com.abyss.sigils.integration.VaultHook;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Rewards editor. Drag-and-drop items into the top area to add them to the
 * pool; click an item to edit its chance%/count range; shift-click to remove.
 *
 * Layout (54 slots, 6 rows):
 *   Rows 0–2 (slots 0–26)  — drop zone for reward items. 27 slot capacity.
 *   Row 3 (slots 27–35)    — separator (filler)
 *   Row 4 (36..44)         — money + xp config
 *      36 max items, 38 money min/max, 39 money chance,
 *      41 xp min/max, 42 xp chance, 44 back arrow
 *   Row 5 (slots 45–53)    — filler border
 *
 * Special handling:
 *  - "Lore overlay" — when we display an entry in the pool, we clone the
 *    ItemStack and append a "Chance: X%  Count: A-B" line so the admin can
 *    see what each pool item is configured for at a glance.
 *  - Click handling is more involved than other GUIs because we need to allow
 *    real placing/removing of items in the top 27 slots.
 */
public final class RewardsGUI implements Listener {

    private static RewardsGUI INSTANCE;
    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new RewardsGUI(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        register(plugin);
        Inventory inv = INSTANCE.build(t);
        INSTANCE.viewers.put(p.getUniqueId(), new Session(t, inv));
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;
    private final Map<UUID, Session> viewers = new HashMap<>();

    private RewardsGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    private static class Session {
        final DungeonTemplate template;
        final Inventory inv;
        Session(DungeonTemplate t, Inventory inv) { this.template = t; this.inv = inv; }
    }

    // ---- layout helpers ----

    private static final int POOL_START = 0;
    private static final int POOL_END_EX = 27; // 0..26 inclusive
    private static final int CONTROL_ROW = 36;

    private Inventory build(DungeonTemplate t) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&5&lRewards: &f" + t.name()));

        // Pool items
        List<RewardEntry> pool = t.rewardPool();
        for (int i = 0; i < Math.min(pool.size(), POOL_END_EX); i++) {
            inv.setItem(i, decorate(pool.get(i)));
        }

        // Separator
        for (int i = 27; i < 36; i++) inv.setItem(i, filler());

        // Controls
        inv.setItem(CONTROL_ROW + 0, icon(Material.HOPPER, "&eMax Items Per Chest",
                "&7Currently: &f" + t.maxRewardItems(),
                "&7Each successful roll fills one slot.",
                "",
                "&eClick &7to change"));

        inv.setItem(CONTROL_ROW + 2, icon(VaultHook.available() ? Material.GOLD_INGOT : Material.IRON_INGOT,
                "&6Money Reward",
                VaultHook.available() ? "" : "&c(Vault not installed — disabled)",
                "&7Range: &f" + t.moneyMin() + " - " + t.moneyMax(),
                "&7Chance: &f" + t.moneyChancePercent() + "%",
                "",
                "&eLeft-click &7→ set min",
                "&eRight-click &7→ set max",
                "&eDrop key &7→ set chance %"));

        inv.setItem(CONTROL_ROW + 5, icon(Material.EXPERIENCE_BOTTLE, "&aXP Levels Reward",
                "&7Range: &f" + t.xpLevelsMin() + " - " + t.xpLevelsMax(),
                "&7Chance: &f" + t.xpChancePercent() + "%",
                "",
                "&eLeft-click &7→ set min",
                "&eRight-click &7→ set max",
                "&eDrop key &7→ set chance %"));

        inv.setItem(CONTROL_ROW + 8, icon(Material.ARROW, "&7← Back to editor"));

        // Bottom border
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler());
        }
        return inv;
    }

    /** Clone the item and append "Chance: X% Count: A-B" to the lore. */
    private ItemStack decorate(RewardEntry r) {
        ItemStack stack = r.itemStack().clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&7Chance: &f" + r.chancePercent() + "%"));
            lore.add(color("&7Count: &f" + r.minCount() + "-" + r.maxCount()));
            lore.add("");
            lore.add(color("&eLeft-click &7→ change chance"));
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

    // ---- click handling ----

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = viewers.get(p.getUniqueId());
        if (s == null || !e.getView().getTopInventory().equals(s.inv)) return;

        int slot = e.getRawSlot();

        // Top inventory interaction
        if (e.getClickedInventory() == s.inv) {
            // POOL ZONE (drag/drop)
            if (slot >= POOL_START && slot < POOL_END_EX) {
                handlePoolClick(e, s, p, slot);
                return;
            }
            // CONTROLS
            handleControlClick(e, s, p, slot);
            return;
        }

        // Bottom inventory: a shift-click would try to dump into the top inv.
        // If the destination is the pool zone, allow it (it ADDS to the pool).
        if (e.isShiftClick()) {
            ItemStack toAdd = e.getCurrentItem();
            if (toAdd != null && toAdd.getType() != Material.AIR) {
                e.setCancelled(true);
                addToPool(s, toAdd);
                e.getWhoClicked().getInventory().setItem(e.getSlot(), null);
                rebuild(p, s);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Session s = viewers.get(p.getUniqueId());
        if (s == null || !e.getView().getTopInventory().equals(s.inv)) return;

        // Whether any of the drag targets land in the top inventory at all
        boolean touchesTop = false;
        for (int slot : e.getRawSlots()) {
            if (slot < s.inv.getSize()) { touchesTop = true; break; }
        }
        if (!touchesTop) return; // drag stayed in player inv — no-op

        // Always cancel — we manage the pool, not Bukkit
        e.setCancelled(true);

        // Take whatever was on the cursor before the drag and try to add it
        ItemStack cursor = e.getOldCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            addToPool(s, cursor.clone());
            e.getView().setCursor(null);
            Bukkit.getScheduler().runTask(plugin, () -> rebuild(p, s));
        }
    }

    private void handlePoolClick(InventoryClickEvent e, Session s, Player p, int slot) {
        List<RewardEntry> pool = s.template.rewardPool();
        ItemStack current = s.inv.getItem(slot);
        ItemStack cursor = e.getView().getCursor();

        // Cursor has an item we're trying to add
        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;
        boolean slotHasItem = current != null && current.getType() != Material.AIR;

        if (cursorHasItem) {
            // Adding to pool
            e.setCancelled(true);
            addToPool(s, cursor.clone());
            e.getView().setCursor(null);
            rebuild(p, s);
            return;
        }

        if (!slotHasItem) {
            e.setCancelled(true);
            return;
        }

        // Cursor empty, slot has an item — interaction with existing entry
        // The slot index in the pool == slot (since we display 0..26)
        if (slot >= pool.size()) {
            e.setCancelled(true);
            return;
        }
        RewardEntry entry = pool.get(slot);
        e.setCancelled(true);

        if (e.isShiftClick()) {
            // Remove
            pool.remove(slot);
            plugin.templates().save(s.template);
            rebuild(p, s);
            p.sendMessage(color("&aRemoved reward."));
            return;
        }
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            // No bound action — fall through to count edit on drop key for entries
            promptCountRange(p, s, entry);
            return;
        }
        if (e.isRightClick()) {
            promptCountRange(p, s, entry);
            return;
        }
        // Left-click → change chance
        AnvilInput.open(plugin, p, "&fChance %", String.valueOf(entry.chancePercent()), text -> {
            try { entry.setChancePercent(Double.parseDouble(text)); plugin.templates().save(s.template); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterAnvil(p, s);
        });
    }

    private void promptCountRange(Player p, Session s, RewardEntry entry) {
        AnvilInput.open(plugin, p, "&fCount range (e.g. 1-3)",
                entry.minCount() + "-" + entry.maxCount(), text -> {
            try {
                if (text.contains("-")) {
                    String[] parts = text.split("-", 2);
                    int lo = Integer.parseInt(parts[0].trim());
                    int hi = Integer.parseInt(parts[1].trim());
                    entry.setCountRange(lo, hi);
                } else {
                    int n = Integer.parseInt(text.trim());
                    entry.setCountRange(n, n);
                }
                plugin.templates().save(s.template);
            } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '3' or '1-5'")); }
            reopenAfterAnvil(p, s);
        });
    }

    private void handleControlClick(InventoryClickEvent e, Session s, Player p, int slot) {
        e.setCancelled(true);
        int relative = slot - CONTROL_ROW;

        if (relative == 8) {
            // Back
            TemplateEditorGUI.openFor(plugin, p, s.template);
            return;
        }
        if (relative == 0) {
            AnvilInput.open(plugin, p, "&fMax items per chest", String.valueOf(s.template.maxRewardItems()), text -> {
                try { s.template.setMaxRewardItems(Integer.parseInt(text)); plugin.templates().save(s.template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterAnvil(p, s);
            });
            return;
        }
        if (relative == 2) {
            // Money
            handleMoneyClick(e, s, p);
            return;
        }
        if (relative == 5) {
            // XP
            handleXpClick(e, s, p);
            return;
        }
    }

    private void handleMoneyClick(InventoryClickEvent e, Session s, Player p) {
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            AnvilInput.open(plugin, p, "&fMoney chance %", String.valueOf(s.template.moneyChancePercent()), text -> {
                try { s.template.setMoneyChancePercent(Double.parseDouble(text)); plugin.templates().save(s.template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterAnvil(p, s);
            });
            return;
        }
        if (e.isRightClick()) {
            AnvilInput.open(plugin, p, "&fMoney max", String.valueOf(s.template.moneyMax()), text -> {
                try { s.template.setMoneyMax(Double.parseDouble(text)); plugin.templates().save(s.template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterAnvil(p, s);
            });
            return;
        }
        AnvilInput.open(plugin, p, "&fMoney min", String.valueOf(s.template.moneyMin()), text -> {
            try { s.template.setMoneyMin(Double.parseDouble(text)); plugin.templates().save(s.template); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterAnvil(p, s);
        });
    }

    private void handleXpClick(InventoryClickEvent e, Session s, Player p) {
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            AnvilInput.open(plugin, p, "&fXP chance %", String.valueOf(s.template.xpChancePercent()), text -> {
                try { s.template.setXpChancePercent(Double.parseDouble(text)); plugin.templates().save(s.template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterAnvil(p, s);
            });
            return;
        }
        if (e.isRightClick()) {
            AnvilInput.open(plugin, p, "&fXP levels max", String.valueOf(s.template.xpLevelsMax()), text -> {
                try { s.template.setXpLevelsMax(Integer.parseInt(text)); plugin.templates().save(s.template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterAnvil(p, s);
            });
            return;
        }
        AnvilInput.open(plugin, p, "&fXP levels min", String.valueOf(s.template.xpLevelsMin()), text -> {
            try { s.template.setXpLevelsMin(Integer.parseInt(text)); plugin.templates().save(s.template); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterAnvil(p, s);
        });
    }

    private void addToPool(Session s, ItemStack item) {
        // Strip the "decorate" lore if for some reason it's in the cursor item
        // (only possible if the player picked a decorated item up and dropped it back).
        // We can't 100% detect this, so we just store the raw clone.
        ItemStack clean = item.clone();
        clean.setAmount(1);
        s.template.rewardPool().add(new RewardEntry(clean, 50, 1, 1));
        plugin.templates().save(s.template);
    }

    private void rebuild(Player p, Session s) {
        Inventory fresh = build(s.template);
        // Copy contents into existing inv to avoid closing/reopening
        for (int i = 0; i < fresh.getSize(); i++) s.inv.setItem(i, fresh.getItem(i));
    }

    private void reopenAfterAnvil(Player p, Session s) {
        // After an anvil-input callback, we need to reopen the rewards GUI.
        // The session map still has us — just rebuild and re-open.
        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, s.template));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Session s = viewers.remove(p.getUniqueId());
        if (s == null) return;
        // Returns any cursor item if any (shouldn't happen since clicks cancel, but safety)
        ItemStack cursor = e.getView().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(cursor);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            e.getView().setCursor(null);
        }
    }
}
