package com.abyss.sigils.dungeon;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A named dungeon template.
 *
 * World lives on disk as "abyss_tpl_&lt;name&gt;" in the server root.
 * Config lives at plugins/AbyssSigils/templates/&lt;name&gt;.yml.
 *
 * Two play modes:
 *  - MAP   — trash flows continuously from spawn points; boss spawns at threshold.
 *  - WAVES — waves play in order; boss spawns after the final wave is cleared.
 *
 * Spawn points carry their own mob list (used in MAP mode). If a spawn point has
 * an empty list, the template's `defaultTrashMobs` is used. In WAVES mode the
 * waves define what spawns; spawn points are still used as the *locations*
 * (mobs distribute across them).
 */
public final class DungeonTemplate {

    private final String name;
    private final File configFile;
    private String worldName;

    private DungeonMode mode = DungeonMode.MAP;

    private Location playerSpawn;
    private Location bossSpawn;

    private final List<SpawnPoint> spawnPoints = new ArrayList<>();
    /** Fallback mobs used by spawn points that don't define their own list (MAP mode). */
    private final List<MobEntry> defaultTrashMobs = new ArrayList<>();
    /** Waves used in WAVES mode. Played in order. */
    private final List<Wave> waves = new ArrayList<>();

    private String bossMobId = "AbyssOverlord";
    private int bossLevel = 1;

    private int mobsBeforeBoss = 30;       // MAP mode only
    private int timeLimitMinutes = 20;
    private int maxConcurrentMobs = 20;
    private int mobsPerWave = 4;           // MAP mode: trash mobs per spawn tick
    private int waveIntervalSeconds = 8;   // MAP mode: tick interval

    // ----- death rules -----
    private int lives = 3;                 // per-player lives; 0 = unlimited
    private boolean keepInventory = true;  // drop items on death?

    // ----- rewards (rolled on dungeon complete) -----
    private final List<RewardEntry> rewardPool = new ArrayList<>();
    private int maxRewardItems = 3;
    private double moneyMin = 0;
    private double moneyMax = 0;
    private double moneyChancePercent = 100;
    private int xpLevelsMin = 0;
    private int xpLevelsMax = 0;
    private double xpChancePercent = 100;

    // ----- forge / upgrade -----
    /**
     * How many forge attempts each player gets after clearing this template.
     * -1 = use the global default from config (upgrade.attempts-per-clear).
     */
    private int upgradeAttemptsPerClear = -1;

    public DungeonTemplate(String name, File configFile) {
        this.name = name;
        this.configFile = configFile;
        this.worldName = "abyss_tpl_" + name;
    }

    // ----- getters -----
    public String name()              { return name; }
    public String worldName()         { return worldName; }
    public File configFile()          { return configFile; }
    public DungeonMode mode()         { return mode; }
    public Location playerSpawn()     { return playerSpawn == null ? null : playerSpawn.clone(); }
    public Location bossSpawn()       { return bossSpawn == null ? null : bossSpawn.clone(); }
    public List<SpawnPoint> spawnPoints()     { return spawnPoints; }
    public List<MobEntry> defaultTrashMobs()  { return defaultTrashMobs; }
    public List<Wave> waves()                 { return waves; }
    public String bossMobId()         { return bossMobId; }
    public int bossLevel()            { return bossLevel; }
    public int mobsBeforeBoss()       { return mobsBeforeBoss; }
    public int timeLimitMinutes()     { return timeLimitMinutes; }
    public int maxConcurrentMobs()    { return maxConcurrentMobs; }
    public int mobsPerWave()          { return mobsPerWave; }
    public int waveIntervalSeconds()  { return waveIntervalSeconds; }
    public int lives()                { return lives; }
    public boolean keepInventory()    { return keepInventory; }

    // Reward getters
    public List<RewardEntry> rewardPool()  { return rewardPool; }
    public int maxRewardItems()            { return maxRewardItems; }
    public double moneyMin()               { return moneyMin; }
    public double moneyMax()               { return moneyMax; }
    public double moneyChancePercent()     { return moneyChancePercent; }
    public int xpLevelsMin()               { return xpLevelsMin; }
    public int xpLevelsMax()               { return xpLevelsMax; }
    public double xpChancePercent()        { return xpChancePercent; }
    public int upgradeAttemptsPerClear()   { return upgradeAttemptsPerClear; }

    // ----- setters -----
    public void setMode(DungeonMode m)             { this.mode = m; }
    public void setPlayerSpawn(Location loc)       { this.playerSpawn = wipeWorld(loc); }
    public void setBossSpawn(Location loc)         { this.bossSpawn = wipeWorld(loc); }
    public void clearPlayerSpawn()                 { this.playerSpawn = null; }
    public void clearBossSpawn()                   { this.bossSpawn = null; }
    public void addSpawnPoint(Location loc)        { spawnPoints.add(new SpawnPoint(wipeWorld(loc))); }
    public void removeSpawnPoint(int index)        { if (index >= 0 && index < spawnPoints.size()) spawnPoints.remove(index); }
    public void clearSpawnPoints()                 { spawnPoints.clear(); }
    public void addWave(Wave w)                    { waves.add(w); }
    public void removeWave(int index)              { if (index >= 0 && index < waves.size()) waves.remove(index); }
    public void clearWaves()                       { waves.clear(); }
    public void setBossMobId(String id)            { this.bossMobId = id; }
    public void setBossLevel(int n)                { this.bossLevel = Math.max(1, n); }
    public void setMobsBeforeBoss(int n)           { this.mobsBeforeBoss = Math.max(1, n); }
    public void setTimeLimitMinutes(int n)         { this.timeLimitMinutes = Math.max(1, n); }
    public void setMaxConcurrentMobs(int n)        { this.maxConcurrentMobs = Math.max(1, n); }
    public void setMobsPerWave(int n)              { this.mobsPerWave = Math.max(1, n); }
    public void setWaveIntervalSeconds(int n)      { this.waveIntervalSeconds = Math.max(1, n); }
    public void setLives(int n)                    { this.lives = Math.max(0, n); }
    public void setKeepInventory(boolean b)        { this.keepInventory = b; }

    // Reward setters
    public void setMaxRewardItems(int n)           { this.maxRewardItems = Math.max(0, n); }
    public void setMoneyMin(double v)              { this.moneyMin = Math.max(0, v); if (moneyMax < moneyMin) moneyMax = moneyMin; }
    public void setMoneyMax(double v)              { this.moneyMax = Math.max(0, v); if (moneyMin > moneyMax) moneyMin = moneyMax; }
    public void setMoneyChancePercent(double p)    { this.moneyChancePercent = clampPct(p); }
    public void setXpLevelsMin(int n)              { this.xpLevelsMin = Math.max(0, n); if (xpLevelsMax < xpLevelsMin) xpLevelsMax = xpLevelsMin; }
    public void setXpLevelsMax(int n)              { this.xpLevelsMax = Math.max(0, n); if (xpLevelsMin > xpLevelsMax) xpLevelsMin = xpLevelsMax; }
    public void setXpChancePercent(double p)       { this.xpChancePercent = clampPct(p); }
    /** -1 = inherit from config, otherwise clamped to [0, +inf). */
    public void setUpgradeAttemptsPerClear(int n)  { this.upgradeAttemptsPerClear = (n < 0) ? -1 : n; }

    private static double clampPct(double p) { return p < 0 ? 0 : (p > 100 ? 100 : p); }

    private static Location wipeWorld(Location l) { Location c = l.clone(); c.setWorld(null); return c; }

    /** True if the template has enough info to actually run. */
    public boolean isPlayable() { return validationError() == null; }

    public String validationError() {
        if (playerSpawn == null) return "missing player spawn";
        if (bossSpawn == null)   return "missing boss spawn";
        if (spawnPoints.isEmpty()) return "no spawn points";
        if (bossMobId == null || bossMobId.isBlank()) return "no boss mob id";
        if (mode == DungeonMode.MAP) {
            // MAP mode needs *some* trash mobs available — either default list or per-spawn-point lists
            boolean hasMobs = !defaultTrashMobs.isEmpty();
            if (!hasMobs) {
                for (SpawnPoint sp : spawnPoints) if (!sp.mobs().isEmpty()) { hasMobs = true; break; }
            }
            if (!hasMobs) return "no trash mobs configured";
        } else {
            if (waves.isEmpty()) return "no waves defined";
            for (int i = 0; i < waves.size(); i++) {
                if (waves.get(i).mobs().isEmpty()) return "wave " + (i + 1) + " has no mobs";
            }
        }
        return null;
    }

    // ----- persistence -----

    public void save() throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("world", worldName);
        cfg.set("mode", mode.name());
        cfg.set("boss-mob-id", bossMobId);
        cfg.set("boss-level", bossLevel);
        cfg.set("mobs-before-boss", mobsBeforeBoss);
        cfg.set("time-limit-minutes", timeLimitMinutes);
        cfg.set("max-concurrent-mobs", maxConcurrentMobs);
        cfg.set("mobs-per-wave", mobsPerWave);
        cfg.set("wave-interval-seconds", waveIntervalSeconds);
        cfg.set("lives", lives);
        cfg.set("keep-inventory", keepInventory);

        if (playerSpawn != null) writeLoc(cfg, "player-spawn", playerSpawn);
        if (bossSpawn   != null) writeLoc(cfg, "boss-spawn",   bossSpawn);

        // Spawn points (each with its own mob list)
        List<java.util.Map<String, Object>> sps = new ArrayList<>();
        for (SpawnPoint sp : spawnPoints) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            Location l = sp.location();
            m.put("x", l.getX()); m.put("y", l.getY()); m.put("z", l.getZ());
            m.put("yaw", l.getYaw()); m.put("pitch", l.getPitch());
            List<String> mobStrs = new ArrayList<>();
            for (MobEntry e : sp.mobs()) mobStrs.add(e.encode());
            m.put("mobs", mobStrs);
            sps.add(m);
        }
        cfg.set("spawn-points", sps);

        // Default trash list
        List<String> trashStrs = new ArrayList<>();
        for (MobEntry e : defaultTrashMobs) trashStrs.add(e.encode());
        cfg.set("default-trash-mobs", trashStrs);

        // Waves
        List<java.util.Map<String, Object>> waveList = new ArrayList<>();
        for (Wave w : waves) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            List<String> mobStrs = new ArrayList<>();
            for (MobEntry e : w.mobs()) mobStrs.add(e.encode());
            m.put("mobs", mobStrs);
            m.put("delay-after-seconds", w.delayAfterSeconds());
            m.put("boss-wave", w.bossWave());
            waveList.add(m);
        }
        cfg.set("waves", waveList);

        // Rewards
        cfg.set("rewards.max-items", maxRewardItems);
        cfg.set("rewards.money.min", moneyMin);
        cfg.set("rewards.money.max", moneyMax);
        cfg.set("rewards.money.chance", moneyChancePercent);
        cfg.set("rewards.xp.min", xpLevelsMin);
        cfg.set("rewards.xp.max", xpLevelsMax);
        cfg.set("rewards.xp.chance", xpChancePercent);
        cfg.set("upgrade.attempts-per-clear", upgradeAttemptsPerClear);
        // We store the pool under a section, one entry per index, so Bukkit
        // can serialize the ItemStack natively (preserving NBT/PDC).
        cfg.set("rewards.pool", null); // clear stale
        for (int i = 0; i < rewardPool.size(); i++) {
            RewardEntry r = rewardPool.get(i);
            if (r.itemStack() == null) continue;
            String base = "rewards.pool." + i;
            cfg.set(base + ".item", r.itemStack());
            cfg.set(base + ".chance", r.chancePercent());
            cfg.set(base + ".min", r.minCount());
            cfg.set(base + ".max", r.maxCount());
        }

        configFile.getParentFile().mkdirs();
        cfg.save(configFile);
    }

    @SuppressWarnings("unchecked")
    public void load() {
        if (!configFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        this.worldName = cfg.getString("world", worldName);
        try { this.mode = DungeonMode.valueOf(cfg.getString("mode", "MAP")); } catch (IllegalArgumentException e) { this.mode = DungeonMode.MAP; }
        this.bossMobId = cfg.getString("boss-mob-id", "AbyssOverlord");
        this.bossLevel = cfg.getInt("boss-level", 1);
        this.mobsBeforeBoss = cfg.getInt("mobs-before-boss", 30);
        this.timeLimitMinutes = cfg.getInt("time-limit-minutes", 20);
        this.maxConcurrentMobs = cfg.getInt("max-concurrent-mobs", 20);
        this.mobsPerWave = cfg.getInt("mobs-per-wave", 4);
        this.waveIntervalSeconds = cfg.getInt("wave-interval-seconds", 8);
        this.lives = cfg.getInt("lives", 3);
        this.keepInventory = cfg.getBoolean("keep-inventory", true);
        this.playerSpawn = readLoc(cfg.getConfigurationSection("player-spawn"));
        this.bossSpawn = readLoc(cfg.getConfigurationSection("boss-spawn"));

        spawnPoints.clear();
        List<java.util.Map<?, ?>> sps = cfg.getMapList("spawn-points");
        for (java.util.Map<?, ?> m : sps) {
            try {
                Location l = new Location(null,
                        ((Number) m.get("x")).doubleValue(),
                        ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(),
                        m.containsKey("yaw") ? ((Number) m.get("yaw")).floatValue() : 0f,
                        m.containsKey("pitch") ? ((Number) m.get("pitch")).floatValue() : 0f);
                SpawnPoint sp = new SpawnPoint(l);
                Object raw = m.get("mobs");
                if (raw instanceof List<?> list) {
                    for (Object o : list) {
                        MobEntry e = MobEntry.decode(String.valueOf(o));
                        if (e != null) sp.mobs().add(e);
                    }
                }
                spawnPoints.add(sp);
            } catch (Exception ignored) {}
        }

        defaultTrashMobs.clear();
        for (String s : cfg.getStringList("default-trash-mobs")) {
            MobEntry e = MobEntry.decode(s);
            if (e != null) defaultTrashMobs.add(e);
        }

        waves.clear();
        List<java.util.Map<?, ?>> wvs = cfg.getMapList("waves");
        for (java.util.Map<?, ?> m : wvs) {
            try {
                Wave w = new Wave();
                Object raw = m.get("mobs");
                if (raw instanceof List<?> list) {
                    for (Object o : list) {
                        MobEntry e = MobEntry.decode(String.valueOf(o));
                        if (e != null) w.mobs().add(e);
                    }
                }
                if (m.get("delay-after-seconds") instanceof Number n) w.setDelayAfterSeconds(n.intValue());
                if (m.get("boss-wave") instanceof Boolean b) w.setBossWave(b);
                waves.add(w);
            } catch (Exception ignored) {}
        }

        // Rewards
        this.maxRewardItems    = cfg.getInt("rewards.max-items", 3);
        this.moneyMin          = cfg.getDouble("rewards.money.min", 0);
        this.moneyMax          = cfg.getDouble("rewards.money.max", 0);
        this.moneyChancePercent= cfg.getDouble("rewards.money.chance", 100);
        this.xpLevelsMin       = cfg.getInt("rewards.xp.min", 0);
        this.xpLevelsMax       = cfg.getInt("rewards.xp.max", 0);
        this.xpChancePercent   = cfg.getDouble("rewards.xp.chance", 100);
        this.upgradeAttemptsPerClear = cfg.getInt("upgrade.attempts-per-clear", -1);

        rewardPool.clear();
        ConfigurationSection pool = cfg.getConfigurationSection("rewards.pool");
        if (pool != null) {
            // Keys are numeric (0, 1, 2, ...) — iterate in numeric order
            List<String> keys = new ArrayList<>(pool.getKeys(false));
            keys.sort((a, b) -> {
                try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            for (String key : keys) {
                ConfigurationSection entry = pool.getConfigurationSection(key);
                if (entry == null) continue;
                org.bukkit.inventory.ItemStack stack = entry.getItemStack("item");
                if (stack == null) continue;
                double chance = entry.getDouble("chance", 100);
                int min = entry.getInt("min", 1);
                int max = entry.getInt("max", 1);
                rewardPool.add(new RewardEntry(stack, chance, min, max));
            }
        }
    }

    private static void writeLoc(YamlConfiguration cfg, String path, Location l) {
        cfg.set(path + ".x", l.getX());
        cfg.set(path + ".y", l.getY());
        cfg.set(path + ".z", l.getZ());
        cfg.set(path + ".yaw", l.getYaw());
        cfg.set(path + ".pitch", l.getPitch());
    }

    private static Location readLoc(ConfigurationSection s) {
        if (s == null) return null;
        return new Location(null,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }
}
