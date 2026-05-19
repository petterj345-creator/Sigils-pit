package com.abyss.sigils.sigils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public final class SigilRegistry {

    private final JavaPlugin plugin;
    private final Map<String, SigilDefinition> byId = new LinkedHashMap<>();

    public SigilRegistry(JavaPlugin plugin) { this.plugin = plugin; }

    public void load() {
        byId.clear();
        File file = new File(plugin.getDataFolder(), "sigils.yml");
        if (!file.exists()) plugin.saveResource("sigils.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = cfg.getConfigurationSection("sigils");
        if (root == null) {
            plugin.getLogger().warning("sigils.yml has no 'sigils' section!");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                String display = s.getString("display", "&fSigil");
                Material mat = Material.matchMaterial(
                        Objects.requireNonNullElse(s.getString("material"), "PAPER"));
                if (mat == null) mat = Material.PAPER;
                int modelData = s.getInt("model-data", 0);

                SigilRank rank;
                try { rank = SigilRank.valueOf(
                        Objects.requireNonNullElse(s.getString("rank"), "MINOR")); }
                catch (IllegalArgumentException e) { rank = SigilRank.MINOR; }

                SigilStat stat;
                try { stat = SigilStat.valueOf(
                        Objects.requireNonNullElse(s.getString("stat"), "DAMAGE_PERCENT")); }
                catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Sigil '" + id + "': unknown stat, skipping.");
                    continue;
                }
                if (!stat.allowedFor(rank)) {
                    plugin.getLogger().warning("Sigil '" + id + "': main stat " + stat
                            + " is major-only but rank is MINOR. Skipping.");
                    continue;
                }

                List<Double> tierList = s.getDoubleList("tier-values");
                if (tierList.isEmpty()) {
                    plugin.getLogger().warning("Sigil '" + id + "' has no tier-values; skipping.");
                    continue;
                }
                double[] tiers = new double[tierList.size()];
                for (int i = 0; i < tierList.size(); i++) tiers[i] = tierList.get(i);

                List<SigilStat> pool = new ArrayList<>();
                for (String sub : s.getStringList("substat-pool")) {
                    try {
                        SigilStat ss = SigilStat.valueOf(sub);
                        if (!ss.allowedFor(rank)) {
                            plugin.getLogger().warning("Sigil '" + id + "': substat " + ss
                                    + " is major-only but sigil is MINOR. Skipped from pool.");
                            continue;
                        }
                        pool.add(ss);
                    } catch (IllegalArgumentException ignored) {}
                }

                byId.put(id.toLowerCase(Locale.ROOT),
                        new SigilDefinition(id.toLowerCase(Locale.ROOT), display, mat, modelData, rank, stat, tiers, pool));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load sigil '" + id + "'", e);
            }
        }
        plugin.getLogger().info("Loaded " + byId.size() + " sigil definitions.");
    }

    public SigilDefinition get(String id) {
        if (id == null) return null;
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<SigilDefinition> all() { return byId.values(); }

    public SigilDefinition random() {
        if (byId.isEmpty()) return null;
        List<SigilDefinition> list = new ArrayList<>(byId.values());
        return list.get(new Random().nextInt(list.size()));
    }

    public SigilDefinition randomOfRank(SigilRank rank) {
        List<SigilDefinition> matches = new ArrayList<>();
        for (SigilDefinition d : byId.values()) if (d.rank() == rank) matches.add(d);
        if (matches.isEmpty()) return null;
        return matches.get(new Random().nextInt(matches.size()));
    }

    /**
     * Save a draft to sigils.yml and reload. Returns true on success.
     * If a sigil with the same id already exists, it's overwritten.
     */
    public boolean saveDraft(SigilDraft draft) {
        String err = draft.validationError();
        if (err != null) {
            plugin.getLogger().warning("Couldn't save sigil draft: " + err);
            return false;
        }
        File file = new File(plugin.getDataFolder(), "sigils.yml");
        YamlConfiguration cfg;
        if (file.exists()) cfg = YamlConfiguration.loadConfiguration(file);
        else { cfg = new YamlConfiguration(); cfg.createSection("sigils"); }

        String path = "sigils." + draft.id().toLowerCase(Locale.ROOT);
        cfg.set(path + ".display", draft.display());
        cfg.set(path + ".material", draft.material().name());
        if (draft.modelData() != 0) cfg.set(path + ".model-data", draft.modelData());
        cfg.set(path + ".rank", draft.rank().name());
        cfg.set(path + ".stat", draft.stat().name());
        cfg.set(path + ".tier-values", draft.tierValues());
        List<String> pool = new ArrayList<>();
        for (SigilStat s : draft.substatPool()) pool.add(s.name());
        cfg.set(path + ".substat-pool", pool);
        try {
            cfg.save(file);
            load(); // reload definitions into memory
            return true;
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to save sigils.yml: " + e.getMessage());
            return false;
        }
    }

    /** Removes a sigil from disk + memory. Returns true if it existed. */
    public boolean delete(String id) {
        if (!byId.containsKey(id.toLowerCase(Locale.ROOT))) return false;
        File file = new File(plugin.getDataFolder(), "sigils.yml");
        if (!file.exists()) return false;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("sigils." + id.toLowerCase(Locale.ROOT), null);
        try { cfg.save(file); load(); return true; }
        catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to delete sigil: " + e.getMessage());
            return false;
        }
    }
}
