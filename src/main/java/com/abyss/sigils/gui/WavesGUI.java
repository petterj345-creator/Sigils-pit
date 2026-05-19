package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.Wave;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * List of waves for a template (WAVES mode).
 *  - Click a wave to edit its mob list (MobListGUI).
 *  - Right-click to set the delay-after.
 *  - Shift-click to delete.
 *  - "Add wave" button at the bottom.
 *  - "Back" returns to the main editor.
 */
public final class WavesGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public WavesGUI(AbyssPlugin plugin, DungeonTemplate t) {
        this.plugin = plugin;
        this.template = t;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new WavesGUI(plugin, t).open(p);
    }

    @Override protected String title() { return color("&5Waves: &f" + template.name()); }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        List<Wave> waves = template.waves();
        int maxDisplay = Math.min(waves.size(), 28);
        int slot = 10;
        for (int i = 0; i < maxDisplay; i++) {
            if (slot % 9 == 8) slot += 2;
            final int idx = i;
            Wave w = waves.get(i);

            ItemStack ic = icon(Material.GOLDEN_HORSE_ARMOR,
                    "&6Wave " + (i + 1),
                    "&7Mobs: &f" + w.mobs().size() + " entries, " + w.totalMobs() + " total",
                    "&7Delay after: &f" + w.delayAfterSeconds() + "s",
                    "",
                    "&eLeft-click &7→ edit mobs",
                    "&eRight-click &7→ change delay",
                    "&cShift-click &7→ delete");
            set(slot, ic, e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isShiftClick()) {
                    template.removeWave(idx);
                    plugin.templates().save(template);
                    refresh(p);
                    return;
                }
                if (e.isRightClick()) {
                    AnvilInput.open(plugin, p, "&fDelay after wave (seconds)",
                            String.valueOf(w.delayAfterSeconds()), s -> {
                        try { w.setDelayAfterSeconds(Integer.parseInt(s)); plugin.templates().save(template); }
                        catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                        open(p);
                    });
                    return;
                }
                // Left-click → edit mobs in this wave
                MobListGUI.openFor(plugin, p, template, w.mobs(),
                        "Wave " + (idx + 1) + " mobs",
                        () -> openFor(plugin, p, template));
            });
            slot++;
        }

        set(49, icon(Material.EMERALD_BLOCK,
                "&a&lAdd Wave",
                "&7Adds a new empty wave."),
            e -> {
                template.addWave(new Wave());
                plugin.templates().save(template);
                refresh((Player) e.getWhoClicked());
            });

        set(45, icon(Material.ARROW, "&7← Back to editor"),
            e -> TemplateEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }
}
