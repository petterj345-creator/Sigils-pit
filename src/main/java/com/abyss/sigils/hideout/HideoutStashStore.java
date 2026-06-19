package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side storage backing each player's Abyss Stash. Items live here, not in
 * the placed chest block, so the stash survives the block being broken, moved, or
 * the hideout being reset — and a fresh chest always shows the same contents.
 *
 * On-disk: data/hideout_stash.yml, one section per player, one key per occupied
 * slot ({@code slotN: <serialized ItemStack>}).
 */
public final class HideoutStashStore {

    private final AbyssPlugin plugin;
    private final File file;
    private final int size;
    private final Map<UUID, ItemStack[]> data = new HashMap<>();

    public HideoutStashStore(AbyssPlugin plugin, int rows) {
        this.plugin = plugin;
        this.size = Math.max(9, Math.min(54, rows * 9));
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "hideout_stash.yml");
        load();
    }

    public int size() { return size; }

    public ItemStack[] get(UUID id) {
        return data.computeIfAbsent(id, k -> new ItemStack[size]);
    }

    /** Replace a player's stash with {@code items} (padded/truncated to {@link #size()}). */
    public void set(UUID id, ItemStack[] items) {
        ItemStack[] arr = new ItemStack[size];
        for (int i = 0; i < size && i < items.length; i++) arr[i] = items[i];
        data.put(id, arr);
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack[]> e : data.entrySet()) {
            ItemStack[] arr = e.getValue();
            for (int i = 0; i < arr.length; i++) {
                ItemStack it = arr[i];
                if (it != null && it.getType() != Material.AIR) {
                    cfg.set(e.getKey() + ".slot" + i, it);
                }
            }
        }
        try { cfg.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Failed to save hideout stash: " + ex.getMessage()); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String k : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ItemStack[] arr = new ItemStack[size];
                ConfigurationSection sec = cfg.getConfigurationSection(k);
                if (sec != null) {
                    for (String sk : sec.getKeys(false)) {
                        if (!sk.startsWith("slot")) continue;
                        int idx = Integer.parseInt(sk.substring(4));
                        if (idx >= 0 && idx < size) arr[idx] = sec.getItemStack(sk);
                    }
                }
                data.put(id, arr);
            } catch (Exception ignored) {}
        }
    }
}
