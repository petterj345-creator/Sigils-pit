package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sets how many souls each MythicMob grants when killed during a ritual.
 *
 * Shows every mob id that is either in the ritual mob list or already has a
 * soul value, plus a picker to add a value for any other MythicMob. Souls are
 * the currency spent in the soul shop.
 */
public final class RitualSoulsGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public RitualSoulsGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new RitualSoulsGUI(plugin, t).open(p);
    }

    @Override protected String title() { return color("&5Souls per Mob: &f" + template.name()); }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // Union of ritual-mob ids and ids that already have a soul value, in order.
        Set<String> ids = new LinkedHashSet<>();
        template.ritualMobs().forEach(m -> ids.add(m.mythicId()));
        ids.addAll(template.ritualMobSouls().keySet());

        int slot = 10;
        for (String id : ids) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            int souls = template.soulsFor(id);
            set(slot, icon(Material.ECHO_SHARD,
                    "&f" + id,
                    "&7Souls on kill: &b" + souls,
                    souls == 0 ? "&c(0 = grants nothing)" : "",
                    "",
                    "&eClick &7→ set souls",
                    "&cShift-click &7→ clear"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    if (e.isShiftClick()) {
                        template.setRitualMobSouls(id, 0);
                        plugin.templates().save(template);
                        refresh(p);
                        return;
                    }
                    ChatInput.prompt(plugin, p, "&fSouls for " + id, String.valueOf(souls), text -> {
                        try { template.setRitualMobSouls(id, Integer.parseInt(text.trim())); plugin.templates().save(template); }
                        catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                        Bukkit.getScheduler().runTask(plugin, () -> open(p));
                    });
                });
            slot++;
        }

        // Add a soul value for any MythicMob
        set(49, icon(Material.EMERALD_BLOCK,
                "&a&lAdd / Set a Mob",
                "&7Pick any MythicMob and set its",
                "&7soul value."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                MobPickerGUI.openFor(plugin, p, picked -> {
                    if (picked != null) {
                        String id = picked.mythicId();
                        ChatInput.prompt(plugin, p, "&fSouls for " + id,
                                String.valueOf(template.soulsFor(id)), text -> {
                            try { template.setRitualMobSouls(id, Integer.parseInt(text.trim())); plugin.templates().save(template); }
                            catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                            Bukkit.getScheduler().runTask(plugin, () -> open(p));
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> open(p));
                    }
                }, () -> Bukkit.getScheduler().runTask(plugin, () -> open(p)));
            });

        // Back
        set(45, icon(Material.ARROW, "&7← Back"),
            e -> RitualEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }
}
