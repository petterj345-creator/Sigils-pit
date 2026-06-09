package com.abyss.sigils.skills;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Per-player skill data, persisted server-side (NOT on the book item, so it
 * survives losing/replacing the book).
 *
 * On-disk format (data/skills.yml):
 *   <uuid>:
 *     points: <total points ever earned>
 *     ranks:
 *       scholar: 2
 *       soul_harvest: 1
 *     completed: [ "templateA", "templateB" ]
 *
 * Available points = points earned − points spent (sum of ranks). Points are
 * earned one at a time, the first time a player completes a given template.
 */
public final class PlayerSkillStore {

    private final AbyssPlugin plugin;
    private final File file;

    private static final class Data {
        int points = 0;
        final Map<SkillType, Integer> ranks = new EnumMap<>(SkillType.class);
        final Set<String> completed = new HashSet<>();
    }

    private final Map<UUID, Data> byPlayer = new HashMap<>();

    public PlayerSkillStore(AbyssPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "skills.yml");
        load();
    }

    private Data data(UUID uuid) {
        return byPlayer.computeIfAbsent(uuid, k -> new Data());
    }

    // ----- queries -----

    public int totalPoints(UUID uuid)   { return data(uuid).points; }
    public int spentPoints(UUID uuid) {
        int sum = 0;
        for (int r : data(uuid).ranks.values()) sum += r;
        return sum;
    }
    public int availablePoints(UUID uuid) { return Math.max(0, totalPoints(uuid) - spentPoints(uuid)); }
    public int rank(UUID uuid, SkillType type) { return data(uuid).ranks.getOrDefault(type, 0); }
    public boolean hasCompleted(UUID uuid, String template) { return data(uuid).completed.contains(key(template)); }

    // ----- mutations -----

    /**
     * Record that the player completed this template. Returns true (and grants
     * one skill point) only the FIRST time — repeat clears give nothing.
     */
    public boolean markCompleted(UUID uuid, String template) {
        if (template == null) return false;
        Data d = data(uuid);
        if (!d.completed.add(key(template))) return false;
        d.points++;
        save();
        return true;
    }

    /** Spend one point to raise a skill by a rank, if affordable and not maxed. */
    public boolean spend(UUID uuid, SkillType type) {
        if (availablePoints(uuid) <= 0) return false;
        Data d = data(uuid);
        int cur = d.ranks.getOrDefault(type, 0);
        if (cur >= type.maxRank()) return false;
        d.ranks.put(type, cur + 1);
        save();
        return true;
    }

    /** Refund all allocated ranks back to the available pool. */
    public void respec(UUID uuid) {
        data(uuid).ranks.clear();
        save();
    }

    // ----- admin -----

    /** Admin: add (or, with a negative n, remove) earned skill points. */
    public void grantPoints(UUID uuid, int n) {
        Data d = data(uuid);
        d.points = Math.max(0, d.points + n);
        save();
    }

    /** Admin: wipe a player's points, allocations, and completed-map history. */
    public void resetPlayer(UUID uuid) {
        byPlayer.remove(uuid);
        save();
    }

    // ----- persistence -----

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Data> en : byPlayer.entrySet()) {
            Data d = en.getValue();
            if (d.points == 0 && d.ranks.isEmpty() && d.completed.isEmpty()) continue;
            String base = en.getKey().toString();
            cfg.set(base + ".points", d.points);
            for (Map.Entry<SkillType, Integer> r : d.ranks.entrySet()) {
                if (r.getValue() > 0) cfg.set(base + ".ranks." + r.getKey().id(), r.getValue());
            }
            cfg.set(base + ".completed", new ArrayList<>(d.completed));
        }
        try { cfg.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save skills: " + ex.getMessage()); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String k : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(k);
                Data d = new Data();
                d.points = cfg.getInt(k + ".points", 0);
                ConfigurationSection ranks = cfg.getConfigurationSection(k + ".ranks");
                if (ranks != null) {
                    for (String sk : ranks.getKeys(false)) {
                        SkillType type = SkillType.fromId(sk);
                        if (type != null) d.ranks.put(type, Math.max(0, Math.min(type.maxRank(), ranks.getInt(sk))));
                    }
                }
                for (String c : cfg.getStringList(k + ".completed")) d.completed.add(key(c));
                byPlayer.put(uuid, d);
            } catch (Exception ignored) {}
        }
    }

    private static String key(String template) {
        return template == null ? "" : template.toLowerCase(Locale.ROOT);
    }
}
