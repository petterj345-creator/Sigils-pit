package com.abyss.sigils.dungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * One wave in WAVES mode.
 *
 * Each wave defines a list of MobEntries. All mobs in the wave are spawned at
 * the start of the wave (distributed across the template's spawn points). The
 * wave is considered complete when all spawned mobs are dead. The next wave
 * starts after `delayAfterSeconds` seconds.
 *
 * If `bossWave` is true, the boss spawns instead of trash for this wave.
 * (Reserved for future per-wave bosses; ignored for now.)
 */
public final class Wave {

    private final List<MobEntry> mobs;
    private int delayAfterSeconds;
    private boolean bossWave;

    public Wave() {
        this(new ArrayList<>(), 5, false);
    }

    public Wave(List<MobEntry> mobs, int delayAfterSeconds, boolean bossWave) {
        this.mobs = new ArrayList<>(mobs);
        this.delayAfterSeconds = Math.max(0, delayAfterSeconds);
        this.bossWave = bossWave;
    }

    public List<MobEntry> mobs()       { return mobs; }
    public int delayAfterSeconds()     { return delayAfterSeconds; }
    public boolean bossWave()          { return bossWave; }

    public void setDelayAfterSeconds(int s) { this.delayAfterSeconds = Math.max(0, s); }
    public void setBossWave(boolean b)      { this.bossWave = b; }

    /** Total mob count for this wave (sum of all entry counts). */
    public int totalMobs() {
        int sum = 0;
        for (MobEntry e : mobs) sum += e.count();
        return sum;
    }
}
