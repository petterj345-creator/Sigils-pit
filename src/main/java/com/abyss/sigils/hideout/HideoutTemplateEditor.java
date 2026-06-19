package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-game editor for the starter hideout {@link HideoutTemplate#WORLD}.
 *
 * {@code /abyss hideout edit} drops an admin into the template world in creative
 * with a <b>Boundary Wand</b>:
 *   - left-click a block  → corner A,
 *   - right-click a block → corner B.
 * Once both corners are set, the square world border that encloses them is applied
 * (live, so it's visible) and saved to the template. {@code /abyss hideout spawn}
 * stores where players should appear.
 *
 * The wand is handed out on entry and stripped when the admin leaves the world.
 */
public final class HideoutTemplateEditor implements Listener {

    private static final NamespacedKey WAND_KEY = new NamespacedKey("abyss", "hideout_wand");

    private final AbyssPlugin plugin;
    private final Map<UUID, int[]> cornerA = new HashMap<>();
    private final Map<UUID, int[]> cornerB = new HashMap<>();

    public HideoutTemplateEditor(AbyssPlugin plugin) { this.plugin = plugin; }

    // ============================================================
    // Commands (routed from AbyssCommand, admin-gated there)
    // ============================================================

    /** Open the editor: load/create the template world, teleport in, give the wand. */
    public void openEditor(Player p) {
        World w = loadOrCreateTemplateWorld();
        if (w == null) { p.sendMessage(Text.color("&cCouldn't open the hideout template world.")); return; }

        Location dest = plugin.hideoutTemplate().spawnIn(w);
        if (dest == null) {
            int y = w.getHighestBlockYAt(0, 0) + 1;
            dest = new Location(w, 0.5, y, 0.5);
        }
        p.teleport(dest);
        p.setGameMode(GameMode.CREATIVE);
        giveWand(p);
        p.sendMessage(Text.color("&5&l✦ Hideout Template Editor"));
        p.sendMessage(Text.color("&7Build the starter hideout here. With the &fBoundary Wand&7:"));
        p.sendMessage(Text.color("  &eleft-click &7a block = corner A, &eright-click &7= corner B."));
        p.sendMessage(Text.color("&7Then &f/abyss hideout spawn &7where players should appear,"));
        p.sendMessage(Text.color("&7and &f/abyss leave &7when done."));
    }

    /** Set the template spawn to the admin's current position. */
    public void setSpawn(Player p) {
        if (!p.getWorld().getName().equals(HideoutTemplate.WORLD)) {
            p.sendMessage(Text.color("&cRun this inside the template (&f/abyss hideout edit&c)."));
            return;
        }
        plugin.hideoutTemplate().setSpawn(p.getLocation());
        p.getWorld().setSpawnLocation(p.getLocation());
        p.sendMessage(Text.color("&aHideout spawn set. New hideouts will start players here."));
    }

    /** Smallest boundary the editor will set — stops a stray double-click trapping you in a 1×1. */
    private static final int MIN_BORDER = 8;

    /** Manually set a square border of the given size, centred on the admin. */
    public void setBorderSize(Player p, int size) {
        if (!p.getWorld().getName().equals(HideoutTemplate.WORLD)) {
            p.sendMessage(Text.color("&cRun this inside the template (&f/abyss hideout edit&c)."));
            return;
        }
        double cx = p.getLocation().getBlockX() + 0.5;
        double cz = p.getLocation().getBlockZ() + 0.5;
        applyBorder(p, cx, cz, size);
    }

    /** Remove the boundary: reset the live border to default and clear the saved one. */
    public void resetBorder(Player p) {
        plugin.hideoutTemplate().clearBorder();
        World w = Bukkit.getWorld(HideoutTemplate.WORLD);
        if (w != null) w.getWorldBorder().reset();   // back to the vanilla 59,999,968 default
        cornerA.remove(p.getUniqueId());
        cornerB.remove(p.getUniqueId());
        p.sendMessage(Text.color("&aBoundary removed. &7New hideouts use &fhideout.border-size &7from config."));
    }

    // ============================================================
    // World + wand
    // ============================================================

    private World loadOrCreateTemplateWorld() {
        World w = Bukkit.getWorld(HideoutTemplate.WORLD);
        if (w != null) return w;

        boolean firstTime = !plugin.hideoutTemplate().worldExists();
        WorldCreator wc = new WorldCreator(HideoutTemplate.WORLD);
        wc.environment(World.Environment.NORMAL);
        wc.type(WorldType.FLAT);
        wc.generateStructures(false);
        w = Bukkit.createWorld(wc);
        if (w == null) return null;

        w.setAutoSave(true);
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setTime(6000);
        if (firstTime) {
            int y = w.getHighestBlockYAt(0, 0) + 1;
            w.setSpawnLocation(0, y, 0);
        }
        // Reflect any saved border so the admin sees it immediately.
        HideoutTemplate t = plugin.hideoutTemplate();
        if (t.hasBorder()) {
            WorldBorder border = w.getWorldBorder();
            border.setCenter(t.borderCenterX(), t.borderCenterZ());
            border.setSize(t.borderSize());
        }
        return w;
    }

    private void giveWand(Player p) {
        for (ItemStack s : p.getInventory().getContents()) if (isWand(s)) return;
        p.getInventory().addItem(createWand());
    }

    private void removeWands(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (isWand(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
        }
    }

    private static ItemStack createWand() {
        ItemStack s = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.color("&6&lHideout Boundary Wand"));
            m.setLore(List.of(
                    Text.color("&eLeft-click &7a block → corner A"),
                    Text.color("&eRight-click &7a block → corner B"),
                    Text.color("&7Sets the hideout's square boundary.")));
            m.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            s.setItemMeta(m);
        }
        return s;
    }

    private static boolean isWand(ItemStack s) {
        if (s == null || s.getType() != Material.BLAZE_ROD) return false;
        ItemMeta m = s.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
    }

    // ============================================================
    // Events
    // ============================================================

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!isWand(p.getInventory().getItemInMainHand())) return;
        if (!p.hasPermission("abyss.admin")) return;
        if (!p.getWorld().getName().equals(HideoutTemplate.WORLD)) return;

        Action a = e.getAction();
        Block b = e.getClickedBlock();

        if (a == Action.LEFT_CLICK_BLOCK && b != null) {
            e.setCancelled(true);
            cornerA.put(p.getUniqueId(), new int[]{ b.getX(), b.getZ() });
            p.sendMessage(Text.color("&aCorner A set at &f" + b.getX() + ", " + b.getZ() + "&a."));
            tryApply(p);
        } else if (a == Action.RIGHT_CLICK_BLOCK && b != null) {
            e.setCancelled(true);
            cornerB.put(p.getUniqueId(), new int[]{ b.getX(), b.getZ() });
            p.sendMessage(Text.color("&aCorner B set at &f" + b.getX() + ", " + b.getZ() + "&a."));
            tryApply(p);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.RIGHT_CLICK_AIR) {
            e.setCancelled(true);
        }
    }

    /** When both corners are set, compute and apply the enclosing square border. */
    private void tryApply(Player p) {
        int[] a = cornerA.get(p.getUniqueId());
        int[] c = cornerB.get(p.getUniqueId());
        if (a == null || c == null) return;

        int minX = Math.min(a[0], c[0]), maxX = Math.max(a[0], c[0]);
        int minZ = Math.min(a[1], c[1]), maxZ = Math.max(a[1], c[1]);
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;
        // +1 because both corner blocks are inside the boundary (inclusive span).
        double size = Math.max(maxX - minX, maxZ - minZ) + 1;
        applyBorder(p, centerX, centerZ, size);
    }

    private void applyBorder(Player p, double centerX, double centerZ, double size) {
        // Floor the size so a stray click (or both corners on one block) can never
        // shrink the border to a cage you can't escape.
        if (size < MIN_BORDER) {
            size = MIN_BORDER;
            p.sendMessage(Text.color("&7(Boundary too small — clamped to the &f" + MIN_BORDER
                    + "&7 minimum. &8/abyss hideout border reset &7to remove it.)"));
        }
        plugin.hideoutTemplate().setBorder(centerX, centerZ, size);
        World w = Bukkit.getWorld(HideoutTemplate.WORLD);
        if (w != null) {
            WorldBorder border = w.getWorldBorder();
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
        p.sendMessage(Text.color("&5Boundary set: &f" + (int) size + "×" + (int) size
                + " &7centred at &f" + (int) centerX + ", " + (int) centerZ + "&7."));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        // Hand the wand out on entry; strip it when leaving the editor world.
        Player p = e.getPlayer();
        if (p.getWorld().getName().equals(HideoutTemplate.WORLD) && p.hasPermission("abyss.admin")) {
            giveWand(p);
        } else {
            removeWands(p);
            cornerA.remove(p.getUniqueId());
            cornerB.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeWands(e.getPlayer());
        cornerA.remove(e.getPlayer().getUniqueId());
        cornerB.remove(e.getPlayer().getUniqueId());
    }
}
