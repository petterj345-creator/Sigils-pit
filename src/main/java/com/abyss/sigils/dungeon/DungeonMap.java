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

    /**
     * PDC key — the mods rolled onto this map, stored as a comma-separated list
     * of {@link MapMod#id()} values. Stored as a single STRING rather than a
     * typed PDC list so it's robust across Bukkit/Paper API versions.
     */
    public static final NamespacedKey KEY_MODS =
            new NamespacedKey("abyss", "map_mods");

    /** PDC key — the map's combat tier (0 = none). Scales mob HP/damage. */
    public static final NamespacedKey KEY_TIER =
            new NamespacedKey("abyss", "map_tier");

    /** PDC key — the map's reward quality (0 = none). Scales end rewards. */
    public static final NamespacedKey KEY_QUALITY =
            new NamespacedKey("abyss", "map_quality");

    private DungeonMap() {}

    /** Format a number for lore — drop a trailing ".0". */
    private static String pct(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    // ----- tier & quality -----

    public static int tierOf(ItemStack item) { return intPdc(item, KEY_TIER); }
    public static int qualityOf(ItemStack item) { return intPdc(item, KEY_QUALITY); }

    private static int intPdc(ItemStack item, NamespacedKey key) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return Math.max(0, meta.getPersistentDataContainer()
                .getOrDefault(key, PersistentDataType.INTEGER, 0));
    }

    /** Set tier (clamped 0..max), re-decorate, return the new value. */
    public static int setTier(AbyssPlugin plugin, ItemStack item, int tier) {
        return setInt(plugin, item, KEY_TIER, tier,
                plugin.getConfig().getInt("scaling.tier.max", 16));
    }

    /** Set quality (clamped 0..max), re-decorate, return the new value. */
    public static int setQuality(AbyssPlugin plugin, ItemStack item, int quality) {
        return setInt(plugin, item, KEY_QUALITY, quality,
                plugin.getConfig().getInt("scaling.quality.max", 20));
    }

    private static int setInt(AbyssPlugin plugin, ItemStack item, NamespacedKey key, int value, int max) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        int clamped = Math.max(0, Math.min(max, value));
        if (clamped == 0) meta.getPersistentDataContainer().remove(key);
        else meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, clamped);
        item.setItemMeta(meta);
        decorate(item, templateOf(plugin, item));
        return clamped;
    }

    // ----- mods -----

    /** The mod ids stored on this item (raw strings; may include unknown ids). */
    public static List<String> modIds(ItemStack item) {
        if (item == null) return new ArrayList<>();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new ArrayList<>();
        String raw = meta.getPersistentDataContainer().get(KEY_MODS, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Resolved mods on this item (unknown ids skipped). */
    public static List<MapMod> mods(ItemStack item) {
        List<MapMod> out = new ArrayList<>();
        for (String id : modIds(item)) {
            MapMod m = MapMod.fromId(id);
            if (m != null && !out.contains(m)) out.add(m);
        }
        return out;
    }

    public static boolean hasMod(ItemStack item, MapMod mod) {
        return modIds(item).stream().anyMatch(id -> id.equalsIgnoreCase(mod.id()));
    }

    /** Write the given mod ids onto the item's PDC (does not touch lore). */
    private static void writeMods(ItemMeta meta, List<String> ids) {
        if (ids.isEmpty()) {
            meta.getPersistentDataContainer().remove(KEY_MODS);
        } else {
            meta.getPersistentDataContainer()
                    .set(KEY_MODS, PersistentDataType.STRING, String.join(",", ids));
        }
    }

    /**
     * Add a mod to this map (no-op if already present). Re-decorates the item so
     * the new mod shows in lore. Returns true if it was actually added.
     */
    public static boolean addMod(AbyssPlugin plugin, ItemStack item, MapMod mod) {
        if (item == null || mod == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        List<String> ids = modIds(item);
        if (ids.stream().anyMatch(id -> id.equalsIgnoreCase(mod.id()))) return false;
        ids.add(mod.id());
        writeMods(meta, ids);
        item.setItemMeta(meta);
        // Re-render lore against the current template (keeps name/lore in sync).
        decorate(item, templateOf(plugin, item));
        return true;
    }

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
            int tier = tierOf(stack);
            int quality = qualityOf(stack);
            String tierSuffix = tier > 0 ? Text.color(" &8[&cT" + tier + "&8]") : "";
            meta.setDisplayName(Text.color("&5&l⛧ Abyss Map &7— &f" + template.name()) + tierSuffix);
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7A tear in reality leading to the"));
            lore.add(Text.color("&7&o" + template.name() + " &7dungeon."));

            // Tier (combat + drop sustain) + quality (end rewards), with the
            // live scaled numbers so players see exactly what the map gives.
            if (tier > 0 || quality > 0) {
                var cfg = com.abyss.sigils.AbyssPlugin.get().getConfig();
                lore.add("");
                if (tier > 0) {
                    lore.add(Text.color("&c⚔ Tier &f" + tier));
                    lore.add(Text.color("  &7Mobs: &c+" + pct(tier * cfg.getDouble("scaling.tier.hp-percent-per-tier", 15))
                            + "% HP&7, &c+" + pct(tier * cfg.getDouble("scaling.tier.damage-percent-per-tier", 3)) + "% dmg"));
                    lore.add(Text.color("  &7Drops: &a+" + pct(tier * cfg.getDouble("scaling.tier.drop-chance-percent-per-tier", 8))
                            + "% &7chance, maps drop pre-tiered"));
                    lore.add(Text.color("  &7Rewards: &a+" + pct(tier * cfg.getDouble("scaling.tier.reward-xp-percent-per-tier", 5))
                            + "% XP&7, &a+" + pct(tier * cfg.getDouble("scaling.tier.reward-money-percent-per-tier", 5)) + "% money"));
                }
                if (quality > 0) {
                    lore.add(Text.color("&b✦ Quality &f+" + quality));
                    lore.add(Text.color("  &7Rewards: &a+" + pct(quality * cfg.getDouble("scaling.quality.xp-percent", 10))
                            + "% XP&7, &a+" + pct(quality * cfg.getDouble("scaling.quality.money-percent", 10)) + "% money"));
                    int bonusItems = quality / Math.max(1, cfg.getInt("scaling.quality.quality-per-extra-item", 3));
                    if (bonusItems > 0) lore.add(Text.color("  &7Loot: &a+" + bonusItems + " &7item" + (bonusItems == 1 ? "" : "s")));
                }
            }

            // Rolled mods — read straight from the item's PDC so they survive
            // re-decoration after a template change.
            List<MapMod> mods = mods(stack);
            if (!mods.isEmpty()) {
                lore.add("");
                lore.add(Text.color("&d&lModifiers:"));
                for (MapMod m : mods) {
                    lore.add(Text.color("&8• " + m.displayName()));
                    for (String l : m.lore()) lore.add(Text.color("  " + l));
                }
            }

            lore.add("");
            lore.add(Text.color("&eRight-click an Abyss portal"));
            lore.add(Text.color("&ewhile holding this to enter."));
            lore.add("");
            lore.add(Text.color("&8Apply currency to add modifiers."));
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
