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

    // ----- item generation / detection (used for MMOItems rewards) -----
    /** The static MMOItems.plugin instance. */
    private Object mmoItemsPluginInstance;
    /** MMOItems#getItem(String type, String id) -> ItemStack (fresh roll). */
    private Method getItemMethod;
    /** MythicLib NBTItem.get(ItemStack) -> NBTItem. */
    private Method nbtGetMethod;
    /** NBTItem#getString(String) -> String. */
    private Method nbtGetStringMethod;

    /** NBT tags MMOItems writes onto every generated item. */
    private static final String NBT_TYPE = "MMOITEMS_ITEM_TYPE";
    private static final String NBT_ID   = "MMOITEMS_ITEM_ID";

    /** Map from our SigilStat → MMOItems stat name. */
    private static final Map<SigilStat, String> STAT_MAPPING;
    static {
        Map<SigilStat, String> m = new java.util.HashMap<>();
        // Originals
        m.put(SigilStat.MMO_ATTACK_DAMAGE,           "ATTACK_DAMAGE");
        m.put(SigilStat.MMO_MAGIC_DAMAGE,            "MAGIC_DAMAGE");
        m.put(SigilStat.MMO_CRITICAL_STRIKE_CHANCE,  "CRITICAL_STRIKE_CHANCE");
        m.put(SigilStat.MMO_CRITICAL_STRIKE_POWER,   "CRITICAL_STRIKE_POWER");
        m.put(SigilStat.MMO_PVE_DAMAGE,              "PVE_DAMAGE");
        m.put(SigilStat.MMO_PVP_DAMAGE,              "PVP_DAMAGE");
        // Defensive
        m.put(SigilStat.MMO_HEALTH_REGEN,            "HEALTH_REGENERATION");
        m.put(SigilStat.MMO_ARMOR,                   "ARMOR");
        m.put(SigilStat.MMO_BLOCK_RATING,            "BLOCK_RATING");
        m.put(SigilStat.MMO_DODGE_RATING,            "DODGE_RATING");
        m.put(SigilStat.MMO_BLOCK_POWER,             "BLOCK_POWER");
        m.put(SigilStat.MMO_MAGIC_RESISTANCE,        "MAGIC_DAMAGE_REDUCTION");
        m.put(SigilStat.MMO_ARMOR_PIERCING,          "ARMOR_PIERCING");
        // Utility
        m.put(SigilStat.MMO_COOLDOWN_REDUCTION,      "COOLDOWN_REDUCTION");
        m.put(SigilStat.MMO_MAX_MANA,                "MAX_MANA");
        m.put(SigilStat.MMO_MANA_REGEN,              "MANA_REGENERATION");
        m.put(SigilStat.MMO_MAGICAL_DAMAGE,          "MAGICAL_DAMAGE");
        STAT_MAPPING = java.util.Collections.unmodifiableMap(m);
    }

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

        // Item generation/detection is independent of the stat reflection above —
        // even if the stat API shifted, we may still be able to generate reward
        // items. Wire it up separately so one failing doesn't kill the other.
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            mmoItemsPluginInstance = mmoItemsClass.getField("plugin").get(null);
            getItemMethod = mmoItemsClass.getMethod("getItem", String.class, String.class);

            Class<?> nbtItemClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            nbtGetMethod = nbtItemClass.getMethod("get", org.bukkit.inventory.ItemStack.class);
            nbtGetStringMethod = nbtItemClass.getMethod("getString", String.class);
            plugin.getLogger().info("MMOItems item generation enabled (rewards can roll live MMOItems).");
        } catch (Throwable t) {
            plugin.getLogger().warning("MMOItems item generation unavailable (API mismatch?): " + t.getMessage());
            mmoItemsPluginInstance = null;
            getItemMethod = null;
        }
    }

    // ============================================================
    // Item generation / detection (for MMOItems rewards)
    // ============================================================

    /** True if MMOItems item generation reflection wired up successfully. */
    public boolean canGenerateItems() {
        return mmoItemsPluginInstance != null && getItemMethod != null;
    }

    /**
     * Read the MMOItems type tag from an item, or null if it isn't an MMOItem
     * (or NBT reflection is unavailable). Backed by MythicLib's NBTItem because
     * MMOItems stores these as raw NBT, not Bukkit PDC.
     */
    public String mmoType(org.bukkit.inventory.ItemStack item) {
        return readNbt(item, NBT_TYPE);
    }

    /** Read the MMOItems id tag from an item, or null. */
    public String mmoId(org.bukkit.inventory.ItemStack item) {
        return readNbt(item, NBT_ID);
    }

    /** True if the item carries both MMOItems type + id tags. */
    public boolean isMMOItem(org.bukkit.inventory.ItemStack item) {
        String t = mmoType(item);
        String i = mmoId(item);
        return t != null && !t.isEmpty() && i != null && !i.isEmpty();
    }

    private String readNbt(org.bukkit.inventory.ItemStack item, String tag) {
        if (item == null || nbtGetMethod == null || nbtGetStringMethod == null) return null;
        try {
            Object nbt = nbtGetMethod.invoke(null, item);
            if (nbt == null) return null;
            Object value = nbtGetStringMethod.invoke(nbt, tag);
            return value == null ? null : value.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Generate a FRESH MMOItems item from a type + id (re-rolling any random
     * stats). Returns null if MMOItems isn't present, the reflection failed, or
     * the type/id no longer exists. {@code amount} is clamped to >= 1.
     */
    public org.bukkit.inventory.ItemStack generate(String type, String id, int amount) {
        if (!canGenerateItems() || type == null || id == null) return null;
        try {
            Object result = getItemMethod.invoke(mmoItemsPluginInstance, type, id);
            if (result instanceof org.bukkit.inventory.ItemStack stack) {
                stack.setAmount(Math.max(1, amount));
                return stack;
            }
            return null;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to generate MMOItems item " + type + ":" + id + " — " + t.getMessage());
            return null;
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
