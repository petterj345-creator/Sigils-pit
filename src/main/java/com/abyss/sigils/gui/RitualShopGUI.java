package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonSession;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.ReservedReward;
import com.abyss.sigils.dungeon.RitualManager;
import com.abyss.sigils.dungeon.RitualReward;
import com.abyss.sigils.integration.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The soul shop, opened by right-clicking a cleared ritual altar.
 *
 * Shows (in order): the player's reserved items (carried across maps), an
 * optional cash offer, then the rolled item offers. Souls are the currency.
 *  - Item offer: left-click buys, right-click reserves (pay a deposit, lock a
 *    discounted price, keep it for a future shop).
 *  - Reserved item: left-click pays the remaining balance, shift-left removes it
 *    (no refund).
 *  - Cash offer: left-click buys, depositing money via Vault.
 * All clicks are cancelled so nothing can be inserted/extracted.
 */
public final class RitualShopGUI implements Listener {

    private static RitualShopGUI INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new RitualShopGUI(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonSession session) {
        register(plugin);
        List<RitualManager.Offer> offers = plugin.ritualManager().offersFor(session, p);
        Holder holder = new Holder(session, offers);
        Inventory inv = INSTANCE.build(holder, p);
        holder.setInventory(inv);
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;
    private RitualShopGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static final class Holder implements InventoryHolder {
        private final DungeonSession session;
        private final List<RitualManager.Offer> offers;
        /** slot -> offer index (cash/item offers). */
        private final Map<Integer, Integer> slotToOffer = new HashMap<>();
        /** slot -> reserved-item index. */
        private final Map<Integer, Integer> slotToReserved = new HashMap<>();
        private Inventory inv;
        Holder(DungeonSession session, List<RitualManager.Offer> offers) {
            this.session = session;
            this.offers = offers;
        }
        void setInventory(Inventory inv) { this.inv = inv; }
        public DungeonSession session() { return session; }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Interior slots offers/reserved are laid into (3 rows, columns 1–7). */
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int BALANCE_SLOT = 49;
    private static final int REROLL_SLOT = 51;

    private Inventory build(Holder holder, Player p) {
        Inventory inv = Bukkit.createInventory(holder, 54, color("&5&l✦ Soul Shop"));
        holder.slotToOffer.clear();
        holder.slotToReserved.clear();

        for (int i = 0; i < 9; i++) inv.setItem(i, filler());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, filler()); inv.setItem(r * 9 + 8, filler()); }

        int balance = holder.session.soulsOf(p.getUniqueId());
        int slotIdx = 0;

        // Reserved items first.
        List<ReservedReward> reserved = plugin.reservedRewards().forPlayer(p.getUniqueId());
        for (int i = 0; i < reserved.size() && slotIdx < SLOTS.length; i++, slotIdx++) {
            int slot = SLOTS[slotIdx];
            inv.setItem(slot, decorateReserved(reserved.get(i), balance));
            holder.slotToReserved.put(slot, i);
        }

        // Then offers (cash + items).
        for (int i = 0; i < holder.offers.size() && slotIdx < SLOTS.length; i++, slotIdx++) {
            int slot = SLOTS[slotIdx];
            inv.setItem(slot, decorateOffer(holder, holder.offers.get(i), balance));
            holder.slotToOffer.put(slot, i);
        }

        inv.setItem(BALANCE_SLOT, icon(Material.ECHO_SHARD,
                "&bYour Souls: &f" + balance,
                "&7Earned by slaying the summoned souls.",
                "",
                "&8Unspent souls are lost when you leave.",
                "&8Reserved items are kept for next time."));

        int rerollCost = rerollCost(holder.session);
        if (rerollCost > 0) {
            boolean can = balance >= rerollCost;
            inv.setItem(REROLL_SLOT, icon(can ? Material.ENDER_EYE : Material.ENDER_PEARL,
                    "&d&l↻ Reroll Shop",
                    "&7Replace the offered items with a",
                    "&7fresh roll (reserved items stay).",
                    "&7Cost: &b" + rerollCost + " souls",
                    "",
                    can ? "&eClick to reroll" : "&cNot enough souls"));
        }
        return inv;
    }

    private DungeonTemplate template(DungeonSession session) {
        return plugin.templates().get(session.templateName());
    }

    private int rerollCost(DungeonSession session) {
        DungeonTemplate t = template(session);
        return t == null ? 0 : t.ritualRerollCost();
    }

    // ----- icons -----

    private ItemStack decorateOffer(Holder holder, RitualManager.Offer offer, int balance) {
        if (offer.isCash()) {
            ItemStack s = icon(Material.PAPER,
                    "&6&l$" + offer.cashAmount + " &eCash",
                    "&7A bundle of coins.",
                    "&7Price: &b" + offer.price + " souls",
                    "",
                    offer.bought ? "&a✔ Purchased"
                            : (VaultHook.available()
                                ? (balance >= offer.price ? "&eClick to buy" : "&cNot enough souls")
                                : "&cVault not installed"));
            return s;
        }
        RitualReward src = offer.source;
        ItemStack stack = src.itemStack().clone();
        stack.setAmount(Math.max(1, Math.min(64, src.amount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&7Price: &b" + offer.price + " souls"));
            if (src.amount() > 1) lore.add(color("&7Quantity: &f" + src.amount()));
            if (offer.bought) {
                lore.add(color("&a✔ Bought / reserved"));
            } else {
                lore.add(color(balance >= offer.price ? "&eLeft-click to buy" : "&cNot enough souls"));
                lore.add(color("&eRight-click &7→ reserve for later"));
            }
            if (src.isMMOItem()) lore.add(color("&8Rolls fresh on purchase"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack decorateReserved(ReservedReward r, int balance) {
        ItemStack stack = r.itemStack().clone();
        stack.setAmount(Math.max(1, Math.min(64, r.amount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&d✦ Reserved"));
            lore.add(color("&7Remaining: &b" + r.remaining() + " souls"));
            if (r.amount() > 1) lore.add(color("&7Quantity: &f" + r.amount()));
            lore.add("");
            lore.add(color(balance >= r.remaining() ? "&eLeft-click to collect" : "&cNot enough souls"));
            lore.add(color("&cShift-left-click &7→ discard (no refund)"));
            if (r.isMMOItem()) lore.add(color("&8Rolls fresh on collect"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ----- clicks -----

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        int raw = e.getRawSlot();

        if (raw == REROLL_SLOT) { handleReroll(holder, p); return; }

        Integer reservedIdx = holder.slotToReserved.get(raw);
        if (reservedIdx != null) { handleReserved(holder, p, reservedIdx, e.isShiftClick()); return; }

        Integer offerIdx = holder.slotToOffer.get(raw);
        if (offerIdx == null) return;
        RitualManager.Offer offer = holder.offers.get(offerIdx);

        if (offer.bought) { p.sendMessage(color("&7You already took that.")); return; }
        if (offer.isCash()) { if (e.isLeftClick()) buyCash(holder, p, offer); return; }
        if (e.isRightClick()) reserve(holder, p, offer);
        else buyItem(holder, p, offer);
    }

    private void handleReroll(Holder holder, Player p) {
        DungeonSession session = holder.session;
        int cost = rerollCost(session);
        if (cost <= 0) return;
        if (!session.spendSouls(p.getUniqueId(), cost)) {
            notEnough(p, cost);
            return;
        }
        plugin.ritualManager().reroll(session, p);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        p.sendMessage(color("&dRerolled for &b" + cost + " souls&d. &7Balance: &b" + session.soulsOf(p.getUniqueId())));
        reopen(p, session);
    }

    private void buyItem(Holder holder, Player p, RitualManager.Offer offer) {
        DungeonSession session = holder.session;
        if (!session.spendSouls(p.getUniqueId(), offer.price)) { notEnough(p, offer.price); return; }
        offer.bought = true;
        give(p, offer.source.resolve(plugin));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        p.sendMessage(color("&aPurchased for &b" + offer.price + " souls&a. &7Balance: &b" + session.soulsOf(p.getUniqueId())));
        reopen(p, session);
    }

    private void buyCash(Holder holder, Player p, RitualManager.Offer offer) {
        DungeonSession session = holder.session;
        if (!VaultHook.available()) { p.sendMessage(color("&cMoney rewards need Vault, which isn't installed.")); return; }
        if (!session.spendSouls(p.getUniqueId(), offer.price)) { notEnough(p, offer.price); return; }
        offer.bought = true;
        VaultHook.deposit(p, offer.cashAmount);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        p.sendMessage(color("&aReceived &f" + VaultHook.format(offer.cashAmount) + " &afor &b" + offer.price + " souls&a."));
        reopen(p, session);
    }

    private void reserve(Holder holder, Player p, RitualManager.Offer offer) {
        DungeonSession session = holder.session;
        DungeonTemplate t = template(session);
        if (t == null) return;
        int limit = t.ritualReserveLimit();
        if (limit <= 0) { p.sendMessage(color("&cReserving is disabled here.")); return; }
        if (plugin.reservedRewards().count(p.getUniqueId()) >= limit) {
            p.sendMessage(color("&cYou can only reserve &f" + limit + " &citems. Collect or discard one first."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int deposit = (int) Math.round(offer.price * t.ritualReserveDepositPct() / 100.0);
        if (!session.spendSouls(p.getUniqueId(), deposit)) {
            p.sendMessage(color("&cNeed &b" + deposit + " souls &cdeposit to reserve. &7You have &b"
                    + session.soulsOf(p.getUniqueId()) + "&7."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int lockedTotal = (int) Math.round(offer.price * (100 - t.ritualReserveDiscountPct()) / 100.0);
        int remaining = Math.max(0, lockedTotal - deposit);
        RitualReward src = offer.source;
        plugin.reservedRewards().add(p.getUniqueId(),
                new ReservedReward(src.itemStack().clone(), src.amount(), remaining, src.mmoType(), src.mmoId()));
        offer.bought = true; // leaves the shop
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
        p.sendMessage(color("&dReserved! &7Paid &b" + deposit + " &7deposit, &b" + remaining
                + " souls &7left to collect (kept across maps)."));
        reopen(p, session);
    }

    private void handleReserved(Holder holder, Player p, int idx, boolean shift) {
        DungeonSession session = holder.session;
        List<ReservedReward> list = plugin.reservedRewards().forPlayer(p.getUniqueId());
        if (idx < 0 || idx >= list.size()) return;
        ReservedReward r = list.get(idx);

        if (shift) {
            plugin.reservedRewards().remove(p.getUniqueId(), idx);
            p.sendMessage(color("&7Discarded a reserved item &8(deposit not refunded)&7."));
            reopen(p, session);
            return;
        }
        if (!session.spendSouls(p.getUniqueId(), r.remaining())) { notEnough(p, r.remaining()); return; }
        give(p, r.resolve(plugin));
        plugin.reservedRewards().remove(p.getUniqueId(), idx);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        p.sendMessage(color("&aCollected your reserved item for &b" + r.remaining() + " souls&a."));
        reopen(p, session);
    }

    // ----- helpers -----

    private void give(Player p, ItemStack item) {
        var overflow = p.getInventory().addItem(item);
        for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
    }

    private void notEnough(Player p, int cost) {
        p.sendMessage(color("&cNot enough souls. &7Need &b" + cost + "&7."));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private void reopen(Player p, DungeonSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, session));
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
}
