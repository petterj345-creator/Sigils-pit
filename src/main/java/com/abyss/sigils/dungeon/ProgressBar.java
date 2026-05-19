package com.abyss.sigils.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Vanilla BossBar wrapper showing dungeon progress to all party members.
 * Shared per-session; updated by the DungeonManager.
 */
public final class ProgressBar {

    private final BossBar bar;

    public ProgressBar(String title) {
        this.bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_10);
        this.bar.setProgress(0);
    }

    /** Update kill progress for MAP mode. */
    public void setKillProgress(int current, int total) {
        bar.setTitle("§5§lThe Abyss §7— §fKills " + current + "§7/§f" + total);
        bar.setColor(BarColor.PURPLE);
        bar.setProgress(total == 0 ? 0 : Math.min(1.0, (double) current / total));
    }

    /** Update wave progress for WAVES mode. */
    public void setWaveProgress(int waveNum, int totalWaves, int killed, int total) {
        bar.setTitle("§5§lThe Abyss §7— §fWave " + waveNum + "§7/§f" + totalWaves
                + " §8(" + killed + "§7/§f" + total + ")");
        bar.setColor(BarColor.PURPLE);
        bar.setProgress(total == 0 ? 0 : Math.min(1.0, (double) killed / total));
    }

    /** Switch the bar to boss-fight mode (red, tracks boss HP). */
    public void setBossPhase(String bossName) {
        bar.setTitle("§4§l" + bossName);
        bar.setColor(BarColor.RED);
        bar.setProgress(1.0);
    }

    /** Update boss HP fraction. */
    public void setBossHealth(double current, double max) {
        if (max <= 0) { bar.setProgress(0); return; }
        bar.setProgress(Math.max(0, Math.min(1.0, current / max)));
    }

    public void addPlayer(Player p)    { bar.addPlayer(p); }
    public void removePlayer(Player p) { bar.removePlayer(p); }
    public void removeAll()            { bar.removeAll(); bar.setVisible(false); }
}
