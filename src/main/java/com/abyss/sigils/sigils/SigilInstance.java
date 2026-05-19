package com.abyss.sigils.sigils;

import java.util.HashMap;
import java.util.Map;

/**
 * A concrete sigil owned by a player or sitting in an item.
 * Carries the type id (which maps to a SigilDefinition), its current tier,
 * and any rolled sub-stats from upgrades.
 */
public final class SigilInstance {

    private final String definitionId;
    private int tier;
    private final Map<SigilStat, Double> subStats;

    public SigilInstance(String definitionId, int tier) {
        this(definitionId, tier, new HashMap<>());
    }

    public SigilInstance(String definitionId, int tier, Map<SigilStat, Double> subStats) {
        this.definitionId = definitionId;
        this.tier = Math.max(1, tier);
        this.subStats = new HashMap<>(subStats);
    }

    public String definitionId() { return definitionId; }
    public int tier() { return tier; }
    public Map<SigilStat, Double> subStats() { return subStats; }

    public void setTier(int tier) { this.tier = tier; }
    public void addSubStat(SigilStat stat, double value) {
        subStats.merge(stat, value, Double::sum);
    }
}
