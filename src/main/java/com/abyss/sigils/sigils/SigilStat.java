package com.abyss.sigils.sigils;

/**
 * All stats a sigil can grant.
 *
 * Categories:
 *  COMBAT     — applied in EntityDamageByEntityEvent / via attribute modifiers
 *  GATHERING  — applied in BlockBreakEvent (chance to drop extra items)
 *  MMOITEMS   — applied via MMOItems' PlayerStats system if MMOItems is present
 *
 * The {@link #rankFloor} field is the LOWEST rank a sigil with this stat can be.
 * Validation happens at YAML load + on substat rolls.
 */
public enum SigilStat {

    // ---------- COMBAT (any rank) ----------
    DAMAGE_PERCENT      (Category.COMBAT, SigilRank.MINOR),
    DEFENSE_PERCENT     (Category.COMBAT, SigilRank.MINOR),
    MAX_HEALTH          (Category.COMBAT, SigilRank.MINOR),
    SPEED_PERCENT       (Category.COMBAT, SigilRank.MINOR),
    CRIT_CHANCE         (Category.COMBAT, SigilRank.MINOR),

    // ---------- COMBAT (major+) ----------
    DAMAGE_PERCENT_BIG  (Category.COMBAT, SigilRank.MAJOR),
    LIFESTEAL_PERCENT   (Category.COMBAT, SigilRank.MAJOR),
    THORNS_PERCENT      (Category.COMBAT, SigilRank.MAJOR),
    EXTRA_CRIT_DAMAGE   (Category.COMBAT, SigilRank.MAJOR),

    // ---------- GATHERING (any rank) ----------
    WOOD_MULTIPLIER     (Category.GATHERING, SigilRank.MINOR),
    ORE_MULTIPLIER      (Category.GATHERING, SigilRank.MINOR),
    CROP_MULTIPLIER     (Category.GATHERING, SigilRank.MINOR),

    // ---------- GATHERING (major+) ----------
    LUCKY_FIND          (Category.GATHERING, SigilRank.MAJOR),
    FORTUNE_AURA        (Category.GATHERING, SigilRank.MAJOR),

    // ---------- MMOITEMS (any rank: small additive contribs allowed on minors) ----------
    MMO_HEALTH_REGEN    (Category.MMOITEMS, SigilRank.MINOR),
    MMO_ARMOR           (Category.MMOITEMS, SigilRank.MINOR),
    MMO_BLOCK_RATING    (Category.MMOITEMS, SigilRank.MINOR),
    MMO_DODGE_RATING    (Category.MMOITEMS, SigilRank.MINOR),

    // ---------- MMOITEMS (major+) ----------
    MMO_ATTACK_DAMAGE   (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_MAGIC_DAMAGE    (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_CRITICAL_STRIKE_CHANCE (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_CRITICAL_STRIKE_POWER  (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_PVE_DAMAGE      (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_PVP_DAMAGE      (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_MAGICAL_DAMAGE  (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_MAGIC_RESISTANCE (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_ARMOR_PIERCING  (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_BLOCK_POWER     (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_COOLDOWN_REDUCTION (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_MAX_MANA        (Category.MMOITEMS, SigilRank.MAJOR),
    MMO_MANA_REGEN      (Category.MMOITEMS, SigilRank.MAJOR),

    // ---------- GRAND-ONLY (the truly OP stats) ----------
    GRAND_OMNIDAMAGE    (Category.COMBAT, SigilRank.GRAND),
    GRAND_FORTRESS      (Category.COMBAT, SigilRank.GRAND),
    GRAND_VITALITY      (Category.COMBAT, SigilRank.GRAND),
    GRAND_SOULSTEAL     (Category.COMBAT, SigilRank.GRAND),
    GRAND_GODSPEED      (Category.COMBAT, SigilRank.GRAND);

    public enum Category { COMBAT, GATHERING, MMOITEMS }

    private final Category category;
    private final SigilRank rankFloor;

    SigilStat(Category category, SigilRank rankFloor) {
        this.category = category;
        this.rankFloor = rankFloor;
    }

    public Category category() { return category; }
    public SigilRank rankFloor() { return rankFloor; }

    /** Kept for compat with older code paths that asked "is this a major-only stat?". */
    public boolean majorOnly() { return rankFloor.ordinal() >= SigilRank.MAJOR.ordinal(); }

    public boolean allowedFor(SigilRank rank) {
        return rank.ordinal() >= rankFloor.ordinal();
    }

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

            case MMO_HEALTH_REGEN        -> "&a+" + fmt(value) + " HP Regen &7(MMO)";
            case MMO_ARMOR               -> "&7+" + fmt(value) + " Armor &7(MMO)";
            case MMO_BLOCK_RATING        -> "&b+" + fmt(value) + "% Block Rating &7(MMO)";
            case MMO_DODGE_RATING        -> "&e+" + fmt(value) + "% Dodge Rating &7(MMO)";
            case MMO_ATTACK_DAMAGE       -> "&c+" + fmt(value) + " Attack Damage &7(MMO)";
            case MMO_MAGIC_DAMAGE        -> "&5+" + fmt(value) + " Magic Damage &7(MMO)";
            case MMO_MAGICAL_DAMAGE      -> "&5+" + fmt(value) + "% Magical Damage &7(MMO)";
            case MMO_CRITICAL_STRIKE_CHANCE -> "&6+" + fmt(value) + "% Crit Strike Chance &7(MMO)";
            case MMO_CRITICAL_STRIKE_POWER  -> "&6+" + fmt(value) + "% Crit Strike Power &7(MMO)";
            case MMO_PVE_DAMAGE          -> "&c+" + fmt(value) + "% PVE Damage &7(MMO)";
            case MMO_PVP_DAMAGE          -> "&c+" + fmt(value) + "% PVP Damage &7(MMO)";
            case MMO_MAGIC_RESISTANCE    -> "&5+" + fmt(value) + "% Magic Resist &7(MMO)";
            case MMO_ARMOR_PIERCING      -> "&c+" + fmt(value) + "% Armor Piercing &7(MMO)";
            case MMO_BLOCK_POWER         -> "&b+" + fmt(value) + "% Block Power &7(MMO)";
            case MMO_COOLDOWN_REDUCTION  -> "&3+" + fmt(value) + "% Cooldown Reduction &7(MMO)";
            case MMO_MAX_MANA            -> "&9+" + fmt(value) + " Max Mana &7(MMO)";
            case MMO_MANA_REGEN          -> "&9+" + fmt(value) + " Mana Regen &7(MMO)";

            case GRAND_OMNIDAMAGE        -> "&4&l+" + fmt(value) + "% &c&lOmnidamage";
            case GRAND_FORTRESS          -> "&1&l+" + fmt(value) + "% &b&lFortress";
            case GRAND_VITALITY          -> "&a&l+" + fmt(value) + " &2&lTrue Vitality";
            case GRAND_SOULSTEAL         -> "&4&l+" + fmt(value) + "% &c&lSoulsteal";
            case GRAND_GODSPEED          -> "&e&l+" + fmt(value) + "% &6&lGodspeed";
        };
    }

    private static String fmt(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
