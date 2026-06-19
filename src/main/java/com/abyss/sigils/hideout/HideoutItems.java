package com.abyss.sigils.hideout;

import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The two placeable hideout fixtures players position in their own hideout:
 *
 *   - {@link #portal()} — an Abyss Portal block; right-click it to enter The Abyss.
 *   - {@link #stash()}  — an Abyss Stash chest; right-click to open Abyss-only storage.
 *
 * Both carry a PDC tag so {@link HideoutFixtureListener} recognises them on
 * placement. The placed blocks themselves are plain (their location is tracked
 * in {@link HideoutFixtures}), so the tag only needs to live on the item.
 */
public final class HideoutItems {

    public static final NamespacedKey KEY = new NamespacedKey("abyss", "hideout_fixture");
    public static final String PORTAL = "portal";
    public static final String STASH = "stash";

    private HideoutItems() {}

    public static ItemStack portal() {
        return tagged(Material.END_PORTAL_FRAME, PORTAL, "&5&l✦ Abyss Portal", List.of(
                "&7Place in your hideout, then",
                "&7right-click to enter The Abyss.",
                "",
                "&8One per hideout."));
    }

    public static ItemStack stash() {
        return tagged(Material.CHEST, STASH, "&6&l✦ Abyss Stash", List.of(
                "&7Place in your hideout to store",
                "&7sigils, maps, currency & tomes.",
                "",
                "&8One per hideout. Abyss items only."));
    }

    /** PDC tag type of a fixture item ("portal"/"stash"), or null if it isn't one. */
    public static String typeOf(ItemStack item) {
        if (item == null) return null;
        ItemMeta m = item.getItemMeta();
        if (m == null) return null;
        return m.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    private static ItemStack tagged(Material mat, String type, String name, List<String> lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m == null) return s;
        m.setDisplayName(Text.color(name));
        m.setLore(lore.stream().map(Text::color).toList());
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, type);
        s.setItemMeta(m);
        return s;
    }
}
