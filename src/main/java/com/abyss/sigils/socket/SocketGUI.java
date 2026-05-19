package com.abyss.sigils.socket;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilRank;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * The Book of Sigils GUI. 54 slots, laid out as two open book pages:
 *
 *   row 0: 0  1  2  3  4  5  6  7  8     ← title bar / decoration
 *   row 1: 9  [S][S][S][S][S]  .  [B]  17  ← 5 small + 1 big (left page top)
 *   row 2: 18 [S][S][S][S][S]  .  [B]  26  ← 5 small + 1 big (left page bot)
 *   row 3: 27 28 29 30 31 32  33  [B] 35  ← spacer + 1 big (right page)
 *   row 4: 36..44  ← stats summary area
 *   row 5: 45..53  ← footer/back
 *
 * Concretely:
 *   small sockets: slots 10..14 and 19..23 → 10 small
 *   big sockets:   slots 16, 25, 34       → 3 big
 *
 * Rules:
 *   - Only MINOR sigils can be placed into small sockets.
 *   - Only MAJOR sigils can be placed into big sockets.
 *   - Anything else is rejected with a message; the cursor item stays.
 */
public final class SocketGUI implements Listener {

    private final AbyssPlugin plugin;
    private final PlayerSigilStore store;
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Map<UUID, Map<Integer, Integer>> slotMap = new HashMap<>(); // playerUUID -> (rawSlot -> storeIndex)
    private final Map<UUID, Map<Integer, SigilRank>> slotRank = new HashMap<>(); // playerUUID -> (rawSlot -> rank accepted)

    public static final int[] SMALL_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
    public static final int[] BIG_SLOTS   = {16, 25, 34};
    /**
     * "Grand" sockets — unlocked at high book tiers (default T16+). They
     * accept MAJOR sigils too, so they're effectively bonus big slots.
     */
    public static final int[] GRAND_SLOTS = {17, 26, 35};

    public SocketGUI(AbyssPlugin plugin, PlayerSigilStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void openFor(Player p) {
        Inventory inv = Bukkit.createInventory(p, 54,
                Text.color(plugin.getConfig().getString("socket.gui-title", "&5&lBook of Sigils")));

        // Fill border + decoration
        for (int i = 0; i < 54; i++) inv.setItem(i, decoFiller());
        // Title bar
        for (int i = 1; i < 8; i++) inv.setItem(i, bookSpine());
        inv.setItem(0, bookCorner());
        inv.setItem(8, bookCorner());
        inv.setItem(45, bookCorner());
        inv.setItem(53, bookCorner());

        // What tier of book does the player have? We resolve by scanning their
        // inventory for any book item and picking the highest tier among them.
        // This way upgrading their book to T5 unlocks the slots even if the
        // upgraded book ends up in slot 8 rather than the hotbar.
        int bookTier = findHighestBookTier(p);
        com.abyss.sigils.sigils.BookTiers tiers = plugin.bookTiers();
        int smallUnlocked = Math.min(SMALL_SLOTS.length, tiers.smallSlots(bookTier));
        int bigUnlocked   = Math.min(BIG_SLOTS.length,   tiers.bigSlots(bookTier));
        int grandUnlocked = Math.min(GRAND_SLOTS.length, tiers.grandSlots(bookTier));

        // Small sockets — unlocked + locked placeholders for the rest
        Map<Integer, Integer> sm = new HashMap<>();
        Map<Integer, SigilRank> sr = new HashMap<>();
        List<SigilInstance> smalls = store.getSmall(p.getUniqueId());
        for (int i = 0; i < SMALL_SLOTS.length; i++) {
            int rawSlot = SMALL_SLOTS[i];
            if (i < smallUnlocked) {
                sm.put(rawSlot, i);
                sr.put(rawSlot, SigilRank.MINOR);
                SigilInstance inst = i < smalls.size() ? smalls.get(i) : null;
                inv.setItem(rawSlot, inst == null ? smallSlotEmpty(i + 1) : SigilItem.toItem(inst));
            } else {
                inv.setItem(rawSlot, slotLocked(bookTier));
            }
        }
        // Big sockets
        List<SigilInstance> bigs = store.getBig(p.getUniqueId());
        for (int i = 0; i < BIG_SLOTS.length; i++) {
            int rawSlot = BIG_SLOTS[i];
            if (i < bigUnlocked) {
                sm.put(rawSlot, i + 100); // +100 marks big-store index
                sr.put(rawSlot, SigilRank.MAJOR);
                SigilInstance inst = i < bigs.size() ? bigs.get(i) : null;
                inv.setItem(rawSlot, inst == null ? bigSlotEmpty(i + 1) : SigilItem.toItem(inst));
            } else {
                inv.setItem(rawSlot, slotLocked(bookTier));
            }
        }
        // Grand sockets — share the big-store backing, indices BIG_SLOTS.length .. +grand
        for (int i = 0; i < GRAND_SLOTS.length; i++) {
            int rawSlot = GRAND_SLOTS[i];
            if (i < grandUnlocked) {
                int storeIdx = i + BIG_SLOTS.length;
                sm.put(rawSlot, storeIdx + 100);
                sr.put(rawSlot, SigilRank.MAJOR); // accepts major sigils (same as big)
                SigilInstance inst = storeIdx < bigs.size() ? bigs.get(storeIdx) : null;
                inv.setItem(rawSlot, inst == null ? grandSlotEmpty(i + 1) : SigilItem.toItem(inst));
            } else {
                inv.setItem(rawSlot, slotLocked(bookTier));
            }
        }

        slotMap.put(p.getUniqueId(), sm);
        slotRank.put(p.getUniqueId(), sr);

        // Stats summary panel (bottom row)
        renderSummary(p, inv);

        // Help text in corners
        inv.setItem(36, icon(Material.BOOK, "&5&lBook of Sigils",
                "&7Insert sigils to gain their power.",
                "",
                "&7Small sockets: &fminor sigils only",
                "&7Big sockets: &fmajor sigils only"));

        open.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    private void renderSummary(Player p, Inventory inv) {
        // Just a snapshot icon — actual stat values come from SigilStatApplier.
        ItemStack icon = icon(Material.NETHER_STAR, "&e&lActive Bonuses",
                "&7Equipped sigils:",
                "&7Small: &f" + countNonNull(store.getSmall(p.getUniqueId())) + "&7/" + store.smallSlots(),
                "&7Big: &f" + countNonNull(store.getBig(p.getUniqueId())) + "&7/" + store.bigSlots(),
                "",
                "&8Stats apply automatically.");
        inv.setItem(44, icon);
    }

    private int countNonNull(List<SigilInstance> list) {
        int n = 0;
        for (SigilInstance i : list) if (i != null) n++;
        return n;
    }

    // ----- icon helpers -----

    private ItemStack smallSlotEmpty(int n) {
        return icon(Material.GRAY_STAINED_GLASS_PANE,
                "&7Small Socket #" + n,
                "&8Insert a minor sigil");
    }

    private ItemStack bigSlotEmpty(int n) {
        return icon(Material.YELLOW_STAINED_GLASS_PANE,
                "&6Big Socket #" + n,
                "&8Insert a major sigil");
    }

    private ItemStack grandSlotEmpty(int n) {
        return icon(Material.PURPLE_STAINED_GLASS_PANE,
                "&5&lGrand Socket #" + n,
                "&8Insert a major sigil",
                "&8Unlocked by high-tier book");
    }

    /**
     * Tile shown in a socket position that the player's current book tier
     * hasn't unlocked yet. Shows a hint about which tier to reach.
     */
    private ItemStack slotLocked(int currentTier) {
        return icon(Material.BARRIER,
                "&c&l✕ Locked",
                "&7Upgrade your Book of Sigils",
                "&7at the Forge to unlock more slots.",
                "&7Current tier: &fT" + currentTier);
    }

    /**
     * Scan the player's whole inventory (including hotbar and offhand) for
     * any Book of Sigils, and return the highest tier found. Returns 1 as
     * fallback if no book is present — we still render something useful.
     */
    private int findHighestBookTier(Player p) {
        int max = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (SigilItem.isBook(it)) {
                int t = SigilItem.bookTierOf(it);
                if (t > max) max = t;
            }
        }
        if (SigilItem.isBook(p.getInventory().getItemInOffHand())) {
            int t = SigilItem.bookTierOf(p.getInventory().getItemInOffHand());
            if (t > max) max = t;
        }
        return Math.max(1, max);
    }

    private ItemStack decoFiller() {
        ItemStack s = new ItemStack(Material.BROWN_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    private ItemStack bookSpine() {
        ItemStack s = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    private ItemStack bookCorner() {
        ItemStack s = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore.length > 0) {
                List<String> l = new ArrayList<>();
                for (String li : lore) l.add(Text.color(li));
                meta.setLore(l);
            }
            s.setItemMeta(meta);
        }
        return s;
    }

    private boolean isEmptySocketIcon(ItemStack stack) {
        return stack != null && (stack.getType() == Material.GRAY_STAINED_GLASS_PANE
                || stack.getType() == Material.YELLOW_STAINED_GLASS_PANE);
    }

    // ----- click handling -----

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory tracked = open.get(p.getUniqueId());
        if (tracked == null || !e.getView().getTopInventory().equals(tracked)) return;

        Map<Integer, Integer> sm = slotMap.get(p.getUniqueId());
        Map<Integer, SigilRank> sr = slotRank.get(p.getUniqueId());
        if (sm == null || sr == null) return;

        // Shift-click from player inventory → try to auto-socket
        if (e.getClickedInventory() != tracked && e.getClick() == ClickType.SHIFT_LEFT) {
            ItemStack source = e.getCurrentItem();
            if (SigilItem.isSigil(source)) {
                e.setCancelled(true);
                tryAutoSocket(p, source, e.getSlot(), tracked);
            }
            return;
        }

        if (e.getClickedInventory() != tracked) return;

        int rawSlot = e.getSlot();
        if (!sm.containsKey(rawSlot)) {
            e.setCancelled(true);
            return;
        }

        SigilRank acceptedRank = sr.get(rawSlot);
        int storeIndex = sm.get(rawSlot);
        boolean isBig = storeIndex >= 100;
        int realIndex = isBig ? storeIndex - 100 : storeIndex;

        ItemStack cursor = e.getView().getCursor();
        ItemStack current = e.getCurrentItem();

        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;
        if (cursorHasItem) {
            if (!SigilItem.isSigil(cursor)) { e.setCancelled(true); return; }
            SigilRank cursorRank = SigilItem.rankOf(cursor);
            if (cursorRank != acceptedRank) {
                e.setCancelled(true);
                p.sendMessage(Text.color("&cThat socket only accepts "
                        + (acceptedRank == SigilRank.MAJOR ? "major" : "minor") + " sigils."));
                return;
            }
            e.setCancelled(true);
            SigilInstance newInst = SigilItem.fromItem(cursor);
            SigilInstance prev = SigilItem.isSigil(current) ? SigilItem.fromItem(current) : null;
            ItemStack newCursor = cursor.clone();
            newCursor.setAmount(newCursor.getAmount() - 1);
            if (newCursor.getAmount() <= 0) newCursor = null;
            tracked.setItem(rawSlot, SigilItem.toItem(newInst));
            if (isBig) store.setBig(p.getUniqueId(), realIndex, newInst);
            else       store.setSmall(p.getUniqueId(), realIndex, newInst);
            if (prev != null) newCursor = SigilItem.toItem(prev);
            e.getView().setCursor(newCursor);
            plugin.statApplier().refresh(p);
            renderSummary(p, tracked);
            return;
        }

        // Empty cursor → pick up the socketed sigil
        if (SigilItem.isSigil(current)) {
            e.setCancelled(true);
            e.getView().setCursor(SigilItem.toItem(SigilItem.fromItem(current)));
            tracked.setItem(rawSlot, isBig ? bigSlotEmpty(realIndex + 1) : smallSlotEmpty(realIndex + 1));
            if (isBig) store.setBig(p.getUniqueId(), realIndex, null);
            else       store.setSmall(p.getUniqueId(), realIndex, null);
            plugin.statApplier().refresh(p);
            renderSummary(p, tracked);
            return;
        }

        if (isEmptySocketIcon(current)) e.setCancelled(true);
    }

    private void tryAutoSocket(Player p, ItemStack stack, int sourceSlot, Inventory inv) {
        SigilInstance inst = SigilItem.fromItem(stack);
        if (inst == null) return;
        SigilRank rank = SigilItem.rankOf(stack);
        if (rank == null) return;

        int[] slots = rank == SigilRank.MAJOR ? BIG_SLOTS : SMALL_SLOTS;
        boolean isBig = rank == SigilRank.MAJOR;
        List<SigilInstance> list = isBig ? store.getBig(p.getUniqueId()) : store.getSmall(p.getUniqueId());

        for (int i = 0; i < slots.length; i++) {
            if (list.get(i) == null) {
                if (isBig) store.setBig(p.getUniqueId(), i, inst);
                else       store.setSmall(p.getUniqueId(), i, inst);
                inv.setItem(slots[i], SigilItem.toItem(inst));
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) p.getInventory().setItem(sourceSlot, null);
                plugin.statApplier().refresh(p);
                renderSummary(p, inv);
                return;
            }
        }
        p.sendMessage(Text.color("&cNo empty " + (isBig ? "big" : "small") + " sockets."));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory tracked = open.remove(p.getUniqueId());
        slotMap.remove(p.getUniqueId());
        slotRank.remove(p.getUniqueId());
        if (tracked == null) return;

        ItemStack cursor = e.getView().getCursor();
        if (SigilItem.isSigil(cursor)) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(cursor);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            e.getView().setCursor(null);
        }
        store.save();
        plugin.statApplier().refresh(p);
    }
}
