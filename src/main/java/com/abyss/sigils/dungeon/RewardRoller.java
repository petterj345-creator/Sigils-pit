package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.skills.SkillType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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
        return rollFor(plugin, t, null, 0, 0);
    }

    public static Roll rollFor(AbyssPlugin plugin, DungeonTemplate t, UUID player, int quality) {
        return rollFor(plugin, t, player, quality, 0);
    }

    /**
     * Roll rewards for a player, stacking three bonus sources:
     *  - the player's Tome of Mastery skills (Greater Spoils / Treasure Hunter /
     *    Scholar), passed via {@code player} (null to skip),
     *  - the map's QUALITY ({@code scaling.quality.*}): more XP, money, and an
     *    extra possible loot item every {@code quality-per-extra-item} quality,
     *  - the map's TIER ({@code scaling.tier.loot-*}): instead of paying more
     *    XP/money, a tougher map's end chest holds MORE loot and RARER loot.
     */
    public static Roll rollFor(AbyssPlugin plugin, DungeonTemplate t, UUID player, int quality, int tier) {
        int bonusItems = 0;
        double moneyMult = 1.0, xpMult = 1.0;

        // Tome of Mastery skills (per-player).
        if (player != null && plugin.skills() != null) {
            bonusItems += SkillType.GREATER_SPOILS.flatAt(plugin.skills().rank(player, SkillType.GREATER_SPOILS));
            moneyMult *= SkillType.TREASURE_HUNTER.multiplierAt(plugin.skills().rank(player, SkillType.TREASURE_HUNTER));
            xpMult *= SkillType.SCHOLAR.multiplierAt(plugin.skills().rank(player, SkillType.SCHOLAR));
        }
        // Map quality.
        if (quality > 0) {
            xpMult *= 1.0 + quality * plugin.getConfig().getDouble("scaling.quality.xp-percent", 10) / 100.0;
            moneyMult *= 1.0 + quality * plugin.getConfig().getDouble("scaling.quality.money-percent", 10) / 100.0;
            int per = Math.max(1, plugin.getConfig().getInt("scaling.quality.quality-per-extra-item", 3));
            bonusItems += quality / per;
        }
        // Map tier — does NOT scale XP/money. Instead a tougher map's end chest
        // holds MORE loot (extra slots per tier) and RARER loot: the rarityBoost
        // closes each entry's gap from its chance% toward 100%, so a 5% entry
        // gains far more than an 80% one — rare drops surface at high tier.
        double rarityBoost = 0.0;
        if (tier > 0) {
            bonusItems += tier * Math.max(0, plugin.getConfig().getInt("scaling.tier.loot-items-per-tier", 1));
            rarityBoost = Math.min(1.0, tier
                    * plugin.getConfig().getDouble("scaling.tier.loot-rarity-boost-per-tier", 4) / 100.0);
        }

        // Items
        List<RewardEntry> candidates = new ArrayList<>();
        for (RewardEntry r : t.rewardPool()) {
            if (r.itemStack() == null) continue;
            double chance = r.chancePercent() + (100.0 - r.chancePercent()) * rarityBoost;
            if (RNG.nextDouble() * 100.0 < chance) candidates.add(r);
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
