package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.RewardEntry;
import com.abyss.sigils.dungeon.RewardRoller;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only "virtually open the reward chest" preview, reached from
 * {@link RewardsGUI}. Lets an admin roll the END-OF-DUNGEON reward chest for a
 * template without running a real dungeon, so loot/money/XP tuning is instant.
 *
 * It calls the SAME {@link RewardRoller#rollFor} the live chest uses, with no
 * player (so personal Tome-of-Mastery skills don't skew the sample) but with a
 * SIMULATED tier and quality the admin dials in — that's the only honest way to
 * see the tier/quality scaling.
 *
 * Two modes, toggled in-place:
 *   REROLL — shows one sampled chest; click Reroll for another draw.
 *   BATCH  — rolls N times and reports the spread (avg items, money/XP hit-rate
 *            and ranges) so you can judge averages, not a single lucky roll.
 *
 * Click safety mirrors {@link RewardsGUI}: the inventory carries a {@link Holder}
 * and EVERY top-inventory click is cancelled before routing, so the previewed
 * loot can never be taken.
 */
public final class RewardPreviewGUI implements Listener {

    private static RewardPreviewGUI INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new RewardPreviewGUI(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        register(plugin);
        Holder holder = new Holder(t);
        Inventory inv = INSTANCE.build(holder);
        holder.setInventory(inv);
        p.openInventory(inv);
    }

    private final AbyssPlugin plugin;

    private RewardPreviewGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Per-open state: the template plus the admin's dialled-in simulation knobs. */
    public static final class Holder implements InventoryHolder {
        private final DungeonTemplate template;
        private int tier = 0;
        private int quality = 0;
        private boolean batch = false;
        private int batchSize = 100;
        private Inventory inv;
        Holder(DungeonTemplate template) { this.template = template; }
        void setInventory(Inventory inv) { this.inv = inv; }
        public DungeonTemplate template() { return template; }
        @Override public Inventory getInventory() { return inv; }
    }

    // ---- layout ----

    private static final int POOL_END_EX = 27;   // slots 0..26 show the rolled / reference items
    private static final int CONTROL_ROW = 36;
    private static final int SLOT_TOGGLE     = CONTROL_ROW + 0; // 36
    private static final int SLOT_TIER       = CONTROL_ROW + 2; // 38
    private static final int SLOT_QUALITY    = CONTROL_ROW + 4; // 40
    private static final int SLOT_ACTION     = CONTROL_ROW + 6; // 42  reroll / re-run batch
    private static final int SLOT_BATCH_SIZE = CONTROL_ROW + 7; // 43  (batch mode only)
    private static final int SLOT_BACK       = CONTROL_ROW + 8; // 44
    private static final int SLOT_SUMMARY    = 49;

    private static final int BATCH_MIN = 10, BATCH_MAX = 1000, BATCH_STEP = 25;

    private Inventory build(Holder holder) {
        DungeonTemplate t = holder.template();
        Inventory inv = Bukkit.createInventory(holder, 54,
                color("&5&lTest Rewards: &f" + t.name()));

        // Separator + bottom border.
        for (int i = 27; i < 36; i++) inv.setItem(i, filler());
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());

        if (holder.batch) buildBatch(inv, holder);
        else buildReroll(inv, holder);

        // ---- controls ----
        inv.setItem(SLOT_TOGGLE, icon(Material.COMPARATOR, "&dMode: &f" + (holder.batch ? "Batch" : "Reroll"),
                "&7Reroll &8— one chest at a time.",
                "&7Batch &8— average many rolls.",
                "",
                "&eClick &7to switch mode"));

        int tierMax = plugin.getConfig().getInt("scaling.tier.max", 16);
        inv.setItem(SLOT_TIER, icon(Material.NETHERITE_SWORD, "&cSimulate Tier: &f" + holder.tier,
                "&7Pretend the map is this tier.",
                "&7Max: &f" + tierMax,
                "",
                "&eLeft-click &7+1   &eRight-click &7-1"));

        int qualityMax = plugin.getConfig().getInt("scaling.quality.max", 20);
        inv.setItem(SLOT_QUALITY, icon(Material.GOLD_BLOCK, "&bSimulate Quality: &f" + holder.quality,
                "&7Pretend the map is this quality.",
                "&7Max: &f" + qualityMax,
                "",
                "&eLeft-click &7+1   &eRight-click &7-1"));

        if (holder.batch) {
            inv.setItem(SLOT_ACTION, icon(Material.REPEATER, "&a&lRe-run Batch",
                    "&7Roll &f" + holder.batchSize + "&7 chests again.",
                    "",
                    "&eClick &7to re-run"));
            inv.setItem(SLOT_BATCH_SIZE, icon(Material.PAPER, "&eBatch Size: &f" + holder.batchSize,
                    "&7How many rolls to average.",
                    "&7Range: &f" + BATCH_MIN + "-" + BATCH_MAX,
                    "",
                    "&eLeft-click &7+" + BATCH_STEP + "   &eRight-click &7-" + BATCH_STEP));
        } else {
            inv.setItem(SLOT_ACTION, icon(Material.ENDER_PEARL, "&a&lReroll",
                    "&7Draw a fresh sample chest.",
                    "",
                    "&eClick &7to reroll"));
        }

        inv.setItem(SLOT_BACK, icon(Material.ARROW, "&7← Back to rewards"));
        return inv;
    }

    /** REROLL: roll once, lay the items out chest-style, summarise this single draw. */
    private void buildReroll(Inventory inv, Holder holder) {
        RewardRoller.Roll roll = RewardRoller.rollFor(plugin, holder.template(), null, holder.quality, holder.tier);

        // Mirror the live chest layout (RewardChestManager): centred, every 2nd slot.
        int slot = 4;
        for (ItemStack item : roll.items) {
            inv.setItem(Math.min(slot, 22), item);
            slot += 2;
        }

        inv.setItem(SLOT_SUMMARY, icon(Material.WRITABLE_BOOK, "&d&lThis Roll",
                "&7Simulating: &cT" + holder.tier + " &7/ &bQ" + holder.quality,
                "",
                "&7Items: &f" + roll.items.size(),
                "&7Money: &f" + (roll.money > 0 ? money(roll.money) : "&8— none"),
                "&7XP levels: &f" + (roll.xpLevels > 0 ? String.valueOf(roll.xpLevels) : "&8— none"),
                "",
                "&8Rolls are random — use Batch mode",
                "&8to see the average."));
    }

    /** BATCH: roll N times, aggregate the spread; show the pool as reference. */
    private void buildBatch(Inventory inv, Holder holder) {
        DungeonTemplate t = holder.template();

        // Reference: the configured pool entries (so you can see WHAT can drop).
        List<RewardEntry> pool = t.rewardPool();
        for (int i = 0; i < Math.min(pool.size(), POOL_END_EX); i++) {
            inv.setItem(i, decorate(pool.get(i)));
        }

        int n = holder.batchSize;
        long totalItems = 0; int minItems = Integer.MAX_VALUE, maxItems = 0;
        int moneyHits = 0; double sumMoney = 0, minMoney = Double.MAX_VALUE, maxMoney = 0;
        int xpHits = 0; long sumXp = 0; int minXp = Integer.MAX_VALUE, maxXp = 0;

        for (int i = 0; i < n; i++) {
            RewardRoller.Roll r = RewardRoller.rollFor(plugin, t, null, holder.quality, holder.tier);
            int c = r.items.size();
            totalItems += c; minItems = Math.min(minItems, c); maxItems = Math.max(maxItems, c);
            if (r.money > 0) { moneyHits++; sumMoney += r.money; minMoney = Math.min(minMoney, r.money); maxMoney = Math.max(maxMoney, r.money); }
            if (r.xpLevels > 0) { xpHits++; sumXp += r.xpLevels; minXp = Math.min(minXp, r.xpLevels); maxXp = Math.max(maxXp, r.xpLevels); }
        }
        if (minItems == Integer.MAX_VALUE) minItems = 0;
        if (minXp == Integer.MAX_VALUE) minXp = 0;
        if (minMoney == Double.MAX_VALUE) minMoney = 0;

        String moneyLine = moneyHits == 0 ? "&8— never paid"
                : "&f" + pct(moneyHits, n) + "% &7paid, avg &f" + money(sumMoney / moneyHits)
                  + " &7(" + money(minMoney) + "–" + money(maxMoney) + ")";
        String xpLine = xpHits == 0 ? "&8— never paid"
                : "&f" + pct(xpHits, n) + "% &7paid, avg &f" + round1((double) sumXp / xpHits)
                  + " &7(" + minXp + "–" + maxXp + ")";

        inv.setItem(SLOT_SUMMARY, icon(Material.ENCHANTED_BOOK, "&d&lBatch Average &7(&f" + n + "&7 rolls)",
                "&7Simulating: &cT" + holder.tier + " &7/ &bQ" + holder.quality,
                "",
                "&7Items/chest: &favg " + round1((double) totalItems / n)
                        + " &7(min " + minItems + ", max " + maxItems + ")",
                "&7Money: " + moneyLine,
                "&7XP levels: " + xpLine,
                "",
                "&8Top area shows the configured pool."));
    }

    /** Clone the entry item and append its configured chance/count (reference view). */
    private ItemStack decorate(RewardEntry r) {
        ItemStack stack = r.itemStack().clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(color("&7Chance: &f" + r.chancePercent() + "%"));
            lore.add(color("&7Count: &f" + r.minCount() + "-" + r.maxCount()));
            if (r.isMMOItem()) lore.add(color("&dMMOItems &8(rolls fresh)"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---- click handling ----

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        int raw = e.getRawSlot();
        // Bottom inventory — block shift-clicks (can't shove items into a preview).
        if (raw >= top.getSize()) {
            if (e.isShiftClick()) e.setCancelled(true);
            return;
        }

        // Top inventory — ALWAYS cancel before routing so loot is never takeable.
        e.setCancelled(true);

        switch (raw) {
            case SLOT_BACK -> RewardsGUI.openFor(plugin, p, holder.template());
            case SLOT_TOGGLE -> { holder.batch = !holder.batch; rebuild(holder); }
            case SLOT_ACTION -> rebuild(holder); // reroll / re-run = fresh draw via build()
            case SLOT_TIER -> {
                int max = plugin.getConfig().getInt("scaling.tier.max", 16);
                holder.tier = clamp(holder.tier + (e.isRightClick() ? -1 : 1), 0, max);
                rebuild(holder);
            }
            case SLOT_QUALITY -> {
                int max = plugin.getConfig().getInt("scaling.quality.max", 20);
                holder.quality = clamp(holder.quality + (e.isRightClick() ? -1 : 1), 0, max);
                rebuild(holder);
            }
            case SLOT_BATCH_SIZE -> {
                if (holder.batch) {
                    holder.batchSize = clamp(holder.batchSize + (e.isRightClick() ? -BATCH_STEP : BATCH_STEP), BATCH_MIN, BATCH_MAX);
                    rebuild(holder);
                }
            }
            default -> { /* loot/reference/filler slot — stays cancelled, no-op */ }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (holderOf(top) == null) return;
        for (int slot : e.getRawSlots()) {
            if (slot < top.getSize()) { e.setCancelled(true); return; }
        }
    }

    /** Rebuild fresh and copy into the open inventory (re-rolls without re-opening). */
    private void rebuild(Holder holder) {
        Inventory existing = holder.getInventory();
        if (existing == null) return;
        Inventory fresh = build(holder);
        existing.setContents(fresh.getContents());
    }

    // ---- helpers ----

    private static Holder holderOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return (h instanceof Holder rh) ? rh : null;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String money(double v) { return String.format(java.util.Locale.US, "%.2f", v); }

    private static String round1(double v) { return String.format(java.util.Locale.US, "%.1f", v); }

    private static int pct(int hits, int total) { return total == 0 ? 0 : (int) Math.round(hits * 100.0 / total); }

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
