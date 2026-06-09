package com.abyss.sigils.skills;

import org.bukkit.Material;

import java.util.Locale;

/**
 * One allocatable node in the Skill Book. Each rank costs one skill point.
 *
 * Skills are intentionally simple metadata holders; the actual effect is read
 * at the relevant hook by asking the {@link PlayerSkillStore} for a player's
 * rank in a specific {@code SkillType} (e.g. the reward roller reads SCHOLAR).
 * To add a new mechanic's skills later: add a constant here and read its rank
 * wherever that mechanic does its work — no dispatch framework needed.
 *
 * {@link #value} is the magnitude PER RANK. For {@link #percent} skills it's a
 * percentage (25 = +25% per rank); otherwise it's a flat amount (1 = +1 per
 * rank). {@code effectNoun} is just for GUI lore.
 */
public enum SkillType {

    // ----- General (every map) -----
    GREATER_SPOILS("greater_spoils", "Greater Spoils", SkillCategory.GENERAL,
            Material.CHEST, 3, 1, false, "max loot items",
            "Reward & cache rolls can contain more items."),
    SCHOLAR("scholar", "Scholar", SkillCategory.GENERAL,
            Material.EXPERIENCE_BOTTLE, 4, 25, true, "XP gained",
            "More XP from map completion rewards."),
    TREASURE_HUNTER("treasure_hunter", "Treasure Hunter", SkillCategory.GENERAL,
            Material.GOLD_INGOT, 4, 20, true, "money gained",
            "More money from map completion rewards."),

    // ----- Ritual -----
    SOUL_HARVEST("soul_harvest", "Soul Harvest", SkillCategory.RITUAL,
            Material.ECHO_SHARD, 5, 20, true, "souls per kill",
            "Ritual mobs yield more souls when you kill them."),

    // ----- Maelstrom -----
    RIFT_SURGE("rift_surge", "Rift Surge", SkillCategory.MAELSTROM,
            Material.HEART_OF_THE_SEA, 5, 10, true, "rift spawn speed",
            "Maelstroms you open spawn mobs faster."),
    CATACLYSM("cataclysm", "Cataclysm", SkillCategory.MAELSTROM,
            Material.MAGMA_BLOCK, 5, 1, false, "mobs per pulse",
            "Maelstroms you open spew more mobs per pulse.");

    private final String id;
    private final String display;
    private final SkillCategory category;
    private final Material icon;
    private final int maxRank;
    private final double value;
    private final boolean percent;
    private final String effectNoun;
    private final String blurb;

    SkillType(String id, String display, SkillCategory category, Material icon,
              int maxRank, double value, boolean percent, String effectNoun, String blurb) {
        this.id = id;
        this.display = display;
        this.category = category;
        this.icon = icon;
        this.maxRank = maxRank;
        this.value = value;
        this.percent = percent;
        this.effectNoun = effectNoun;
        this.blurb = blurb;
    }

    public String id()             { return id; }
    public String display()        { return display; }
    public SkillCategory category(){ return category; }
    public Material icon()         { return icon; }
    public int maxRank()           { return maxRank; }
    public double value()          { return value; }
    public boolean percent()      { return percent; }
    public String effectNoun()     { return effectNoun; }
    public String blurb()          { return blurb; }

    /** Total effect at a rank, e.g. rank 2 of +25% → 50. */
    public double totalAt(int rank) { return value * Math.max(0, rank); }

    /** A 1+pct/100 multiplier for percent skills (e.g. rank 2 of +25% → 1.50). */
    public double multiplierAt(int rank) { return 1.0 + totalAt(rank) / 100.0; }

    /** Flat bonus for non-percent skills (e.g. rank 2 of +1 → 2). */
    public int flatAt(int rank) { return (int) Math.round(totalAt(rank)); }

    /** Human label of the effect at a given rank, for GUI lore. */
    public String effectLabel(int rank) {
        String n = fmt(totalAt(rank));
        return "+" + n + (percent ? "%" : "") + " " + effectNoun;
    }

    /** Human label of the per-rank step. */
    public String perRankLabel() {
        return "+" + fmt(value) + (percent ? "%" : "") + " " + effectNoun + " per rank";
    }

    public static SkillType fromId(String id) {
        if (id == null) return null;
        for (SkillType s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.1f", d);
    }
}
