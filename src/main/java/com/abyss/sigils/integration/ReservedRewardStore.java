package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.ReservedReward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Persists items players reserved from the soul shop, keyed by player UUID, in
 * the plugin's own reservedrewards.yml. Reserved items survive relogs and carry
 * across maps — the player pays the remaining souls in a later ritual shop.
 */
public final class ReservedRewardStore {

    private final AbyssPlugin plugin;
    private final File file;
    private final Map<UUID, List<ReservedReward>> reserved = new HashMap<>();

    public ReservedRewardStore(AbyssPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reservedrewards.yml");
    }

    public List<ReservedReward> forPlayer(UUID id) {
        return reserved.computeIfAbsent(id, k -> new ArrayList<>());
    }

    public int count(UUID id) {
        List<ReservedReward> l = reserved.get(id);
        return l == null ? 0 : l.size();
    }

    public void add(UUID id, ReservedReward r) {
        forPlayer(id).add(r);
        save();
    }

    public void remove(UUID id, int index) {
        List<ReservedReward> l = reserved.get(id);
        if (l != null && index >= 0 && index < l.size()) {
            l.remove(index);
            if (l.isEmpty()) reserved.remove(id);
            save();
        }
    }

    // ----- persistence -----

    public void load() {
        reserved.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) continue;
            List<ReservedReward> list = new ArrayList<>();
            List<String> idxKeys = new ArrayList<>(sec.getKeys(false));
            idxKeys.sort(Comparator.comparingInt(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }));
            for (String ik : idxKeys) {
                ConfigurationSection e = sec.getConfigurationSection(ik);
                if (e == null) continue;
                ItemStack stack = e.getItemStack("item");
                if (stack == null) continue;
                list.add(new ReservedReward(stack,
                        e.getInt("amount", 1),
                        e.getInt("remaining", 0),
                        e.getString("mmo-type", null),
                        e.getString("mmo-id", null)));
            }
            if (!list.isEmpty()) reserved.put(id, list);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<ReservedReward>> en : reserved.entrySet()) {
            List<ReservedReward> list = en.getValue();
            for (int i = 0; i < list.size(); i++) {
                ReservedReward r = list.get(i);
                String base = en.getKey() + "." + i;
                cfg.set(base + ".item", r.itemStack());
                cfg.set(base + ".amount", r.amount());
                cfg.set(base + ".remaining", r.remaining());
                if (r.isMMOItem()) {
                    cfg.set(base + ".mmo-type", r.mmoType());
                    cfg.set(base + ".mmo-id", r.mmoId());
                }
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save reservedrewards.yml: " + e.getMessage());
        }
    }
}
