package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

/**
 * The confirmation GUI shown when a player right-clicks the Abyss portal block.
 *
 * Old behaviour: right-click → IMMEDIATELY started a dungeon. With no debounce
 * and the click event firing twice on some Paper builds, players could rapid-
 * fire dozens of dungeons in a second — boss bars stacked, instance worlds
 * piled up, sessions hung around. This GUI replaces that with an explicit
 * "drop your map in and click ENTER" flow.
 *
 * Layout (27 slots, 3 rows):
 *   slot 13  — map drop slot (accepts only Abyss Maps; can be left empty)
 *   slot 11  — confirm button (green if ready, yellow if no map → random)
 *   slot 15  — cancel button (closes GUI)
 *   rest     — purple-glass filler
 *
 * --- Why a Holder ---
 * Same pattern as RewardsGUI/UpgradeGUI: identifying the inventory by holder
 * reference is bullet-proof. ALL clicks in the top half are cancelled BEFORE
 * any routing, so the buttons can never be picked up as items.
 *
 * --- Click guard ---
 * If a player is already in a dungeon session, we refuse to open the GUI
 * (they have to leave first). This kills the "boss bars everywhere" bug
 * even if some other code path tries to call start() again.
 */
public final class PortalEntryGUI implements Listener {

    private static final int MAP_SLOT     = 13;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT  = 15;

    private final AbyssPlugin plugin;

    public PortalEntryGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    /**
     * Open the entry GUI for a player. If they already have a map equipped
     * (main or off hand), we pre-fill the map slot so they don't have to
     * shuffle items around — feels nicer.
     */
    public void openFor(Player p) {
        // Block re-entry if they're already in a dungeon
        if (plugin.dungeonManager().sessionOf(p) != null) {
            p.sendMessage(Text.color("&cYou're already in The Abyss. &7Type &f/abyss leave &7first."));
            return;
        }

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.color("&5&l✦ Enter The Abyss"));
        holder.setInventory(inv);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());
        inv.setItem(MAP_SLOT, null);
        inv.setItem(CANCEL_SLOT, cancelButton());

        // Auto-pull a map from the player's hands so they don't have to manually drag it.
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!DungeonMap.isMap(hand)) hand = p.getInventory().getItemInOffHand();
        if (DungeonMap.isMap(hand)) {
            ItemStack one = hand.clone();
            one.setAmount(1);
            inv.setItem(MAP_SLOT, one);
            // Remove one from the player's hand
            hand.setAmount(hand.getAmount() - 1);
        }

        refreshConfirm(inv);
        p.openInventory(inv);
    }

    /** Per-open holder — used to identify the inventory by reference. */
    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        Holder() {}
        void setInventory(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static Holder holderOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return (h instanceof Holder ph) ? ph : null;
    }

    private ItemStack filler() {
        ItemStack s = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(" "); s.setItemMeta(m); }
        return s;
    }

    private ItemStack cancelButton() {
        ItemStack s = new ItemStack(Material.BARRIER);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&c&lCancel"));
            m.setLore(List.of(Text.color("&7Close without entering.")));
            s.setItemMeta(m);
        }
        return s;
    }

    /**
     * Update the confirm button to reflect whether a map is in the slot.
     * - Map present and valid → green button "Enter [tplName]"
     * - Map present but template missing → red "Broken map" button
     * - No map → grey "Need a map" tile (can't enter without one)
     */
    private void refreshConfirm(Inventory inv) {
        ItemStack map = inv.getItem(MAP_SLOT);
        if (map == null || map.getType() == Material.AIR) {
            inv.setItem(CONFIRM_SLOT, needMapButton());
            return;
        }
        DungeonTemplate tpl = DungeonMap.templateOf(plugin, map);
        if (tpl == null) {
            inv.setItem(CONFIRM_SLOT, brokenMapButton());
            return;
        }
        inv.setItem(CONFIRM_SLOT, enterButton(tpl));
    }

    private ItemStack enterButton(DungeonTemplate tpl) {
        ItemStack s = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&a&l▶ Enter Dungeon"));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7Destination: &f" + tpl.name()));
            lore.add("");
            lore.add(Text.color("&8Sneak when entering to bring"));
            lore.add(Text.color("&8nearby party members (8 blocks)."));
            lore.add("");
            lore.add(Text.color("&7Map will be consumed."));
            lore.add(Text.color("&eClick to confirm."));
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack needMapButton() {
        ItemStack s = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&7&l✗ Need an Abyss Map"));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7You need a map to enter."));
            lore.add(Text.color("&7Drop one into the center slot."));
            lore.add("");
            lore.add(Text.color("&8Maps drop rarely from Abyss mobs"));
            lore.add(Text.color("&8or can be granted by admins."));
            m.setLore(lore);
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack brokenMapButton() {
        ItemStack s = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&c&l✗ Broken Map"));
            m.setLore(List.of(
                    Text.color("&7This map points to a dungeon"),
                    Text.color("&7that no longer exists."),
                    Text.color("&7Remove it or pick another.")));
            s.setItemMeta(m);
        }
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        int raw = e.getRawSlot();
        int topSize = top.getSize();
        boolean isTopClick = raw < topSize;

        // Bottom-inv: allow shift-click of a map into the slot, block everything else interesting
        if (!isTopClick) {
            if (e.isShiftClick()) {
                ItemStack moving = e.getCurrentItem();
                if (moving != null && DungeonMap.isMap(moving) && top.getItem(MAP_SLOT) == null) {
                    e.setCancelled(true);
                    ItemStack one = moving.clone();
                    one.setAmount(1);
                    top.setItem(MAP_SLOT, one);
                    if (moving.getAmount() <= 1) p.getInventory().setItem(e.getSlot(), null);
                    else moving.setAmount(moving.getAmount() - 1);
                    Bukkit.getScheduler().runTask(plugin, () -> refreshConfirm(top));
                } else {
                    // Any other shift-click into our GUI: block
                    e.setCancelled(true);
                }
            }
            return;
        }

        // ALWAYS cancel top-half clicks before routing. This is the safety net
        // that ensures the buttons can never be picked up as items even if
        // any branch below returns early.
        e.setCancelled(true);

        if (raw == CANCEL_SLOT) {
            p.closeInventory();
            return;
        }
        if (raw == CONFIRM_SLOT) {
            confirmEntry(p, top);
            return;
        }
        if (raw == MAP_SLOT) {
            // Allow placing/removing maps. We can't just let the click through
            // because it would have been cancelled above; instead, handle it
            // manually: swap cursor and slot contents if the cursor item is a
            // map or AIR.
            ItemStack cursor = e.getView().getCursor();
            ItemStack current = top.getItem(MAP_SLOT);
            boolean cursorIsMapOrEmpty = cursor == null || cursor.getType() == Material.AIR || DungeonMap.isMap(cursor);
            if (!cursorIsMapOrEmpty) return; // reject non-map items silently
            // Swap
            top.setItem(MAP_SLOT, cursor == null || cursor.getType() == Material.AIR ? null : cursor.clone());
            // Decrement / clear cursor; give back any current item
            e.getView().setCursor(current == null || current.getType() == Material.AIR ? null : current);
            // If we placed a stack >1, only keep 1 in the slot
            ItemStack inSlot = top.getItem(MAP_SLOT);
            if (inSlot != null && inSlot.getAmount() > 1) {
                ItemStack overflow = inSlot.clone();
                overflow.setAmount(inSlot.getAmount() - 1);
                inSlot.setAmount(1);
                // Place overflow back on cursor (preferred) or in inv
                ItemStack onCursor = e.getView().getCursor();
                if (onCursor == null || onCursor.getType() == Material.AIR) {
                    e.getView().setCursor(overflow);
                } else {
                    Map<Integer, ItemStack> rejected = p.getInventory().addItem(overflow);
                    for (ItemStack o : rejected.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> refreshConfirm(top));
            return;
        }
        // Filler — already cancelled, do nothing.
    }

    /** User clicked the confirm button. Validate and start the dungeon. */
    private void confirmEntry(Player p, Inventory inv) {
        // Re-check the not-already-in-dungeon guard (paranoid; the open-time
        // check should prevent this, but a player could be added to a session
        // between open and click)
        if (plugin.dungeonManager().sessionOf(p) != null) {
            p.sendMessage(Text.color("&cYou're already in The Abyss."));
            p.closeInventory();
            return;
        }

        ItemStack map = inv.getItem(MAP_SLOT);
        if (map == null || map.getType() == Material.AIR) {
            // No random fallback — players need a map.
            p.sendMessage(Text.color("&cYou need an Abyss Map to enter."));
            return;
        }
        DungeonTemplate template = DungeonMap.templateOf(plugin, map);
        if (template == null) {
            p.sendMessage(Text.color("&cThat map points to a deleted dungeon."));
            return;
        }

        // Build party (sneak-at-time-of-confirm includes nearby players in real world)
        List<Player> party;
        if (p.isSneaking()) {
            party = p.getWorld().getNearbyEntitiesByType(Player.class, p.getLocation(), 8)
                    .stream().toList();
        } else {
            party = List.of(p);
        }

        // Consume the map. Set the slot to null so the close handler doesn't
        // return it.
        inv.setItem(MAP_SLOT, null);
        p.closeInventory();

        // Kick the actual start on the NEXT tick so the inventory close
        // event processes cleanly first.
        final DungeonTemplate finalTemplate = template;
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.sendMessage(Text.color("&5&lThe Abyss &7is opening... &7(" + finalTemplate.name() + ")"));
            plugin.dungeonManager().start(party, finalTemplate);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Holder holder = holderOf(top);
        if (holder == null) return;

        // Return any map left in the slot — they didn't enter.
        ItemStack map = top.getItem(MAP_SLOT);
        if (map != null && map.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(map);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            top.setItem(MAP_SLOT, null);
        }
        // Return any cursor item too (shouldn't happen since clicks cancel, but safety)
        ItemStack cursor = e.getView().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(cursor);
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            e.getView().setCursor(null);
        }
    }
}
