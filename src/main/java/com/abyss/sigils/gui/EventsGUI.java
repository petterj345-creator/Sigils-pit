package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Hub listing the per-map "events" — optional encounters layered onto a
 * template. Right now the only event is the Altar of Souls ritual, but new
 * events get their own icon here rather than cluttering the main editor.
 */
public final class EventsGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final DungeonTemplate template;

    public EventsGUI(AbyssPlugin plugin, DungeonTemplate template) {
        this.plugin = plugin;
        this.template = template;
    }

    public static void openFor(AbyssPlugin plugin, Player p, DungeonTemplate t) {
        new EventsGUI(plugin, t).open(p);
    }

    @Override protected String title() {
        return color("&5&l✦ Events: &f" + template.name());
    }

    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        // 20 Rituals (Altar of Souls)
        set(20, icon(Material.SOUL_LANTERN,
                "&5&l✦ Ritual",
                "&7Altar of Souls setup for maps that",
                "&7carry the ritual modifier.",
                "&7Altars: &f" + template.ritualAltars().size()
                        + "  &7Mobs: &f" + template.ritualMobs().size()
                        + "  &7Shop items: &f" + template.ritualRewardPool().size(),
                "",
                "&8Place altars with the wand.",
                "&eClick &7to configure"),
            e -> RitualEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 22 Maelstrom (breach-style rift event)
        set(22, icon(Material.HEART_OF_THE_SEA,
                "&3&l✦ Maelstrom",
                "&7Breach-style rift setup for maps that",
                "&7carry the maelstrom modifier.",
                "&7Rifts: &f" + template.maelstromCenters().size()
                        + "  &7Mobs: &f" + template.maelstromMobs().size()
                        + "  &7Loot: &f" + template.maelstromLoot().size(),
                "",
                "&8Place rifts with the wand.",
                "&eClick &7to configure"),
            e -> MaelstromEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 24 Reliquary (strongbox-style: unseal, clear guardians, loot)
        set(24, icon(Material.TRIAL_KEY,
                "&6&l✦ Reliquary",
                "&7Sealed-chest setup for maps that",
                "&7carry the reliquary modifier.",
                "&7Reliquaries: &f" + template.reliquaryCenters().size()
                        + "  &7Guards: &f" + template.reliquaryGuards().size()
                        + "  &7Loot: &f" + template.reliquaryLoot().size(),
                "",
                "&8Place reliquaries with the wand.",
                "&eClick &7to configure"),
            e -> ReliquaryEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));

        // 49 Back
        set(49, icon(Material.ARROW, "&7← Back to editor"),
            e -> TemplateEditorGUI.openFor(plugin, (Player) e.getWhoClicked(), template));
    }
}
