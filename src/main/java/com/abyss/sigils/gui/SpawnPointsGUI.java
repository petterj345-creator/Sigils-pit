package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.SpawnPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * List of all spawn points for a template.
 *  - Click a point to edit its mob list (MobListGUI).
 *  - Shift-click to delete.
 *  - "Add at my location" button at the bottom.
 *  - "Back" returns to the main editor.
 */
public final class SpawnPointsGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public SpawnPointsGUI(AbyssPlugin plugin, DungeonTemplate t) {
        this.plugin = plugin;
        this.template = t;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new SpawnPointsGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&5Spawn Points: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        List<SpawnPoint> pts = template.spawnPoints();
        int maxDisplay = Math.min(pts.size(), 28); // slots 10..37 inner area
        int slot = 10;
        for (int i = 0; i < maxDisplay; i++) {
            if (slot % 9 == 8) slot += 2; // skip border
            final int idx = i;
            SpawnPoint sp = pts.get(i);
            Location l = sp.location();
            int mobCount = sp.mobs().size();
            ItemStack ic = icon(Material.ENDER_EYE,
                    "&bPoint #" + (i + 1),
                    String.format("&7%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ()),
                    "&7Mobs configured: &f" + (mobCount == 0 ? "&7(uses default)" : mobCount),
                    "",
                    "&eClick &7to edit mob list",
                    "&cShift-click &7to delete",
                    "&aDrop key &7to teleport here");
            set(slot, ic, e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isShiftClick()) {
                    template.removeSpawnPoint(idx);
                    plugin.templates().save(template);
                    refresh(p);
                    p.sendMessage(color("&aSpawn point removed."));
                } else if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                        || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
                    org.bukkit.World w = plugin.templates().loadWorld(template);
                    if (w != null) {
                        p.closeInventory();
                        Location dst = sp.location().clone();
                        dst.setWorld(w);
                        p.teleport(dst);
                        p.sendMessage(color("&aTeleported to spawn point #" + (idx + 1)));
                    }
                } else {
                    MobListGUI.openFor(plugin, p, template, sp.mobs(),
                            "Spawn Point #" + (idx + 1));
                }
            });
            slot++;
        }

        // Add button
        set(49, icon(Material.EMERALD_BLOCK,
                "&a&lAdd Spawn Point",
                "&7Adds a new point at your",
                "&7current location."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (!p.getWorld().getName().equals(template.worldName())) {
                    p.sendMessage(color("&cYou must be in the template world (&f" + template.worldName() + "&c)."));
                    return;
                }
                template.addSpawnPoint(p.getLocation());
                plugin.templates().save(template);
                refresh(p);
                p.sendMessage(color("&aAdded spawn point #" + template.spawnPoints().size() + "."));
            });

        // Back
        set(45, icon(Material.ARROW, "&7← Back to editor"),
            e -> TemplateEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }
}
