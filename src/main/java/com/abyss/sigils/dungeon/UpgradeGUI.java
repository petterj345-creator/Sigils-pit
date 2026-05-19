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

    private ItemStack buttonGreen(int tier, int maxTier, int successPct, int xpCost,
                                  int attemptsRemaining, int attemptsCap) {
        ItemStack s = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&a&lFORGE"));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7T" + tier + " &8→ &fT" + Math.min(tier + 1, maxTier)));
            lore.add(Text.color("&7Success: " + colorPct(successPct) + successPct + "%"));
            if (xpCost > 0) lore.add(Text.color("&7Cost: &f" + xpCost + " XP levels"));
            if (attemptsCap >= 0) {
                lore.add(Text.color("&7Attempts left: &f" + attemptsRemaining + "&7/" + attemptsCap));
            }
            lore.add(Text.color("&8Click to attempt upgrade."));
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    /** Book-flavoured version of the green forge button. */
    private ItemStack buttonGreenBook(int tier, int maxTier, int successPct, int xpCost,
                                       int attemptsRemaining, int attemptsCap) {
        ItemStack s = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&a&lFORGE BOOK"));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&5Book of Sigils"));
            lore.add(Text.color("&7T" + tier + " &8→ &fT" + Math.min(tier + 1, maxTier)));
            lore.add(Text.color("&7Success: " + colorPct(successPct) + successPct + "%"));
            if (xpCost > 0) lore.add(Text.color("&7Cost: &f" + xpCost + " XP levels"));
            if (attemptsCap >= 0) {
                lore.add(Text.color("&7Attempts left: &f" + attemptsRemaining + "&7/" + attemptsCap));
            }
            lore.add("");
            lore.add(Text.color("&c&oBook upgrades are rare."));
            lore.add(Text.color("&c&oFailure leaves the book unchanged."));
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
            // Only allow a shift-click of a sigil OR book, routed to the sigil slot.
            if (e.isShiftClick()) {
                ItemStack moving = e.getCurrentItem();
                boolean canForge = moving != null && (SigilItem.isSigil(moving) || SigilItem.isBook(moving));
                if (canForge && top.getItem(SIGIL_SLOT) == null) {
                    e.setCancelled(true);
                    top.setItem(SIGIL_SLOT, moving.clone());
                    p.getInventory().setItem(e.getSlot(), null);
                    Bukkit.getScheduler().runTask(plugin, () -> refreshButton(top));
                } else {
                    e.setCancelled(true);
                }
            }
            return;
        }

        // ---- Clicks in top inventory (our GUI) ----
        if (raw == SIGIL_SLOT) {
            // Allow sigils OR books to be placed.
            ItemStack cursor = e.getView().getCursor();
            if (cursor != null && cursor.getType() != Material.AIR
                    && !SigilItem.isSigil(cursor) && !SigilItem.isBook(cursor)) {
                e.setCancelled(true);
                return;
            }
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
        // Find the viewer (we need their session for the attempt counter)
        Player viewer = null;
        for (org.bukkit.entity.HumanEntity h : inv.getViewers()) {
            if (h instanceof Player pl) { viewer = pl; break; }
        }

        ItemStack input = inv.getItem(SIGIL_SLOT);

        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 0);

        // Branch on what's in the slot — sigil or book
        if (input == null || input.getType() == Material.AIR) {
            inv.setItem(BUTTON_SLOT, buttonRed("Insert a sigil or book"));
            return;
        }

        // Book branch
        if (SigilItem.isBook(input)) {
            int bookTier = SigilItem.bookTierOf(input);
            int maxBookTier = plugin.bookTiers().maxTier();
            if (bookTier >= maxBookTier) {
                inv.setItem(BUTTON_SLOT, buttonRed("Book already max tier"));
                return;
            }
            int successPct = plugin.bookTiers().successPercentFor(bookTier);
            DungeonSession sForBook = (viewer != null) ? plugin.dungeonManager().sessionOf(viewer) : null;
            int capForBook = resolveAttemptsCap(sForBook);
            if (capForBook >= 0 && viewer != null && sForBook != null) {
                int used = sForBook.upgradeAttemptsUsed(viewer.getUniqueId());
                int remaining = Math.max(0, capForBook - used);
                if (remaining == 0) { inv.setItem(BUTTON_SLOT, buttonRed("No attempts left")); return; }
                inv.setItem(BUTTON_SLOT, buttonGreenBook(bookTier, maxBookTier, successPct, xpCost, remaining, capForBook));
            } else {
                inv.setItem(BUTTON_SLOT, buttonGreenBook(bookTier, maxBookTier, successPct, xpCost, -1, -1));
            }
            return;
        }

        // Sigil branch (legacy)
        SigilInstance inst = SigilItem.fromItem(input);
        int maxTierCap = plugin.getConfig().getInt("upgrade.max-tier", 5);

        if (inst == null) { inv.setItem(BUTTON_SLOT, buttonRed("Insert a sigil or book")); return; }
        SigilDefinition def = plugin.sigils().get(inst.definitionId());
        if (def == null) { inv.setItem(BUTTON_SLOT, buttonRed("Unknown sigil")); return; }

        int maxTier = Math.min(def.maxTier(), maxTierCap);
        if (inst.tier() >= maxTier) {
            inv.setItem(BUTTON_SLOT, buttonRed("Already max tier"));
            return;
        }

        // Check attempt budget for this player
        DungeonSession sForViewer = (viewer != null) ? plugin.dungeonManager().sessionOf(viewer) : null;
        int attemptsCap = resolveAttemptsCap(sForViewer);
        if (attemptsCap >= 0 && viewer != null && sForViewer != null) {
            int used = sForViewer.upgradeAttemptsUsed(viewer.getUniqueId());
            int remaining = Math.max(0, attemptsCap - used);
            if (remaining == 0) {
                inv.setItem(BUTTON_SLOT, buttonRed("No attempts left"));
                return;
            }
            int successPct = successPercentFor(inst.tier());
            inv.setItem(BUTTON_SLOT, buttonGreen(inst.tier(), maxTier, successPct, xpCost, remaining, attemptsCap));
            return;
        }

        int successPct = successPercentFor(inst.tier());
        inv.setItem(BUTTON_SLOT, buttonGreen(inst.tier(), maxTier, successPct, xpCost, -1, -1));
    }

    /**
     * Per-template override of upgrade.attempts-per-clear, with fallback to
     * the global config default. Looks up the template by the session's
     * templateName(); if no session/template found, just uses the config value.
     * Returns -1 for unlimited.
     */
    private int resolveAttemptsCap(DungeonSession session) {
        int globalCap = plugin.getConfig().getInt("upgrade.attempts-per-clear", -1);
        if (session == null) return globalCap;
        String tplName = session.templateName();
        if (tplName == null) return globalCap;
        DungeonTemplate tpl = plugin.templates().get(tplName);
        if (tpl == null) return globalCap;
        int perTemplate = tpl.upgradeAttemptsPerClear();
        return (perTemplate < 0) ? globalCap : perTemplate;
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
        ItemStack input = inv.getItem(SIGIL_SLOT);
        if (input == null || input.getType() == Material.AIR) {
            p.sendMessage(Text.color("&cInsert a sigil or book first."));
            return;
        }

        // Book branch
        if (SigilItem.isBook(input)) {
            attemptBookUpgrade(p, inv, input);
            return;
        }

        // Sigil branch
        SigilInstance inst = SigilItem.fromItem(input);
        if (inst == null) { p.sendMessage(Text.color("&cInsert a sigil or book first.")); return; }

        SigilDefinition def = plugin.sigils().get(inst.definitionId());
        if (def == null) { p.sendMessage(Text.color("&cUnknown sigil.")); return; }

        int maxTier = Math.min(def.maxTier(), plugin.getConfig().getInt("upgrade.max-tier", 5));
        if (inst.tier() >= maxTier) { p.sendMessage(Text.color("&cAlready at max tier.")); return; }

        // Attempt budget: -1 = unlimited, otherwise enforce
        DungeonSession session = plugin.dungeonManager().sessionOf(p);
        int attemptsCap = resolveAttemptsCap(session);
        if (attemptsCap >= 0 && session != null) {
            int used = session.upgradeAttemptsUsed(p.getUniqueId());
            if (used >= attemptsCap) {
                p.sendMessage(Text.color("&cYou've used all " + attemptsCap + " forge attempts for this clear."));
                refreshButton(inv);
                return;
            }
        }

        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 0);
        boolean consumeXpOnFail = plugin.getConfig().getBoolean("upgrade.consume-xp-on-fail", true);

        if (xpCost > 0 && p.getLevel() < xpCost) {
            p.sendMessage(Text.color("&cNeed " + xpCost + " XP levels."));
            return;
        }

        // From here we WILL attempt. Count it before rolling so a successful
        // attempt and a failed attempt both consume one slot.
        if (session != null) session.incrementUpgradeAttempts(p.getUniqueId());

        int successPct = successPercentFor(inst.tier());
        boolean succeeded = rng.nextInt(100) < successPct;

        if (xpCost > 0 && (succeeded || consumeXpOnFail)) {
            p.setLevel(p.getLevel() - xpCost);
        }

        if (!succeeded) {
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

    /**
     * Book upgrade flow — mirrors the sigil flow but uses book.success-chance-per-tier,
     * book.max-tier, and the BookTiers lookup. On success we rebuild the item via
     * {@link SigilItem#createBook(int)} so its lore reflects the new socket counts.
     */
    private void attemptBookUpgrade(Player p, Inventory inv, ItemStack bookItem) {
        int currentTier = SigilItem.bookTierOf(bookItem);
        int maxTier = plugin.bookTiers().maxTier();
        if (currentTier >= maxTier) {
            p.sendMessage(Text.color("&cBook is already max tier."));
            return;
        }

        // Same shared attempt budget as sigils
        DungeonSession session = plugin.dungeonManager().sessionOf(p);
        int attemptsCap = resolveAttemptsCap(session);
        if (attemptsCap >= 0 && session != null) {
            int used = session.upgradeAttemptsUsed(p.getUniqueId());
            if (used >= attemptsCap) {
                p.sendMessage(Text.color("&cYou've used all " + attemptsCap + " forge attempts for this clear."));
                refreshButton(inv);
                return;
            }
        }

        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 0);
        boolean consumeXpOnFail = plugin.getConfig().getBoolean("upgrade.consume-xp-on-fail", true);

        if (xpCost > 0 && p.getLevel() < xpCost) {
            p.sendMessage(Text.color("&cNeed " + xpCost + " XP levels."));
            return;
        }

        // Consume an attempt regardless of outcome
        if (session != null) session.incrementUpgradeAttempts(p.getUniqueId());

        int successPct = plugin.bookTiers().successPercentFor(currentTier);
        boolean succeeded = rng.nextInt(100) < successPct;

        if (xpCost > 0 && (succeeded || consumeXpOnFail)) {
            p.setLevel(p.getLevel() - xpCost);
        }

        if (!succeeded) {
            p.sendMessage(Text.color("&c&l✗ The book resists. &7It remains T" + currentTier + "."));
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
            refreshButton(inv);
            return;
        }

        // SUCCESS — rebuild the book at the new tier (lore + PDC tier update)
        int newTier = currentTier + 1;
        ItemStack newBook = SigilItem.createBook(newTier);
        inv.setItem(SIGIL_SLOT, newBook);
        p.sendMessage(Text.color("&5&l✦ Book of Sigils &7is now &fT" + newTier + "&7."));
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
