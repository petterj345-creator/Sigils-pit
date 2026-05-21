package com.abyss.sigils.sigils;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class SigilItem {

    public static final NamespacedKey KEY_ID    = key("sigil_id");
    public static final NamespacedKey KEY_TIER  = key("sigil_tier");
    public static final NamespacedKey KEY_RANK  = key("sigil_rank");   // "MINOR" or "MAJOR"
    public static final NamespacedKey KEY_SUBS  = key("sigil_substats");
    public static final NamespacedKey KEY_DUST  = key("sigil_dust");
    public static final NamespacedKey KEY_BOOK  = key("sigil_book");
    public static final NamespacedKey KEY_BOOK_TIER = key("sigil_book_tier");

    private static NamespacedKey key(String s) {
        return new NamespacedKey(AbyssPlugin.get(), s);
    }

    private SigilItem() {}

    /** Build an ItemStack for the given sigil instance. */
    public static ItemStack toItem(SigilInstance inst) {
        SigilDefinition def = AbyssPlugin.get().sigils().get(inst.definitionId());
        if (def == null) return null;

        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rankTag = switch (def.rank()) {
            case GRAND -> "&5&l[GRAND]";
            case MAJOR -> "&6&l[MAJOR]";
            case MINOR -> "&7[minor]";
        };
        meta.setDisplayName(Text.color(def.display() + " &7[T" + inst.tier() + "] " + rankTag));

        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&8" + switch (def.rank()) {
            case GRAND -> "Grand Sigil";
            case MAJOR -> "Major Sigil";
            case MINOR -> "Minor Sigil";
        }));
        lore.add("");
        lore.add(Text.color(def.stat().display(def.valueAtTier(inst.tier())) + " &7(main)"));
        if (!inst.subStats().isEmpty()) {
            lore.add("");
            lore.add(Text.color("&7Sub-stats:"));
            for (Map.Entry<SigilStat, Double> e : inst.subStats().entrySet()) {
                lore.add(Text.color("&8• " + e.getKey().display(e.getValue())));
            }
        }
        lore.add("");
        lore.add(Text.color(switch (def.rank()) {
            case GRAND -> "&8Fits in a &5grand&8 socket";
            case MAJOR -> "&8Fits in a &6big&8 socket";
            case MINOR -> "&8Fits in a &7small&8 socket";
        }));
        meta.setLore(lore);

        if (def.modelData() != 0) meta.setCustomModelData(def.modelData());
        meta.addItemFlags(ItemFlag.values());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ID, PersistentDataType.STRING, def.id());
        pdc.set(KEY_TIER, PersistentDataType.INTEGER, inst.tier());
        pdc.set(KEY_RANK, PersistentDataType.STRING, def.rank().name());
        pdc.set(KEY_SUBS, PersistentDataType.STRING, encodeSubstats(inst.subStats()));

        item.setItemMeta(meta);
        return item;
    }

    public static SigilInstance fromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(KEY_ID, PersistentDataType.STRING)) return null;
        String id   = pdc.get(KEY_ID, PersistentDataType.STRING);
        int tier    = pdc.getOrDefault(KEY_TIER, PersistentDataType.INTEGER, 1);
        String subs = pdc.getOrDefault(KEY_SUBS, PersistentDataType.STRING, "");
        return new SigilInstance(id, tier, decodeSubstats(subs));
    }

    /** Returns the rank stored on the item (so we can validate slot eligibility without a registry lookup). */
    public static SigilRank rankOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String r = pdc.get(KEY_RANK, PersistentDataType.STRING);
        if (r == null) return null;
        try { return SigilRank.valueOf(r); } catch (IllegalArgumentException e) { return null; }
    }

    public static boolean isSigil(ItemStack item) { return fromItem(item) != null; }

    public static boolean isDust(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KEY_DUST, PersistentDataType.BYTE);
    }

    public static boolean isBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(KEY_BOOK, PersistentDataType.BYTE);
    }

    public static ItemStack createDust(int amount) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&d&lSigil Dust"));
            meta.setLore(List.of(
                    Text.color("&7Used to upgrade sigils."),
                    Text.color("&8Dropped by Abyss mobs.")));
            meta.getPersistentDataContainer().set(KEY_DUST, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** The Book of Sigils — right-click to open the socket GUI. Tier 1 default. */
    public static ItemStack createBook() { return createBook(1); }

    /**
     * Build a Book of Sigils at a specific tier. Tier dictates how many sockets
     * unlock in the socket GUI (looked up via {@link BookTiers}). The tier is
     * stored in PDC under {@link #KEY_BOOK_TIER} so the same physical paper
     * stays distinct after rename/upgrade.
     */
    public static ItemStack createBook(int tier) {
        if (tier < 1) tier = 1;
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String tierTag = "&7[T" + tier + "]";
            meta.setDisplayName(Text.color("&5&lBook of Sigils " + tierTag));

            BookTiers tiers = AbyssPlugin.get().bookTiers();
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&7A relic of the Abyss."));
            lore.add(Text.color("&7Bind sigils to its pages to"));
            lore.add(Text.color("&7gain their power."));
            lore.add("");
            if (tiers != null) {
                int small = tiers.smallSlots(tier);
                int big   = tiers.bigSlots(tier);
                int grand = tiers.grandSlots(tier);
                lore.add(Text.color("&7Sockets:"));
                lore.add(Text.color("&8• &f" + small + " &7small &8(minor)"));
                lore.add(Text.color("&8• &f" + big   + " &7big &8(major)"));
                if (grand > 0) lore.add(Text.color("&8• &f" + grand + " &7grand &8(major)"));
                lore.add("");
            }
            lore.add(Text.color("&eRight-click &7to open"));
            if (tiers != null && tier < tiers.maxTier()) {
                lore.add(Text.color("&7Forge to upgrade tier."));
            } else if (tiers != null) {
                lore.add(Text.color("&6Max tier"));
            }
            lore.add(Text.color("&8Soulbound — keep on death"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_BOOK, PersistentDataType.BYTE, (byte) 1);
            pdc.set(KEY_BOOK_TIER, PersistentDataType.INTEGER, tier);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Return the tier of a Book of Sigils, or 1 for a legacy book missing the field. */
    public static int bookTierOf(ItemStack item) {
        if (!isBook(item)) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(KEY_BOOK_TIER, PersistentDataType.INTEGER, 1);
    }

    // ----- substat encoding -----

    private static String encodeSubstats(Map<SigilStat, Double> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<SigilStat, Double> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    private static Map<SigilStat, Double> decodeSubstats(String s) {
        Map<SigilStat, Double> out = new HashMap<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try {
                out.put(SigilStat.valueOf(kv[0]), Double.parseDouble(kv[1]));
            } catch (Exception ignored) {}
        }
        return out;
    }
}
