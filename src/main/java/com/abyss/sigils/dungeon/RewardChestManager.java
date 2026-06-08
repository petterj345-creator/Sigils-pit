package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.integration.VaultHook;
import com.abyss.sigils.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles the reward chest that spawns at the boss's death location.
 *
 * On boss death, DungeonManager calls {@link #placeChest(DungeonSession, Location)}.
 * Each party member gets ONE personal chest GUI (rolled the first time they click).
 * After the player closes the chest, any items they didn't take drop at their feet
 * so nothing is silently lost.
 *
 * Each player can only claim once per session; clicking the block again after
 * claiming reopens the same rolled inventory until they close it empty.
 */
public final class RewardChestManager implements Listener {

    private final AbyssPlugin plugin;

    /** Tracks: chest block location → session id (so we know which session owns it). */
    private final Map<Location, UUID> chestToSession = new HashMap<>();

    /** Per-(session,player) → the rolled inventory shown to that player. */
    private final Map<String, Inventory> rolledInventories = new HashMap<>();
    /** Per-(session,player) → already-given money/xp flag, so re-opening doesn't re-pay. */
    private final Set<String> nonItemRewardsGiven = new HashSet<>();
    /** Players currently viewing a reward chest (for the close handler). */
    private final Map<UUID, String> openInventoryKey = new HashMap<>();

    public RewardChestManager(AbyssPlugin plugin) { this.plugin = plugin; }

    /** Called by DungeonManager when the boss dies. Places the chest one block beside the forge. */
    public void placeChest(DungeonSession session, Location forgeLoc) {
        // forgeLoc is the actual forge BLOCK position (floor level). Place the
        // chest at the same Y so both sit on the floor — historically this
        // was at Y+1 (boss head height) and chests would end up floating
        // when the boss died mid-air.
        Location base = forgeLoc.clone();
        int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{2,0,0},{0,0,2}};
        Location placed = null;
        for (int[] o : offsets) {
            Location candidate = base.clone().add(o[0], o[1], o[2]);
            Block b = candidate.getBlock();
            if (b.getType() == Material.AIR) {
                b.setType(Material.CHEST);
                placed = candidate;
                break;
            }
        }
        if (placed == null) {
            // Fall back: replace whatever's there at forge + 1X
            placed = base.clone().add(1, 0, 0);
            placed.getBlock().setType(Material.CHEST);
        }
        chestToSession.put(placed.getBlock().getLocation(), session.id());

        for (UUID id : session.players()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Text.color("&6&l✦ &eA reward chest has appeared. Right-click it to claim."));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.CHEST) return;
        UUID sessionId = chestToSession.get(b.getLocation());
        if (sessionId == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        DungeonSession session = plugin.dungeonManager().sessionById(sessionId);
        if (session == null) { p.sendMessage(Text.color("&cThis chest's dungeon is no longer active.")); return; }
        if (!session.players().contains(p.getUniqueId())) {
            p.sendMessage(Text.color("&cThis chest isn't for you."));
            return;
        }

        String key = sessionId + ":" + p.getUniqueId();
        Inventory inv = rolledInventories.get(key);
        if (inv == null) {
            // First time clicking — roll
            DungeonTemplate t = plugin.templates().get(session.templateName());
            if (t == null) { p.sendMessage(Text.color("&cTemplate missing.")); return; }
            RewardRoller.Roll roll = RewardRoller.rollFor(plugin, t);

            // Give money + XP immediately, once per player per session
            if (!nonItemRewardsGiven.contains(key)) {
                if (roll.money > 0 && VaultHook.deposit(p, roll.money)) {
                    p.sendMessage(Text.color("&aReceived &f" + VaultHook.format(roll.money) + "&a."));
                }
                if (roll.xpLevels > 0) {
                    p.giveExpLevels(roll.xpLevels);
                    p.sendMessage(Text.color("&aReceived &f" + roll.xpLevels + " XP level(s)&a."));
                }
                nonItemRewardsGiven.add(key);
            }

            // Build the chest GUI
            inv = Bukkit.createInventory(null, 27, Text.color("&5&lReward Chest"));
            int slot = 4;
            for (ItemStack item : roll.items) {
                // Distribute roughly centered in the chest
                inv.setItem(Math.min(slot, 22), item);
                slot += 2;
            }
            rolledInventories.put(key, inv);

            if (roll.items.isEmpty() && roll.money == 0 && roll.xpLevels == 0) {
                p.sendMessage(Text.color("&7The Abyss yields nothing this time."));
            }
        }

        openInventoryKey.put(p.getUniqueId(), key);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        String key = openInventoryKey.remove(p.getUniqueId());
        if (key == null) return;
        Inventory inv = e.getInventory();
        // Don't drop anything yet — let them reopen if they want.
        // BUT, if the chest is empty, free it so memory doesn't pile up.
        boolean empty = true;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.getType() != Material.AIR) { empty = false; break; }
        }
        if (empty) {
            rolledInventories.remove(key);
        }
    }

    /** Block players from putting items INTO the reward chest. */
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String key = openInventoryKey.get(p.getUniqueId());
        if (key == null) return;
        // If they click in the top inventory, only allow extraction (take an item) — no placing
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            if (e.getCursor() != null && e.getCursor().getType() != Material.AIR) {
                e.setCancelled(true);
            }
        }
    }

    /** Cleans up all chests/inventories belonging to an ended session. */
    public void cleanupSession(UUID sessionId) {
        chestToSession.entrySet().removeIf(en -> en.getValue().equals(sessionId));
        rolledInventories.keySet().removeIf(k -> k.startsWith(sessionId + ":"));
        nonItemRewardsGiven.removeIf(k -> k.startsWith(sessionId + ":"));
    }
}
