package com.abyss.sigils.dungeon;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A single spawn point in a dungeon template.
 *
 * In MAP mode, the dungeon picks a random spawn point each wave tick, picks a
 * random MobEntry from its list, and spawns it. If a spawn point's `mobs` list
 * is empty, the template's global trash mobs are used as a fallback.
 *
 * The location is stored world-less and re-bound to the instance world at runtime.
 */
public final class SpawnPoint {

    private final Location location; // world is null on disk; bound at runtime
    private final List<MobEntry> mobs;

    public SpawnPoint(Location location) {
        this(location, new ArrayList<>());
    }

    public SpawnPoint(Location location, List<MobEntry> mobs) {
        this.location = location.clone();
        this.location.setWorld(null);
        this.mobs = new ArrayList<>(mobs);
    }

    public Location location() { return location.clone(); }
    public List<MobEntry> mobs() { return mobs; }

    public Location boundTo(World w) {
        Location l = location.clone();
        l.setWorld(w);
        return l;
    }
}
