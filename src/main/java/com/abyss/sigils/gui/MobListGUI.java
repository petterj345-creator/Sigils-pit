package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.MobEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Generic list editor for a {@code List&lt;MobEntry&gt;}.
 *
 * Used by:
 *  - Default trash mob list (template-wide)
 *  - Per-spawn-point mob lists (MAP mode override)
 *  - Per-wave mob lists (WAVES mode)
 *
 * The list is held by reference: edits in the GUI directly mutate the caller's
 * list. The GUI calls plugin.templates().save(template) after any change.
 */
public final class MobListGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;
    private final List<MobEntry> list;
    private final String label;
    private final Runnable onBack;

    public MobListGUI(AbyssPlugin plugin, DungeonTemplate template,
                      List<MobEntry> list, String label, Runnable onBack) {
        this.plugin = plugin;
        this.template = template;
        this.list = list;
        this.label = label;
        this.onBack = onBack;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate template,
                               List<MobEntry> list, String label) {
        openFor(plugin, p, template, list, label, null);
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate template,
                               List<MobEntry> list, String label, Runnable onBack) {
        Runnable back = onBack != null ? onBack
                : () -> TemplateEditorGUI.openFor(plugin, p, template);
        new MobListGUI(plugin, template, list, label, back).open(p);
    }

    @Override protected String title() {
        return color("&5" + label + " &7(" + list.size() + ")");
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        int slot = 10;
        int maxDisplay = Math.min(list.size(), 28);
        for (int i = 0; i < maxDisplay; i++) {
            if (slot % 9 == 8) slot += 2;
            final int idx = i;
            MobEntry entry = list.get(i);
            ItemStack ic = icon(Material.ZOMBIE_HEAD,
                    "&f" + entry.mythicId(),
                    "&7Count: &f" + entry.count(),
                    "&7Level: &f" + entry.level(),
                    "",
                    "&eLeft-click &7→ change ID",
                    "&eRight-click &7→ change count",
                    "&eDrop key &7→ change level",
                    "&cShift-click &7→ delete");
            set(slot, ic, e -> handleEntryClick(e, idx, entry));
            slot++;
        }

        // Add new entry — open the picker
        set(49, icon(Material.EMERALD_BLOCK,
                "&a&lAdd Mob Entry",
                "&7Browse your MythicMobs",
                "&7and pick one with a search."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                MobPickerGUI.openFor(plugin, p, picked -> {
                    if (picked != null) {
                        list.add(picked);
                        plugin.templates().save(template);
                    }
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                }, () -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p)));
            });

        // Back
        set(45, icon(Material.ARROW, "&7← Back"),
            e -> onBack.run());
    }

    private void handleEntryClick(org.bukkit.event.inventory.InventoryClickEvent e, int idx, MobEntry entry) {
        Player p = (Player) e.getWhoClicked();
        if (e.isShiftClick()) {
            list.remove(idx);
            plugin.templates().save(template);
            refresh(p);
            p.sendMessage(color("&aRemoved entry."));
            return;
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
            ChatInput.prompt(plugin, p, "&fLevel", String.valueOf(entry.level()), s -> {
                try { entry.setLevel(Integer.parseInt(s)); plugin.templates().save(template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                open(p);
            });
            return;
        }
        if (e.isRightClick()) {
            ChatInput.prompt(plugin, p, "&fCount", String.valueOf(entry.count()), s -> {
                try { entry.setCount(Integer.parseInt(s)); plugin.templates().save(template); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                open(p);
            });
            return;
        }
        // Left-click → change ID via picker
        MobPickerGUI.openFor(plugin, p, picked -> {
            if (picked != null) {
                entry.setMythicId(picked.mythicId());
                // keep existing count + level (user said "change ID" not "reconfigure")
                plugin.templates().save(template);
            }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
        }, () -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p)));
    }
}
