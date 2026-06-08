package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonSession;
import com.abyss.sigils.dungeon.RitualManager;
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
 * The soul shop, opened by right-clicking a cleared ritual altar. Shows the
 * player's per-run soul balance and the items rolled for them (rolled once,
 * cached in {@link RitualManager}). Clicking an affordable item buys it: souls
 * are deducted, a fresh item (MMOItems re-rolled) is given, and the offer is
 * marked bought. All clicks are cancelled so nothing can be removed/inserted.
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
        /** slot -> offer index */
        private final Map<Integer, Integer> slotToOffer = new HashMap<>();
        private Inventory inv;
        Holder(DungeonSession session, List<RitualManager.Offer> offers) {
            this.session = session;
            this.offers = offers;
        }
        void setInventory(Inventory inv) { this.inv = inv; }
        public DungeonSession session() { return session; }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Interior slots offers are laid into (3 rows, columns 1–7). */
    private static final int[] OFFER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int BALANCE_SLOT = 49;
    private static final int REROLL_SLOT = 51;

    private Inventory build(Holder holder, Player p) {
        Inventory inv = Bukkit.createInventory(holder, 54, color("&5&l✦ Soul Shop"));
        holder.slotToOffer.clear();

        // border
        for (int i = 0; i < 9; i++) inv.setItem(i, filler());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, filler()); inv.setItem(r * 9 + 8, filler()); }

        int balance = holder.session.soulsOf(p.getUniqueId());
        List<RitualManager.Offer> offers = holder.offers;
        for (int i = 0; i < offers.size() && i < OFFER_SLOTS.length; i++) {
            int slot = OFFER_SLOTS[i];
            inv.setItem(slot, decorate(offers.get(i), balance));
            holder.slotToOffer.put(slot, i);
        }

        inv.setItem(BALANCE_SLOT, icon(Material.ECHO_SHARD,
                "&bYour Souls: &f" + balance,
                "&7Earned by slaying ritual mobs.",
                "&7Spend them on the items above.",
                "",
                "&8Unspent souls are lost when you leave."));

        int rerollCost = rerollCost(holder.session);
        if (rerollCost > 0) {
            boolean can = balance >= rerollCost;
            inv.setItem(REROLL_SLOT, icon(can ? Material.ENDER_EYE : Material.ENDER_PEARL,
                    "&d&l↻ Reroll Shop",
                    "&7Replace the items above with a",
                    "&7fresh roll from the pool.",
                    "&7Cost: &b" + rerollCost + " souls",
                    "",
                    can ? "&eClick to reroll" : "&cNot enough souls"));
        }
        return inv;
    }

    private int rerollCost(DungeonSession session) {
        var t = plugin.templates().get(session.templateName());
        return t == null ? 0 : t.ritualRerollCost();
    }

    private ItemStack decorate(RitualManager.Offer offer, int balance) {
        ItemStack stack = offer.source.itemStack().clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&7Price: &b" + offer.price + " souls"));
            if (offer.bought) {
                lore.add(color("&a✔ Purchased"));
            } else if (balance >= offer.price) {
                lore.add(color("&eClick to buy"));
            } else {
                lore.add(color("&cNot enough souls"));
            }
            if (offer.source.isMMOItem()) lore.add(color("&8Rolls fresh on purchase"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;

        // Cancel everything — the shop is read-only except buying.
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        DungeonSession session = holder.session;

        // Reroll button
        if (e.getRawSlot() == REROLL_SLOT) {
            int cost = rerollCost(session);
            if (cost <= 0) return;
            if (!session.spendSouls(p.getUniqueId(), cost)) {
                p.sendMessage(color("&cNot enough souls to reroll. &7Need &b" + cost + "&7."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            plugin.ritualManager().reroll(session, p);
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
            p.sendMessage(color("&dRerolled the shop for &b" + cost + " souls&d. "
                    + "&7Balance: &b" + session.soulsOf(p.getUniqueId())));
            // Reopen so the new offers + balance show.
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, session));
            return;
        }

        Integer offerIdx = holder.slotToOffer.get(e.getRawSlot());
        if (offerIdx == null) return;
        RitualManager.Offer offer = holder.offers.get(offerIdx);

        if (offer.bought) {
            p.sendMessage(color("&7You already bought that."));
            return;
        }
        if (!session.spendSouls(p.getUniqueId(), offer.price)) {
            p.sendMessage(color("&cNot enough souls. &7You have &b"
                    + session.soulsOf(p.getUniqueId()) + "&7, need &b" + offer.price + "&7."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        offer.bought = true;
        ItemStack reward = offer.source.resolve(plugin);
        var overflow = p.getInventory().addItem(reward);
        for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        p.sendMessage(color("&aPurchased for &b" + offer.price + " souls&a. "
                + "&7Balance: &b" + session.soulsOf(p.getUniqueId())));

        // Rebuild in place so balance + "Purchased" labels update live.
        Inventory fresh = build(holder, p);
        for (int i = 0; i < fresh.getSize(); i++) top.setItem(i, fresh.getItem(i));
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
