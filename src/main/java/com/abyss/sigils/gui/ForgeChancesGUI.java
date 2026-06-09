package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.UpgradeGUI;
import com.abyss.sigils.sigils.SigilRank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Admin editor for the per-rank forge success chances
 * ({@code upgrade.success-chance-per-rank} in config.yml). One button per rank;
 * click to type a new percent. Better ranks are meant to be rarer to upgrade,
 * so a GRAND sigil is never a sure thing.
 *
 * Values are written straight to config.yml and take effect immediately — the
 * forge reads them live on each attempt.
 */
public final class ForgeChancesGUI extends EditorGUI.Holder {

    private static final int SLOT_MINOR = 11;
    private static final int SLOT_MAJOR = 13;
    private static final int SLOT_GRAND = 15;
    private static final int SLOT_BACK  = 22;

    private final AbyssPlugin plugin;

    public ForgeChancesGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new ForgeChancesGUI(plugin).open(p);
    }

    @Override protected String title() { return color("&5&l✦ Forge Chances"); }

    @Override protected int size() { return 27; }

    @Override protected void build(Player viewer) {
        fillBorder();

        set(SLOT_MINOR, rankIcon(Material.IRON_BLOCK, "&7minor", SigilRank.MINOR),
            e -> promptFor((Player) e.getWhoClicked(), SigilRank.MINOR));
        set(SLOT_MAJOR, rankIcon(Material.GOLD_BLOCK, "&6MAJOR", SigilRank.MAJOR),
            e -> promptFor((Player) e.getWhoClicked(), SigilRank.MAJOR));
        set(SLOT_GRAND, rankIcon(Material.DIAMOND_BLOCK, "&5&lGRAND", SigilRank.GRAND),
            e -> promptFor((Player) e.getWhoClicked(), SigilRank.GRAND));

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
