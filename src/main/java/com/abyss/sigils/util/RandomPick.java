package com.abyss.sigils.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Helper for "place many, use a random few each run" mechanics.
 *
 * Admins place a pool of candidate spots in the editor (e.g. ritual altars),
 * then set how many should actually be used per run. Each run we pick that many
 * at random so the map feels different every time. Generic on purpose so any
 * future mechanic (traps, treasure, boss adds, …) can reuse it.
 */
public final class RandomPick {

    private RandomPick() {}

    /**
     * Returns up to {@code count} random distinct elements from {@code all}.
     * If {@code count <= 0} or {@code count >= all.size()} the WHOLE pool is
     * returned (order unchanged) — i.e. "0 = use all", which keeps existing
     * setups behaving exactly as before.
     */
    public static <T> List<T> some(List<T> all, int count, Random rng) {
        if (all == null || all.isEmpty()) return new ArrayList<>();
        if (count <= 0 || count >= all.size()) return new ArrayList<>(all);
        List<T> copy = new ArrayList<>(all);
        Collections.shuffle(copy, rng);
        return new ArrayList<>(copy.subList(0, count));
    }
}
