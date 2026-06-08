package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.inventory.ItemStack;

/**
 * One purchasable item in a template's ritual (soul shop) pool.
 *
 * Like {@link RewardEntry}, the {@code itemStack} is a snapshot of whatever the
 * admin dragged in, and MMOItems entries record their {@code mmoType}/{@code
 * mmoId} so {@link #resolve} can roll a fresh item per purchase.
 *
 * Price (in souls) is rolled per shop-open in [{@code priceMin}, {@code
 * priceMax}]. A value of {@code -1} means "inherit the template's global ritual
 * price range" — so admins can set most items to a global price and override
 * specific ones.
 */
public final class RitualReward {

    private ItemStack itemStack;
    /** -1 = inherit template default. */
    private int priceMin;
    private int priceMax;
    private String mmoType;
    private String mmoId;

    public RitualReward(ItemStack itemStack, int priceMin, int priceMax) {
        this.itemStack = itemStack;
        this.priceMin = priceMin;
        this.priceMax = priceMax;
        normalize();
    }

    public ItemStack itemStack() { return itemStack; }
    public int priceMin()        { return priceMin; }
    public int priceMax()        { return priceMax; }
    public String mmoType()      { return mmoType; }
    public String mmoId()        { return mmoId; }
    public boolean isMMOItem()   { return mmoType != null && mmoId != null; }
    /** True if this entry uses the template's global price range. */
    public boolean inheritsPrice() { return priceMin < 0 || priceMax < 0; }

    public void setItemStack(ItemStack s) { this.itemStack = s; }
    public void setMMOItem(String type, String id) { this.mmoType = type; this.mmoId = id; }
    public void setPriceRange(int min, int max) {
        this.priceMin = min;
        this.priceMax = max;
        normalize();
    }
    /** Reset to inherit the template's global price range. */
    public void clearPrice() { this.priceMin = -1; this.priceMax = -1; }

    private void normalize() {
        if (priceMin < 0 || priceMax < 0) { return; } // inherit sentinel
        if (priceMax < priceMin) { int t = priceMin; priceMin = priceMax; priceMax = t; }
    }

    /** A single fresh ItemStack (MMOItems re-rolled, snapshot cloned otherwise). */
    public ItemStack resolve(AbyssPlugin plugin) {
        if (isMMOItem() && plugin.mmoItemsHook() != null) {
            ItemStack generated = plugin.mmoItemsHook().generate(mmoType, mmoId, itemStack.getAmount());
            if (generated != null) return generated;
        }
        return itemStack.clone();
    }
}
