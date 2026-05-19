package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.MobEntry;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;

/**
 * Small 27-slot GUI to configure count + level for a picked MythicMob,
 * then confirm. On confirm, calls {@code onPicked} with the final MobEntry.
 *
 * Layout:
 *   slot 4   - the picked mob (info-only)
 *   slot 11  - count (left-click +1, right-click -1, drop-key type)
 *   slot 13  - level (left-click +1, right-click -1, drop-key type)
 *   slot 15  - confirm (lime)
 *   slot 22  - back to picker
 *   slot 26  - cancel entirely
 */
public final class MobConfigGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final MythicMob mob;
    private final Consumer<MobEntry> onPicked;
    private final Runnable onCancel;
    private final Runnable onBack;
    private int count = 1;
    private int level = 1;

    public MobConfigGUI(AbyssPlugin plugin, MythicMob mob,
                        Consumer<MobEntry> onPicked, Runnable onCancel, Runnable onBack) {
        this.plugin = plugin;
        this.mob = mob;
        this.onPicked = onPicked;
        this.onCancel = onCancel;
        this.onBack = onBack;
    }

    @Override protected String title() {
        return color("&5Configure: &f" + MobPickerGUI.niceName(mob));
    }
    @Override protected int size() { return 27; }

    @Override protected void build(Player viewer) {
        fillBorder();

        set(4, icon(Material.ZOMBIE_HEAD,
                "&f" + MobPickerGUI.niceName(mob),
                "&8" + mob.getInternalName()),
            null);

        set(11, icon(Material.GHAST_TEAR,
                "&fCount: &e" + count,
                "&7How many to spawn at once.",
                "",
                "&eLeft-click &7+1",
                "&eRight-click &7-1",
                "&eDrop key &7type a number"),
            this::handleCountClick);

        set(13, icon(Material.EXPERIENCE_BOTTLE,
                "&fLevel: &e" + level,
                "&7Mob level (passed to MythicMobs).",
                "",
                "&eLeft-click &7+1",
                "&eRight-click &7-1",
                "&eDrop key &7type a number"),
            this::handleLevelClick);

        set(15, icon(Material.LIME_WOOL,
                "&a&l✓ Confirm",
                "&7" + count + "x " + mob.getInternalName() + " at level " + level),
            e -> {
                Player p = (Player) e.getWhoClicked();
                MobEntry result = new MobEntry(mob.getInternalName(), count, level);
                p.closeInventory();
                if (onPicked != null) onPicked.accept(result);
            });

        set(22, icon(Material.ARROW, "&7← Back to picker"),
            e -> {
                ((Player) e.getWhoClicked()).closeInventory();
                if (onBack != null) org.bukkit.Bukkit.getScheduler().runTask(plugin, onBack);
            });

        set(26, icon(Material.BARRIER, "&cCancel"),
            e -> {
                ((Player) e.getWhoClicked()).closeInventory();
                if (onCancel != null) onCancel.run();
            });
    }

    private void handleCountClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
            AnvilInput.open(plugin, p, "&fCount", String.valueOf(count), text -> {
                try { count = Math.max(1, Integer.parseInt(text)); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
            });
            return;
        }
        if (e.isRightClick()) count = Math.max(1, count - 1);
        else count = Math.min(99, count + 1);
        refresh(p);
    }

    private void handleLevelClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
            AnvilInput.open(plugin, p, "&fLevel", String.valueOf(level), text -> {
                try { level = Math.max(1, Integer.parseInt(text)); }
                catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number.")); }
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
            });
            return;
        }
        if (e.isRightClick()) level = Math.max(1, level - 1);
        else level = Math.min(99, level + 1);
        refresh(p);
    }
}
