package com.abyss.sigils.dungeon;

import org.bukkit.inventory.ItemStack;

/**
 * One item in a template's reward pool.
 *
 * The {@code itemStack} is a snapshot of whatever the admin dragged into the
 * editor — vanilla, MMOItems, sigils, anything. We serialize via Bukkit's
 * built-in ItemStack serialization so NBT/PDC survives.
 *
 * On roll, {@code chancePercent} (0..100) decides whether this entry is a
 * candidate; if chosen, {@code minCount}..{@code maxCount} decides amount.
 */
public final class RewardEntry {

    private ItemStack itemStack;
    private double chancePercent;
    private int minCount;
    private int maxCount;

    public RewardEntry(ItemStack itemStack, double chancePercent, int minCount, int maxCount) {
        this.itemStack = itemStack;
        this.chancePercent = clampPct(chancePercent);
        this.minCount = Math.max(1, minCount);
        this.maxCount = Math.max(this.minCount, maxCount);
    }

    public ItemStack itemStack()     { return itemStack; }
    public double chancePercent()    { return chancePercent; }
    public int minCount()            { return minCount; }
    public int maxCount()            { return maxCount; }

    public void setItemStack(ItemStack s)  { this.itemStack = s; }
    public void setChancePercent(double p) { this.chancePercent = clampPct(p); }
    public void setMinCount(int n) {
        this.minCount = Math.max(1, n);
        if (this.maxCount < this.minCount) this.maxCount = this.minCount;
    }
    public void setMaxCount(int n) {
        this.maxCount = Math.max(1, n);
        if (this.minCount > this.maxCount) this.minCount = this.maxCount;
    }
    public void setCountRange(int min, int max) {
        this.minCount = Math.max(1, Math.min(min, max));
        this.maxCount = Math.max(this.minCount, Math.max(min, max));
    }

    private static double clampPct(double p) {
        if (p < 0) return 0;
        if (p > 100) return 100;
        return p;
    }
}
