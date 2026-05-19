package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilStat;
import com.abyss.sigils.sigils.SigilStatApplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Optional MMOItems integration. Uses reflection so the plugin still builds
 * and runs without MMOItems present.
 *
 * MMOItems exposes per-player stats via:
 *   PlayerData.get(Player).getStats().getStat("ATTACK_DAMAGE")
 *
 * To apply a temporary modifier you wrap it in StatModifier and call
 * StatInstance.addModifier(StatModifier). To remove all our modifiers we
 * tag each one with a known key ("abyss_sigil") and remove by key.
 *
 * If MMOItems isn't installed or its API changes, we no-op silently.
 */
public final class MMOItemsHook {

    private final AbyssPlugin plugin;
    private boolean available = false;

    // Reflected stuff cached at init time
    private Class<?> playerDataClass;
    private Class<?> playerStatsClass;
    private Class<?> statInstanceClass;
    private Class<?> statModifierClass;
    private Method playerDataGet;
    private Method getStatsMethod;
    private Method getStatMethod;
    private Method addModifierMethod;
    private Method removeIfMethod;
    // Constructor for StatModifier(String key, double value, ModifierType FLAT)
    private java.lang.reflect.Constructor<?> statModifierCtor;
    private Object modifierTypeFlat;

    /** Map from our SigilStat → MMOItems stat name. */
    private static final Map<SigilStat, String> STAT_MAPPING = Map.of(
            SigilStat.MMO_ATTACK_DAMAGE,           "ATTACK_DAMAGE",
            SigilStat.MMO_MAGIC_DAMAGE,            "MAGIC_DAMAGE",
            SigilStat.MMO_CRITICAL_STRIKE_CHANCE,  "CRITICAL_STRIKE_CHANCE",
            SigilStat.MMO_CRITICAL_STRIKE_POWER,   "CRITICAL_STRIKE_POWER",
            SigilStat.MMO_PVE_DAMAGE,              "PVE_DAMAGE",
            SigilStat.MMO_PVP_DAMAGE,              "PVP_DAMAGE"
    );

    private static final String MOD_KEY = "abyss_sigil_modifier";

    public MMOItemsHook(AbyssPlugin plugin) { this.plugin = plugin; }

    public void initIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
            plugin.getLogger().info("MMOItems not present; MMO sigil stats will be inert.");
            return;
        }
        try {
            playerDataClass    = Class.forName("net.Indyuce.mmoitems.api.player.PlayerData");
            playerStatsClass   = Class.forName("io.lumine.mythic.lib.player.PlayerStats");
            statInstanceClass  = Class.forName("io.lumine.mythic.lib.api.stat.StatInstance");
            statModifierClass  = Class.forName("io.lumine.mythic.lib.api.stat.modifier.StatModifier");
            Class<?> modTypeClass = Class.forName("io.lumine.mythic.lib.api.stat.modifier.ModifierType");

            playerDataGet      = playerDataClass.getMethod("get", Player.class);
            getStatsMethod     = playerDataClass.getMethod("getStats");
            getStatMethod      = playerStatsClass.getMethod("getStat", String.class);
            // Common signature: addModifier(StatModifier)
            addModifierMethod  = statInstanceClass.getMethod("addModifier", statModifierClass);
            // removeIf(Predicate) by key
            removeIfMethod     = statInstanceClass.getMethod("removeIf", java.util.function.Predicate.class);
            // StatModifier(String key, double value, ModifierType type)
            statModifierCtor   = statModifierClass.getConstructor(String.class, double.class, modTypeClass);
            modifierTypeFlat   = null;
            for (Object c : modTypeClass.getEnumConstants()) {
                if ("FLAT".equals(((Enum<?>) c).name())) { modifierTypeFlat = c; break; }
            }
            if (modifierTypeFlat == null) modifierTypeFlat = modTypeClass.getEnumConstants()[0];

            available = true;
            plugin.getLogger().info("MMOItems integration enabled.");
        } catch (Throwable t) {
            plugin.getLogger().warning("MMOItems found but API mismatch — MMO sigil stats disabled: " + t.getMessage());
            available = false;
        }
    }

    /** Applies (refreshes) MMO modifiers for a player from their socketed sigils. */
    public void applyTo(Player p, SigilStatApplier applier) {
        if (!available) return;
        try {
            Object playerData = playerDataGet.invoke(null, p);
            if (playerData == null) return;
            Object stats = getStatsMethod.invoke(playerData);
            for (Map.Entry<SigilStat, String> mapping : STAT_MAPPING.entrySet()) {
                Object statInstance = getStatMethod.invoke(stats, mapping.getValue());
                // Remove any existing modifiers we added
                removeIfMethod.invoke(statInstance,
                        (java.util.function.Predicate<Object>) mod -> isOurs(mod));
                double value = applier.totalStat(p, mapping.getKey());
                if (value > 0) {
                    Object modifier = statModifierCtor.newInstance(MOD_KEY, value, modifierTypeFlat);
                    addModifierMethod.invoke(statInstance, modifier);
                }
            }
        } catch (Throwable t) {
            // Don't spam — log once per player per session would be better, but for now suppress.
        }
    }

    /** Clears all our modifiers for a player. */
    public void clearFor(Player p) {
        if (!available) return;
        try {
            Object playerData = playerDataGet.invoke(null, p);
            if (playerData == null) return;
            Object stats = getStatsMethod.invoke(playerData);
            for (String statName : STAT_MAPPING.values()) {
                Object statInstance = getStatMethod.invoke(stats, statName);
                removeIfMethod.invoke(statInstance,
                        (java.util.function.Predicate<Object>) mod -> isOurs(mod));
            }
        } catch (Throwable ignored) {}
    }

    /** Best-effort check that a StatModifier was created by us. */
    private boolean isOurs(Object modifier) {
        try {
            Method getKey = modifier.getClass().getMethod("getKey");
            Object k = getKey.invoke(modifier);
            return MOD_KEY.equals(k);
        } catch (Throwable t) { return false; }
    }

    public boolean available() { return available; }
}
