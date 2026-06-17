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
import java.util.UUID;

/**
 * The placeable explosive handed to players during {@link MapMod#SUNDERING}.
 *
 * Each charge is bound to the dungeon session that issued it (PDC key
 * {@link #KEY_SESSION} holds the session UUID), so a leftover charge can never
 * be planted in a different run. {@link SunderingManager} gives a stack of these
 * out on init, consumes one per placement, and sweeps any leftovers on cleanup.
 */
public final class SeismicCharge {

    /** PDC key — the session UUID this charge belongs to. */
    public static final NamespacedKey KEY_SESSION =
            new NamespacedKey("abyss", "sundering_charge");

    private SeismicCharge() {}

    /** Build a stack of {@code amount} charges bound to a session. */
    public static ItemStack create(UUID sessionId, int amount) {
        ItemStack stack = new ItemStack(Material.TNT, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(Text.color("&c&l✦ Seismic Charge"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7An unstable charge for &cThe Sundering&7."));
        lore.add("");
        lore.add(Text.color("&eRight-click the ground &7near the dig"));
        lore.add(Text.color("&7site to plant it. Each charge must be"));
        lore.add(Text.color("&7within reach of the site or another"));
        lore.add(Text.color("&7charge — chain them across the field."));
        lore.add("");
        lore.add(Text.color("&7Strike the &cDetonator &7to set them off."));
        lore.add(Text.color("&8Useless outside its own dungeon."));
        meta.setLore(lore);

        meta.getPersistentDataContainer()
                .set(KEY_SESSION, PersistentDataType.STRING, sessionId.toString());

        // Glint, consistent with maps/sigils/currency.
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        stack.setItemMeta(meta);
        return stack;
    }

    /** True if this item is a seismic charge. */
    public static boolean isCharge(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_SESSION, PersistentDataType.STRING);
    }

    /** The session this charge is bound to, or null if it isn't a charge. */
    public static UUID sessionOf(ItemStack item) {
        if (!isCharge(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_SESSION, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ex) { return null; }
    }

    /** True if this item is a charge bound to the given session. */
    public static boolean isChargeFor(ItemStack item, UUID sessionId) {
        return sessionId != null && sessionId.equals(sessionOf(item));
    }
}
