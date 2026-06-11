package com.abyss.sigils.dungeon;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A modifier ("event") that can be rolled onto an Abyss Map by applying a
 * currency item. PoE-style: a blank map gains mods, and when the dungeon runs
 * the {@link com.abyss.sigils.dungeon.DungeonManager} fires the behaviour for
 * each mod the map carries.
 *
 * Mods are stored on the map item as a PDC string-list of their {@link #id()}
 * (see {@link DungeonMap}). Keep ids stable — they're persisted on items in
 * player inventories.
 *
 * Adding a new event later = add a constant here, give it a currency
 * ({@code abyss_currency&#123;type=...&#125;}), and handle it in the runtime.
 */
public enum MapMod {

    /**
     * Altar of Souls. Spawns ritual altars (armor stands) in the instance.
     * Activating an altar spawns a burst of mobs that grant "souls"; after the
     * last ritual is cleared, an altar opens a soul shop. Configured per
     * template in the editor (mobs, soul values, reward pool).
     */
    RITUAL("ritual", "&5&l✦ Altar of Souls", "&5&lSoul Catalyst", Material.ECHO_SHARD,
            "&7Soul altars appear in the dungeon.",
            "&7Activate them to summon souls,",
            "&7then spend them at the soul shop."),

    /**
     * Maelstrom. Places rift markers in the instance; right-clicking one tears
     * open a churning circle that spawns mobs at a tickrate for a fixed time,
     * then collapses — deleting any mobs it spawned and dropping a loot chest
     * whose contents scale with how many you killed. Configured per template in
     * the editor (mobs/trash, timing, radius, loot + kill thresholds).
     */
    MAELSTROM("maelstrom", "&3&l✦ Maelstrom", "&3&lMaelstrom Catalyst", Material.HEART_OF_THE_SEA,
            "&7A churning rift can be torn open in the",
            "&7dungeon. Survive the horde it spews,",
            "&7then loot the eye of the storm."),

    /**
     * Reliquary. Places sealed reliquary markers in the instance; right-clicking
     * one unseals it, summoning a guardian pack. Slay every guardian to crack the
     * reliquary open, dropping a loot cache whose contents scale with the map
     * tier. Configured per template in the editor (guardians, loot pool).
     */
    RELIQUARY("reliquary", "&6&l✦ Reliquary", "&6&lReliquary Catalyst", Material.TRIAL_KEY,
            "&7Sealed reliquaries appear in the dungeon.",
            "&7Slay the guardians that spill out to",
            "&7crack one open and claim its hoard.");

    private final String id;
    private final String displayName;
    /** Display name of the currency item that applies this mod. */
    private final String currencyName;
    /** Material of the currency item. */
    private final Material currencyMaterial;
    private final String[] loreLines;

    MapMod(String id, String displayName, String currencyName, Material currencyMaterial,
           String... loreLines) {
        this.id = id;
        this.displayName = displayName;
        this.currencyName = currencyName;
        this.currencyMaterial = currencyMaterial;
        this.loreLines = loreLines;
    }

    public String id()                 { return id; }
    public String displayName()        { return displayName; }
    public String currencyName()       { return currencyName; }
    public Material currencyMaterial() { return currencyMaterial; }
    public List<String> lore()         { return new ArrayList<>(List.of(loreLines)); }

    /** Resolve a mod by its persisted id, or null if unknown. */
    public static MapMod fromId(String id) {
        if (id == null) return null;
        for (MapMod m : values()) if (m.id.equalsIgnoreCase(id)) return m;
        return null;
    }
}
