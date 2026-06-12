package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.inventory.ItemStack;

/**
 * A reward a player reserved from the soul shop (paid a deposit, locked a
 * discounted price). Either an item or a cash payout. Persisted per-player
 * across maps by {@link com.abyss.sigils.integration.ReservedRewardStore} and
 * redeemed by paying the remaining souls in a future ritual shop.
 */
public final class ReservedReward {

    private final ItemStack itemStack; // snapshot for display + fallback; null for cash
    private final int amount;
    private int remaining;             // souls still owed
    private final String mmoType;      // null unless an MMOItems reward
    private final String mmoId;
    private final int cashAmount;      // money paid out on collect; 0 for item reservations

    public ReservedReward(ItemStack itemStack, int amount, int remaining, String mmoType, String mmoId) {
        this(itemStack, amount, remaining, mmoType, mmoId, 0);
    }

    private ReservedReward(ItemStack itemStack, int amount, int remaining, String mmoType, String mmoId, int cashAmount) {
        this.itemStack = itemStack;
        this.amount = Math.max(1, amount);
        this.remaining = Math.max(0, remaining);
        this.mmoType = mmoType;
        this.mmoId = mmoId;
        this.cashAmount = Math.max(0, cashAmount);
    }

    /** A reserved cash payout: {@code cashAmount} money owed for {@code remaining} souls. */
    public static ReservedReward cash(int cashAmount, int remaining) {
        return new ReservedReward(null, 1, remaining, null, null, Math.max(0, cashAmount));
    }

    public ItemStack itemStack() { return itemStack; }
    public int amount()          { return amount; }
    public int remaining()       { return remaining; }
    public String mmoType()      { return mmoType; }
    public String mmoId()        { return mmoId; }
    public int cashAmount()      { return cashAmount; }
    public boolean isCash()      { return cashAmount > 0; }
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
