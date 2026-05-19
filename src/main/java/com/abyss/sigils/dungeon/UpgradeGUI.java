package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilStat;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The upgrade menu opened by clicking the upgrade block in a completed dungeon.
 *
 * Layout (27 slots / 3 rows):
 *   slot 13: sigil input (only input; sigil-only, no dust)
 *   slot 15: confirm button (lime/red depending on validity)
 *   rest:    filler
 *
 * Upgrade rules (from config, defaults shown):
 *   upgrade.max-tier: 5
 *   upgrade.substat-chance: 0.25
 *   upgrade.xp-level-cost: 0          # 0 = no XP required (the new default)
 *   upgrade.success-chance-per-tier:  # success % for going FROM tier N to tier N+1
 *     - 100   # T1 -> T2
 *     - 100   # T2 -> T3
 *     - 75    # T3 -> T4
 *     - 50    # T4 -> T5
 *
 * On failure the sigil is unchanged — the player just keeps trying.
 *
 * --- Click safety ---
 * Earlier versions created the inventory with the Player as the holder, which
 * is ambiguous (every inventory the player owns has the same holder) and the
 * onClick listener relied on a UUID→Inventory map + inventory equality. If
 * that equality check missed, NO click was cancelled — and the button click
 * was registered as a normal item-move ("I just move things around, nothing
 * upgrades"). The current version uses a dedicated {@link Holder} so the
 * listener can identify our inventory by holder reference, and ALL clicks in
 * the top half are cancelled up front before any routing logic runs.
 */
public final class UpgradeGUI implements Listener {

    private final AbyssPlugin plugin;
    private final Random rng = new Random();

    private static final int SIGIL_SLOT  = 13;
    private static final int BUTTON_SLOT = 15;

    public UpgradeGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Per-open holder. Used to identify our inventory by reference. */
    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        Holder() {}
        void setInventory(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.color("&5&lForge of the Abyss"));
        holder.setInventory(inv);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());
        inv.setItem(SIGIL_SLOT, null);
        inv.setItem(BUTTON_SLOT, buttonRed("Insert a sigil"));
        p.openInventory(inv);
    }

    private static Holder holderOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return (h instanceof Holder uh) ? uh : null;
    }

    private ItemStack filler() {
        ItemStack s = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    private ItemStack buttonRed(String label) {
        ItemStack s = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&c" + label));
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack buttonGreen(int tier, int maxTier, int successPct, int xpCost) {
        ItemStack s = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&a&lFORGE"));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7T" + tier + " &8→ &fT" + Math.min(tier + 1, maxTier)));
            lore.add(Text.color("&7Success: " + colorPct(successPct) + successPct + "%"));
            if (xpCost > 0) lore.add(Text.color("&7Cost: &f" + xpCost + " XP levels"));
            lore.add(Text.color("&8Click to attempt upgrade."));
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    /** Colour the success number based on how risky it is — green/yellow/red. */
    private String colorPct(int pct) {
        if (pct >= 90) return "&a";
        if (pct >= 60) return "&e";
        return "&c";
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return; // not our GUI

        int raw = e.getRawSlot();
        int topSize = top.getSize();
        boolean isTopClick = raw < topSize;

        // ---- Clicks in player inventory (bottom) ----
        if (!isTopClick) {
            // Block shift-clicks from below — they'd dump items into our fillers.
            // Only allow a shift-click of a sigil, and route it to the sigil slot.
            if (e.isShiftClick()) {
                ItemStack moving = e.getCurrentItem();
                if (moving != null && SigilItem.isSigil(moving) && top.getItem(SIGIL_SLOT) == null) {
                    e.setCancelled(true);
                    top.setItem(SIGIL_SLOT, moving.clone());
                    p.getInventory().setItem(e.getSlot(), null);
                    Bukkit.getScheduler().runTask(plugin, () -> refreshButton(top));
                } else {
                    // Any other shift-click from player inv into our GUI: block it.
                    e.setCancelled(true);
                }
            }
            return; // normal bottom-inv interaction is fine otherwise
        }

        // ---- Clicks in top inventory (our GUI) ----
        if (raw == SIGIL_SLOT) {
            // Only allow sigils to be placed, anything else is blocked.
            ItemStack cursor = e.getView().getCursor();
            if (cursor != null && cursor.getType() != Material.AIR && !SigilItem.isSigil(cursor)) {
                e.setCancelled(true);
                return;
            }
            // Allow the click to go through (place/take the sigil), then
            // refresh the button on the next tick to reflect new state.
            Bukkit.getScheduler().runTask(plugin, () -> refreshButton(top));
            return;
        }

        if (raw == BUTTON_SLOT) {
            // Always cancel button slot — it's a UI element, never an item.
            e.setCancelled(true);
            attemptUpgrade(p, top);
            return;
        }

        // Any other slot in the top half is filler — block all interaction.
        e.setCancelled(true);
    }

    private void refreshButton(Inventory inv) {
        ItemStack sigilItem = inv.getItem(SIGIL_SLOT);
        SigilInstance inst = SigilItem.fromItem(sigilItem);

        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 0);
        int maxTierCap = plugin.getConfig().getInt("upgrade.max-tier", 5);

        if (inst == null) { inv.setItem(BUTTON_SLOT, buttonRed("Insert a sigil")); return; }
        SigilDefinition def = plugin.sigils().get(inst.definitionId());
        if (def == null) { inv.setItem(BUTTON_SLOT, buttonRed("Unknown sigil")); return; }

        int maxTier = Math.min(def.maxTier(), maxTierCap);
        if (inst.tier() >= maxTier) {
            inv.setItem(BUTTON_SLOT, buttonRed("Already max tier"));
            return;
        }
        int successPct = successPercentFor(inst.tier());
        inv.setItem(BUTTON_SLOT, buttonGreen(inst.tier(), maxTier, successPct, xpCost));
    }

    /**
     * Success % for going FROM the given tier to (tier+1). Reads from config
     * list "upgrade.success-chance-per-tier" — index 0 is T1→T2, index 1 is
     * T2→T3, etc. If the list is too short for the requested tier, the last
     * value in the list is reused (so e.g. a single "75" applies to every
     * tier). If the list is missing entirely, a sensible default curve is used.
     */
    private int successPercentFor(int currentTier) {
        List<Integer> list = plugin.getConfig().getIntegerList("upgrade.success-chance-per-tier");
        if (list == null || list.isEmpty()) {
            return switch (currentTier) {
                case 1, 2 -> 100;
                case 3    -> 75;
                case 4    -> 50;
                default   -> 25;
            };
        }
        int idx = Math.max(0, currentTier - 1);
        if (idx >= list.size()) idx = list.size() - 1;
        int pct = list.get(idx);
        if (pct < 0)   pct = 0;
        if (pct > 100) pct = 100;
        return pct;
    }

    private void attemptUpgrade(Player p, Inventory inv) {
        ItemStack sigilItem = inv.getItem(SIGIL_SLOT);
        SigilInstance inst = SigilItem.fromItem(sigilItem);
        if (inst == null) { p.sendMessage(Text.color("&cInsert a sigil first.")); return; }

        SigilDefinition def = plugin.sigils().get(inst.definitionId());
        if (def == null) { p.sendMessage(Text.color("&cUnknown sigil.")); return; }

        int maxTier = Math.min(def.maxTier(), plugin.getConfig().getInt("upgrade.max-tier", 5));
        if (inst.tier() >= maxTier) { p.sendMessage(Text.color("&cAlready at max tier.")); return; }

        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 0);
        boolean consumeXpOnFail = plugin.getConfig().getBoolean("upgrade.consume-xp-on-fail", true);

        if (xpCost > 0 && p.getLevel() < xpCost) {
            p.sendMessage(Text.color("&cNeed " + xpCost + " XP levels."));
            return;
        }

        int successPct = successPercentFor(inst.tier());
        boolean succeeded = rng.nextInt(100) < successPct;

        if (xpCost > 0 && (succeeded || consumeXpOnFail)) {
            p.setLevel(p.getLevel() - xpCost);
        }

        if (!succeeded) {
            // User-asked behaviour: sigil stays unchanged, just retry.
            p.sendMessage(Text.color("&c&l✗ The forging failed. &7Your sigil is unchanged."));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
            refreshButton(inv);
            return;
        }

        // SUCCESS — bump tier and roll a possible sub-stat
        inst.setTier(inst.tier() + 1);

        double substatChance = plugin.getConfig().getDouble("upgrade.substat-chance", 0.25);
        if (!def.substatPool().isEmpty() && rng.nextDouble() < substatChance) {
            SigilStat sub = def.substatPool().get(rng.nextInt(def.substatPool().size()));
            double value = Math.max(1.0, def.valueAtTier(inst.tier()) * 0.25);
            inst.addSubStat(sub, value);
            p.sendMessage(Text.color("&d✦ A new sub-stat was forged!"));
        }

        inv.setItem(SIGIL_SLOT, SigilItem.toItem(inst));
        p.sendMessage(Text.color("&a&lForged! &7" + def.id() + " is now T" + inst.tier() + "."));
        p.getWorld().strikeLightningEffect(p.getLocation());
        refreshButton(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;
        // Return any sigil left in the input slot to the player
        ItemStack item = top.getItem(SIGIL_SLOT);
        if (item != null && item.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(item);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            top.setItem(SIGIL_SLOT, null);
        }
    }
}
