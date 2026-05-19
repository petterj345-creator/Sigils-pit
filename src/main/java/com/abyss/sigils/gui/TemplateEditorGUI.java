package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonMode;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Main editor menu for a single template. Like MythicDungeons' dungeon editor.
 *
 * Layout (54 slots):
 *   10  Player Spawn          11  Boss Spawn           13  Mode toggle (MAP/WAVES)
 *   19  Spawn Points (count)  20  Default Trash Mobs   22  Boss Mob (id + level)
 *   28  Waves (only WAVES)    29  Threshold (only MAP) 31  Time Limit
 *   37  Mobs Per Tick (MAP)   38  Wave Interval (MAP)  40  Max Concurrent
 *   45  TP to template        49  Status / Validation  53  Test (force enter)
 *
 * Everything else is filler glass.
 */
public final class TemplateEditorGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public TemplateEditorGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new TemplateEditorGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&5&l✦ Edit: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // 10 Player spawn
        set(10, icon(template.playerSpawn() == null ? Material.RED_BED : Material.LIME_BED,
                "&aPlayer Spawn",
                template.playerSpawn() == null ? "&cNot set" : "&7" + fmt(template.playerSpawn()),
                "",
                "&eClick &7to set to your current location"),
            e -> {
                if (!(e.getWhoClicked() instanceof Player p)) return;
                if (!isInTemplateWorld(p)) { p.sendMessage(color("&cYou must be inside the template world.")); return; }
                template.setPlayerSpawn(p.getLocation());
                save();
                refresh(p);
                p.sendMessage(color("&aPlayer spawn set."));
            });

        // 11 Boss spawn
        set(11, icon(template.bossSpawn() == null ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                "&cBoss Spawn",
                template.bossSpawn() == null ? "&cNot set" : "&7" + fmt(template.bossSpawn()),
                "",
                "&eClick &7to set to your current location"),
            e -> {
                if (!(e.getWhoClicked() instanceof Player p)) return;
                if (!isInTemplateWorld(p)) { p.sendMessage(color("&cYou must be inside the template world.")); return; }
                template.setBossSpawn(p.getLocation());
                save();
                refresh(p);
                p.sendMessage(color("&aBoss spawn set."));
            });

        // 13 Mode toggle
        set(13, icon(template.mode() == DungeonMode.MAP ? Material.FILLED_MAP : Material.MUSIC_DISC_PIGSTEP,
                "&dMode: &f" + template.mode().name(),
                "&7" + (template.mode() == DungeonMode.MAP
                        ? "Continuous trash, boss at threshold"
                        : "Discrete waves, boss after last wave"),
                "",
                "&eClick &7to switch"),
            e -> {
                template.setMode(template.mode() == DungeonMode.MAP ? DungeonMode.WAVES : DungeonMode.MAP);
                save();
                refresh((Player) e.getWhoClicked());
            });

        // 19 Spawn points
        set(19, icon(Material.ENDER_EYE,
                "&bSpawn Points &7(" + template.spawnPoints().size() + ")",
                "&7Locations where mobs spawn.",
                "&7Each can have its own mob list (MAP mode).",
                "",
                "&eClick &7to manage"),
            e -> SpawnPointsGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 20 Default trash mobs
        set(20, icon(Material.ZOMBIE_HEAD,
                "&7Default Trash Mobs &7(" + template.defaultTrashMobs().size() + ")",
                "&7Fallback mob list for spawn points",
                "&7that don't define their own.",
                "",
                "&eClick &7to manage"),
            e -> MobListGUI.openFor(plugin, (Player) e.getWhoClicked(), template,
                    template.defaultTrashMobs(), "Default Trash Mobs"));

        // 22 Boss mob
        set(22, icon(Material.WITHER_SKELETON_SKULL,
                "&4Boss Mob",
                "&7ID: &f" + template.bossMobId(),
                "&7Level: &f" + template.bossLevel(),
                "",
                "&eLeft-click &7to pick mob",
                "&eRight-click &7to change level"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    AnvilInput.open(plugin, p, "&fBoss Level", String.valueOf(template.bossLevel()), s -> {
                        try { template.setBossLevel(Integer.parseInt(s)); save(); }
                        catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                        open(p);
                    });
                } else {
                    // Use the picker. The count from MobConfigGUI is ignored
                    // for boss (always 1), but we re-use level if the user set one.
                    MobPickerGUI.openFor(plugin, p, picked -> {
                        if (picked != null) {
                            template.setBossMobId(picked.mythicId());
                            template.setBossLevel(picked.level());
                            save();
                        }
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                    }, () -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p)));
                }
            });

        // 28 Waves (only in WAVES mode)
        if (template.mode() == DungeonMode.WAVES) {
            set(28, icon(Material.GOLDEN_HORSE_ARMOR,
                    "&6Waves &7(" + template.waves().size() + ")",
                    "&7Discrete waves; next starts after",
                    "&7all mobs in the current wave die.",
                    "",
                    "&eClick &7to manage"),
                e -> WavesGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
        } else {
            // 29 Threshold (only in MAP)
            set(29, icon(Material.REDSTONE,
                    "&cKill Threshold",
                    "&7Mobs to kill before boss: &f" + template.mobsBeforeBoss(),
                    "",
                    "&eClick &7to change"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    AnvilInput.open(plugin, p, "&fKills before boss", String.valueOf(template.mobsBeforeBoss()), s -> {
                        try { template.setMobsBeforeBoss(Integer.parseInt(s)); save(); }
                        catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                        open(p);
                    });
                });
        }

        // 31 Time limit
        set(31, icon(Material.CLOCK,
                "&eTime Limit",
                "&7Minutes: &f" + template.timeLimitMinutes(),
                "",
                "&eClick &7to change"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                AnvilInput.open(plugin, p, "&fTime limit (minutes)", String.valueOf(template.timeLimitMinutes()), s -> {
                    try { template.setTimeLimitMinutes(Integer.parseInt(s)); save(); }
                    catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                    open(p);
                });
            });

        // MAP-only tuning
        if (template.mode() == DungeonMode.MAP) {
            set(37, icon(Material.SPAWNER,
                    "&7Mobs per Tick",
                    "&7Currently: &f" + template.mobsPerWave(),
                    "&7How many trash to spawn each interval.",
                    "",
                    "&eClick &7to change"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    AnvilInput.open(plugin, p, "&fMobs per spawn tick", String.valueOf(template.mobsPerWave()), s -> {
                        try { template.setMobsPerWave(Integer.parseInt(s)); save(); }
                        catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                        open(p);
                    });
                });

            set(38, icon(Material.REPEATER,
                    "&7Wave Interval",
                    "&7Currently: &f" + template.waveIntervalSeconds() + "s",
                    "",
                    "&eClick &7to change"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    AnvilInput.open(plugin, p, "&fSeconds between ticks", String.valueOf(template.waveIntervalSeconds()), s -> {
                        try { template.setWaveIntervalSeconds(Integer.parseInt(s)); save(); }
                        catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                        open(p);
                    });
                });
        }

        set(40, icon(Material.OBSIDIAN,
                "&8Max Concurrent Mobs",
                "&7Currently: &f" + template.maxConcurrentMobs(),
                "&7Cap to prevent server lag.",
                "",
                "&eClick &7to change"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                AnvilInput.open(plugin, p, "&fMax concurrent mobs", String.valueOf(template.maxConcurrentMobs()), s -> {
                    try { template.setMaxConcurrentMobs(Integer.parseInt(s)); save(); }
                    catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                    open(p);
                });
            });

        // 45 Teleport to template
        set(45, icon(Material.ENDER_PEARL,
                "&dTeleport to Template",
                "&7Brings you into the template world",
                "&7to build/edit."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                World w = plugin.templates().loadWorld(template);
                if (w == null) { p.sendMessage(color("&cWorld is missing on disk!")); return; }
                p.closeInventory();
                int y = w.getHighestBlockYAt(0, 0) + 1;
                p.teleport(new Location(w, 0.5, y, 0.5));
                p.setGameMode(GameMode.CREATIVE);
                p.sendMessage(color("&aTeleported into &f" + template.name() + "&a."));
            });

        // 47 Rewards
        set(47, icon(Material.CHEST,
                "&6&lRewards",
                "&7Item pool, money, and XP rewarded",
                "&7when the dungeon is completed.",
                "&7Items in pool: &f" + template.rewardPool().size(),
                "",
                "&eClick &7to edit"),
            e -> RewardsGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 49 Status
        String err = template.validationError();
        set(49,
            icon(err == null ? Material.LIME_WOOL : Material.RED_WOOL,
                err == null ? "&a&l✓ Ready to play" : "&c&l✗ Not playable",
                err == null ? "&7All required fields are set." : "&c" + err),
            null);

        // 50 Lives
        set(50, icon(Material.TOTEM_OF_UNDYING, "&c&l❤ Lives Per Player",
                "&7Currently: &f" + (template.lives() == 0 ? "&aunlimited" : template.lives()),
                "&7Players lose one on death.",
                "&7Set to 0 for unlimited (just respawn).",
                "",
                "&eClick &7to change"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                com.abyss.sigils.gui.AnvilInput.open(plugin, p,
                        "&fLives (0 = unlimited)",
                        String.valueOf(template.lives()),
                        text -> {
                            try { template.setLives(Integer.parseInt(text)); save(); }
                            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> openFor(plugin, p, template));
                        });
            });

        // 51 Keep Inventory toggle
        set(51, icon(template.keepInventory() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&fKeep Inventory: " + (template.keepInventory() ? "&aON" : "&cOFF"),
                "&7Players keep their items when they die.",
                "",
                "&eClick &7to toggle"),
            e -> {
                template.setKeepInventory(!template.keepInventory());
                save();
                refresh((Player) e.getWhoClicked());
            });

        // 53 Test
        set(53, icon(Material.NETHER_STAR,
                "&6&lTest This Dungeon",
                "&7Forces a dungeon start for you only.",
                err == null ? "&aReady" : "&cFix validation first"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (err != null) { p.sendMessage(color("&cFix validation first: " + err)); return; }
                p.closeInventory();
                plugin.dungeonManager().start(List.of(p), template);
            });
    }

    private boolean isInTemplateWorld(Player p) {
        return p.getWorld().getName().equals(template.worldName());
    }

    private void save() { plugin.templates().save(template); }

    private static String fmt(Location l) {
        return String.format("%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ());
    }
}
