package com.abyss.sigils.sigils;

/**
 * All stats a sigil can grant.
 *
 * Categories:
 *  COMBAT     — applied in EntityDamageByEntityEvent / via attribute modifiers
 *  GATHERING  — applied in BlockBreakEvent (chance to drop extra items)
 *  MMOITEMS   — applied via MMOItems' PlayerStats system if MMOItems is present
 *
 * The {@code majorOnly} flag means: this stat is only allowed on MAJOR sigils.
 * Minor sigils can NEVER carry a major-only stat (we validate at load + on roll).
 */
public enum SigilStat {

    // ---------- COMBAT (minor + major) ----------
    DAMAGE_PERCENT      (Category.COMBAT, false),
    DEFENSE_PERCENT     (Category.COMBAT, false),
    MAX_HEALTH          (Category.COMBAT, false),
    SPEED_PERCENT       (Category.COMBAT, false),
    CRIT_CHANCE         (Category.COMBAT, false),

    // ---------- COMBAT (major-only — the big numbers) ----------
    DAMAGE_PERCENT_BIG  (Category.COMBAT, true),   // +50% etc.
    LIFESTEAL_PERCENT   (Category.COMBAT, true),
    THORNS_PERCENT      (Category.COMBAT, true),
    EXTRA_CRIT_DAMAGE   (Category.COMBAT, true),

    // ---------- GATHERING (minor + major) ----------
    WOOD_MULTIPLIER     (Category.GATHERING, false),  // % chance of double drop
    ORE_MULTIPLIER      (Category.GATHERING, false),
    CROP_MULTIPLIER     (Category.GATHERING, false),

    // ---------- GATHERING (major-only) ----------
    LUCKY_FIND          (Category.GATHERING, true),   // % chance for "tripled" drop
    FORTUNE_AURA        (Category.GATHERING, true),   // affects ALL block drops

    // ---------- MMOITEMS (major-only — these tap into MMOItems stat system) ----------
    MMO_ATTACK_DAMAGE   (Category.MMOITEMS, true),
    MMO_MAGIC_DAMAGE    (Category.MMOITEMS, true),
    MMO_CRITICAL_STRIKE_CHANCE (Category.MMOITEMS, true),
    MMO_CRITICAL_STRIKE_POWER  (Category.MMOITEMS, true),
    MMO_PVE_DAMAGE      (Category.MMOITEMS, true),
    MMO_PVP_DAMAGE      (Category.MMOITEMS, true);

    public enum Category { COMBAT, GATHERING, MMOITEMS }

    private final Category category;
    private final boolean majorOnly;

    SigilStat(Category category, boolean majorOnly) {
        this.category = category;
        this.majorOnly = majorOnly;
    }

    public Category category() { return category; }
    public boolean majorOnly() { return majorOnly; }

    /** Is this stat valid for the given rank? */
    public boolean allowedFor(SigilRank rank) {
        if (majorOnly) return rank == SigilRank.MAJOR;
        return true; // minors and majors both ok
    }

    /** Pretty-print for lore. Returns colored text. */
    public String display(double value) {
        return switch (this) {
            case DAMAGE_PERCENT          -> "&c+" + fmt(value) + "% Damage";
            case DAMAGE_PERCENT_BIG      -> "&4&l+" + fmt(value) + "% Damage";
            case DEFENSE_PERCENT         -> "&9+" + fmt(value) + "% Defense";
            case MAX_HEALTH              -> "&a+" + fmt(value) + " Max HP";
            case SPEED_PERCENT           -> "&e+" + fmt(value) + "% Speed";
            case CRIT_CHANCE             -> "&6+" + fmt(value) + "% Crit Chance";
            case LIFESTEAL_PERCENT       -> "&4+" + fmt(value) + "% Lifesteal";
            case THORNS_PERCENT          -> "&7+" + fmt(value) + "% Thorns";
            case EXTRA_CRIT_DAMAGE       -> "&6+" + fmt(value) + "% Crit Damage";
            case WOOD_MULTIPLIER         -> "&2+" + fmt(value) + "% Wood Drops";
            case ORE_MULTIPLIER          -> "&b+" + fmt(value) + "% Ore Drops";
            case CROP_MULTIPLIER         -> "&a+" + fmt(value) + "% Crop Drops";
            case LUCKY_FIND              -> "&d+" + fmt(value) + "% Triple Drop Chance";
            case FORTUNE_AURA            -> "&d+" + fmt(value) + "% Drops (all blocks)";
            case MMO_ATTACK_DAMAGE       -> "&c+" + fmt(value) + " Attack Damage &7(MMO)";
            case MMO_MAGIC_DAMAGE        -> "&5+" + fmt(value) + " Magic Damage &7(MMO)";
            case MMO_CRITICAL_STRIKE_CHANCE -> "&6+" + fmt(value) + "% Crit Strike Chance &7(MMO)";
            case MMO_CRITICAL_STRIKE_POWER  -> "&6+" + fmt(value) + "% Crit Strike Power &7(MMO)";
            case MMO_PVE_DAMAGE          -> "&c+" + fmt(value) + "% PVE Damage &7(MMO)";
            case MMO_PVP_DAMAGE          -> "&c+" + fmt(value) + "% PVP Damage &7(MMO)";
        };
    }

    private static String fmt(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
