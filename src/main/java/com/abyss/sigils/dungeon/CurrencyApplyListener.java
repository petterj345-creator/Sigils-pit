package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * PoE-style currency application: pick up an {@link AbyssCurrency} on the cursor
 * and left-click an Abyss Map in your inventory to roll its mod onto the map.
 * One currency is consumed per application.
 *
 * Only fires for plain clicks in a player's own inventory (no custom GUI open),
 * so it never interferes with the editor/reward GUIs (which run in their own
 * holders and cancel their clicks first).
 */
public final class CurrencyApplyListener implements Listener {

    private final AbyssPlugin plugin;

    public CurrencyApplyListener(AbyssPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // Only act when the player has just their own inventory open (view type
        // CRAFTING). Any open container/GUI has a different view type, so we
        // never interfere with the editor, reward, or shop GUIs.
        if (e.getView().getType() != InventoryType.CRAFTING) return;

        // Left-click only — a currency on the cursor, clicking a map in a slot.
        if (e.getClick() != ClickType.LEFT) return;

        ItemStack cursor = e.getCursor();
        ItemStack target = e.getCurrentItem();
        boolean isMod = AbyssCurrency.isCurrency(cursor);
        boolean isCatalyst = MapUpgradeCurrency.isCatalyst(cursor);
        if (!isMod && !isCatalyst) return;
        if (!DungeonMap.isMap(target)) return;

        // We're handling this — never let the default swap happen.
        e.setCancelled(true);

        // Maps must be a single item to carry per-item PDC cleanly.
        if (target.getAmount() > 1) {
            p.sendMessage(Text.color("&cSplit the map stack first — apply to a single map."));
            return;
        }

        // Tier/quality catalysts.
        if (isCatalyst) {
            applyCatalyst(p, e, cursor, target);
            return;
        }

        MapMod mod = AbyssCurrency.modOf(cursor);
        if (mod == null) {
            p.sendMessage(Text.color("&cThis currency is for an unknown modifier."));
            return;
        }

        if (DungeonMap.hasMod(target, mod)) {
            p.sendMessage(Text.color("&7This map already has &f" + Text.color(mod.displayName()) + "&7."));
            return;
        }

        boolean added = DungeonMap.addMod(plugin, target, mod);
        if (!added) {
            p.sendMessage(Text.color("&cCouldn't apply that modifier."));
            return;
        }

        consume(e, cursor);
        p.updateInventory();
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        p.sendMessage(Text.color("&aApplied &f" + Text.color(mod.displayName()) + " &ato the map."));
    }

    /** Apply a tier/quality catalyst to a single map. */
    private void applyCatalyst(Player p, InventoryClickEvent e, ItemStack cursor, ItemStack map) {
        MapUpgradeCurrency.Op op = MapUpgradeCurrency.opOf(cursor);
        if (op == null) { p.sendMessage(Text.color("&cThis catalyst is unknown.")); return; }

        String result;
        switch (op) {
            case TIER -> {
                int before = DungeonMap.tierOf(map);
                int after = DungeonMap.setTier(plugin, map, before + 1);
                if (after == before) { p.sendMessage(Text.color("&7This map is already at the max tier.")); return; }
                result = "&cTier &7is now &f" + after;
            }
            case QUALITY -> {
                int before = DungeonMap.qualityOf(map);
                int after = DungeonMap.setQuality(plugin, map, before + 1);
                if (after == before) { p.sendMessage(Text.color("&7This map is already at max quality.")); return; }
                result = "&bQuality &7is now &f+" + after;
            }
            default -> { // SCOUR
                if (DungeonMap.qualityOf(map) == 0) { p.sendMessage(Text.color("&7This map has no quality to remove.")); return; }
                DungeonMap.setQuality(plugin, map, 0);
                result = "&bQuality &7removed";
            }
        }

        consume(e, cursor);
        p.updateInventory();
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        p.sendMessage(Text.color("&aApplied &f" + Text.color(MapUpgradeCurrency.displayName(op)) + "&a — " + result + "&a."));
    }

    /** Consume one currency from the cursor. */
    private void consume(InventoryClickEvent e, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            e.getView().setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            e.getView().setCursor(cursor);
        }
    }
}
