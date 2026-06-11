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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewards editor. Drag-and-drop items into the top area to add them to the
 * pool; click an item to edit its chance %/count range; shift-click to remove.
 *
 * Layout (54 slots, 6 rows):
 *   Rows 0–2 (slots 0–26)  — drop zone for reward items. 27 slot capacity.
 *   Row 3 (slots 27–35)    — separator (filler)
 *   Row 4 (36..44)         — money + xp config
 *      36 max items, 38 money min/max+chance, 40 test-rewards preview,
 *      41 xp min/max+chance, 44 back arrow
 *   Row 5 (slots 45–53)    — filler border + help book
 *
 * --- How click safety works ---
 * Each open instance attaches a {@link Holder} as the inventory's holder. The
 * single listener identifies our inventory by checking
 * {@code top.getHolder() instanceof Holder} — reference-stable, no map needed.
 * If we recognise the inventory at all, we ALWAYS cancel clicks in the top
 * half BEFORE any routing logic runs. Earlier versions of this class kept a
 * UUID→Session map and compared {@code top.equals(session.inv)}, which under
 * some Paper builds led to the cancel-vs-route logic getting skipped (the
 * click then went through as a normal item move — that's the "I click the
 * gold ingot and it just gets picked up" bug). The holder-based check fixes
 * that for good.
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
        Holder holder = new Holder(t);
        Inventory inv = INSTANCE.build(holder, t);
        holder.setInventory(inv);
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;

    private RewardsGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    /**
     * The per-open holder. Carries the template the player is editing so we
     * can recover state on every click without a side-map. Bukkit identifies
     * inventories by holder reference, which is the safest pattern.
     */
    public static final class Holder implements InventoryHolder {
        private final DungeonTemplate template;
        private Inventory inv;
        Holder(DungeonTemplate template) { this.template = template; }
        void setInventory(Inventory inv) { this.inv = inv; }
        public DungeonTemplate template() { return template; }
        @Override public Inventory getInventory() { return inv; }
    }

    // ---- layout helpers ----

    private static final int POOL_START = 0;
    private static final int POOL_END_EX = 27; // 0..26 inclusive
    private static final int CONTROL_ROW = 36;

    private Inventory build(Holder holder, DungeonTemplate t) {
        Inventory inv = Bukkit.createInventory(holder, 54, color("&5&lRewards: &f" + t.name()));

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

        inv.setItem(CONTROL_ROW + 4, icon(Material.ENDER_EYE, "&5&lTest End Rewards",
                "&7Virtually open the reward chest and",
                "&7preview what this map will roll.",
                "&7Simulate any tier/quality, reroll, or",
                "&7batch-average many rolls.",
                "",
                "&eClick &7to preview"));

        inv.setItem(CONTROL_ROW + 8, icon(Material.ARROW, "&7← Back to editor"));

        // Bottom border with a help tile in the middle
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler());
        }
        inv.setItem(49, icon(Material.BOOK, "&e&lHow it works",
                "&7Drop or click items into the top area",
                "&7to add them as possible rewards.",
                "",
                "&7Left-click an entry → set chance %",
                "&7Right-click an entry → set count range",
                "&7Shift-click an entry → return + remove",
                "",
                "&8Number prompts close this menu and",
                "&8ask you to type the value in chat."));
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
            if (r.isMMOItem()) {
                lore.add(color("&dMMOItems: &f" + r.mmoType() + ":" + r.mmoId()));
                lore.add(color("&8Rolls fresh per player"));
            }
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

    /** Identify our inventory by holder reference. Safer than inventory equals. */
    private static Holder holderOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return (h instanceof Holder rh) ? rh : null;
    }

    // ---- click handling ----

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return; // not our GUI

        int raw = e.getRawSlot();
        int topSize = top.getSize();

        // ---- Click in player inventory (bottom) ----
        if (raw >= topSize) {
            // Shift-click from player inv → add the clicked item to the pool
            if (e.isShiftClick()) {
                ItemStack toAdd = e.getCurrentItem();
                if (toAdd != null && toAdd.getType() != Material.AIR) {
                    e.setCancelled(true);
                    addToPool(holder, toAdd);
                    p.getInventory().setItem(e.getSlot(), null);
                    rebuild(holder);
                }
            }
            // Otherwise let normal inventory interaction happen in the bottom inv
            return;
        }

        // ---- Click in top inventory (our GUI) ----
        // ALWAYS cancel before routing. This is critical: if any branch below
        // throws or returns early without cancelling, the click would still
        // pick up the item. Doing it unconditionally up front guarantees the
        // gold ingot, hopper, etc. can never be moved.
        e.setCancelled(true);

        // POOL ZONE
        if (raw >= POOL_START && raw < POOL_END_EX) {
            handlePoolClick(e, holder, p, raw);
            return;
        }
        // CONTROLS
        handleControlClick(e, holder, p, raw);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        // Does the drag land in the top inventory at all?
        int topSize = top.getSize();
        boolean touchesTop = false;
        for (int slot : e.getRawSlots()) {
            if (slot < topSize) { touchesTop = true; break; }
        }
        if (!touchesTop) return; // drag stayed in player inv — let it happen

        // Drag into our GUI — always cancel, then maybe add to pool
        e.setCancelled(true);
        ItemStack cursor = e.getOldCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            boolean dropInPool = false;
            for (int slot : e.getRawSlots()) {
                if (slot >= POOL_START && slot < POOL_END_EX) { dropInPool = true; break; }
            }
            if (dropInPool) {
                addToPool(holder, cursor.clone());
                e.getView().setCursor(null);
            }
            Bukkit.getScheduler().runTask(plugin, () -> rebuild(holder));
        }
    }

    private void handlePoolClick(InventoryClickEvent e, Holder holder, Player p, int slot) {
        List<RewardEntry> pool = holder.template().rewardPool();
        ItemStack cursor = e.getView().getCursor();

        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;

        if (cursorHasItem) {
            addToPool(holder, cursor.clone());
            e.getView().setCursor(null);
            rebuild(holder);
            return;
        }

        // Cursor is empty. If they clicked an empty pool slot, do nothing.
        if (slot >= pool.size()) return;

        RewardEntry entry = pool.get(slot);

        if (e.isShiftClick()) {
            // Shift-click an entry → return it to your inventory + remove
            pool.remove(slot);
            plugin.templates().save(holder.template());
            ItemStack original = entry.itemStack().clone();
            var overflow = p.getInventory().addItem(original);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            rebuild(holder);
            p.sendMessage(color("&aRemoved &7" + entry.itemStack().getType() + "&a from pool."));
            return;
        }
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            promptCountRange(p, holder, entry);
            return;
        }
        if (e.isRightClick()) {
            promptCountRange(p, holder, entry);
            return;
        }
        // Left-click → change chance %
        ChatInput.prompt(plugin, p, "&fChance %", String.valueOf(entry.chancePercent()), text -> {
            try { entry.setChancePercent(Double.parseDouble(text)); plugin.templates().save(holder.template()); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterInput(p, holder);
        });
    }

    private void promptCountRange(Player p, Holder holder, RewardEntry entry) {
        ChatInput.prompt(plugin, p, "&fCount range (e.g. 1-3)",
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
                plugin.templates().save(holder.template());
            } catch (NumberFormatException ex) { p.sendMessage(color("&cFormat: '3' or '1-5'")); }
            reopenAfterInput(p, holder);
        });
    }

    private void handleControlClick(InventoryClickEvent e, Holder holder, Player p, int slot) {
        // Cancel was already done by the caller, but no harm doing it again.
        e.setCancelled(true);
        int relative = slot - CONTROL_ROW;

        if (relative == 8) {
            TemplateEditorGUI.openFor(plugin, p, holder.template());
            return;
        }
        if (relative == 0) {
            ChatInput.prompt(plugin, p, "&fMax items per chest", String.valueOf(holder.template().maxRewardItems()), text -> {
                try { holder.template().setMaxRewardItems(Integer.parseInt(text)); plugin.templates().save(holder.template()); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterInput(p, holder);
            });
            return;
        }
        if (relative == 2) {
            handleMoneyClick(e, holder, p);
            return;
        }
        if (relative == 5) {
            handleXpClick(e, holder, p);
            return;
        }
        if (relative == 4) {
            RewardPreviewGUI.openFor(plugin, p, holder.template());
            return;
        }
        // Any other slot in the control row (37, 39, 42, 43) — no-op, click stays cancelled.
    }

    private void handleMoneyClick(InventoryClickEvent e, Holder holder, Player p) {
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            ChatInput.prompt(plugin, p, "&fMoney chance %", String.valueOf(holder.template().moneyChancePercent()), text -> {
                try { holder.template().setMoneyChancePercent(Double.parseDouble(text)); plugin.templates().save(holder.template()); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterInput(p, holder);
            });
            return;
        }
        if (e.isRightClick()) {
            ChatInput.prompt(plugin, p, "&fMoney max", String.valueOf(holder.template().moneyMax()), text -> {
                try { holder.template().setMoneyMax(Double.parseDouble(text)); plugin.templates().save(holder.template()); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterInput(p, holder);
            });
            return;
        }
        ChatInput.prompt(plugin, p, "&fMoney min", String.valueOf(holder.template().moneyMin()), text -> {
            try { holder.template().setMoneyMin(Double.parseDouble(text)); plugin.templates().save(holder.template()); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterInput(p, holder);
        });
    }

    private void handleXpClick(InventoryClickEvent e, Holder holder, Player p) {
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            ChatInput.prompt(plugin, p, "&fXP chance %", String.valueOf(holder.template().xpChancePercent()), text -> {
                try { holder.template().setXpChancePercent(Double.parseDouble(text)); plugin.templates().save(holder.template()); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterInput(p, holder);
            });
            return;
        }
        if (e.isRightClick()) {
            ChatInput.prompt(plugin, p, "&fXP levels max", String.valueOf(holder.template().xpLevelsMax()), text -> {
                try { holder.template().setXpLevelsMax(Integer.parseInt(text)); plugin.templates().save(holder.template()); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                reopenAfterInput(p, holder);
            });
            return;
        }
        ChatInput.prompt(plugin, p, "&fXP levels min", String.valueOf(holder.template().xpLevelsMin()), text -> {
            try { holder.template().setXpLevelsMin(Integer.parseInt(text)); plugin.templates().save(holder.template()); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            reopenAfterInput(p, holder);
        });
    }

    private void addToPool(Holder holder, ItemStack item) {
        ItemStack clean = item.clone();
        clean.setAmount(1);
        RewardEntry entry = new RewardEntry(clean, 50, 1, 1);
        // Auto-detect MMOItems so this entry rolls a fresh item per player.
        if (plugin.mmoItemsHook() != null && plugin.mmoItemsHook().isMMOItem(clean)) {
            entry.setMMOItem(plugin.mmoItemsHook().mmoType(clean),
                             plugin.mmoItemsHook().mmoId(clean));
        }
        holder.template().rewardPool().add(entry);
        plugin.templates().save(holder.template());
    }

    private void rebuild(Holder holder) {
        // Rebuild fresh and copy contents into the existing inv (so we don't
        // have to re-open and lose the viewer's cursor / scroll state).
        Inventory fresh = build(holder, holder.template());
        Inventory existing = holder.getInventory();
        if (existing == null) return;
        for (int i = 0; i < fresh.getSize(); i++) existing.setItem(i, fresh.getItem(i));
    }

    private void reopenAfterInput(Player p, Holder holder) {
        // After a chat-input callback, re-open the rewards GUI on the next tick
        // so the player sees the freshly-saved value reflected immediately.
        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, holder.template()));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;
        // Return any cursor item if any (shouldn't happen since clicks cancel, but safety)
        ItemStack cursor = e.getView().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            var overflow = p.getInventory().addItem(cursor);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            e.getView().setCursor(null);
        }
    }
}
