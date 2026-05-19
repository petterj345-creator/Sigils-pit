package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Abyss Editor Wand. A blaze rod, tagged in PDC, that admins use while
 * standing inside a template world.
 *
 *  - Right-click AIR  → opens the template editor GUI.
 *  - Right-click BLOCK → opens a block-context menu so the admin can mark the
 *                        block as player spawn, boss spawn, spawn point, or
 *                        reward chest anchor. (See {@link com.abyss.sigils.gui.WandBlockMenu}.)
 *  - Sneak + left-click BLOCK → quick-remove anything our plugin marked there.
 *
 * The wand is given to admins automatically when they enter an editor world,
 * and removed from inventory when they leave.
 */
public final class EditorWand {

    public static final NamespacedKey KEY_WAND = new NamespacedKey(AbyssPlugin.get(), "editor_wand");

    private EditorWand() {}

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&5&l✦ Abyss Editor Wand"));
            meta.setLore(List.of(
                    Text.color("&7Right-click &fair&7 → open editor menu"),
                    Text.color("&7Right-click &fblock&7 → mark/inspect block"),
                    Text.color("&7Sneak + left-click block → remove marker"),
                    "",
                    Text.color("&8Auto-removed when leaving the template world")));
            meta.addItemFlags(ItemFlag.values());
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(KEY_WAND, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isWand(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(KEY_WAND, PersistentDataType.BYTE);
    }
}
