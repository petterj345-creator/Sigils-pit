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
    private String templateName;

    /** Per-player remaining lives. */
    private final Map<UUID, Integer> livesRemaining = new HashMap<>();
    /** Players who've been kicked out (out of lives). */
    private final Set<UUID> eliminated = new HashSet<>();

    // MAP mode
    private int kills = 0;
    /** Trash mobs alive right now. */
    private final Set<UUID> aliveMobs = new HashSet<>();

    // WAVES mode
    private int currentWaveIndex = -1;
    private int currentWaveTotal = 0;
    private int currentWaveKilled = 0;

    private ProgressBar progressBar;

    public DungeonSession(World world, Collection<Player> initial) {
        this.world = world;
        for (Player p : initial) players.add(p.getUniqueId());
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
}
