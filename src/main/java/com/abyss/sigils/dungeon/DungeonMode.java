package com.abyss.sigils.dungeon;

/**
 * How a dungeon plays.
 *
 * MAP   — PoE-style. Trash mobs spawn continuously from all spawn points.
 *         Once the kill threshold is met, the boss spawns.
 *
 * WAVES — Infernal-Hordes-style. Discrete waves play in order. Each wave
 *         defines its own mob list, counts, and level. When a wave is fully
 *         killed, the next wave starts after a short delay. After the final
 *         wave is cleared, the boss spawns.
 */
public enum DungeonMode { MAP, WAVES }
