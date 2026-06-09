package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Stateless helper that, given a template, rolls the rewards for one player.
 *
 * Algorithm:
 *  1. For each {@link RewardEntry}, roll its chance%. If it passes, it's a candidate.
 *  2. Shuffle candidates, take the first {@code maxRewardItems}.
 *  3. For each chosen entry, roll a count in [min, max] and clone its ItemStack.
 *
 * Money + XP are rolled separately (their own chance, their own range).
 */
public final class RewardRoller {

    private static final Random RNG = new Random();

    public static class Roll {
        public final List<ItemStack> items;
        public final double money;
        public final int xpLevels;
        public Roll(List<ItemStack> items, double money, int xpLevels) {
            this.items = items;
            this.money = money;
            this.xpLevels = xpLevels;
        }
    }

    private RewardRoller() {}

    public static Roll rollFor(AbyssPlugin plugin, DungeonTemplate t) {
        return rollFor(plugin, t, 0);
    }

    /**
     * Roll rewards, scaled by the map's QUALITY: each quality grants more XP and
     * money (config {@code scaling.quality.*}) and an extra possible loot item
     * every {@code quality-per-extra-item} quality. Pass 0 for an unscaled roll.
     */
    public static Roll rollFor(AbyssPlugin plugin, DungeonTemplate t, int quality) {
        double xpMult = 1.0, moneyMult = 1.0;
        int bonusItems = 0;
        if (quality > 0) {
            xpMult = 1.0 + quality * plugin.getConfig().getDouble("scaling.quality.xp-percent", 10) / 100.0;
            moneyMult = 1.0 + quality * plugin.getConfig().getDouble("scaling.quality.money-percent", 10) / 100.0;
            int per = Math.max(1, plugin.getConfig().getInt("scaling.quality.quality-per-extra-item", 3));
            bonusItems = quality / per;
        }

        // Items
        List<RewardEntry> candidates = new ArrayList<>();
        for (RewardEntry r : t.rewardPool()) {
            if (r.itemStack() == null) continue;
            if (RNG.nextDouble() * 100.0 < r.chancePercent()) candidates.add(r);
        }
        Collections.shuffle(candidates, RNG);
        int cap = Math.min(candidates.size(), t.maxRewardItems() + bonusItems);
        List<ItemStack> chosen = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            RewardEntry r = candidates.get(i);
            int count = r.minCount() + (r.maxCount() > r.minCount()
                    ? RNG.nextInt(r.maxCount() - r.minCount() + 1) : 0);
            // resolve() regenerates MMOItems fresh; clones the snapshot otherwise.
            chosen.add(r.resolve(plugin, count));
        }

        // Money
        double money = 0;
        if (t.moneyMax() > 0 && RNG.nextDouble() * 100.0 < t.moneyChancePercent()) {
            double range = t.moneyMax() - t.moneyMin();
            money = t.moneyMin() + (range > 0 ? RNG.nextDouble() * range : 0);
            money = Math.round(money * moneyMult * 100.0) / 100.0; // 2 decimal places
        }

        // XP
        int xp = 0;
        if (t.xpLevelsMax() > 0 && RNG.nextDouble() * 100.0 < t.xpChancePercent()) {
            int range = t.xpLevelsMax() - t.xpLevelsMin();
            xp = t.xpLevelsMin() + (range > 0 ? RNG.nextInt(range + 1) : 0);
            xp = (int) Math.round(xp * xpMult);
        }

        return new Roll(chosen, money, xp);
    }
}
