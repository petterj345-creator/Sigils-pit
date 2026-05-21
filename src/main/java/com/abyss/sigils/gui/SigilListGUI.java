package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilDraft;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists all sigils as their actual ItemStacks. Click to edit, shift-click to
 * delete, "Create new" button creates a fresh draft.
 *
 * Paginated: 28 sigils per page (the 4x7 inner grid), with prev/next arrows so
 * the full set — minors, majors, and grands — is browsable.
 */
public final class SigilListGUI extends EditorGUI.Holder {

    private static final int PAGE_SIZE = 28;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int CREATE_SLOT = 49;

    private final AbyssPlugin plugin;
    private int page;

    public SigilListGUI(AbyssPlugin plugin, int page) {
        this.plugin = plugin;
        this.page = Math.max(0, page);
    }

    public static void openFor(AbyssPlugin plugin, Player p) {
        new SigilListGUI(plugin, 0).open(p);
    }

    public static void openFor(AbyssPlugin plugin, Player p, int page) {
        new SigilListGUI(plugin, page).open(p);
    }

    @Override protected String title() {
        int total = plugin.sigils().all().size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        return color("&5&lAll Sigils &7(" + (page + 1) + "/" + pages + ")");
    }
    @Override protected int size() { return 54; }

    @Override protected void build(Player viewer) {
        fillBorder();

        List<SigilDefinition> all = new ArrayList<>(plugin.sigils().all());
        int total = all.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, total);

        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            final SigilDefinition def = all.get(i);

            SigilInstance inst = new SigilInstance(def.id(), 1);
            ItemStack stack = SigilItem.toItem(inst);
            if (stack != null) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(color("&7ID: &f" + def.id()));
                    lore.add(color("&7Rank: " + switch (def.rank()) {
                        case GRAND -> "&5&lGRAND";
                        case MAJOR -> "&6MAJOR";
                        case MINOR -> "&7minor";
                    }));
                    lore.add(color("&7Max tier: &f" + def.maxTier()));
                    lore.add("");
                    lore.add(color("&eClick &7to edit"));
                    lore.add(color("&aRight-click &7to get one"));
                    lore.add(color("&cShift-click &7to delete"));
                    meta.setLore(lore);
                    stack.setItemMeta(meta);
                }
            }
            set(slot, stack, e -> {
                Player p = (Player) e.getWhoClicked();
                if (e.isShiftClick()) {
                    plugin.sigils().delete(def.id());
                    p.sendMessage(color("&aDeleted sigil &f" + def.id() + "&a."));
                    refresh(p);
                } else if (e.isRightClick()) {
                    // Hand the player a real T1 copy of this sigil
                    SigilInstance give = new SigilInstance(def.id(), 1);
                    ItemStack item = SigilItem.toItem(give);
                    if (item != null) {
                        var overflow = p.getInventory().addItem(item);
                        for (ItemStack o : overflow.values())
                            p.getWorld().dropItemNaturally(p.getLocation(), o);
                        p.sendMessage(color("&aGave you &f" + def.id() + " &7(T1)&a."));
                    }
                } else {
                    SigilCreatorGUI.openFor(plugin, p, SigilDraft.from(def));
                }
            });
            slot++;
        }

        // Prev / next page arrows
        if (page > 0) {
            set(PREV_SLOT, icon(Material.SPECTRAL_ARROW, "&e← Previous Page",
                    "&7Page " + page + " of " + pages),
                e -> SigilListGUI.openFor(plugin, (Player) e.getWhoClicked(), page - 1));
        }
        if (page < pages - 1) {
            set(NEXT_SLOT, icon(Material.SPECTRAL_ARROW, "&eNext Page →",
                    "&7Page " + (page + 2) + " of " + pages),
                e -> SigilListGUI.openFor(plugin, (Player) e.getWhoClicked(), page + 1));
        }

        set(CREATE_SLOT, icon(Material.EMERALD_BLOCK, "&a&lCreate New Sigil",
                "&7Opens the sigil creator with a fresh draft."),
            e -> {
                Player p = (Player) e.getWhoClicked();
                String id = "new_" + System.currentTimeMillis() % 100000;
                SigilCreatorGUI.openFor(plugin, p, new SigilDraft(id));
            });
    }
}
