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

    /**
     * Like {@link #some} but rolls the count randomly within an inclusive
     * {@code [min, max]} interval each call — admins set a range (e.g. "2-4")
     * and every run activates a different number of markers.
     *
     * Semantics (back-compatible with the old single-count field, where the old
     * value loads as both min and max):
     *  - An upper bound of {@code <= 0} means "use ALL" (preserves "0 = all").
     *  - Otherwise N is a uniform random int in [min, max], clamped to the pool
     *    size; N {@code <= 0} returns none, N {@code >= size} returns all.
     * Tolerates a swapped/negative range.
     */
    public static <T> List<T> someInRange(List<T> all, int min, int max, Random rng) {
        if (all == null || all.isEmpty()) return new ArrayList<>();
        int placed = all.size();
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        if (hi <= 0) return new ArrayList<>(all); // "0 = use all"
        lo = Math.max(0, lo);
        hi = Math.min(hi, placed);
        lo = Math.min(lo, hi);
        int n = lo + (hi > lo ? rng.nextInt(hi - lo + 1) : 0);
        if (n >= placed) return new ArrayList<>(all);
        if (n <= 0) return new ArrayList<>();
        List<T> copy = new ArrayList<>(all);
        Collections.shuffle(copy, rng);
        return new ArrayList<>(copy.subList(0, n));
    }
}
