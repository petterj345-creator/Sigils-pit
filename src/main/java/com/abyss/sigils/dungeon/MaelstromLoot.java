package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.inventory.ItemStack;

/**
 * One item in a Maelstrom's collapse-loot pool.
 *
 * Mirrors {@link RewardEntry} (snapshot item, chance%, count range, optional
 * MMOItems regeneration) but adds {@link #minKills}: this entry is only
 * eligible to roll if the player killed at least that many mobs during the
 * rift. That's how "more kills = better rewards" works — admins set higher
 * thresholds on the good loot so it only unlocks after a real slaughter.
 */
public final class MaelstromLoot {

    private ItemStack itemStack;
    private double chancePercent;
    private int minCount;
    private int maxCount;
    /** Minimum kills required during the rift for this entry to be eligible. */
    private int minKills;
    private String mmoType;
    private String mmoId;

    public MaelstromLoot(ItemStack itemStack, double chancePercent, int minCount, int maxCount, int minKills) {
        this.itemStack = itemStack;
        this.chancePercent = clampPct(chancePercent);
        this.minCount = Math.max(1, minCount);
        this.maxCount = Math.max(this.minCount, maxCount);
        this.minKills = Math.max(0, minKills);
    }

    public ItemStack itemStack()  { return itemStack; }
    public double chancePercent() { return chancePercent; }
    public int minCount()         { return minCount; }
    public int maxCount()         { return maxCount; }
    public int minKills()         { return minKills; }
    public String mmoType()       { return mmoType; }
    public String mmoId()         { return mmoId; }
    public boolean isMMOItem()    { return mmoType != null && mmoId != null; }

    public void setMMOItem(String type, String id) { this.mmoType = type; this.mmoId = id; }
    public void setItemStack(ItemStack s)  { this.itemStack = s; }
    public void setChancePercent(double p) { this.chancePercent = clampPct(p); }
    public void setMinKills(int n)         { this.minKills = Math.max(0, n); }
    public void setCountRange(int min, int max) {
        this.minCount = Math.max(1, Math.min(min, max));
        this.maxCount = Math.max(this.minCount, Math.max(min, max));
    }

    /** Produce the ItemStack to hand out at the given count (MMOItems roll fresh). */
    public ItemStack resolve(AbyssPlugin plugin, int count) {
        int amount = Math.max(1, count);
        if (isMMOItem() && plugin.mmoItemsHook() != null) {
            ItemStack generated = plugin.mmoItemsHook().generate(mmoType, mmoId, amount);
            if (generated != null) return generated;
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(amount);
        return clone;
    }

    private static double clampPct(double p) {
        if (p < 0) return 0;
        if (p > 100) return 100;
        return p;
    }
}
