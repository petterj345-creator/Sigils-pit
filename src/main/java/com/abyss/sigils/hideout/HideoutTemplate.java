package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * The starter hideout every new player's hideout is cloned from.
 *
 * Admins build the layout in the world {@link #WORLD} and set its boundary +
 * spawn with the in-game editor ({@code /abyss hideout edit}). Those settings are
 * persisted here so {@link HideoutManager} can apply them when spinning up each
 * player's copy. If no template has been set up yet, the manager falls back to a
 * plain flat world bounded by {@code config hideout.border-size}.
 */
public final class HideoutTemplate {

    /** The single shared editor world admins design the starter hideout in. */
    public static final String WORLD = "abyss_hideout_template";

    private final AbyssPlugin plugin;
    private final File file;

    private boolean hasBorder;
    private double borderCenterX, borderCenterZ, borderSize;

    private boolean hasSpawn;
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;

    public HideoutTemplate(AbyssPlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "hideout_template.yml");
        load();
    }

    /** True once an admin has designed the starter world on disk. */
    public boolean worldExists() {
        return new File(org.bukkit.Bukkit.getWorldContainer(), WORLD).isDirectory();
    }

    public boolean hasBorder() { return hasBorder; }
    public double borderCenterX() { return borderCenterX; }
    public double borderCenterZ() { return borderCenterZ; }
    public double borderSize() { return borderSize; }

    public boolean hasSpawn() { return hasSpawn; }

    /** The configured spawn bound to world {@code w}, or null if none is set. */
    public Location spawnIn(World w) {
        return hasSpawn ? new Location(w, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch) : null;
    }

    public void setBorder(double centerX, double centerZ, double size) {
        this.borderCenterX = centerX;
        this.borderCenterZ = centerZ;
        this.borderSize = size;
        this.hasBorder = true;
        save();
    }

    public void setSpawn(Location l) {
        this.spawnX = l.getX();
        this.spawnY = l.getY();
        this.spawnZ = l.getZ();
        this.spawnYaw = l.getYaw();
        this.spawnPitch = l.getPitch();
        this.hasSpawn = true;
        save();
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (hasBorder) {
            cfg.set("border.centerX", borderCenterX);
            cfg.set("border.centerZ", borderCenterZ);
            cfg.set("border.size", borderSize);
        }
        if (hasSpawn) {
            cfg.set("spawn.x", spawnX);
            cfg.set("spawn.y", spawnY);
            cfg.set("spawn.z", spawnZ);
            cfg.set("spawn.yaw", spawnYaw);
            cfg.set("spawn.pitch", spawnPitch);
        }
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save hideout template: " + e.getMessage()); }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.isConfigurationSection("border")) {
            borderCenterX = cfg.getDouble("border.centerX");
            borderCenterZ = cfg.getDouble("border.centerZ");
            borderSize = cfg.getDouble("border.size");
            hasBorder = borderSize > 0;
        }
        if (cfg.isConfigurationSection("spawn")) {
            spawnX = cfg.getDouble("spawn.x");
            spawnY = cfg.getDouble("spawn.y");
            spawnZ = cfg.getDouble("spawn.z");
            spawnYaw = (float) cfg.getDouble("spawn.yaw");
            spawnPitch = (float) cfg.getDouble("spawn.pitch");
            hasSpawn = true;
        }
    }
}
