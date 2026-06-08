package com.abyss.sigils.dungeon;

import com.abyss.sigils.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * A currency item that, when applied to an Abyss Map, rolls a {@link MapMod}
 * onto it (PoE-style crafting). Each currency type maps 1:1 to a mod.
 *
 * Identification: PDC key {@link #KEY_CURRENCY} holds the {@link MapMod#id()}.
 * Application is handled by {@code CurrencyApplyListener} (pick up the currency
 * on the cursor, click a map).
 *
 * Drops via the MythicMobs item type {@code abyss_currency&#123;type=ritual&#125;}.
 */
public final class AbyssCurrency {

    /** PDC key — the MapMod id this currency applies. */
    public static final NamespacedKey KEY_CURRENCY =
            new NamespacedKey("abyss", "currency_type");

    private AbyssCurrency() {}

    /** Build a currency item for the given mod. */
    public static ItemStack create(MapMod mod) {
        ItemStack stack = new ItemStack(mod.currencyMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(Text.color(mod.currencyName()));
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7Abyssal currency."));
        lore.add("");
        lore.add(Text.color("&7Adds this modifier to a map:"));
        lore.add(Text.color("&8• " + mod.displayName()));
        lore.add("");
        lore.add(Text.color("&eHold this on your cursor and"));
        lore.add(Text.color("&eclick an Abyss Map to apply."));
        lore.add(Text.color("&8Consumed on use."));
        meta.setLore(lore);

        meta.getPersistentDataContainer()
                .set(KEY_CURRENCY, PersistentDataType.STRING, mod.id());

        // Glint, consistent with maps/sigils
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        stack.setItemMeta(meta);
        return stack;
    }

    /** True if this item is an Abyss currency. */
    public static boolean isCurrency(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_CURRENCY, PersistentDataType.STRING);
    }

    /** The mod this currency applies, or null if it isn't a currency / unknown mod. */
    public static MapMod modOf(ItemStack item) {
        if (!isCurrency(item)) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_CURRENCY, PersistentDataType.STRING);
        return MapMod.fromId(id);
    }
}
