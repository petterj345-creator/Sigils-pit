package com.abyss.sigils.sigils;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/**
 * Resolves Book-of-Sigils tier information from config.
 *
 * The book.tiers list in config.yml maps each tier (index = tier-1) to a small/
 * big/grand slot count. The book.success-chance-per-tier list maps the same
 * way to forge success percentages.
 *
 * This class is pure read-side: it doesn't modify config, just looks values up.
 * Out-of-range tiers reuse the last entry (so a partial config still works).
 */
public final class BookTiers {

    private final AbyssPlugin plugin;
    public BookTiers(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Largest tier any book can be upgraded to. */
    public int maxTier() {
        return Math.max(1, plugin.getConfig().getInt("book.max-tier", 20));
    }

    /** Small slots for this tier. Clamped to ≥0. */
    public int smallSlots(int tier) {
        return readSlot(tier, "small", legacySmall());
    }

    /** Big (major) slots for this tier. */
    public int bigSlots(int tier) {
        return readSlot(tier, "big", legacyBig());
    }

    /**
     * "Grand" slots for this tier. These render as a third row in the socket
     * GUI but currently accept MAJOR sigils (same as big slots) — they're
     * effectively bonus big slots gated behind high tiers. If you want a true
     * 3rd rank with its own sigil drops, that's a separate feature.
     */
    public int grandSlots(int tier) {
        return readSlot(tier, "grand", 0);
    }

    /** Success % for forging T(currentTier) → T(currentTier+1). 0..100. */
    public int successPercentFor(int currentTier) {
        List<Integer> list = plugin.getConfig().getIntegerList("book.success-chance-per-tier");
        if (list == null || list.isEmpty()) return 10; // sensible default
        int idx = Math.max(0, currentTier - 1);
        if (idx >= list.size()) idx = list.size() - 1;
        int pct = list.get(idx);
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        return pct;
    }

    /** Total slot capacity across all three rank kinds for one tier. */
    public int totalSlots(int tier) {
        return smallSlots(tier) + bigSlots(tier) + grandSlots(tier);
    }

    // ----- internals -----

    @SuppressWarnings("unchecked")
    private int readSlot(int tier, String key, int fallback) {
        List<?> raw = plugin.getConfig().getList("book.tiers");
        if (raw == null || raw.isEmpty()) return fallback;
        int idx = Math.max(0, tier - 1);
        if (idx >= raw.size()) idx = raw.size() - 1;
        Object entry = raw.get(idx);
        if (entry instanceof ConfigurationSection cs) {
            return Math.max(0, cs.getInt(key, fallback));
        } else if (entry instanceof Map<?, ?> m) {
            Object v = m.get(key);
            if (v instanceof Number n) return Math.max(0, n.intValue());
        }
        return fallback;
    }

    private int legacySmall() { return plugin.getConfig().getInt("socket.small-slots", 10); }
    private int legacyBig()   { return plugin.getConfig().getInt("socket.big-slots", 3); }
}
