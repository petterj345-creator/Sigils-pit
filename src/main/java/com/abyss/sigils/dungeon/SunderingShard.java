package com.abyss.sigils.dungeon;

import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Sundering Shards — the physical currency earned in The Sundering. Unearthed mobs
 * drop them into the killer's inventory; the crater vendor charges them straight
 * from inventory. Unlike the old per-session balance, shards are real items, so
 * players carry and accumulate them across runs.
 *
 * They stack to {@link #MAX_STACK} (99 — the engine ceiling for a custom stack
 * size; a 500-stack isn't possible for a real item).
 */
public final class SunderingShard {

    public static final NamespacedKey KEY_SHARD = new NamespacedKey("abyss", "sundering_shard");
    /** Minecraft clamps custom stack sizes to 99; this is as high as a real stack goes. */
    public static final int MAX_STACK = 99;

    private SunderingShard() {}

    /** A shard stack of {@code amount} (clamped to [1, {@link #MAX_STACK}]). */
    public static ItemStack create(int amount) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(Text.color("&d&lSundering Shard"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7Currency torn from the buried hoard."));
        lore.add("");
        lore.add(Text.color("&7Spend at the &6Sundered Hoard &7vendor."));
        lore.add(Text.color("&8Yours to keep — carry them out."));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY_SHARD, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(MAX_STACK);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        stack.setItemMeta(meta);
        stack.setAmount(Math.max(1, Math.min(MAX_STACK, amount)));
        return stack;
    }

    public static boolean isShard(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY_SHARD, PersistentDataType.BYTE);
    }

    /** Total shards a player is carrying. */
    public static int count(Player p) {
        int total = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (isShard(s)) total += s.getAmount();
        }
        return total;
    }

    /** Give shards, splitting into {@link #MAX_STACK} stacks; overflow drops at the player's feet. */
    public static void give(Player p, int amount) {
        int remaining = Math.max(0, amount);
        while (remaining > 0) {
            int n = Math.min(MAX_STACK, remaining);
            remaining -= n;
            for (ItemStack o : p.getInventory().addItem(create(n)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), o);
            }
        }
    }

    /**
     * Remove exactly {@code amount} shards from the player's inventory. Returns
     * false (removing nothing) if they don't have enough.
     */
    public static boolean take(Player p, int amount) {
        if (amount <= 0) return true;
        if (count(p) < amount) return false;

        int remaining = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (!isShard(s)) continue;
            int take = Math.min(remaining, s.getAmount());
            remaining -= take;
            if (take >= s.getAmount()) {
                p.getInventory().setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
            }
        }
        return true;
    }
}
