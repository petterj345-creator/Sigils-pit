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
    /**
     * Where the upgrade altar + reward chest spawn after boss death.
     * If null, falls back to the boss death location (legacy behaviour).
     * Set via the editor wand in the template world.
     */
    private Location forgeSpawn;
    /**
     * Where the return portal block spawns after boss death.
     * If null, falls back to an offset from the forge location.
     */
    private Location exitPortalSpawn;

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

    // ----- map drops -----
    /**
     * Chance (0-100) that each mob killed in this dungeon drops a map for
     * this template. 0 = mobs never drop a map (admin-issued only).
     */
    private double mapDropChancePercent = 0.0;

    // ----- shared event anchors -----
    /**
     * Generic event-anchor blocks placed via the wand. Any event (ritual,
     * maelstrom, reliquary) that has NO type-specific markers placed spawns on a
     * random subset of these instead — so an admin can place one shared pool of
     * blocks once rather than separate markers per event type. A type-specific
     * marker list, if non-empty, overrides this pool for that event. Stored
     * "stand on top".
     */
    private final List<Location> eventBlocks = new ArrayList<>();

    /**
     * Shared trash pool that ALL events (ritual, maelstrom, reliquary) draw
     * from. Separate from {@link #defaultTrashMobs}, which feeds the normal
     * MAP-mode dungeon spawner. Set once per map via Events → Event Trash Mobs;
     * each event decides how many to pull — a per-event count for ritual and
     * reliquary, or a continuous toggle for the maelstrom.
     */
    private final List<MobEntry> eventTrashMobs = new ArrayList<>();

    // ----- ritual (Altar of Souls) -----
    /** Altar locations (armor stands) placed via the wand. Stored "stand on top". */
    private final List<Location> ritualAltars = new ArrayList<>();
    /**
     * How many of the placed altars actually spawn each run, chosen at random
     * so the map feels different every play. This is the UPPER bound of the
     * range; {@link #ritualAltarsActiveMin} is the lower bound. 0 = use all.
     */
    private int ritualAltarsActive = 0;
    /** Lower bound of the random active-altar range (defaults to the max). */
    private int ritualAltarsActiveMin = 0;
    /** Mobs each ritual summons (count = how many of each). Don't count toward boss. */
    private final List<MobEntry> ritualMobs = new ArrayList<>();
    /**
     * Random mobs pulled from {@link #eventTrashMobs} each ritual, spawned on
     * TOP of {@link #ritualMobs}. 0 = targeted mobs only.
     */
    private int ritualTrashCount = 0;
    /** Souls granted per MythicMob id when killed during a ritual, as {min,max}. */
    private final java.util.Map<String, int[]> ritualMobSouls = new java.util.LinkedHashMap<>();
    /** Purchasable items in the soul shop. */
    private final List<RitualReward> ritualRewardPool = new ArrayList<>();
    /** How many items the shop rolls (range). */
    private int ritualItemsMin = 3;
    private int ritualItemsMax = 5;
    /** Global soul price range for rolled items (used when an item inherits). */
    private int ritualPriceMin = 10;
    private int ritualPriceMax = 50;
    /** Soul cost to reroll the shop offers. 0 = reroll disabled. */
    private int ritualRerollCost = 25;
    /** Optional single cash reward sold in the shop (pays money via Vault). */
    private boolean ritualCashEnabled = false;
    private double ritualCashMoneyMin = 0;
    private double ritualCashMoneyMax = 0;
    private int ritualCashPriceMin = 50;
    private int ritualCashPriceMax = 100;
    /** Reserve (layaway) settings. */
    private int ritualReserveDepositPct = 10;
    private int ritualReserveDiscountPct = 10;
    private int ritualReserveLimit = 3;

    // ----- maelstrom (rift event) -----
    /** Rift marker locations placed via the wand. Stored "stand on top". */
    private final List<Location> maelstromCenters = new ArrayList<>();
    /** How many of the placed rifts actually open per run — upper bound (0 = all). */
    private int maelstromActive = 0;
    /** Lower bound of the random active-rift range (defaults to the max). */
    private int maelstromActiveMin = 0;
    /** Specific mobs the rift spawns. Empty + useTrash = use the dungeon trash list. */
    private final List<MobEntry> maelstromMobs = new ArrayList<>();
    /** If true, also draw from the template's default trash mobs (fast filler). */
    private boolean maelstromUseTrash = false;
    /** How long (seconds) a rift spawns before it collapses. */
    private int maelstromDurationSeconds = 45;
    /** Ticks between spawn pulses while a rift is open. */
    private int maelstromSpawnIntervalTicks = 10;
    /** Mobs spawned per pulse. */
    private int maelstromMobsPerPulse = 4;
    /** Radius (blocks) of the spawn circle. */
    private int maelstromRadius = 6;
    /** Cap on rift mobs alive at once, so it stays clearable. */
    private int maelstromMaxAlive = 30;
    /** Loot pool dropped when the rift collapses (gated by kill thresholds). */
    private final List<MaelstromLoot> maelstromLoot = new ArrayList<>();
    /** Max distinct loot items rolled into the collapse chest. */
    private int maelstromMaxLootItems = 5;

    // ----- reliquary (sealed guarded chest — "open, clear guards, loot") -----
    /** Reliquary marker locations placed via the wand. Stored "stand on top". */
    private final List<Location> reliquaryCenters = new ArrayList<>();
    /** How many of the placed reliquaries unseal per run — upper bound (0 = all). */
    private int reliquaryActive = 0;
    /** Lower bound of the random active-reliquary range (defaults to the max). */
    private int reliquaryActiveMin = 0;
    /** Guardian pack summoned when a reliquary is unsealed (count = how many of each). */
    private final List<MobEntry> reliquaryGuards = new ArrayList<>();
    /**
     * Random mobs pulled from {@link #eventTrashMobs} each reliquary, added to
     * the targeted guardian pack. 0 = targeted guardians only.
     */
    private int reliquaryTrashCount = 0;
    /** Loot pool dropped when the reliquary cracks open (gated by guardians slain). */
    private final List<MaelstromLoot> reliquaryLoot = new ArrayList<>();
    /** Max distinct loot items rolled into the cracked-open cache. */
    private int reliquaryMaxLootItems = 5;

    // ----- the sundering (expedition-style: plant charges, detonate, loot) -----
    /**
     * Wares stocked by the Sundered Hoard vendor. Its OWN pool, separate from the
     * maelstrom/reliquary pools. Reuses {@link MaelstromLoot} (item + chance% +
     * count range), but the vendor prices items by their chance% (rarer = pricier)
     * rather than rolling them into a cache; {@code minKills} is unused here.
     */
    private final List<MaelstromLoot> sunderingLoot = new ArrayList<>();

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
    public Location forgeSpawn()      { return forgeSpawn == null ? null : forgeSpawn.clone(); }
    public Location exitPortalSpawn() { return exitPortalSpawn == null ? null : exitPortalSpawn.clone(); }
    public List<SpawnPoint> spawnPoints()     { return spawnPoints; }
    public List<MobEntry> defaultTrashMobs()  { return defaultTrashMobs; }
    public List<MobEntry> eventTrashMobs()    { return eventTrashMobs; }
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
    public double mapDropChancePercent()   { return mapDropChancePercent; }

    // Ritual getters
    public List<Location> ritualAltars()              { return ritualAltars; }
    public int ritualAltarsActive()                   { return ritualAltarsActive; }
    public int ritualAltarsActiveMin()                { return ritualAltarsActiveMin; }
    public List<MobEntry> ritualMobs()                { return ritualMobs; }
    public int ritualTrashCount()                     { return ritualTrashCount; }
    public java.util.Map<String,int[]> ritualMobSouls() { return ritualMobSouls; }
    public List<RitualReward> ritualRewardPool()      { return ritualRewardPool; }
    public int ritualItemsMin()                       { return ritualItemsMin; }
    public int ritualItemsMax()                       { return ritualItemsMax; }
    public int ritualPriceMin()                       { return ritualPriceMin; }
    public int ritualPriceMax()                       { return ritualPriceMax; }
    public int ritualRerollCost()                     { return ritualRerollCost; }
    public boolean ritualCashEnabled()                { return ritualCashEnabled; }
    public double ritualCashMoneyMin()                { return ritualCashMoneyMin; }
    public double ritualCashMoneyMax()                { return ritualCashMoneyMax; }
    public int ritualCashPriceMin()                   { return ritualCashPriceMin; }
    public int ritualCashPriceMax()                   { return ritualCashPriceMax; }
    public int ritualReserveDepositPct()              { return ritualReserveDepositPct; }
    public int ritualReserveDiscountPct()             { return ritualReserveDiscountPct; }
    public int ritualReserveLimit()                   { return ritualReserveLimit; }
    /** Soul range for a mythic id as {min,max}; {0,0} if unset. */
    public int[] soulsRange(String mythicId)          { return ritualMobSouls.getOrDefault(mythicId, new int[]{0, 0}); }
    /** Roll a random soul amount for killing the given mythic id (0 if unset). */
    public int rollSoulsFor(String mythicId) {
        int[] r = ritualMobSouls.get(mythicId);
        if (r == null || r[1] <= 0) return 0;
        int min = Math.max(0, r[0]), max = Math.max(min, r[1]);
        return min + (max > min ? java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1) : 0);
    }
    /** True if this template has a usable ritual setup (anchors + some mob source). */
    public boolean hasRitual() {
        return !ritualSpawnAnchors().isEmpty()
                && (!ritualMobs.isEmpty() || (ritualTrashCount > 0 && !eventTrashMobs.isEmpty()));
    }

    // Shared event anchors
    public List<Location> eventBlocks() { return eventBlocks; }
    /** Where rituals spawn: type-specific altars if placed, else shared event blocks. */
    public List<Location> ritualSpawnAnchors()    { return ritualAltars.isEmpty()     ? eventBlocks : ritualAltars; }
    /** Where maelstroms open: type-specific rifts if placed, else shared event blocks. */
    public List<Location> maelstromSpawnAnchors() { return maelstromCenters.isEmpty()  ? eventBlocks : maelstromCenters; }
    /** Where reliquaries spawn: type-specific markers if placed, else shared event blocks. */
    public List<Location> reliquarySpawnAnchors() { return reliquaryCenters.isEmpty()  ? eventBlocks : reliquaryCenters; }

    /**
     * Roll {@code n} random picks from the shared event-trash pool. Each pick is
     * a single mob (the entry's count is ignored — the pool acts as a weighted
     * bag), spawned at the entry's level. Returns an empty list when no trash is
     * configured or {@code n <= 0}.
     */
    public List<MobEntry> rollEventTrash(int n) {
        List<MobEntry> out = new ArrayList<>();
        if (n <= 0 || eventTrashMobs.isEmpty()) return out;
        var rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) out.add(eventTrashMobs.get(rng.nextInt(eventTrashMobs.size())));
        return out;
    }

    // Maelstrom getters
    public List<Location> maelstromCenters()  { return maelstromCenters; }
    public int maelstromActive()              { return maelstromActive; }
    public int maelstromActiveMin()           { return maelstromActiveMin; }
    public List<MobEntry> maelstromMobs()     { return maelstromMobs; }
    public boolean maelstromUseTrash()        { return maelstromUseTrash; }
    public int maelstromDurationSeconds()     { return maelstromDurationSeconds; }
    public int maelstromSpawnIntervalTicks()  { return maelstromSpawnIntervalTicks; }
    public int maelstromMobsPerPulse()        { return maelstromMobsPerPulse; }
    public int maelstromRadius()              { return maelstromRadius; }
    public int maelstromMaxAlive()            { return maelstromMaxAlive; }
    public List<MaelstromLoot> maelstromLoot() { return maelstromLoot; }
    public int maelstromMaxLootItems()        { return maelstromMaxLootItems; }
    /** True if this template has a usable maelstrom (an anchor + some mob source). */
    public boolean hasMaelstrom() {
        return !maelstromSpawnAnchors().isEmpty()
                && (!maelstromMobs.isEmpty() || (maelstromUseTrash && !eventTrashMobs.isEmpty()));
    }

    // Reliquary getters
    public List<Location> reliquaryCenters()  { return reliquaryCenters; }
    public int reliquaryActive()              { return reliquaryActive; }
    public int reliquaryActiveMin()           { return reliquaryActiveMin; }
    public List<MobEntry> reliquaryGuards()   { return reliquaryGuards; }
    public int reliquaryTrashCount()          { return reliquaryTrashCount; }
    public List<MaelstromLoot> reliquaryLoot() { return reliquaryLoot; }
    public int reliquaryMaxLootItems()        { return reliquaryMaxLootItems; }
    /** True if this template has a usable reliquary (an anchor + some guard source). */
    public boolean hasReliquary() {
        return !reliquarySpawnAnchors().isEmpty()
                && (!reliquaryGuards.isEmpty() || (reliquaryTrashCount > 0 && !eventTrashMobs.isEmpty()));
    }

    // Sundering getters
    public List<MaelstromLoot> sunderingLoot() { return sunderingLoot; }

    // ----- setters -----
    public void setMode(DungeonMode m)             { this.mode = m; }
    public void setPlayerSpawn(Location loc)       { this.playerSpawn = wipeWorld(loc); }
    public void setBossSpawn(Location loc)         { this.bossSpawn = wipeWorld(loc); }
    public void setForgeSpawn(Location loc)        { this.forgeSpawn = wipeWorld(loc); }
    public void setExitPortalSpawn(Location loc)   { this.exitPortalSpawn = wipeWorld(loc); }
    public void clearPlayerSpawn()                 { this.playerSpawn = null; }
    public void clearBossSpawn()                   { this.bossSpawn = null; }
    public void clearForgeSpawn()                  { this.forgeSpawn = null; }
    public void clearExitPortalSpawn()             { this.exitPortalSpawn = null; }
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
    public void setMapDropChancePercent(double p)  { this.mapDropChancePercent = clampPct(p); }

    // Shared event-anchor setters
    public void addEventBlock(Location loc) { eventBlocks.add(wipeWorld(loc)); }
    public void clearEventBlocks()          { eventBlocks.clear(); }
    /** Remove any shared event block at the given block (stand-on-top match). Returns count removed. */
    public int removeEventBlockAt(int bx, int by, int bz) {
        int removed = 0;
        for (int i = eventBlocks.size() - 1; i >= 0; i--) {
            Location l = eventBlocks.get(i);
            int lx = (int) Math.floor(l.getX());
            int ly = (int) Math.floor(l.getY());
            int lz = (int) Math.floor(l.getZ());
            if (lx == bx && lz == bz && (ly - 1 == by || ly == by)) {
                eventBlocks.remove(i); removed++;
            }
        }
        return removed;
    }

    // Ritual setters
    public void addRitualAltar(Location loc)  { ritualAltars.add(wipeWorld(loc)); }
    public void clearRitualAltars()           { ritualAltars.clear(); }
    public void setRitualAltarsActive(int n)  { this.ritualAltarsActive = Math.max(0, n); }
    public void setRitualAltarsActiveMin(int n) { this.ritualAltarsActiveMin = Math.max(0, n); }
    public void setRitualTrashCount(int n)    { this.ritualTrashCount = Math.max(0, n); }
    public void setRitualMobSouls(String id, int min, int max) {
        if (max <= 0) { ritualMobSouls.remove(id); return; }
        int lo = Math.max(0, min), hi = Math.max(lo, max);
        ritualMobSouls.put(id, new int[]{lo, hi});
    }
    public void setRitualItemsMin(int n) { this.ritualItemsMin = Math.max(0, n); if (ritualItemsMax < ritualItemsMin) ritualItemsMax = ritualItemsMin; }
    public void setRitualItemsMax(int n) { this.ritualItemsMax = Math.max(0, n); if (ritualItemsMin > ritualItemsMax) ritualItemsMin = ritualItemsMax; }
    public void setRitualPriceMin(int n) { this.ritualPriceMin = Math.max(0, n); if (ritualPriceMax < ritualPriceMin) ritualPriceMax = ritualPriceMin; }
    public void setRitualPriceMax(int n) { this.ritualPriceMax = Math.max(0, n); if (ritualPriceMin > ritualPriceMax) ritualPriceMin = ritualPriceMax; }
    public void setRitualRerollCost(int n) { this.ritualRerollCost = Math.max(0, n); }
    public void setRitualCashEnabled(boolean b) { this.ritualCashEnabled = b; }
    public void setRitualCashMoneyMin(double v) { this.ritualCashMoneyMin = Math.max(0, v); if (ritualCashMoneyMax < ritualCashMoneyMin) ritualCashMoneyMax = ritualCashMoneyMin; }
    public void setRitualCashMoneyMax(double v) { this.ritualCashMoneyMax = Math.max(0, v); if (ritualCashMoneyMin > ritualCashMoneyMax) ritualCashMoneyMin = ritualCashMoneyMax; }
    public void setRitualCashPriceMin(int n) { this.ritualCashPriceMin = Math.max(0, n); if (ritualCashPriceMax < ritualCashPriceMin) ritualCashPriceMax = ritualCashPriceMin; }
    public void setRitualCashPriceMax(int n) { this.ritualCashPriceMax = Math.max(0, n); if (ritualCashPriceMin > ritualCashPriceMax) ritualCashPriceMin = ritualCashPriceMax; }
    public void setRitualReserveDepositPct(int n) { this.ritualReserveDepositPct = Math.max(0, Math.min(100, n)); }
    public void setRitualReserveDiscountPct(int n) { this.ritualReserveDiscountPct = Math.max(0, Math.min(100, n)); }
    public void setRitualReserveLimit(int n) { this.ritualReserveLimit = Math.max(0, n); }

    // Maelstrom setters
    public void addMaelstromCenter(Location loc)      { maelstromCenters.add(wipeWorld(loc)); }
    public void setMaelstromActive(int n)             { this.maelstromActive = Math.max(0, n); }
    public void setMaelstromActiveMin(int n)          { this.maelstromActiveMin = Math.max(0, n); }
    public void setMaelstromUseTrash(boolean b)       { this.maelstromUseTrash = b; }
    public void setMaelstromDurationSeconds(int n)    { this.maelstromDurationSeconds = Math.max(1, n); }
    public void setMaelstromSpawnIntervalTicks(int n) { this.maelstromSpawnIntervalTicks = Math.max(1, n); }
    public void setMaelstromMobsPerPulse(int n)       { this.maelstromMobsPerPulse = Math.max(1, n); }
    public void setMaelstromRadius(int n)             { this.maelstromRadius = Math.max(1, n); }
    public void setMaelstromMaxAlive(int n)           { this.maelstromMaxAlive = Math.max(1, n); }
    public void setMaelstromMaxLootItems(int n)       { this.maelstromMaxLootItems = Math.max(0, n); }

    // Reliquary setters
    public void addReliquaryCenter(Location loc)      { reliquaryCenters.add(wipeWorld(loc)); }
    public void setReliquaryActive(int n)             { this.reliquaryActive = Math.max(0, n); }
    public void setReliquaryActiveMin(int n)          { this.reliquaryActiveMin = Math.max(0, n); }
    public void setReliquaryTrashCount(int n)         { this.reliquaryTrashCount = Math.max(0, n); }
    public void setReliquaryMaxLootItems(int n)       { this.reliquaryMaxLootItems = Math.max(0, n); }
    /** Remove any reliquary marker at the given block (stand-on-top match). Returns count removed. */
    public int removeReliquaryCenterAt(int bx, int by, int bz) {
        int removed = 0;
        for (int i = reliquaryCenters.size() - 1; i >= 0; i--) {
            Location l = reliquaryCenters.get(i);
            int lx = (int) Math.floor(l.getX());
            int ly = (int) Math.floor(l.getY());
            int lz = (int) Math.floor(l.getZ());
            if (lx == bx && lz == bz && (ly - 1 == by || ly == by)) {
                reliquaryCenters.remove(i); removed++;
            }
        }
        return removed;
    }
    /** Remove any maelstrom rift marker at the given block (stand-on-top match). Returns count removed. */
    public int removeMaelstromCenterAt(int bx, int by, int bz) {
        int removed = 0;
        for (int i = maelstromCenters.size() - 1; i >= 0; i--) {
            Location l = maelstromCenters.get(i);
            int lx = (int) Math.floor(l.getX());
            int ly = (int) Math.floor(l.getY());
            int lz = (int) Math.floor(l.getZ());
            if (lx == bx && lz == bz && (ly - 1 == by || ly == by)) {
                maelstromCenters.remove(i); removed++;
            }
        }
        return removed;
    }
    /** Remove any ritual altar marker at the given block (stand-on-top match). Returns count removed. */
    public int removeRitualAltarAt(int bx, int by, int bz) {
        int removed = 0;
        for (int i = ritualAltars.size() - 1; i >= 0; i--) {
            Location l = ritualAltars.get(i);
            int lx = (int) Math.floor(l.getX());
            int ly = (int) Math.floor(l.getY());
            int lz = (int) Math.floor(l.getZ());
            if (lx == bx && lz == bz && (ly - 1 == by || ly == by)) {
                ritualAltars.remove(i); removed++;
            }
        }
        return removed;
    }

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
        if (forgeSpawn  != null) writeLoc(cfg, "forge-spawn",  forgeSpawn);
        if (exitPortalSpawn != null) writeLoc(cfg, "exit-portal-spawn", exitPortalSpawn);

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

        // Shared event-trash list (all events draw from this)
        List<String> eventTrashStrs = new ArrayList<>();
        for (MobEntry e : eventTrashMobs) eventTrashStrs.add(e.encode());
        cfg.set("event-trash-mobs", eventTrashStrs);

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
        cfg.set("map.drop-chance-percent", mapDropChancePercent);
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
            if (r.isMMOItem()) {
                cfg.set(base + ".mmo-type", r.mmoType());
                cfg.set(base + ".mmo-id", r.mmoId());
            }
        }

        // ----- shared event anchors -----
        List<java.util.Map<String, Object>> eventBlockList = new ArrayList<>();
        for (Location l : eventBlocks) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", l.getX()); m.put("y", l.getY()); m.put("z", l.getZ());
            m.put("yaw", l.getYaw()); m.put("pitch", l.getPitch());
            eventBlockList.add(m);
        }
        cfg.set("event-blocks", eventBlockList);

        // ----- ritual -----
        cfg.set("ritual.items-min", ritualItemsMin);
        cfg.set("ritual.items-max", ritualItemsMax);
        cfg.set("ritual.price-min", ritualPriceMin);
        cfg.set("ritual.price-max", ritualPriceMax);
        cfg.set("ritual.reroll-cost", ritualRerollCost);
        cfg.set("ritual.cash.enabled", ritualCashEnabled);
        cfg.set("ritual.cash.money-min", ritualCashMoneyMin);
        cfg.set("ritual.cash.money-max", ritualCashMoneyMax);
        cfg.set("ritual.cash.price-min", ritualCashPriceMin);
        cfg.set("ritual.cash.price-max", ritualCashPriceMax);
        cfg.set("ritual.reserve.deposit-pct", ritualReserveDepositPct);
        cfg.set("ritual.reserve.discount-pct", ritualReserveDiscountPct);
        cfg.set("ritual.reserve.limit", ritualReserveLimit);
        cfg.set("ritual.altars-active", ritualAltarsActive);
        cfg.set("ritual.altars-active-min", ritualAltarsActiveMin);
        cfg.set("ritual.trash-count", ritualTrashCount);

        List<java.util.Map<String, Object>> altars = new ArrayList<>();
        for (Location l : ritualAltars) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", l.getX()); m.put("y", l.getY()); m.put("z", l.getZ());
            m.put("yaw", l.getYaw()); m.put("pitch", l.getPitch());
            altars.add(m);
        }
        cfg.set("ritual.altars", altars);

        List<String> ritualMobStrs = new ArrayList<>();
        for (MobEntry e : ritualMobs) ritualMobStrs.add(e.encode());
        cfg.set("ritual.mobs", ritualMobStrs);

        cfg.set("ritual.mob-souls", null); // clear stale
        for (java.util.Map.Entry<String, int[]> en : ritualMobSouls.entrySet()) {
            int[] r = en.getValue();
            cfg.set("ritual.mob-souls." + en.getKey(), r[0] + "-" + r[1]);
        }

        cfg.set("ritual.rewards.pool", null); // clear stale
        for (int i = 0; i < ritualRewardPool.size(); i++) {
            RitualReward r = ritualRewardPool.get(i);
            if (r.itemStack() == null) continue;
            String base = "ritual.rewards.pool." + i;
            cfg.set(base + ".item", r.itemStack());
            cfg.set(base + ".price-min", r.priceMin());
            cfg.set(base + ".price-max", r.priceMax());
            cfg.set(base + ".amount", r.amount());
            if (r.isMMOItem()) {
                cfg.set(base + ".mmo-type", r.mmoType());
                cfg.set(base + ".mmo-id", r.mmoId());
            }
        }

        // ----- maelstrom -----
        cfg.set("maelstrom.active", maelstromActive);
        cfg.set("maelstrom.active-min", maelstromActiveMin);
        cfg.set("maelstrom.use-trash", maelstromUseTrash);
        cfg.set("maelstrom.duration-seconds", maelstromDurationSeconds);
        cfg.set("maelstrom.spawn-interval-ticks", maelstromSpawnIntervalTicks);
        cfg.set("maelstrom.mobs-per-pulse", maelstromMobsPerPulse);
        cfg.set("maelstrom.radius", maelstromRadius);
        cfg.set("maelstrom.max-alive", maelstromMaxAlive);
        cfg.set("maelstrom.max-loot-items", maelstromMaxLootItems);

        List<java.util.Map<String, Object>> rifts = new ArrayList<>();
        for (Location l : maelstromCenters) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", l.getX()); m.put("y", l.getY()); m.put("z", l.getZ());
            m.put("yaw", l.getYaw()); m.put("pitch", l.getPitch());
            rifts.add(m);
        }
        cfg.set("maelstrom.centers", rifts);

        List<String> maelstromMobStrs = new ArrayList<>();
        for (MobEntry e : maelstromMobs) maelstromMobStrs.add(e.encode());
        cfg.set("maelstrom.mobs", maelstromMobStrs);

        cfg.set("maelstrom.loot", null); // clear stale
        for (int i = 0; i < maelstromLoot.size(); i++) {
            MaelstromLoot r = maelstromLoot.get(i);
            if (r.itemStack() == null) continue;
            String base = "maelstrom.loot." + i;
            cfg.set(base + ".item", r.itemStack());
            cfg.set(base + ".chance", r.chancePercent());
            cfg.set(base + ".min", r.minCount());
            cfg.set(base + ".max", r.maxCount());
            cfg.set(base + ".min-kills", r.minKills());
            if (r.isMMOItem()) {
                cfg.set(base + ".mmo-type", r.mmoType());
                cfg.set(base + ".mmo-id", r.mmoId());
            }
        }

        // ----- reliquary -----
        cfg.set("reliquary.active", reliquaryActive);
        cfg.set("reliquary.active-min", reliquaryActiveMin);
        cfg.set("reliquary.trash-count", reliquaryTrashCount);
        cfg.set("reliquary.max-loot-items", reliquaryMaxLootItems);

        List<java.util.Map<String, Object>> reliquaries = new ArrayList<>();
        for (Location l : reliquaryCenters) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", l.getX()); m.put("y", l.getY()); m.put("z", l.getZ());
            m.put("yaw", l.getYaw()); m.put("pitch", l.getPitch());
            reliquaries.add(m);
        }
        cfg.set("reliquary.centers", reliquaries);

        List<String> reliquaryGuardStrs = new ArrayList<>();
        for (MobEntry e : reliquaryGuards) reliquaryGuardStrs.add(e.encode());
        cfg.set("reliquary.guards", reliquaryGuardStrs);

        cfg.set("reliquary.loot", null); // clear stale
        for (int i = 0; i < reliquaryLoot.size(); i++) {
            MaelstromLoot r = reliquaryLoot.get(i);
            if (r.itemStack() == null) continue;
            String base = "reliquary.loot." + i;
            cfg.set(base + ".item", r.itemStack());
            cfg.set(base + ".chance", r.chancePercent());
            cfg.set(base + ".min", r.minCount());
            cfg.set(base + ".max", r.maxCount());
            cfg.set(base + ".min-kills", r.minKills());
            if (r.isMMOItem()) {
                cfg.set(base + ".mmo-type", r.mmoType());
                cfg.set(base + ".mmo-id", r.mmoId());
            }
        }

        // ----- the sundering -----
        cfg.set("sundering.loot", null); // clear stale
        for (int i = 0; i < sunderingLoot.size(); i++) {
            MaelstromLoot r = sunderingLoot.get(i);
            if (r.itemStack() == null) continue;
            String base = "sundering.loot." + i;
            cfg.set(base + ".item", r.itemStack());
            cfg.set(base + ".chance", r.chancePercent());
            cfg.set(base + ".min", r.minCount());
            cfg.set(base + ".max", r.maxCount());
            cfg.set(base + ".min-kills", r.minKills());
            if (r.isMMOItem()) {
                cfg.set(base + ".mmo-type", r.mmoType());
                cfg.set(base + ".mmo-id", r.mmoId());
            }
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
        this.forgeSpawn = readLoc(cfg.getConfigurationSection("forge-spawn"));
        this.exitPortalSpawn = readLoc(cfg.getConfigurationSection("exit-portal-spawn"));

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

        eventTrashMobs.clear();
        for (String s : cfg.getStringList("event-trash-mobs")) {
            MobEntry e = MobEntry.decode(s);
            if (e != null) eventTrashMobs.add(e);
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
        this.mapDropChancePercent = cfg.getDouble("map.drop-chance-percent", 0.0);

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
                RewardEntry re = new RewardEntry(stack, chance, min, max);
                String mmoType = entry.getString("mmo-type", null);
                String mmoId = entry.getString("mmo-id", null);
                if (mmoType != null && mmoId != null) re.setMMOItem(mmoType, mmoId);
                rewardPool.add(re);
            }
        }

        // ----- shared event anchors -----
        eventBlocks.clear();
        for (java.util.Map<?, ?> m : cfg.getMapList("event-blocks")) {
            try {
                eventBlocks.add(new Location(null,
                        ((Number) m.get("x")).doubleValue(),
                        ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(),
                        m.containsKey("yaw") ? ((Number) m.get("yaw")).floatValue() : 0f,
                        m.containsKey("pitch") ? ((Number) m.get("pitch")).floatValue() : 0f));
            } catch (Exception ignored) {}
        }

        // ----- ritual -----
        this.ritualItemsMin = cfg.getInt("ritual.items-min", 3);
        this.ritualItemsMax = cfg.getInt("ritual.items-max", 5);
        this.ritualPriceMin = cfg.getInt("ritual.price-min", 10);
        this.ritualPriceMax = cfg.getInt("ritual.price-max", 50);
        this.ritualRerollCost = cfg.getInt("ritual.reroll-cost", 25);

        this.ritualAltarsActive = cfg.getInt("ritual.altars-active", 0);
        // Back-compat: a template saved before the range existed has no min key,
        // so default min to the max → behaves exactly as before (deterministic).
        this.ritualAltarsActiveMin = cfg.getInt("ritual.altars-active-min", ritualAltarsActive);
        this.ritualTrashCount = cfg.getInt("ritual.trash-count", 0);

        ritualAltars.clear();
        for (java.util.Map<?, ?> m : cfg.getMapList("ritual.altars")) {
            try {
                ritualAltars.add(new Location(null,
                        ((Number) m.get("x")).doubleValue(),
                        ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(),
                        m.containsKey("yaw") ? ((Number) m.get("yaw")).floatValue() : 0f,
                        m.containsKey("pitch") ? ((Number) m.get("pitch")).floatValue() : 0f));
            } catch (Exception ignored) {}
        }

        ritualMobs.clear();
        for (String s : cfg.getStringList("ritual.mobs")) {
            MobEntry e = MobEntry.decode(s);
            if (e != null) ritualMobs.add(e);
        }

        ritualMobSouls.clear();
        ConfigurationSection souls = cfg.getConfigurationSection("ritual.mob-souls");
        if (souls != null) {
            for (String key : souls.getKeys(false)) {
                int[] r = parseSoulRange(souls.get(key));
                if (r != null && r[1] > 0) ritualMobSouls.put(key, r);
            }
        }

        ritualRewardPool.clear();
        ConfigurationSection rpool = cfg.getConfigurationSection("ritual.rewards.pool");
        if (rpool != null) {
            List<String> keys = new ArrayList<>(rpool.getKeys(false));
            keys.sort((a, b) -> {
                try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            for (String key : keys) {
                ConfigurationSection entry = rpool.getConfigurationSection(key);
                if (entry == null) continue;
                org.bukkit.inventory.ItemStack stack = entry.getItemStack("item");
                if (stack == null) continue;
                int pmin = entry.getInt("price-min", -1);
                int pmax = entry.getInt("price-max", -1);
                RitualReward rr = new RitualReward(stack, pmin, pmax);
                rr.setAmount(entry.getInt("amount", 1));
                String mmoType = entry.getString("mmo-type", null);
                String mmoId = entry.getString("mmo-id", null);
                if (mmoType != null && mmoId != null) rr.setMMOItem(mmoType, mmoId);
                ritualRewardPool.add(rr);
            }
        }

        // Cash reward + reserve settings
        this.ritualCashEnabled  = cfg.getBoolean("ritual.cash.enabled", false);
        this.ritualCashMoneyMin = cfg.getDouble("ritual.cash.money-min", 0);
        this.ritualCashMoneyMax = cfg.getDouble("ritual.cash.money-max", 0);
        this.ritualCashPriceMin = cfg.getInt("ritual.cash.price-min", 50);
        this.ritualCashPriceMax = cfg.getInt("ritual.cash.price-max", 100);
        this.ritualReserveDepositPct  = cfg.getInt("ritual.reserve.deposit-pct", 10);
        this.ritualReserveDiscountPct = cfg.getInt("ritual.reserve.discount-pct", 10);
        this.ritualReserveLimit       = cfg.getInt("ritual.reserve.limit", 3);

        // ----- maelstrom -----
        this.maelstromActive             = cfg.getInt("maelstrom.active", 0);
        this.maelstromActiveMin          = cfg.getInt("maelstrom.active-min", maelstromActive);
        this.maelstromUseTrash           = cfg.getBoolean("maelstrom.use-trash", false);
        this.maelstromDurationSeconds    = cfg.getInt("maelstrom.duration-seconds", 45);
        this.maelstromSpawnIntervalTicks = cfg.getInt("maelstrom.spawn-interval-ticks", 10);
        this.maelstromMobsPerPulse       = cfg.getInt("maelstrom.mobs-per-pulse", 4);
        this.maelstromRadius             = cfg.getInt("maelstrom.radius", 6);
        this.maelstromMaxAlive           = cfg.getInt("maelstrom.max-alive", 30);
        this.maelstromMaxLootItems       = cfg.getInt("maelstrom.max-loot-items", 5);

        maelstromCenters.clear();
        for (java.util.Map<?, ?> m : cfg.getMapList("maelstrom.centers")) {
            try {
                maelstromCenters.add(new Location(null,
                        ((Number) m.get("x")).doubleValue(),
                        ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(),
                        m.containsKey("yaw") ? ((Number) m.get("yaw")).floatValue() : 0f,
                        m.containsKey("pitch") ? ((Number) m.get("pitch")).floatValue() : 0f));
            } catch (Exception ignored) {}
        }

        maelstromMobs.clear();
        for (String s : cfg.getStringList("maelstrom.mobs")) {
            MobEntry e = MobEntry.decode(s);
            if (e != null) maelstromMobs.add(e);
        }

        maelstromLoot.clear();
        ConfigurationSection mloot = cfg.getConfigurationSection("maelstrom.loot");
        if (mloot != null) {
            List<String> keys = new ArrayList<>(mloot.getKeys(false));
            keys.sort((a, b) -> {
                try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            for (String key : keys) {
                ConfigurationSection entry = mloot.getConfigurationSection(key);
                if (entry == null) continue;
                org.bukkit.inventory.ItemStack stack = entry.getItemStack("item");
                if (stack == null) continue;
                MaelstromLoot ml = new MaelstromLoot(stack,
                        entry.getDouble("chance", 100),
                        entry.getInt("min", 1),
                        entry.getInt("max", 1),
                        entry.getInt("min-kills", 0));
                String mmoType = entry.getString("mmo-type", null);
                String mmoId = entry.getString("mmo-id", null);
                if (mmoType != null && mmoId != null) ml.setMMOItem(mmoType, mmoId);
                maelstromLoot.add(ml);
            }
        }

        // ----- reliquary -----
        this.reliquaryActive       = cfg.getInt("reliquary.active", 0);
        this.reliquaryActiveMin    = cfg.getInt("reliquary.active-min", reliquaryActive);
        this.reliquaryTrashCount   = cfg.getInt("reliquary.trash-count", 0);
        this.reliquaryMaxLootItems = cfg.getInt("reliquary.max-loot-items", 5);

        reliquaryCenters.clear();
        for (java.util.Map<?, ?> m : cfg.getMapList("reliquary.centers")) {
            try {
                reliquaryCenters.add(new Location(null,
                        ((Number) m.get("x")).doubleValue(),
                        ((Number) m.get("y")).doubleValue(),
                        ((Number) m.get("z")).doubleValue(),
                        m.containsKey("yaw") ? ((Number) m.get("yaw")).floatValue() : 0f,
                        m.containsKey("pitch") ? ((Number) m.get("pitch")).floatValue() : 0f));
            } catch (Exception ignored) {}
        }

        reliquaryGuards.clear();
        for (String s : cfg.getStringList("reliquary.guards")) {
            MobEntry e = MobEntry.decode(s);
            if (e != null) reliquaryGuards.add(e);
        }

        reliquaryLoot.clear();
        ConfigurationSection rloot = cfg.getConfigurationSection("reliquary.loot");
        if (rloot != null) {
            List<String> keys = new ArrayList<>(rloot.getKeys(false));
            keys.sort((a, b) -> {
                try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            for (String key : keys) {
                ConfigurationSection entry = rloot.getConfigurationSection(key);
                if (entry == null) continue;
                org.bukkit.inventory.ItemStack stack = entry.getItemStack("item");
                if (stack == null) continue;
                MaelstromLoot ml = new MaelstromLoot(stack,
                        entry.getDouble("chance", 100),
                        entry.getInt("min", 1),
                        entry.getInt("max", 1),
                        entry.getInt("min-kills", 0));
                String mmoType = entry.getString("mmo-type", null);
                String mmoId = entry.getString("mmo-id", null);
                if (mmoType != null && mmoId != null) ml.setMMOItem(mmoType, mmoId);
                reliquaryLoot.add(ml);
            }
        }

        // ----- the sundering -----
        sunderingLoot.clear();
        ConfigurationSection sloot = cfg.getConfigurationSection("sundering.loot");
        if (sloot != null) {
            List<String> keys = new ArrayList<>(sloot.getKeys(false));
            keys.sort((a, b) -> {
                try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            for (String key : keys) {
                ConfigurationSection entry = sloot.getConfigurationSection(key);
                if (entry == null) continue;
                org.bukkit.inventory.ItemStack stack = entry.getItemStack("item");
                if (stack == null) continue;
                MaelstromLoot ml = new MaelstromLoot(stack,
                        entry.getDouble("chance", 100),
                        entry.getInt("min", 1),
                        entry.getInt("max", 1),
                        entry.getInt("min-kills", 0));
                String mmoType = entry.getString("mmo-type", null);
                String mmoId = entry.getString("mmo-id", null);
                if (mmoType != null && mmoId != null) ml.setMMOItem(mmoType, mmoId);
                sunderingLoot.add(ml);
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

    /**
     * Parse a soul value into {min,max}. Accepts a plain number (legacy: fixed
     * value → min==max) or a "min-max" string. Returns null if unparseable.
     */
    private static int[] parseSoulRange(Object v) {
        if (v instanceof Number n) { int i = n.intValue(); return new int[]{i, i}; }
        if (v instanceof String s) {
            s = s.trim();
            try {
                if (s.contains("-")) {
                    String[] p = s.split("-", 2);
                    int lo = Integer.parseInt(p[0].trim()), hi = Integer.parseInt(p[1].trim());
                    return new int[]{Math.min(lo, hi), Math.max(lo, hi)};
                }
                int i = Integer.parseInt(s);
                return new int[]{i, i};
            } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
