package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-form representation of a dungeon "ticket". Players obtain a DungeonMap
 * (either via admin command or as a drop from mobs in the matching dungeon),
 * then right-click the portal block while holding it to enter that specific
 * dungeon. The map is consumed on entry.
 *
 * Identification:
 *   - Material is {@link Material#PAPER} so it stacks visually with normal paper.
 *   - The template name is written into PDC under {@link #KEY_TEMPLATE} so we
 *     can recover it even after the player renames the item in an anvil. The
 *     display name and lore are cosmetic only.
 */
public final class DungeonMap {

    /** PDC key — the template name this map opens. */
    public static final NamespacedKey KEY_TEMPLATE =
            new NamespacedKey("abyss", "map_template");

    private DungeonMap() {}

    /** Build a fresh map item for the given template. */
    public static ItemStack create(DungeonTemplate template) {
        ItemStack stack = new ItemStack(Material.PAPER);
        decorate(stack, template);
        return stack;
    }

    /**
     * Re-stamp an existing map's name, lore, and PDC tag based on the current
     * state of the template. Used to keep old maps in player inventories in
     * sync with config changes — see {@link MapRefreshListener}.
     *
     * If {@code template} is null, the item is decorated as "broken" (template
     * deleted) so the player can see what happened without losing the item.
     */
    public static void decorate(ItemStack stack, DungeonTemplate template) {
        if (stack == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        if (template == null) {
            // Broken map — template referenced by PDC no longer exists.
            String oldName = meta.getPersistentDataContainer()
                    .get(KEY_TEMPLATE, PersistentDataType.STRING);
            meta.setDisplayName(Text.color("&c&l⛧ Abyss Map &7— &c&m" + (oldName == null ? "?" : oldName)));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&c&lBROKEN"));
            lore.add(Text.color("&7The dungeon this map led to"));
            lore.add(Text.color("&7no longer exists."));
            lore.add("");
            lore.add(Text.color("&8Ask an admin to recreate it,"));
            lore.add(Text.color("&8or discard this map."));
            meta.setLore(lore);
        } else {
            meta.setDisplayName(Text.color("&5&l⛧ Abyss Map &7— &f" + template.name()));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7A tear in reality leading to the"));
            lore.add(Text.color("&7&o" + template.name() + " &7dungeon."));
            lore.add("");
            lore.add(Text.color("&eRight-click an Abyss portal"));
            lore.add(Text.color("&ewhile holding this to enter."));
            lore.add("");
            lore.add(Text.color("&8Consumed on use."));
            meta.setLore(lore);

            // (Re-)tag the item with the template name. If the template was
            // renamed, this updates the PDC to the new name. (We don't have a
            // rename event currently — renames need to be done by deleting +
            // recreating, in which case decorate() runs at give time anyway.)
            meta.getPersistentDataContainer()
                    .set(KEY_TEMPLATE, PersistentDataType.STRING, template.name());
        }

        // Glint via fake enchant — visually distinct from regular paper
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        stack.setItemMeta(meta);
    }

    /** True if this item is an Abyss map (has the template PDC key). */
    public static boolean isMap(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_TEMPLATE, PersistentDataType.STRING);
    }

    /**
     * Resolve the template this map points to. Returns null if the item isn't
     * a map OR the template no longer exists (e.g. admin deleted the template
     * after handing out maps).
     */
    public static DungeonTemplate templateOf(AbyssPlugin plugin, ItemStack item) {
        if (!isMap(item)) return null;
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_TEMPLATE, PersistentDataType.STRING);
        if (name == null) return null;
        return plugin.templates().get(name);
    }

    /** The template name encoded into the item, or null if not a map. */
    public static String templateNameOf(ItemStack item) {
        if (!isMap(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(KEY_TEMPLATE, PersistentDataType.STRING);
    }
}
