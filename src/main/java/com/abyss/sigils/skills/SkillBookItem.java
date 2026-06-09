package com.abyss.sigils.skills;

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
 * The Tome of Mastery — the endgame skill book. It's just a marker item; all
 * skill data lives server-side in {@link PlayerSkillStore}, so replacing the
 * book never loses progress. Right-click it to open the skill tree.
 *
 * It never drops on death (handled by SkillBookListener) and every player is
 * meant to carry exactly one.
 */
public final class SkillBookItem {

    public static final NamespacedKey KEY_SKILL_BOOK =
            new NamespacedKey("abyss", "skill_book");

    private SkillBookItem() {}

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(Text.color("&6&l✦ Tome of Mastery"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7Your endgame mastery tree."));
        lore.add("");
        lore.add(Text.color("&7Earn &e1 skill point &7the first time"));
        lore.add(Text.color("&7you complete each map."));
        lore.add(Text.color("&7Spend points on map & event bonuses."));
        lore.add("");
        lore.add(Text.color("&eRight-click &7to open."));
        lore.add(Text.color("&8Never lost on death."));
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY_SKILL_BOOK, PersistentDataType.BYTE, (byte) 1);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isSkillBook(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(KEY_SKILL_BOOK, PersistentDataType.BYTE);
    }

    /** True if the player already carries a skill book somewhere in their inventory. */
    public static boolean has(org.bukkit.entity.Player p) {
        for (ItemStack s : p.getInventory().getContents()) if (isSkillBook(s)) return true;
        return false;
    }
}
