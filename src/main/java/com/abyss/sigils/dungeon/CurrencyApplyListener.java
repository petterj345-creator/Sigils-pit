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
        if (!AbyssCurrency.isCurrency(cursor)) return;
        if (!DungeonMap.isMap(target)) return;

        // We're handling this — never let the default swap happen.
        e.setCancelled(true);

        MapMod mod = AbyssCurrency.modOf(cursor);
        if (mod == null) {
            p.sendMessage(Text.color("&cThis currency is for an unknown modifier."));
            return;
        }

        // Maps must be a single item to receive a mod (mods are per-item; a
        // stack of maps can't carry per-item PDC cleanly).
        if (target.getAmount() > 1) {
            p.sendMessage(Text.color("&cSplit the map stack first — apply to a single map."));
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

        // Consume one currency from the cursor.
        if (cursor.getAmount() <= 1) {
            e.getView().setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            e.getView().setCursor(cursor);
        }

        p.updateInventory();
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        p.sendMessage(Text.color("&aApplied &f" + Text.color(mod.displayName()) + " &ato the map."));
    }
}
