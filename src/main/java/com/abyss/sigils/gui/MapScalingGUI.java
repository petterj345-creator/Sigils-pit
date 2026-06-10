package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Admin editor for map tier/quality scaling ({@code scaling.*} in config.yml).
 * Tier controls combat difficulty (mob HP + a little damage); quality controls
 * end rewards (XP/money/loot). Values are written straight to config.yml and
 * take effect on the next dungeon start.
 */
public final class MapScalingGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;

    public MapScalingGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new MapScalingGUI(plugin).open(p);
    }

    @Override protected String title() { return color("&5&l✦ Map Scaling"); }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // ---- Tier (combat) ----
        set(10, icon(Material.NETHERITE_SWORD, "&c&lTier — Max",
                "&7Highest tier a map can reach.",
                "&7Currently: &f" + plugin.getConfig().getInt("scaling.tier.max", 16),
                "", "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "scaling.tier.max", "&fMax tier"));

        set(12, icon(Material.REDSTONE, "&cTier — Mob HP % per Tier",
                "&7Extra max health per tier.",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.tier.hp-percent-per-tier", 15) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.tier.hp-percent-per-tier", "&fMob HP % per tier"));

        set(14, icon(Material.IRON_SWORD, "&cTier — Mob Damage % per Tier",
                "&7Extra attack damage per tier.",
                "&7Keep this low — our mobs are already strong.",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.tier.damage-percent-per-tier", 3) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.tier.damage-percent-per-tier", "&fMob damage % per tier"));

        set(15, icon(Material.PAPER, "&cTier — Drop Chance % per Tier",
                "&7Per tier, mob drop chances are boosted",
                "&7by this much (more maps/currency/sigils).",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.tier.drop-chance-percent-per-tier", 8) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.tier.drop-chance-percent-per-tier", "&fDrop chance % per tier"));

        set(16, icon(Material.MAP, "&cTier — Map Drop Tier Offset",
                "&7Maps that drop in a tiered run come",
                "&7pre-tiered at (run tier - this).",
                "&7Currently: &f" + plugin.getConfig().getInt("scaling.tier.map-drop-tier-offset", 1),
                "", "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "scaling.tier.map-drop-tier-offset", "&fMap drop tier offset"));

        set(11, icon(Material.EXPERIENCE_BOTTLE, "&cTier — Reward XP % per Tier",
                "&7Risk/reward: a tougher map also pays",
                "&7this much more XP at the end.",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.tier.reward-xp-percent-per-tier", 5) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.tier.reward-xp-percent-per-tier", "&fTier reward XP % per tier"));

        set(13, icon(Material.GOLD_INGOT, "&cTier — Reward Money % per Tier",
                "&7Risk/reward: a tougher map also pays",
                "&7this much more money at the end.",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.tier.reward-money-percent-per-tier", 5) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.tier.reward-money-percent-per-tier", "&fTier reward money % per tier"));

        // ---- Quality (rewards) ----
        set(28, icon(Material.GOLD_BLOCK, "&b&lQuality — Max",
                "&7Highest quality a map can reach.",
                "&7Currently: &f" + plugin.getConfig().getInt("scaling.quality.max", 20),
                "", "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "scaling.quality.max", "&fMax quality"));

        set(30, icon(Material.EXPERIENCE_BOTTLE, "&bQuality — XP % per Quality",
                "&7Extra XP rewarded per quality.",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.quality.xp-percent", 10) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.quality.xp-percent", "&fXP % per quality"));

        set(32, icon(Material.GOLD_INGOT, "&bQuality — Money % per Quality",
                "&7Extra money rewarded per quality.",
                "&7Currently: &f" + plugin.getConfig().getDouble("scaling.quality.money-percent", 10) + "%",
                "", "&eClick &7to set"),
            e -> promptDouble((Player) e.getWhoClicked(), "scaling.quality.money-percent", "&fMoney % per quality"));

        set(34, icon(Material.HOPPER, "&bQuality — Quality per Extra Item",
                "&7How much quality grants one more",
                "&7possible loot item from the pool.",
                "&7Currently: &f" + plugin.getConfig().getInt("scaling.quality.quality-per-extra-item", 3),
                "", "&eClick &7to set"),
            e -> promptInt((Player) e.getWhoClicked(), "scaling.quality.quality-per-extra-item", "&fQuality per extra item"));

        set(49, icon(Material.ARROW, "&7← Back to admin hub"),
            e -> AdminGUI.openHub(plugin, (Player) e.getWhoClicked()));
    }

    private void promptInt(Player p, String path, String label) {
        ChatInput.prompt(plugin, p, label, String.valueOf(plugin.getConfig().getInt(path, 0)), text -> {
            try { plugin.getConfig().set(path, Math.max(0, Integer.parseInt(text.trim()))); plugin.saveConfig(); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a whole number.")); }
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p));
        });
    }

    private void promptDouble(Player p, String path, String label) {
        ChatInput.prompt(plugin, p, label, String.valueOf(plugin.getConfig().getDouble(path, 0)), text -> {
            try { plugin.getConfig().set(path, Math.max(0, Double.parseDouble(text.trim()))); plugin.saveConfig(); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p));
        });
    }
}
