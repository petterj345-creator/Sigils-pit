package com.abyss.sigils.skills;

/**
 * Groups skills in the Skill Book GUI. General skills affect every map; the
 * others tune a specific event. Adding a new mechanic later = add a category
 * here (and the skills that belong to it in {@link SkillType}).
 */
public enum SkillCategory {

    GENERAL("&f&lGeneral", "&7Bonuses that apply to every map."),
    RITUAL("&5&lAltar of Souls", "&7Altar of Souls bonuses."),
    MAELSTROM("&3&lMaelstrom", "&7Rift event bonuses.");

    private final String display;
    private final String blurb;

    SkillCategory(String display, String blurb) {
        this.display = display;
        this.blurb = blurb;
    }

    public String display() { return display; }
    public String blurb()   { return blurb; }
}
