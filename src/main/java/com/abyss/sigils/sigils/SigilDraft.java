package com.abyss.sigils.sigils;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable counterpart to {@link SigilDefinition} used by the creator GUI.
 * When the admin clicks "Save" the registry serializer flushes this to sigils.yml
 * and triggers a reload.
 */
public final class SigilDraft {

    private String id;                // must match [a-z0-9_]+
    private String display = "&fNew Sigil";
    private Material material = Material.PAPER;
    private int modelData = 0;
    private SigilRank rank = SigilRank.MINOR;
    private SigilStat stat = SigilStat.DAMAGE_PERCENT;
    private final List<Double> tierValues = new ArrayList<>(List.of(5.0, 10.0, 15.0, 20.0, 30.0));
    private final List<SigilStat> substatPool = new ArrayList<>();

    public SigilDraft(String id) { this.id = id; }

    /** Build a draft pre-filled from an existing definition (for /abyss sigil edit). */
    public static SigilDraft from(SigilDefinition def) {
        SigilDraft d = new SigilDraft(def.id());
        d.display = def.display();
        d.material = def.material();
        d.modelData = def.modelData();
        d.rank = def.rank();
        d.stat = def.stat();
        d.tierValues.clear();
        for (int t = 1; t <= def.maxTier(); t++) d.tierValues.add(def.valueAtTier(t));
        d.substatPool.addAll(def.substatPool());
        return d;
    }

    public String id() { return id; }
    public String display() { return display; }
    public Material material() { return material; }
    public int modelData() { return modelData; }
    public SigilRank rank() { return rank; }
    public SigilStat stat() { return stat; }
    public List<Double> tierValues() { return tierValues; }
    public List<SigilStat> substatPool() { return substatPool; }

    public void setId(String id) { this.id = id; }
    public void setDisplay(String d) { this.display = d; }
    public void setMaterial(Material m) { this.material = m; }
    public void setModelData(int n) { this.modelData = n; }
    public void setRank(SigilRank r) {
        this.rank = r;
        // Drop any stats that don't fit the new rank
        if (!stat.allowedFor(r)) stat = SigilStat.DAMAGE_PERCENT;
        substatPool.removeIf(s -> !s.allowedFor(r));
    }
    public void setStat(SigilStat s) {
        if (!s.allowedFor(rank)) return; // ignore invalid
        this.stat = s;
    }
    public void setTierValue(int tierIndex, double value) {
        while (tierValues.size() <= tierIndex) tierValues.add(0.0);
        tierValues.set(tierIndex, Math.max(0, value));
    }
    public void addTier()    { tierValues.add(tierValues.isEmpty() ? 5.0 : tierValues.get(tierValues.size() - 1) + 5.0); }
    public void removeTier() { if (tierValues.size() > 1) tierValues.remove(tierValues.size() - 1); }
    public void toggleSubstat(SigilStat s) {
        if (!s.allowedFor(rank)) return;
        if (!substatPool.remove(s)) substatPool.add(s);
    }

    public String validationError() {
        if (id == null || id.isBlank()) return "id is empty";
        if (!id.matches("[a-z0-9_]+")) return "id must be lowercase letters/digits/underscores";
        if (tierValues.isEmpty()) return "no tier values";
        if (!stat.allowedFor(rank)) return "main stat not valid for rank";
        return null;
    }
}
