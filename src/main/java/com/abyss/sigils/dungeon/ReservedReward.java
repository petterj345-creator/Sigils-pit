package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.inventory.ItemStack;

/**
 * An item a player reserved from the soul shop (paid a deposit, locked a
 * discounted price). Persisted per-player across maps by {@link
 * com.abyss.sigils.integration.ReservedRewardStore} and redeemed by paying the
 * remaining souls in a future ritual shop.
 */
public final class ReservedReward {

    private final ItemStack itemStack; // snapshot for display + fallback
    private final int amount;
    private int remaining;             // souls still owed
    private final String mmoType;      // null unless an MMOItems reward
    private final String mmoId;

    public ReservedReward(ItemStack itemStack, int amount, int remaining, String mmoType, String mmoId) {
        this.itemStack = itemStack;
        this.amount = Math.max(1, amount);
        this.remaining = Math.max(0, remaining);
        this.mmoType = mmoType;
        this.mmoId = mmoId;
    }

    public ItemStack itemStack() { return itemStack; }
    public int amount()          { return amount; }
    public int remaining()       { return remaining; }
    public String mmoType()      { return mmoType; }
    public String mmoId()        { return mmoId; }
    public boolean isMMOItem()   { return mmoType != null && mmoId != null; }

    /** A fresh ItemStack at the reserved quantity (MMOItems re-rolled on redeem). */
    public ItemStack resolve(AbyssPlugin plugin) {
        if (isMMOItem() && plugin.mmoItemsHook() != null) {
            ItemStack generated = plugin.mmoItemsHook().generate(mmoType, mmoId, amount);
            if (generated != null) return generated;
        }
        ItemStack clone = itemStack.clone();
        clone.setAmount(amount);
        return clone;
    }
}
