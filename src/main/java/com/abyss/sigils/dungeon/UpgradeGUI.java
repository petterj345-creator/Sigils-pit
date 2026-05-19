package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilStat;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * The upgrade menu opened by clicking the upgrade block in a completed dungeon.
 *
 * Layout (27 slots / 3 rows):
 *   slot 11: sigil input
 *   slot 13: dust input
 *   slot 15: confirm button (lime/red glass depending on validity)
 *   rest:    filler
 *
 * Upgrade rules (from config):
 *   - Consumes N Sigil Dust + X XP levels.
 *   - Increases tier by 1 (capped at max-tier from sigil definition).
 *   - Has substat-chance % to roll a sub-stat from the definition's pool,
 *     adding tier-scaled value to it.
 */
public final class UpgradeGUI implements Listener {

    private final AbyssPlugin plugin;
    private final Random rng = new Random();
    private final Map<UUID, Inventory> open = new HashMap<>();

    private static final int SIGIL_SLOT = 11;
    private static final int DUST_SLOT  = 13;
    private static final int BUTTON_SLOT = 15;

    public UpgradeGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, Text.color("&5&lForge of the Abyss"));
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());
        inv.setItem(SIGIL_SLOT, null);
        inv.setItem(DUST_SLOT, null);
        inv.setItem(BUTTON_SLOT, buttonRed("Insert a sigil + dust"));
        open.put(p.getUniqueId(), inv);
        p.openInventory(inv);
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

    private ItemStack buttonGreen(int tier, int maxTier, int dustCost, int xpCost) {
        ItemStack s = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&a&lFORGE"));
            m.setLore(List.of(
                    Text.color("&7T" + tier + " &8→ &fT" + Math.min(tier + 1, maxTier)),
                    Text.color("&7Cost: &f" + dustCost + " Dust&7, &f" + xpCost + " XP levels"),
                    Text.color("&8Click to upgrade.")
            ));
            s.setItemMeta(m);
        }
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory tracked = open.get(p.getUniqueId());
        if (tracked == null || !e.getView().getTopInventory().equals(tracked)) return;

        int slot = e.getRawSlot();
        boolean isTop = slot < tracked.getSize();

        if (!isTop) return; // allow normal player-inv interaction

        if (slot == SIGIL_SLOT || slot == DUST_SLOT) {
            // Filter what can be placed
            ItemStack cursor = e.getView().getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (slot == SIGIL_SLOT && !SigilItem.isSigil(cursor)) { e.setCancelled(true); return; }
                if (slot == DUST_SLOT && !SigilItem.isDust(cursor)) { e.setCancelled(true); return; }
            }
            // Let click happen, then refresh button next tick
            Bukkit.getScheduler().runTask(plugin, () -> refreshButton(tracked));
            return;
        }

        if (slot == BUTTON_SLOT) {
            e.setCancelled(true);
            attemptUpgrade(p, tracked);
            return;
        }

        // Click on filler — block
        e.setCancelled(true);
    }

    private void refreshButton(Inventory inv) {
        ItemStack sigilItem = inv.getItem(SIGIL_SLOT);
        ItemStack dustItem = inv.getItem(DUST_SLOT);
        SigilInstance inst = SigilItem.fromItem(sigilItem);

        int dustCost = plugin.getConfig().getInt("upgrade.dust-cost", 3);
        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 5);

        if (inst == null) { inv.setItem(BUTTON_SLOT, buttonRed("Insert a sigil")); return; }
        SigilDefinition def = plugin.sigils().get(inst.definitionId());
        if (def == null) { inv.setItem(BUTTON_SLOT, buttonRed("Unknown sigil")); return; }

        if (inst.tier() >= Math.min(def.maxTier(), plugin.getConfig().getInt("upgrade.max-tier", 5))) {
            inv.setItem(BUTTON_SLOT, buttonRed("Already max tier"));
            return;
        }
        if (dustItem == null || !SigilItem.isDust(dustItem) || dustItem.getAmount() < dustCost) {
            inv.setItem(BUTTON_SLOT, buttonRed("Need " + dustCost + " Sigil Dust"));
            return;
        }
        inv.setItem(BUTTON_SLOT, buttonGreen(inst.tier(),
                Math.min(def.maxTier(), plugin.getConfig().getInt("upgrade.max-tier", 5)),
                dustCost, xpCost));
    }

    private void attemptUpgrade(Player p, Inventory inv) {
        ItemStack sigilItem = inv.getItem(SIGIL_SLOT);
        ItemStack dustItem = inv.getItem(DUST_SLOT);
        SigilInstance inst = SigilItem.fromItem(sigilItem);
        if (inst == null) { p.sendMessage(Text.color("&cInsert a sigil first.")); return; }

        SigilDefinition def = plugin.sigils().get(inst.definitionId());
        if (def == null) { p.sendMessage(Text.color("&cUnknown sigil.")); return; }

        int maxTier = Math.min(def.maxTier(), plugin.getConfig().getInt("upgrade.max-tier", 5));
        if (inst.tier() >= maxTier) { p.sendMessage(Text.color("&cAlready at max tier.")); return; }

        int dustCost = plugin.getConfig().getInt("upgrade.dust-cost", 3);
        int xpCost = plugin.getConfig().getInt("upgrade.xp-level-cost", 5);

        if (dustItem == null || !SigilItem.isDust(dustItem) || dustItem.getAmount() < dustCost) {
            p.sendMessage(Text.color("&cNeed " + dustCost + " Sigil Dust."));
            return;
        }
        if (p.getLevel() < xpCost) {
            p.sendMessage(Text.color("&cNeed " + xpCost + " XP levels."));
            return;
        }

        // Charge costs
        p.setLevel(p.getLevel() - xpCost);
        dustItem.setAmount(dustItem.getAmount() - dustCost);
        if (dustItem.getAmount() <= 0) inv.setItem(DUST_SLOT, null);

        // Apply upgrade
        inst.setTier(inst.tier() + 1);

        // Substat roll
        double chance = plugin.getConfig().getDouble("upgrade.substat-chance", 0.25);
        if (!def.substatPool().isEmpty() && rng.nextDouble() < chance) {
            SigilStat sub = def.substatPool().get(rng.nextInt(def.substatPool().size()));
            // Add a value that scales loosely with new tier
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
        Inventory tracked = open.remove(p.getUniqueId());
        if (tracked == null) return;
        // Return any items left in the input slots
        for (int slot : new int[]{SIGIL_SLOT, DUST_SLOT}) {
            ItemStack item = tracked.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                Map<Integer, ItemStack> overflow = p.getInventory().addItem(item);
                for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            }
        }
    }
}
