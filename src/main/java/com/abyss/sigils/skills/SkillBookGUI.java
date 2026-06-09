package com.abyss.sigils.skills;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.gui.EditorGUI;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tome of Mastery skill tree. One row per {@link SkillCategory}; each node
 * is a {@link SkillType}. Click a node to spend one available point. A respec
 * button refunds every allocated rank back to the pool.
 */
public final class SkillBookGUI extends EditorGUI.Holder {

    /** First slot of each category's row. */
    private static final int ROW_GENERAL   = 10;
    private static final int ROW_RITUAL     = 19;
    private static final int ROW_MAELSTROM  = 28;
    private static final int INFO_SLOT   = 4;
    private static final int RESPEC_SLOT = 49;

    private final AbyssPlugin plugin;

    public SkillBookGUI(AbyssPlugin plugin) { this.plugin = plugin; }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new SkillBookGUI(plugin).open(p);
    }

    @Override protected String title() { return color("&6&l✦ Tome of Mastery"); }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();
        PlayerSkillStore store = plugin.skills();
        java.util.UUID id = viewer.getUniqueId();

        int avail = store.availablePoints(id);
        int total = store.totalPoints(id);
        set(INFO_SLOT, icon(Material.NETHER_STAR,
                "&6&lSkill Points",
                "&7Available: &a" + avail,
                "&7Earned total: &f" + total,
                "",
                "&7Earn &e1 point &7the first time you",
                "&7complete each map."),
            null);

        placeCategory(viewer, SkillCategory.GENERAL, ROW_GENERAL);
        placeCategory(viewer, SkillCategory.RITUAL, ROW_RITUAL);
        placeCategory(viewer, SkillCategory.MAELSTROM, ROW_MAELSTROM);

        set(RESPEC_SLOT, icon(Material.GRINDSTONE,
                "&c&lRespec",
                "&7Refund every allocated point back",
                "&7into your available pool.",
                "",
                store.spentPoints(id) > 0 ? "&eClick &7to reset all skills" : "&8Nothing allocated yet"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (store.spentPoints(p.getUniqueId()) <= 0) return;
                store.respec(p.getUniqueId());
                p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
                p.sendMessage(color("&7Skills reset — your points are available again."));
                refresh(p);
            });
    }

    private void placeCategory(Player viewer, SkillCategory cat, int baseSlot) {
        PlayerSkillStore store = plugin.skills();
        java.util.UUID id = viewer.getUniqueId();
        int slot = baseSlot;
        for (SkillType type : SkillType.values()) {
            if (type.category() != cat) continue;
            int rank = store.rank(id, type);
            boolean maxed = rank >= type.maxRank();
            boolean canBuy = !maxed && store.availablePoints(id) > 0;

            List<String> lore = new ArrayList<>();
            lore.add(color("&8" + cat.display().replaceAll("&.", "") + " skill"));
            lore.add(color("&7" + type.blurb()));
            lore.add("");
            lore.add(color("&7Rank: &f" + rank + "&7/&f" + type.maxRank()));
            if (rank > 0) lore.add(color("&7Current: &a" + type.effectLabel(rank)));
            if (!maxed) lore.add(color("&7Next: &e" + type.effectLabel(rank + 1)));
            lore.add(color("&8" + type.perRankLabel()));
            lore.add("");
            if (maxed) lore.add(color("&a&lMAXED"));
            else if (canBuy) lore.add(color("&eClick &7to spend 1 point"));
            else lore.add(color("&cNo points available"));

            Material mat = rank > 0 ? type.icon() : Material.GRAY_DYE;
            // Show the real icon once at least rank 1, else a muted placeholder
            // unless it's affordable (then show the real icon to invite buying).
            if (canBuy) mat = type.icon();

            final SkillType ft = type;
            set(slot, icon(mat, (maxed ? "&a" : "&f") + type.display()
                            + " &7[" + rank + "/" + type.maxRank() + "]",
                    lore.toArray(String[]::new)),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    if (store.rank(p.getUniqueId(), ft) >= ft.maxRank()) {
                        p.sendMessage(color("&7" + ft.display() + " is already maxed."));
                        return;
                    }
                    if (!store.spend(p.getUniqueId(), ft)) {
                        p.sendMessage(color("&cNo skill points available. &7Complete new maps to earn more."));
                        return;
                    }
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                    p.sendMessage(color("&aLearned &f" + ft.display() + " &7(rank "
                            + store.rank(p.getUniqueId(), ft) + ")&a."));
                    refresh(p);
                });
            slot++;
        }
    }
}
