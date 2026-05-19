package com.abyss.sigils.socket;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilStat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Per-player socket storage with separate small (minor) and big (major) slot lists.
 *
 * On-disk format:
 *   <uuid>:
 *     small: [ "id:tier:stat=val;stat=val", "", "id:tier:...", ... ]
 *     big:   [ "id:tier:...", "", "" ]
 */
public final class PlayerSigilStore {

    private final AbyssPlugin plugin;
    private final int smallSlots;
    private final int bigSlots;
    private final Map<UUID, List<SigilInstance>> smallData = new HashMap<>();
    private final Map<UUID, List<SigilInstance>> bigData = new HashMap<>();
    private final File file;

    public PlayerSigilStore(AbyssPlugin plugin, int smallSlots, int bigSlots) {
        this.plugin = plugin;
        this.smallSlots = smallSlots;
        this.bigSlots = bigSlots;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "sockets.yml");
        load();
    }

    public int smallSlots() { return smallSlots; }
    public int bigSlots()   { return bigSlots; }

    public List<SigilInstance> getSmall(UUID uuid) {
        return smallData.computeIfAbsent(uuid, k -> blank(smallSlots));
    }

    public List<SigilInstance> getBig(UUID uuid) {
        return bigData.computeIfAbsent(uuid, k -> blank(bigSlots));
    }

    private List<SigilInstance> blank(int n) {
        List<SigilInstance> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(null);
        return list;
    }

    public SigilInstance setSmall(UUID uuid, int slot, SigilInstance inst) {
        List<SigilInstance> list = getSmall(uuid);
        if (slot < 0 || slot >= smallSlots) return inst;
        SigilInstance prev = list.get(slot);
        list.set(slot, inst);
        return prev;
    }

    public SigilInstance setBig(UUID uuid, int slot, SigilInstance inst) {
        List<SigilInstance> list = getBig(uuid);
        if (slot < 0 || slot >= bigSlots) return inst;
        SigilInstance prev = list.get(slot);
        list.set(slot, inst);
        return prev;
    }

    /** Concatenated view (small + big) of all socketed sigils for a player. */
    public List<SigilInstance> allSocketed(UUID uuid) {
        List<SigilInstance> all = new ArrayList<>();
        all.addAll(getSmall(uuid));
        all.addAll(getBig(uuid));
        return all;
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        Set<UUID> allUuids = new HashSet<>();
        allUuids.addAll(smallData.keySet());
        allUuids.addAll(bigData.keySet());
        for (UUID uuid : allUuids) {
            List<String> smallStrs = new ArrayList<>();
            for (SigilInstance i : getSmall(uuid)) smallStrs.add(i == null ? "" : serialize(i));
            List<String> bigStrs = new ArrayList<>();
            for (SigilInstance i : getBig(uuid)) bigStrs.add(i == null ? "" : serialize(i));
            cfg.set(uuid.toString() + ".small", smallStrs);
            cfg.set(uuid.toString() + ".big",   bigStrs);
        }
        try { cfg.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save sockets: " + ex.getMessage()); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String k : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(k);
                List<String> smallList = cfg.getStringList(k + ".small");
                List<String> bigList   = cfg.getStringList(k + ".big");
                smallData.put(uuid, padDeserialize(smallList, smallSlots));
                bigData.put(uuid,   padDeserialize(bigList,   bigSlots));
            } catch (Exception ignored) {}
        }
    }

    private List<SigilInstance> padDeserialize(List<String> list, int target) {
        List<SigilInstance> out = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            if (i < list.size()) {
                String s = list.get(i);
                out.add(s == null || s.isEmpty() ? null : deserialize(s));
            } else out.add(null);
        }
        return out;
    }

    private static String serialize(SigilInstance inst) {
        StringBuilder sb = new StringBuilder();
        sb.append(inst.definitionId()).append(':').append(inst.tier()).append(':');
        boolean first = true;
        for (var e : inst.subStats().entrySet()) {
            if (!first) sb.append(';');
            sb.append(e.getKey().name()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static SigilInstance deserialize(String s) {
        String[] parts = s.split(":", 3);
        String id = parts[0];
        int tier = Integer.parseInt(parts[1]);
        SigilInstance inst = new SigilInstance(id, tier);
        if (parts.length >= 3 && !parts[2].isEmpty()) {
            for (String kv : parts[2].split(";")) {
                String[] p = kv.split("=");
                if (p.length == 2) {
                    try { inst.addSubStat(SigilStat.valueOf(p[0]), Double.parseDouble(p[1])); }
                    catch (Exception ignored) {}
                }
            }
        }
        return inst;
    }

    public void returnToPlayer(Player p, SigilInstance inst) {
        if (inst == null) return;
        ItemStack item = SigilItem.toItem(inst);
        if (item == null) return;
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(item);
        for (ItemStack stack : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), stack);
        }
    }
}
