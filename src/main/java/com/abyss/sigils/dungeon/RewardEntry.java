package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
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
 *
 * <b>MMOItems integration:</b> if the dragged item was an MMOItems item, we
 * also record its {@code mmoType}/{@code mmoId}. At award time {@link #resolve}
 * regenerates a FRESH MMOItems roll (so each player gets their own stats),
 * falling back to a clone of the snapshot if MMOItems is gone or generation
 * fails. Non-MMOItems entries always clone the snapshot.
 */
public final class RewardEntry {

    private ItemStack itemStack;
    private double chancePercent;
    private int minCount;
    private int maxCount;
    /** MMOItems type/id, or null for a plain snapshot entry. */
    private String mmoType;
    private String mmoId;

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
    public String mmoType()          { return mmoType; }
    public String mmoId()            { return mmoId; }
    public boolean isMMOItem()       { return mmoType != null && mmoId != null; }

    public void setMMOItem(String type, String id) { this.mmoType = type; this.mmoId = id; }

    /**
     * Produce the actual ItemStack to hand to a player, at the given count.
     * MMOItems entries roll fresh; everything else clones the snapshot.
     */
    public ItemStack resolve(AbyssPlugin plugin, int count) {
        int amount = Math.max(1, count);
        if (isMMOItem() && plugin.mmoItemsHook() != null) {
            ItemStack generated = plugin.mmoItemsHook().generate(mmoType, mmoId, amount);
            if (generated != null) return generated;
            // Fall through to snapshot clone if generation failed.
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(amount);
        return clone;
    }

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
