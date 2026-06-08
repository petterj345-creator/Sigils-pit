package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Stores and rolls abyss drops attached to MythicMobs, keyed by the mob's
 * internal name. Persisted to plugins/AbyssSigils/mobdrops.yml — the plugin's
 * own file, so we never touch (or corrupt) MythicMobs' YAML, and drops work
 * regardless of MythicMobs version.
 *
 * Drops are rolled in {@link com.abyss.sigils.integration.MythicHook} on
 * MythicMobDeathEvent and dropped at the mob's death location.
 */
public final class MobDropStore {

    private final AbyssPlugin plugin;
    private final File file;
    /** mob internal name -> its drops. */
    private final Map<String, List<MobDropEntry>> drops = new HashMap<>();

    public MobDropStore(AbyssPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mobdrops.yml");
    }

    public List<MobDropEntry> entriesFor(String mobId) {
        return drops.computeIfAbsent(mobId, k -> new ArrayList<>());
    }

    public void add(String mobId, MobDropEntry entry) {
        entriesFor(mobId).add(entry);
        save();
    }

    public void remove(String mobId, int index) {
        List<MobDropEntry> list = drops.get(mobId);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            if (list.isEmpty()) drops.remove(mobId);
            save();
        }
    }

    /** Roll every drop configured for this mob and drop the results at {@code loc}. */
    public void rollAndDrop(String mobId, Location loc, Player killer) {
        List<MobDropEntry> list = drops.get(mobId);
        if (list == null || list.isEmpty() || loc.getWorld() == null) return;
        for (MobDropEntry entry : list) {
            ItemStack item = entry.roll(plugin);
            if (item != null) loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    // ----- persistence -----

    public void load() {
        drops.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String mobId : cfg.getKeys(false)) {
            List<MobDropEntry> list = new ArrayList<>();
            for (Map<?, ?> m : cfg.getMapList(mobId)) {
                try {
                    MobDropEntry.Kind kind = MobDropEntry.Kind.valueOf(String.valueOf(m.get("kind")).toUpperCase(Locale.ROOT));
                    String ref = String.valueOf(m.get("ref"));
                    int tier = num(m.get("tier"), 1);
                    int min = num(m.get("amount-min"), 1);
                    int max = num(m.get("amount-max"), min);
                    double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 0.1;
                    list.add(new MobDropEntry(kind, ref, tier, min, max, chance));
                } catch (Exception ignored) {}
            }
            if (!list.isEmpty()) drops.put(mobId, list);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, List<MobDropEntry>> en : drops.entrySet()) {
            List<Map<String, Object>> serial = new ArrayList<>();
            for (MobDropEntry e : en.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", e.kind().name());
                m.put("ref", e.ref());
                if (e.kind() == MobDropEntry.Kind.SIGIL) m.put("tier", e.tier());
                m.put("amount-min", e.amountMin());
                m.put("amount-max", e.amountMax());
                m.put("chance", e.chance());
                serial.add(m);
            }
            cfg.set(en.getKey(), serial);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save mobdrops.yml: " + e.getMessage());
        }
    }

    private static int num(Object o, int dflt) {
        return (o instanceof Number n) ? n.intValue() : dflt;
    }
}
