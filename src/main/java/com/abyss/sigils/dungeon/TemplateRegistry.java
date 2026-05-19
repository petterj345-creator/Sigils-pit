package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all DungeonTemplates: creating new worlds, loading existing ones from disk,
 * listing/deleting templates.
 *
 * Templates are paired (world + yaml):
 *   - World folder: server-root/abyss_tpl_&lt;name&gt;
 *   - YAML config:  plugins/AbyssSigils/templates/&lt;name&gt;.yml
 *
 * On plugin enable, we load all yamls into memory but do NOT load their worlds
 * (worlds are heavy). Worlds are loaded lazily when an admin runs /abyss edit,
 * or when a party enters (DungeonManager loads them just before cloning).
 */
public final class TemplateRegistry {

    private final AbyssPlugin plugin;
    private final Map<String, DungeonTemplate> templates = new LinkedHashMap<>();
    private final File templatesDir;

    public TemplateRegistry(AbyssPlugin plugin) {
        this.plugin = plugin;
        this.templatesDir = new File(plugin.getDataFolder(), "templates");
        if (!templatesDir.exists()) templatesDir.mkdirs();
    }

    /** Loads all template metadata from disk. */
    public void loadAll() {
        templates.clear();
        File[] files = templatesDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            String name = f.getName().substring(0, f.getName().length() - 4);
            DungeonTemplate t = new DungeonTemplate(name, f);
            t.load();
            templates.put(name.toLowerCase(Locale.ROOT), t);
        }
        plugin.getLogger().info("Loaded " + templates.size() + " dungeon template(s).");
    }

    public DungeonTemplate get(String name) {
        if (name == null) return null;
        return templates.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<DungeonTemplate> all() { return templates.values(); }

    public Collection<DungeonTemplate> playable() {
        List<DungeonTemplate> out = new ArrayList<>();
        for (DungeonTemplate t : templates.values()) if (t.isPlayable()) out.add(t);
        return out;
    }

    /** Pick a random playable template, or null if none. */
    public DungeonTemplate randomPlayable() {
        List<DungeonTemplate> p = new ArrayList<>(playable());
        if (p.isEmpty()) return null;
        return p.get(new Random().nextInt(p.size()));
    }

    /**
     * Creates a new template:
     *   1. creates the YAML stub
     *   2. creates a fresh void world named "abyss_tpl_&lt;name&gt;"
     *   3. returns the template (caller can teleport an admin to it)
     */
    public DungeonTemplate create(String name) throws IOException {
        String key = name.toLowerCase(Locale.ROOT);
        if (templates.containsKey(key)) {
            throw new IOException("Template '" + name + "' already exists.");
        }

        File yml = new File(templatesDir, name + ".yml");
        DungeonTemplate t = new DungeonTemplate(name, yml);
        t.save(); // creates the file with default values

        // Create the world if it doesn't exist on disk
        String worldName = "abyss_tpl_" + name;
        if (Bukkit.getWorld(worldName) == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.FLAT);
            wc.generator(new VoidGenerator());
            wc.generateStructures(false);
            World w = Bukkit.createWorld(wc);
            if (w != null) {
                // Place a small starter platform so the admin doesn't fall into the void
                int y = 64;
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        w.getBlockAt(x, y, z).setType(Material.STONE);
                    }
                }
                w.setSpawnLocation(0, y + 1, 0);
                w.setAutoSave(true);
                w.setDifficulty(Difficulty.PEACEFUL);
                w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                w.setTime(6000);
            }
        }

        templates.put(key, t);
        return t;
    }

    /** Loads the world for an existing template (used by /abyss edit). */
    public World loadWorld(DungeonTemplate t) {
        World w = Bukkit.getWorld(t.worldName());
        if (w != null) return w;
        File worldDir = new File(Bukkit.getWorldContainer(), t.worldName());
        if (!worldDir.exists()) {
            plugin.getLogger().warning("Template '" + t.name() + "' has no world folder on disk!");
            return null;
        }
        WorldCreator wc = new WorldCreator(t.worldName());
        // Use the same void generator so it can be re-loaded without regenerating
        wc.generator(new VoidGenerator());
        return Bukkit.createWorld(wc);
    }

    /**
     * Deletes a template entirely: unload its world, delete the world folder,
     * and remove the YAML.
     */
    public void delete(DungeonTemplate t) throws IOException {
        World w = Bukkit.getWorld(t.worldName());
        if (w != null) {
            // Kick anyone out of the template world first
            World fallback = Bukkit.getWorlds().get(0);
            for (var p : w.getPlayers()) p.teleport(fallback.getSpawnLocation());
            Bukkit.unloadWorld(w, false);
        }
        File worldDir = new File(Bukkit.getWorldContainer(), t.worldName());
        if (worldDir.exists()) deleteRecursive(worldDir);
        if (t.configFile().exists()) t.configFile().delete();
        templates.remove(t.name().toLowerCase(Locale.ROOT));
    }

    public void save(DungeonTemplate t) {
        try { t.save(); }
        catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save template " + t.name(), e);
        }
    }

    // -----

    public static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    /** Empty void world generator so create() makes a fully empty world. */
    private static class VoidGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            // intentionally empty — void
        }
        @Override public boolean shouldGenerateNoise()     { return false; }
        @Override public boolean shouldGenerateSurface()   { return false; }
        @Override public boolean shouldGenerateBedrock()   { return false; }
        @Override public boolean shouldGenerateCaves()     { return false; }
        @Override public boolean shouldGenerateDecorations(){ return false; }
        @Override public boolean shouldGenerateMobs()      { return false; }
        @Override public boolean shouldGenerateStructures(){ return false; }
    }
}
