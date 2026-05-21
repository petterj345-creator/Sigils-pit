package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilRank;
import com.abyss.sigils.sigils.SigilStat;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Pick a SigilStat from those valid for the given rank.
 * Stats are grouped visually: combat first, gathering next, MMO last.
 */
public final class StatPickerGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final SigilRank rank;
    private final Consumer<SigilStat> onPick;

    public StatPickerGUI(AbyssPlugin plugin, SigilRank rank, Consumer<SigilStat> onPick) {
        this.plugin = plugin;
        this.rank = rank;
        this.onPick = onPick;
    }

    public static void openFor(AbyssPlugin plugin, Player p, SigilRank rank, Consumer<SigilStat> onPick) {
        new StatPickerGUI(plugin, rank, onPick).open(p);
    }

    @Override protected String title() { return color("&5Pick a stat (" + rank + ")"); }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();
        int slot = 10;
        for (SigilStat s : SigilStat.values()) {
            if (!s.allowedFor(rank)) continue;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break; // safety

            Material icon = switch (s.category()) {
                case COMBAT    -> Material.IRON_SWORD;
                case GATHERING -> Material.IRON_PICKAXE;
                case MMOITEMS  -> Material.NETHER_STAR;
            };
            set(slot, icon(icon, "&f" + s.name(),
                    "&8" + s.category(),
                    switch (s.rankFloor()) {
                        case GRAND -> "&5Grand only";
                        case MAJOR -> "&6Major+";
                        case MINOR -> "&7Any rank";
                    },
                    "",
                    "&eClick &7to select"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    p.closeInventory();
                    onPick.accept(s);
                });
            slot++;
        }
    }
}
