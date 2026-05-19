package com.abyss.sigils.integration;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilRegistry;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * MythicMobs integration.
 *
 * Registers a custom item type so MythicMobs YAML can write:
 *     Drops:
 *       - abyss_sigil{id=wrath;tier=1} 1 0.25
 *       - abyss_sigil{id=random;tier=2} 1 0.05
 *       - abyss_sigil_dust 3 1.0
 *
 * Also listens for MythicMobDeathEvent so the dungeon manager can react.
 */
public final class MythicHook implements Listener {

    private final AbyssPlugin plugin;
    private final SigilRegistry registry;

    public MythicHook(AbyssPlugin plugin, SigilRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void register() {
        try {
            MythicBukkit mythic = MythicBukkit.inst();
            Object items = mythic.getItemManager();

            // The exact method name has shifted across MythicMobs versions:
            //   5.x recent:  registerItem(String, Function<MythicLineConfig, AbstractItemStack>)
            //   5.x older:   registerItemType(...)
            // Try the modern name first, then fall back via reflection.
            try {
                items.getClass()
                        .getMethod("registerItem", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_sigil",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildSigil);
                items.getClass()
                        .getMethod("registerItem", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_sigil_dust",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildDust);
                items.getClass()
                        .getMethod("registerItem", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_map",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildMap);
                items.getClass()
                        .getMethod("registerItem", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_book",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildBook);
            } catch (NoSuchMethodException nsme) {
                items.getClass()
                        .getMethod("registerItemType", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_sigil",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildSigil);
                items.getClass()
                        .getMethod("registerItemType", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_sigil_dust",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildDust);
                items.getClass()
                        .getMethod("registerItemType", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_map",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildMap);
                items.getClass()
                        .getMethod("registerItemType", String.class, java.util.function.Function.class)
                        .invoke(items, "abyss_book",
                                (java.util.function.Function<MythicLineConfig, AbstractItemStack>) this::buildBook);
            }
            plugin.getLogger().info("Registered MythicMobs item types: abyss_sigil, abyss_sigil_dust, abyss_map, abyss_book");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "Couldn't register MythicMobs item types (Mythic API version mismatch?): " + t.getMessage());
        }
    }

    /** Called by Mythic to build the ItemStack when it sees `abyss_sigil{...}` in YAML. */
    private AbstractItemStack buildSigil(MythicLineConfig line) {
        String id = line.getString(new String[]{"id"}, "random");
        int tier  = line.getInteger(new String[]{"tier"}, 1);
        String rankStr = line.getString(new String[]{"rank"}, null);

        SigilDefinition def;
        if ("random".equalsIgnoreCase(id)) {
            if (rankStr != null) {
                try {
                    com.abyss.sigils.sigils.SigilRank rank = com.abyss.sigils.sigils.SigilRank.valueOf(rankStr.toUpperCase());
                    def = registry.randomOfRank(rank);
                } catch (IllegalArgumentException e) { def = registry.random(); }
            } else {
                def = registry.random();
            }
        } else {
            def = registry.get(id);
        }
        if (def == null) {
            plugin.getLogger().warning("MythicMobs requested unknown sigil id: " + id);
            return null;
        }
        SigilInstance inst = new SigilInstance(def.id(), Math.max(1, Math.min(tier, def.maxTier())));
        ItemStack stack = SigilItem.toItem(inst);
        return stack == null ? null : BukkitAdapter.adapt(stack);
    }

    /** Builds the Sigil Dust item. Amount controlled by the Drops line (e.g. `abyss_sigil_dust 3`). */
    private AbstractItemStack buildDust(MythicLineConfig line) {
        return BukkitAdapter.adapt(SigilItem.createDust(1));
    }

    /**
     * Builds an Abyss Map item for a specific template. YAML syntax:
     *   abyss_map{template=&lt;name&gt;} &lt;amount&gt; &lt;chance&gt;
     * If the template doesn't exist (anymore), nothing is dropped — Mythic
     * gets a null and skips the line. We don't crash the mob death.
     */
    private AbstractItemStack buildMap(MythicLineConfig line) {
        String tplName = line.getString(new String[]{"template", "tpl"}, null);
        if (tplName == null) {
            plugin.getLogger().warning("abyss_map drop missing template= argument");
            return null;
        }
        com.abyss.sigils.dungeon.DungeonTemplate tpl = plugin.templates().get(tplName);
        if (tpl == null) {
            plugin.getLogger().warning("abyss_map drop references unknown template: " + tplName);
            return null;
        }
        ItemStack stack = com.abyss.sigils.dungeon.DungeonMap.create(tpl);
        return stack == null ? null : BukkitAdapter.adapt(stack);
    }

    /**
     * Builds an Abyss Book item at the given tier. YAML syntax:
     *   abyss_book{tier=N} 1 0.001
     * Tier defaults to 1. We clamp to [1, book.max-tier].
     */
    private AbstractItemStack buildBook(MythicLineConfig line) {
        int tier = line.getInteger(new String[]{"tier"}, 1);
        int max = plugin.bookTiers().maxTier();
        if (tier < 1) tier = 1;
        if (tier > max) tier = max;
        ItemStack stack = SigilItem.createBook(tier);
        return stack == null ? null : BukkitAdapter.adapt(stack);
    }

    @EventHandler
    public void onMythicDeath(MythicMobDeathEvent e) {
        // Bridge to dungeon manager so kill counts/boss trigger work
        plugin.dungeonManager().handleMythicMobDeath(e);
    }

    @EventHandler
    public void onMythicReload(MythicReloadedEvent e) {
        // Re-register on Mythic reload, otherwise our types get wiped.
        register();
    }
}
