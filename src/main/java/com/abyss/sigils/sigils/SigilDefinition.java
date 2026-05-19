package com.abyss.sigils.sigils;

import org.bukkit.Material;
import java.util.List;

/**
 * Immutable definition of a sigil type.
 *
 * Validation rule: if the rank is MINOR, the main stat and every entry in the
 * substat pool MUST be allowed for minors (i.e. not major-only). The
 * SigilRegistry filters out invalid combinations at load time with a warning.
 */
public final class SigilDefinition {

    private final String id;
    private final String display;
    private final Material material;
    private final int modelData;
    private final SigilRank rank;
    private final SigilStat stat;
    private final double[] tierValues;
    private final List<SigilStat> substatPool;

    public SigilDefinition(String id, String display, Material material, int modelData,
                           SigilRank rank, SigilStat stat, double[] tierValues,
                           List<SigilStat> substatPool) {
        this.id = id;
        this.display = display;
        this.material = material;
        this.modelData = modelData;
        this.rank = rank;
        this.stat = stat;
        this.tierValues = tierValues;
        this.substatPool = List.copyOf(substatPool);
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material material() { return material; }
    public int modelData() { return modelData; }
    public SigilRank rank() { return rank; }
    public SigilStat stat() { return stat; }
    public List<SigilStat> substatPool() { return substatPool; }
    public int maxTier() { return tierValues.length; }

    public double valueAtTier(int tier) {
        if (tier < 1) tier = 1;
        if (tier > tierValues.length) tier = tierValues.length;
        return tierValues[tier - 1];
    }
}
