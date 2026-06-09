package com.abyss.sigils.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * One active dungeon instance. Holds the cloned world, participants, kill count,
 * wave/boss state, the upgrade-block location, and the shared boss bar.
 */
public final class DungeonSession {

    public enum Phase { TRASH, WAVES, BOSS, COMPLETE, FAILED }

    private final UUID id = UUID.randomUUID();
    private final World world;
    private final Set<UUID> players = new HashSet<>();
    private Phase phase = Phase.TRASH;
    private UUID bossEntityId;
    private long startedAt = System.currentTimeMillis();
    private Location upgradeBlock;
    /**
     * Block placed at boss death that players right-click to return to the
     * overworld portal. Null until the boss is killed. Configured via
     * `return-portal.block-type` in config.yml (default END_GATEWAY).
     */
    private Location returnPortalBlock;
    private String templateName;

    /** Per-player remaining lives. */
    private final Map<UUID, Integer> livesRemaining = new HashMap<>();
    /** Players who've been kicked out (out of lives). */
    private final Set<UUID> eliminated = new HashSet<>();

    /**
     * How many forge attempts each player has used at the Forge of the Abyss
     * after clearing this dungeon. Compared against
     * {@code upgrade.attempts-per-clear} from the plugin config. -1 in config
     * means unlimited.
     *
     * Note: attempts count every CLICK on the FORGE button, regardless of
     * success/failure. This matches what players expect — "I get 3 tries
     * after a clear, not 3 successes".
     */
    private final Map<UUID, Integer> upgradeAttemptsUsed = new HashMap<>();

    // MAP mode
    private int kills = 0;
    /** Trash mobs alive right now. */
    private final Set<UUID> aliveMobs = new HashSet<>();
    /**
     * MAP mode: how many mobs each spawn-point index has spawned SO FAR this
     * run. A spawn point's `count` is a one-time budget — once it has spawned
     * `count` mobs total, it never spawns again (no respawn). Keyed by spawn
     * point index.
     */
    private final Map<Integer, Integer> spawnPointSpawned = new HashMap<>();

    public int spawnPointSpawned(int spawnPointIndex) {
        return spawnPointSpawned.getOrDefault(spawnPointIndex, 0);
    }
    public void incrementSpawnPointSpawned(int spawnPointIndex) {
        spawnPointSpawned.merge(spawnPointIndex, 1, Integer::sum);
    }

    // WAVES mode
    private int currentWaveIndex = -1;
    private int currentWaveTotal = 0;
    private int currentWaveKilled = 0;

    private ProgressBar progressBar;

    // ----- ritual (Altar of Souls) -----
    /** Map modifiers active this run (MapMod ids), from the entry map item. */
    private final List<String> mapMods = new ArrayList<>();
    /** Per-player soul balance, earned from ritual mobs, spent in the soul shop. */
    private final Map<UUID, Integer> souls = new HashMap<>();

    /** Combat tier of the entry map (0 = none) — scales mob HP/damage. */
    private int tier = 0;
    /** Reward quality of the entry map (0 = none) — scales end rewards. */
    private int quality = 0;

    public DungeonSession(World world, Collection<Player> initial) {
        this.world = world;
        for (Player p : initial) players.add(p.getUniqueId());
    }

    public List<String> mapMods() { return mapMods; }
    public void setMapMods(Collection<String> mods) {
        mapMods.clear();
        if (mods != null) mapMods.addAll(mods);
    }
    public boolean hasMod(String id) {
        for (String m : mapMods) if (m.equalsIgnoreCase(id)) return true;
        return false;
    }

    public int tier() { return tier; }
    public void setTier(int n) { this.tier = Math.max(0, n); }
    public int quality() { return quality; }
    public void setQuality(int n) { this.quality = Math.max(0, n); }

    public int soulsOf(UUID id) { return souls.getOrDefault(id, 0); }
    public void addSouls(UUID id, int amount) {
        if (amount <= 0) return;
        souls.merge(id, amount, Integer::sum);
    }
    /** Spend souls if the player can afford it. Returns true on success. */
    public boolean spendSouls(UUID id, int amount) {
        int have = souls.getOrDefault(id, 0);
        if (amount <= 0 || have < amount) return false;
        souls.put(id, have - amount);
        return true;
    }

    public UUID id() { return id; }
    public World world() { return world; }
    public Set<UUID> players() { return players; }
    public Phase phase() { return phase; }
    public void setPhase(Phase p) { this.phase = p; }
    public UUID bossEntityId() { return bossEntityId; }
    public void setBossEntityId(UUID id) { this.bossEntityId = id; }
    public long startedAt() { return startedAt; }
    public Location upgradeBlock() { return upgradeBlock; }
    public void setUpgradeBlock(Location loc) { this.upgradeBlock = loc; }
    public Location returnPortalBlock() { return returnPortalBlock; }
    public void setReturnPortalBlock(Location loc) { this.returnPortalBlock = loc; }
    public String templateName() { return templateName; }
    public void setTemplateName(String n) { this.templateName = n; }

    // MAP
    public int kills() { return kills; }
    public void incrementKills() { kills++; }
    public Set<UUID> aliveMobs() { return aliveMobs; }

    // WAVES
    public int currentWaveIndex() { return currentWaveIndex; }
    public int currentWaveTotal() { return currentWaveTotal; }
    public int currentWaveKilled() { return currentWaveKilled; }
    public void setCurrentWave(int idx, int total) {
        this.currentWaveIndex = idx;
        this.currentWaveTotal = total;
        this.currentWaveKilled = 0;
    }
    public void incrementWaveKilled() { currentWaveKilled++; }
    public boolean isWaveCleared() { return currentWaveKilled >= currentWaveTotal; }

    public ProgressBar progressBar() { return progressBar; }
    public void setProgressBar(ProgressBar bar) { this.progressBar = bar; }

    /** Per-player lives. */
    public Map<UUID, Integer> livesRemaining() { return livesRemaining; }
    public Set<UUID> eliminated() { return eliminated; }

    public int livesOf(UUID id) { return livesRemaining.getOrDefault(id, 0); }
    public void setLives(UUID id, int n) { livesRemaining.put(id, Math.max(0, n)); }
    /** Returns true if the player still has lives left after decrementing. */
    public boolean decrementLife(UUID id) {
        int now = livesRemaining.getOrDefault(id, 0) - 1;
        livesRemaining.put(id, Math.max(0, now));
        return now > 0;
    }
    public boolean isEliminated(UUID id) { return eliminated.contains(id); }
    public void eliminate(UUID id) { eliminated.add(id); }

    /** How many upgrade attempts this player has used at the Forge. */
    public int upgradeAttemptsUsed(UUID id) { return upgradeAttemptsUsed.getOrDefault(id, 0); }
    /** Record one more upgrade attempt for this player. */
    public void incrementUpgradeAttempts(UUID id) {
        upgradeAttemptsUsed.merge(id, 1, Integer::sum);
    }
}
