package com.abyss.sigils.dungeon;

/**
 * A single (MythicMob id, count, level) tuple.
 * Used both inside spawn points (MAP mode) and waves (WAVES mode).
 */
public final class MobEntry {

    private String mythicId;
    private int count;
    private int level;

    public MobEntry(String mythicId, int count, int level) {
        this.mythicId = mythicId;
        this.count = Math.max(1, count);
        this.level = Math.max(1, level);
    }

    public String mythicId() { return mythicId; }
    public int count()       { return count; }
    public int level()       { return level; }

    public void setMythicId(String id) { this.mythicId = id; }
    public void setCount(int n)        { this.count = Math.max(1, n); }
    public void setLevel(int n)        { this.level = Math.max(1, n); }

    /** Format used when storing in YAML lists: "id:count:level" */
    public String encode() { return mythicId + ":" + count + ":" + level; }

    public static MobEntry decode(String s) {
        if (s == null) return null;
        String[] parts = s.split(":");
        if (parts.length < 1) return null;
        String id = parts[0];
        int count = parts.length > 1 ? safeInt(parts[1], 1) : 1;
        int level = parts.length > 2 ? safeInt(parts[2], 1) : 1;
        return new MobEntry(id, count, level);
    }

    private static int safeInt(String s, int dflt) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return dflt; }
    }
}
