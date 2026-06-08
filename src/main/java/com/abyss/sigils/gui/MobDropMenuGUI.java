package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.AbyssCurrency;
import com.abyss.sigils.dungeon.DungeonMap;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.dungeon.MapMod;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * For one MythicMob: choose which kind of Abyss drop to add — sigil, map, or
 * currency — then route to the matching config GUI. Reached from the mob
 * browser ({@link MythicDropsGUI}) so it works from /abyss admin and
 * /abyss mythicdrops without entering a template world.
 */
public final class MobDropMenuGUI extends EditorGUI.Holder {

    private enum Page { ROOT, MAPS, CURRENCY }

    private final AbyssPlugin plugin;
    private final MythicMob mob;
    private final Page page;

    public MobDropMenuGUI(AbyssPlugin plugin, MythicMob mob, Page page) {
        this.plugin = plugin;
        this.mob = mob;
        this.page = page;
    }

    public static void openFor(AbyssPlugin plugin, Player p, MythicMob m) {
        new MobDropMenuGUI(plugin, m, Page.ROOT).open(p);
    }

    @Override protected String title() {
        return switch (page) {
            case ROOT     -> color("&5Drops: &f" + mob.getInternalName());
            case MAPS     -> color("&5Map drop → pick template");
            case CURRENCY -> color("&5Currency drop → pick type");
        };
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();
        switch (page) {
            case ROOT -> buildRoot();
            case MAPS -> buildMaps();
            case CURRENCY -> buildCurrency();
        }
    }

    private void buildRoot() {
        set(4, icon(Material.ZOMBIE_HEAD, "&f" + MobPickerGUI.niceName(mob),
                "&7Internal ID: &8" + mob.getInternalName(),
                "", "&7Choose a drop to add:"), null);

        set(20, icon(Material.AMETHYST_SHARD, "&d&lSigil Drop",
                "&7Drop a sigil from this mob.",
                "", "&eClick to configure"),
            e -> MobSigilDropGUI.openFor(plugin, (Player) e.getWhoClicked(), mob));

        set(22, icon(Material.PAPER, "&5&lMap Drop",
                "&7Drop an Abyss Map for a template.",
                "", "&eClick to pick a template"),
            e -> new MobDropMenuGUI(plugin, mob, Page.MAPS).open((Player) e.getWhoClicked()));

        set(24, icon(Material.ECHO_SHARD, "&5&lCurrency Drop",
                "&7Drop map-modifier currency.",
                "", "&eClick to pick a type"),
            e -> new MobDropMenuGUI(plugin, mob, Page.CURRENCY).open((Player) e.getWhoClicked()));

        // Current drops on this mob — shift-click to remove.
        java.util.List<com.abyss.sigils.integration.MobDropEntry> current =
                plugin.mobDrops().entriesFor(mob.getInternalName());
        set(30, icon(Material.BOOK, "&f&lCurrent Drops &7(" + current.size() + ")",
                current.isEmpty() ? "&7None yet." : "&7Shift-click one below to remove it."), null);
        int slot = 37;
        for (int i = 0; i < current.size() && slot <= 43; i++) {
            final int idx = i;
            com.abyss.sigils.integration.MobDropEntry entry = current.get(i);
            set(slot, icon(Material.PAPER, "&f" + entry.describe(),
                    "", "&cShift-click &7→ remove"),
                e -> {
                    Player p = (Player) e.getWhoClicked();
                    if (e.isShiftClick()) {
                        plugin.mobDrops().remove(mob.getInternalName(), idx);
                        p.sendMessage(color("&aRemoved a drop from &f" + mob.getInternalName() + "&a."));
                        refresh(p);
                    }
                });
            slot++;
        }

        set(49, icon(Material.ARROW, "&7← Back to mob list"),
            e -> MythicDropsGUI.openFor(plugin, (Player) e.getWhoClicked()));
    }

    private void buildMaps() {
        int slot = 10;
        for (DungeonTemplate tpl : plugin.templates().all()) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            ItemStack ic = DungeonMap.create(tpl);
            decorate(ic, "&eClick &7→ configure map drop");
            set(slot, ic, e -> MobMapDropGUI.openFor(plugin, (Player) e.getWhoClicked(), mob, tpl));
            slot++;
        }
        if (plugin.templates().all().isEmpty()) {
            set(22, icon(Material.BARRIER, "&cNo templates",
                    "&7Create a dungeon template first."), null);
        }
        set(45, icon(Material.ARROW, "&7← Back"),
            e -> new MobDropMenuGUI(plugin, mob, Page.ROOT).open((Player) e.getWhoClicked()));
    }

    private void buildCurrency() {
        int slot = 10;
        for (MapMod mod : MapMod.values()) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            ItemStack ic = AbyssCurrency.create(mod);
            decorate(ic, "&eClick &7→ configure currency drop");
            set(slot, ic, e -> MobCurrencyDropGUI.openFor(plugin, (Player) e.getWhoClicked(), mob, mod));
            slot++;
        }
        set(45, icon(Material.ARROW, "&7← Back"),
            e -> new MobDropMenuGUI(plugin, mob, Page.ROOT).open((Player) e.getWhoClicked()));
    }

    /** Append a hint line to an item's lore. */
    private void decorate(ItemStack stack, String hint) {
        var meta = stack.getItemMeta();
        if (meta == null) return;
        java.util.List<String> lore = meta.hasLore()
                ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
        lore.add("");
        lore.add(color(hint));
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }
}
