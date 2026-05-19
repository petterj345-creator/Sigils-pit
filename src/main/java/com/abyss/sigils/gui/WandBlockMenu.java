package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.SpawnPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu shown when an admin right-clicks a block with the wand.
 * Lets them assign that block as: player spawn, boss spawn, mob spawn point,
 * or jump into mob-config for an existing spawn point at this location.
 *
 * Block coords compare using floored x/y/z and the same world (we only ever
 * call this from the template world).
 */
public final class WandBlockMenu extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;
    private final Block block;

    public WandBlockMenu(AbyssPlugin plugin, DungeonTemplate template, Block block) {
        this.plugin = plugin;
        this.template = template;
        this.block = block;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t, Block b) {
        new WandBlockMenu(plugin, t, b).open(p);
    }

    @Override protected String title() {
        return color("&5Block @ " + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    @Override protected int size() { return 27; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // Tells the admin what's currently marked at this block
        List<String> existing = describeMarkers();
        set(4, icon(Material.PAPER, "&fBlock Inspection",
                existing.isEmpty() ? "&7Nothing marked here." : "&7Currently marked as:"),
            null);
        if (!existing.isEmpty()) {
            // Reuse slot 4's lore — rebuild with concrete state
            ItemStack info = icon(Material.PAPER, "&fBlock Inspection",
                    existing.toArray(String[]::new));
            getInventory().setItem(4, info);
        }

        // 10 → player spawn
        set(10, icon(Material.LIME_BED, "&aSet as Player Spawn",
                samePlayerSpawn() ? "&7&oAlready set here" : "&7Click to mark"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                template.setPlayerSpawn(centerOf(block));
                plugin.templates().save(template);
                p.sendMessage(color("&aPlayer spawn set."));
                refresh(p);
            });

        // 12 → boss spawn
        set(12, icon(Material.LIME_CONCRETE, "&cSet as Boss Spawn",
                sameBossSpawn() ? "&7&oAlready set here" : "&7Click to mark"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                template.setBossSpawn(centerOf(block));
                plugin.templates().save(template);
                p.sendMessage(color("&aBoss spawn set."));
                refresh(p);
            });

        // 14 → add/edit spawn point
        int existingSpawnIdx = findSpawnPointIndex();
        if (existingSpawnIdx >= 0) {
            SpawnPoint sp = template.spawnPoints().get(existingSpawnIdx);
            set(14, icon(Material.ENDER_EYE, "&bEdit Spawn Point",
                    "&7Mobs here: &f" + sp.mobs().size(),
                    "",
                    "&eClick &7to edit mob list"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    p.closeInventory();
                    MobListGUI.openFor(plugin, p, template, sp.mobs(),
                            "Spawn point @ " + block.getX() + "," + block.getY() + "," + block.getZ(),
                            () -> p.closeInventory());
                });
        } else {
            set(14, icon(Material.ENDER_EYE, "&bAdd as Spawn Point",
                    "&7Adds this block as a mob spawn point.",
                    "",
                    "&eClick &7to add"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    template.addSpawnPoint(centerOf(block));
                    plugin.templates().save(template);
                    SpawnPoint sp = template.spawnPoints().get(template.spawnPoints().size() - 1);
                    p.sendMessage(color("&aAdded spawn point. Opening mob list..."));
                    p.closeInventory();
                    MobListGUI.openFor(plugin, p, template, sp.mobs(),
                            "Spawn point @ " + block.getX() + "," + block.getY() + "," + block.getZ(),
                            () -> p.closeInventory());
                });
        }

        // 16 → open main editor
        set(16, icon(Material.WRITABLE_BOOK, "&dOpen Template Editor",
                "&7Full settings menu."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                TemplateEditorGUI.openFor(plugin, p, template);
            });

        // 22 → remove all markers at this block
        if (!existing.isEmpty()) {
            set(22, icon(Material.BARRIER, "&cRemove ALL markers here",
                    "&7Removes whatever this block",
                    "&7was marked as."),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    int removed = removeMarkersAt(template, block);
                    plugin.templates().save(template);
                    p.sendMessage(color("&aRemoved " + removed + " marker(s)."));
                    refresh(p);
                });
        }
    }

    private List<String> describeMarkers() {
        List<String> out = new ArrayList<>();
        if (samePlayerSpawn()) out.add(color("&8• &aPlayer spawn"));
        if (sameBossSpawn())   out.add(color("&8• &cBoss spawn"));
        int idx = findSpawnPointIndex();
        if (idx >= 0) {
            SpawnPoint sp = template.spawnPoints().get(idx);
            out.add(color("&8• &bSpawn point &7(" + sp.mobs().size() + " mob entries)"));
        }
        return out;
    }

    private boolean samePlayerSpawn() {
        return template.playerSpawn() != null && sameBlock(template.playerSpawn(), block);
    }

    private boolean sameBossSpawn() {
        return template.bossSpawn() != null && sameBlock(template.bossSpawn(), block);
    }

    private int findSpawnPointIndex() {
        List<SpawnPoint> sps = template.spawnPoints();
        for (int i = 0; i < sps.size(); i++) {
            if (sameBlock(sps.get(i).location(), block)) return i;
        }
        return -1;
    }

    private static boolean sameBlock(Location loc, Block b) {
        if (loc == null) return false;
        return Math.floor(loc.getX()) == b.getX()
                && Math.floor(loc.getY()) == b.getY()
                && Math.floor(loc.getZ()) == b.getZ();
    }

    private static Location centerOf(Block b) {
        return new Location(b.getWorld(),
                b.getX() + 0.5,
                b.getY() + 1.0, // stand on top
                b.getZ() + 0.5);
    }

    /**
     * Removes every marker (player spawn / boss spawn / spawn point) that points
     * at the given block. Returns the count removed.
     */
    public static int removeMarkersAt(DungeonTemplate t, Block b) {
        int removed = 0;
        if (t.playerSpawn() != null && sameBlock(t.playerSpawn(), b)) {
            t.clearPlayerSpawn(); removed++;
        }
        if (t.bossSpawn() != null && sameBlock(t.bossSpawn(), b)) {
            t.clearBossSpawn(); removed++;
        }
        List<SpawnPoint> sps = t.spawnPoints();
        for (int i = sps.size() - 1; i >= 0; i--) {
            if (sameBlock(sps.get(i).location(), b)) {
                sps.remove(i); removed++;
            }
        }
        return removed;
    }
}
