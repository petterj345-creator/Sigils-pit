package com.abyss.sigils.dungeon;

import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalyst currencies that change a map's numeric tier / quality (distinct from
 * the {@link AbyssCurrency} mod-rolling currencies).
 *
 *  - {@link Op#TIER}    Ascendant Catalyst  → +1 tier (tougher mobs)
 *  - {@link Op#QUALITY} Gilded Catalyst     → +1 quality (better rewards)
 *  - {@link Op#SCOUR}   Cleansing Catalyst  → quality back to 0
 *
 * Applied by holding one on the cursor and left-clicking a single Abyss Map
 * (see {@link CurrencyApplyListener}).
 */
public final class MapUpgradeCurrency {

    public enum Op { TIER, QUALITY, SCOUR }

    /** PDC key — which op this catalyst performs (Op.name()). */
    public static final NamespacedKey KEY_OP = new NamespacedKey("abyss", "map_op");

    private MapUpgradeCurrency() {}

    public static ItemStack create(Op op) {
        Material mat; String name; String[] lore;
        switch (op) {
            case TIER -> {
                mat = Material.BLAZE_POWDER;
                name = "&c&lAscendant Catalyst";
                lore = new String[]{"&7Raises a map's &cTier &7by &f1&7.", "&7Higher tier = tougher mobs."};
            }
            case QUALITY -> {
                mat = Material.GOLD_NUGGET;
                name = "&b&lGilded Catalyst";
                lore = new String[]{"&7Raises a map's &bQuality &7by &f1&7.", "&7Higher quality = better rewards."};
            }
            default -> { // SCOUR
                mat = Material.SPONGE;
                name = "&f&lCleansing Catalyst";
                lore = new String[]{"&7Removes &ball quality &7from a map", "&7(resets it to 0)."};
            }
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(Text.color(name));
        List<String> l = new ArrayList<>();
        l.add(Text.color("&7Abyssal catalyst."));
        l.add("");
        for (String line : lore) l.add(Text.color(line));
        l.add("");
        l.add(Text.color("&eHold on your cursor and left-click"));
        l.add(Text.color("&ean Abyss Map to apply."));
        l.add(Text.color("&8Consumed on use."));
        meta.setLore(l);
        meta.getPersistentDataContainer().set(KEY_OP, PersistentDataType.STRING, op.name());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isCatalyst(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY_OP, PersistentDataType.STRING);
    }

    /** The op this catalyst performs, or null if it isn't one. */
    public static Op opOf(ItemStack item) {
        if (!isCatalyst(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_OP, PersistentDataType.STRING);
        try { return Op.valueOf(raw); }
        catch (IllegalArgumentException | NullPointerException e) { return null; }
    }

    /** Display name for messages. */
    public static String displayName(Op op) {
        return switch (op) {
            case TIER -> "&c&lAscendant Catalyst";
            case QUALITY -> "&b&lGilded Catalyst";
            case SCOUR -> "&f&lCleansing Catalyst";
        };
    }
}
