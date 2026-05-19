package com.abyss.sigils.sigils;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Random;

/**
 * Applies sigil gathering multipliers when a player breaks a block.
 *
 *  - WOOD_MULTIPLIER: extra drops on logs (Tag.LOGS).
 *  - ORE_MULTIPLIER: extra drops on ores (Tag.STONE_ORE_REPLACEABLES + ores).
 *  - CROP_MULTIPLIER: extra drops on crops.
 *  - FORTUNE_AURA (major): extra drops on ANY block.
 *  - LUCKY_FIND (major): roll for a tripled drop on any matched block.
 *
 * Mechanic: the relevant multiplier is treated as a percentage chance to double
 * the drop. FORTUNE_AURA stacks on top. LUCKY_FIND rolls separately for triple.
 */
public final class GatheringListener implements Listener {

    private final AbyssPlugin plugin;
    private final Random rng = new Random();

    public GatheringListener(AbyssPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        Block b = e.getBlock();
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand());
        if (drops.isEmpty()) return;

        SigilStatApplier applier = plugin.statApplier();
        double categoryBonus = categoryMultiplier(applier, p, b.getType());
        double fortuneAura = applier.totalStat(p, SigilStat.FORTUNE_AURA);
        double luckyFind = applier.totalStat(p, SigilStat.LUCKY_FIND);

        double doubleChance = categoryBonus + fortuneAura;
        if (doubleChance <= 0 && luckyFind <= 0) return;

        boolean doubled = doubleChance > 0 && rng.nextDouble() * 100.0 < doubleChance;
        boolean tripled = luckyFind > 0 && rng.nextDouble() * 100.0 < luckyFind;
        int multiplier = 1 + (doubled ? 1 : 0) + (tripled ? 2 : 0);
        if (multiplier == 1) return;

        // Drop extra copies at the broken block's location
        for (ItemStack drop : drops) {
            ItemStack extra = drop.clone();
            extra.setAmount(drop.getAmount() * (multiplier - 1));
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), extra);
        }
    }

    private double categoryMultiplier(SigilStatApplier applier, Player p, Material mat) {
        if (Tag.LOGS.isTagged(mat)) {
            return applier.totalStat(p, SigilStat.WOOD_MULTIPLIER);
        }
        if (isOre(mat)) {
            return applier.totalStat(p, SigilStat.ORE_MULTIPLIER);
        }
        if (isCrop(mat)) {
            return applier.totalStat(p, SigilStat.CROP_MULTIPLIER);
        }
        return 0;
    }

    private boolean isOre(Material m) {
        // Crude but works for vanilla — covers all the common ore variants.
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private boolean isCrop(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES
                || m == Material.BEETROOTS || m == Material.NETHER_WART
                || m == Material.MELON || m == Material.PUMPKIN
                || m == Material.SUGAR_CANE || m == Material.COCOA;
    }
}
