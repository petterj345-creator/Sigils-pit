package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tracks where each player placed their hideout fixtures (the Abyss Portal and
 * the Abyss Stash) so right-clicks on those blocks route to the right behaviour.
 *
 * Locations are stored as block coords keyed by the hideout OWNER. The world is
 * implied — a fixture only ever lives in {@code abyss_hideout_<owner>} — so given
 * any block we recover the owner from the world name and compare coords.
 */
public final class HideoutFixtures {

    private final AbyssPlugin plugin;
    private final File file;
    private final Map<UUID, int[]> portals = new HashMap<>();
    private final Map<UUID, int[]> stashes = new HashMap<>();

    public HideoutFixtures(AbyssPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "hideout_fixtures.yml");
        load();
    }

    public int[] portal(UUID owner) { return portals.get(owner); }
    public int[] stash(UUID owner) { return stashes.get(owner); }

    public void setPortal(UUID owner, Block b) { portals.put(owner, coords(b)); save(); }
    public void setStash(UUID owner, Block b)  { stashes.put(owner, coords(b)); save(); }
    public void clearPortal(UUID owner) { if (portals.remove(owner) != null) save(); }
    public void clearStash(UUID owner)  { if (stashes.remove(owner) != null) save(); }

    public boolean isPortal(Block b) { return matches(portals, b); }
    public boolean isStash(Block b)  { return matches(stashes, b); }

    private boolean matches(Map<UUID, int[]> map, Block b) {
        UUID owner = HideoutManager.ownerOf(b.getWorld());
        if (owner == null) return false;
        int[] c = map.get(owner);
        return c != null && c[0] == b.getX() && c[1] == b.getY() && c[2] == b.getZ();
    }

    private static int[] coords(Block b) { return new int[]{ b.getX(), b.getY(), b.getZ() }; }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        Set<UUID> all = new HashSet<>();
        all.addAll(portals.keySet());
        all.addAll(stashes.keySet());
        for (UUID id : all) {
            int[] p = portals.get(id);
            int[] s = stashes.get(id);
            if (p != null) cfg.set(id + ".portal", Arrays.asList(p[0], p[1], p[2]));
            if (s != null) cfg.set(id + ".stash",  Arrays.asList(s[0], s[1], s[2]));
        }
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save hideout fixtures: " + e.getMessage()); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String k : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ConfigurationSection sec = cfg.getConfigurationSection(k);
                if (sec == null) continue;
                if (sec.isList("portal")) portals.put(id, toArr(sec.getIntegerList("portal")));
                if (sec.isList("stash"))  stashes.put(id, toArr(sec.getIntegerList("stash")));
            } catch (Exception ignored) {}
        }
    }

    private static int[] toArr(List<Integer> l) {
        if (l.size() < 3) return null;
        return new int[]{ l.get(0), l.get(1), l.get(2) };
    }
}
