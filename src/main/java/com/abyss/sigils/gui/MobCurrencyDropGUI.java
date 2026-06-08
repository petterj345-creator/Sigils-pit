package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.AbyssCurrency;
import com.abyss.sigils.dungeon.MapMod;
import com.abyss.sigils.integration.MobDropEntry;
import io.lumine.mythic.api.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Configure a map-modifier currency drop on a specific MythicMob. Same UX as
 * {@link MobMapDropGUI}: tweak amount + chance, then save. Stored by the plugin
 * (mobdrops.yml), not MythicMobs' files.
 */
public final class MobCurrencyDropGUI extends EditorGUI.Holder {

    private final AbyssPlugin plugin;
    private final MythicMob mob;
    private final MapMod mod;
    private int amount = 1;
    private double chance = 0.05;

    public MobCurrencyDropGUI(AbyssPlugin plugin, MythicMob mob, MapMod mod) {
        this.plugin = plugin;
        this.mob = mob;
        this.mod = mod;
    }

    public static void openFor(AbyssPlugin plugin, Player p, MythicMob m, MapMod mod) {
        new MobCurrencyDropGUI(plugin, m, mod).open(p);
    }

    @Override protected String title() {
        return color("&5Currency drop: " + mob.getInternalName());
    }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        set(4, AbyssCurrency.create(mod), null);

        set(20, icon(Material.GHAST_TEAR, "&fAmount: &e" + amount,
                "&7How many drop on a successful roll.",
                "",
                "&eLeft-click &7+1   &eRight-click &7-1"),
            e -> {
                if (e.isRightClick()) amount = Math.max(1, amount - 1);
                else amount = Math.min(64, amount + 1);
                refresh((Player) e.getWhoClicked());
            });

        set(24, icon(Material.GOLD_NUGGET, "&fChance: &e" + (chance * 100) + "%",
                "&7Probability the drop fires.",
                "",
                "&eLeft-click &7type a new chance",
                "&eRight-click &7+5%   &eDrop key &7-5%"),
            e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isRightClick()) {
                    chance = Math.min(1.0, chance + 0.05);
                    refresh(p);
                } else if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                        || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
                    chance = Math.max(0.0, chance - 0.05);
                    refresh(p);
                } else {
                    ChatInput.prompt(plugin, p, "&fChance (0.0 to 1.0)",
                            String.valueOf(chance), text -> {
                        try {
                            double c = Double.parseDouble(text);
                            chance = Math.max(0, Math.min(1, c));
                        } catch (NumberFormatException ex) { p.sendMessage(color("&cMust be a number 0..1")); }
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> open(p));
                    });
                }
            });

        set(49, icon(Material.LIME_WOOL, "&a&lAdd Currency Drop",
                "&7" + mob.getInternalName() + " will drop &f" + mod.id() + " &7currency",
                "&7" + (int) Math.round(chance * 100) + "% of the time on death.",
                "",
                "&8Stored by the plugin — your mob files",
                "&8are never modified."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                plugin.mobDrops().add(mob.getInternalName(),
                        new MobDropEntry(MobDropEntry.Kind.CURRENCY, mod.id(), 1, amount, amount, chance));
                p.sendMessage(color("&aCurrency drop added to &f" + mob.getInternalName() + "&a."));
                p.closeInventory();
            });

        set(45, icon(Material.ARROW, "&7← Back"),
            e -> MobDropMenuGUI.openFor(plugin, (Player) e.getWhoClicked(), mob));

        set(53, icon(Material.BARRIER, "&cCancel", "&7Close without saving."),
            e -> ((Player) e.getWhoClicked()).closeInventory());
    }
}
