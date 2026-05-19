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

    @Override protected int size() { return 45; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // Tells the admin what's currently marked at this block
        List<String> existing = describeMarkers();
        set(4, icon(Material.PAPER, "&fBlock Inspection",
                existing.isEmpty() ? "&7Nothing marked here." : "&7Currently marked as:"),
            null);
        if (!existing.isEmpty()) {
            ItemStack info = icon(Material.PAPER, "&fBlock Inspection",
                    existing.toArray(String[]::new));
            getInventory().setItem(4, info);
        }

        // ---- Row 1: original spawns + spawn point ----

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

        // ---- Row 2: post-boss placements ----

        // 28 → forge spawn (where the lodestone + chest spawn after boss death)
        set(28, icon(Material.LODESTONE,
                sameForgeSpawn() ? "&6Forge Spawn (set)" : "&6Set as Forge Spawn",
                "&7Where the upgrade altar + reward chest",
                "&7spawn when the boss dies.",
                samePlayerSpawn() ? "" : "",
                sameForgeSpawn() ? "&7&oAlready set here" : "&7Click to mark"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                template.setForgeSpawn(centerOf(block));
                plugin.templates().save(template);
                p.sendMessage(color("&aForge spawn set."));
                refresh(p);
            });

        // 30 → exit portal spawn
        set(30, icon(Material.END_GATEWAY,
                sameExitPortalSpawn() ? "&dExit Portal Spawn (set)" : "&dSet as Exit Portal Spawn",
                "&7Where the return portal spawns",
                "&7when the boss dies.",
                "",
                sameExitPortalSpawn() ? "&7&oAlready set here" : "&7Click to mark"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                template.setExitPortalSpawn(centerOf(block));
                plugin.templates().save(template);
                p.sendMessage(color("&aExit portal spawn set."));
                refresh(p);
            });

        // 32 → remove all markers at this block
        if (!existing.isEmpty()) {
            set(32, icon(Material.BARRIER, "&cRemove ALL markers here",
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
        if (samePlayerSpawn())     out.add(color("&8• &aPlayer spawn"));
        if (sameBossSpawn())       out.add(color("&8• &cBoss spawn"));
        if (sameForgeSpawn())      out.add(color("&8• &6Forge spawn"));
        if (sameExitPortalSpawn()) out.add(color("&8• &dExit portal spawn"));
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

    private boolean sameForgeSpawn() {
        return template.forgeSpawn() != null && sameBlock(template.forgeSpawn(), block);
    }

    private boolean sameExitPortalSpawn() {
        return template.exitPortalSpawn() != null && sameBlock(template.exitPortalSpawn(), block);
    }

    private int findSpawnPointIndex() {
        List<SpawnPoint> sps = template.spawnPoints();
        for (int i = 0; i < sps.size(); i++) {
            if (sameBlock(sps.get(i).location(), block)) return i;
        }
        return -1;
    }

    /**
     * Marker locations are stored at "stand on top" position — i.e. one block
     * ABOVE the actual marker block (see {@link #centerOf(Block)} and the
     * various callers that pass {@code player.getLocation()}, whose feet sit
     * on top of the block they're standing on).
     *
     * So when the admin right-clicks a block with the wand and we ask "is this
     * block the player/boss/spawn marker?", we compare the clicked block
     * against the block one tile BELOW the stored location.
     *
     * Without this -1 offset, the inspection would always say "Nothing marked
     * here." even on a block that had just been marked, because floor(65.0)
     * does not equal block.y=64.
     *
     * We also accept an exact match (no offset) as a fallback, just in case
     * something stored a location at exactly block coordinates (legacy data
     * or non-standard callers).
     */
    private static boolean sameBlock(Location loc, Block b) {
        if (loc == null) return false;
        int lx = (int) Math.floor(loc.getX());
        int ly = (int) Math.floor(loc.getY());
        int lz = (int) Math.floor(loc.getZ());
        if (lx != b.getX() || lz != b.getZ()) return false;
        // Standard: stored Y is one above the marker block (stand-on-top).
        if (ly - 1 == b.getY()) return true;
        // Fallback: stored Y equals the marker block exactly.
        return ly == b.getY();
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
        if (t.forgeSpawn() != null && sameBlock(t.forgeSpawn(), b)) {
            t.clearForgeSpawn(); removed++;
        }
        if (t.exitPortalSpawn() != null && sameBlock(t.exitPortalSpawn(), b)) {
            t.clearExitPortalSpawn(); removed++;
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
