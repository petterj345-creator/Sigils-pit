package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.UpgradeGUI;
import com.abyss.sigils.sigils.SigilRank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Admin editor for the forge settings: the per-rank success chances
 * ({@code upgrade.success-chance-per-rank}) and the XP-level cost charged per
 * forge attempt ({@code upgrade.xp-level-cost}; 0 = free). One button per rank
 * plus an XP-cost button; click to type a new value. Better ranks are meant to
 * be rarer to upgrade, so a GRAND sigil is never a sure thing.
 *
 * Values are written straight to config.yml and take effect immediately — the
 * forge reads them live on each attempt.
 */
public final class ForgeChancesGUI extends EditorGUI.Holder {

    private static final int SLOT_MINOR   = 11;
    private static final int SLOT_MAJOR   = 13;
    private static final int SLOT_GRAND   = 15;
    private static final int SLOT_XP_COST = 16;
    private static final int SLOT_BACK    = 22;

    private final AbyssPlugin plugin;

    public ForgeChancesGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new ForgeChancesGUI(plugin).open(p);
    }

    @Override protected String title() { return color("&5&l✦ Forge Settings"); }

    @Override protected int size() { return 27; }

    @Override protected void build(Player viewer) {
        fillBorder();

        set(SLOT_MINOR, rankIcon(Material.IRON_BLOCK, "&7minor", SigilRank.MINOR),
            e -> promptFor((Player) e.getWhoClicked(), SigilRank.MINOR));
        set(SLOT_MAJOR, rankIcon(Material.GOLD_BLOCK, "&6MAJOR", SigilRank.MAJOR),
            e -> promptFor((Player) e.getWhoClicked(), SigilRank.MAJOR));
        set(SLOT_GRAND, rankIcon(Material.DIAMOND_BLOCK, "&5&lGRAND", SigilRank.GRAND),
            e -> promptFor((Player) e.getWhoClicked(), SigilRank.GRAND));

        set(SLOT_XP_COST, xpCostIcon(),
            e -> promptXpCost((Player) e.getWhoClicked()));

        set(SLOT_BACK, icon(Material.ARROW, "&7← Back to admin hub"),
            e -> AdminGUI.openHub(plugin, (Player) e.getWhoClicked()));
    }

    private org.bukkit.inventory.ItemStack rankIcon(Material mat, String label, SigilRank rank) {
        int pct = chanceFor(rank);
        return icon(mat,
                "&fForge Chance: " + label,
                "&7Success when forging a " + label.replaceAll("&.", "") + " &7sigil",
                "&7to its next tier.",
                "",
                "&7Currently: " + colorPct(pct) + pct + "%",
                "",
                "&eClick &7to set a new percent");
    }

    private org.bukkit.inventory.ItemStack xpCostIcon() {
        int cost = xpCost();
        return icon(Material.EXPERIENCE_BOTTLE,
                "&fForge Cost: &aXP Levels",
                "&7XP levels charged each time a player",
                "&7clicks FORGE at the altar.",
                "",
                "&7Currently: " + (cost <= 0 ? "&aFree" : "&e" + cost + " level" + (cost == 1 ? "" : "s")),
                "",
                "&eClick &7to set (0 = free)");
    }

    private void promptXpCost(Player p) {
        ChatInput.prompt(plugin, p, "&fXP levels per forge (0 = free)",
                String.valueOf(xpCost()), text -> {
            try {
                int cost = Math.max(0, Integer.parseInt(text.trim()));
                plugin.getConfig().set("upgrade.xp-level-cost", cost);
                plugin.saveConfig();
                p.sendMessage(color(cost <= 0
                        ? "&aForging is now &lfree&a (no XP cost)."
                        : "&aForge XP cost set to &f" + cost + " level" + (cost == 1 ? "" : "s") + "&a."));
            } catch (NumberFormatException ex) {
                p.sendMessage(color("&cMust be a whole number 0 or higher."));
            }
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p));
        });
    }

    /** Current configured XP-level cost per forge attempt (0 = free). */
    private int xpCost() {
        return plugin.getConfig().getInt("upgrade.xp-level-cost", 0);
    }

    private void promptFor(Player p, SigilRank rank) {
        ChatInput.prompt(plugin, p, "&fForge chance % for " + rank.name(),
                String.valueOf(chanceFor(rank)), text -> {
            try {
                int pct = Math.max(0, Math.min(100, Integer.parseInt(text.trim())));
                plugin.getConfig().set("upgrade.success-chance-per-rank." + rank.name(), pct);
                plugin.saveConfig();
                p.sendMessage(color("&a" + rank.name() + " forge chance set to &f" + pct + "%&a."));
            } catch (NumberFormatException ex) {
                p.sendMessage(color("&cMust be a whole number 0-100."));
            }
            Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p));
        });
    }

    /** Current configured chance for a rank, or the built-in default if unset. */
    private int chanceFor(SigilRank rank) {
        return plugin.getConfig().getInt("upgrade.success-chance-per-rank." + rank.name(),
                UpgradeGUI.defaultRankChance(rank));
    }

    private String colorPct(int pct) {
        if (pct >= 90) return "&a";
        if (pct >= 60) return "&e";
        return "&c";
    }
}
