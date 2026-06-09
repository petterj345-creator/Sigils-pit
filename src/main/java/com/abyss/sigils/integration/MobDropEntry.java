package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.AbyssCurrency;
import com.abyss.sigils.dungeon.DungeonMap;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.MapMod;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * One abyss drop attached to a MythicMob — stored by the plugin itself (in
 * mobdrops.yml), NOT in MythicMobs' YAML. Rolled on the mob's death by
 * {@link MobDropStore}. This avoids editing MythicMobs files (fragile, and
 * MythicMobs doesn't recognise our custom drop types on every version).
 */
public final class MobDropEntry {

    public enum Kind { SIGIL, MAP, CURRENCY }

    private static final Random RNG = new Random();

    private final Kind kind;
    /** sigil id, template name, or currency (MapMod) id depending on kind. */
    private final String ref;
    private final int tier;        // SIGIL only
    private final int amountMin;
    private final int amountMax;
    private final double chance;   // 0..1

    public MobDropEntry(Kind kind, String ref, int tier, int amountMin, int amountMax, double chance) {
        this.kind = kind;
        this.ref = ref;
        this.tier = Math.max(1, tier);
        this.amountMin = Math.max(1, amountMin);
        this.amountMax = Math.max(this.amountMin, amountMax);
        this.chance = Math.max(0, Math.min(1, chance));
    }

    public Kind kind()      { return kind; }
    public String ref()     { return ref; }
    public int tier()       { return tier; }
    public int amountMin()  { return amountMin; }
    public int amountMax()  { return amountMax; }
    public double chance()  { return chance; }

    /** Short human description for menus. */
    public String describe() {
        String amt = (amountMin == amountMax) ? String.valueOf(amountMin) : amountMin + "-" + amountMax;
        int pct = (int) Math.round(chance * 100);
        return switch (kind) {
            case SIGIL    -> "Sigil " + ref + " T" + tier + " ×" + amt + " @ " + pct + "%";
            case MAP      -> "Map " + ref + " ×" + amt + " @ " + pct + "%";
            case CURRENCY -> "Currency " + ref + " ×" + amt + " @ " + pct + "%";
        };
    }

    /** Roll with no tier context (tier 0). */
    public ItemStack roll(AbyssPlugin plugin) { return roll(plugin, 0); }

    /**
     * Roll this drop, boosted by the run's map {@code tier}. Returns the
     * ItemStack to drop, or null if the chance failed or the referenced thing no
     * longer exists. Higher tier raises the effective drop chance, and any MAP
     * that drops comes pre-tiered so high-tier runs sustain high-tier maps.
     */
    public ItemStack roll(AbyssPlugin plugin, int runTier) {
        double effChance = chance;
        if (runTier > 0) {
            double per = plugin.getConfig().getDouble("scaling.tier.drop-chance-percent-per-tier", 8);
            effChance = Math.min(1.0, chance * (1.0 + runTier * per / 100.0));
        }
        if (RNG.nextDouble() > effChance) return null;
        int amount = amountMin + (amountMax > amountMin ? RNG.nextInt(amountMax - amountMin + 1) : 0);
        amount = Math.max(1, amount);

        switch (kind) {
            case SIGIL -> {
                if (plugin.sigils().get(ref) == null) return null;
                ItemStack s = SigilItem.toItem(new SigilInstance(ref, tier));
                if (s != null) s.setAmount(amount);
                return s;
            }
            case MAP -> {
                DungeonTemplate tpl = plugin.templates().get(ref);
                if (tpl == null) return null;
                ItemStack s = DungeonMap.create(tpl);
                s.setAmount(amount);
                // Map sustain: drop near-tier maps so high runs keep you in high maps.
                if (runTier > 0) {
                    int offset = Math.max(0, plugin.getConfig().getInt("scaling.tier.map-drop-tier-offset", 1));
                    int dropTier = Math.max(0, runTier - offset);
                    if (dropTier > 0) DungeonMap.setTier(plugin, s, dropTier);
                }
                return s;
            }
            case CURRENCY -> {
                MapMod mod = MapMod.fromId(ref);
                if (mod == null) return null;
                ItemStack s = AbyssCurrency.create(mod);
                s.setAmount(amount);
                return s;
            }
        }
        return null;
    }
}
