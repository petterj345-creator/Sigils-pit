package com.abyss.sigils.gui;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lightweight chat-input helper. Closes the player's GUI, sends a prompt, and
 * waits for the next chat message — then fires a callback (on the main thread)
 * with the typed text.
 *
 *  ChatInput.prompt(plugin, player,
 *      "&fEnter money min",
 *      String.valueOf(template.moneyMin()),
 *      text -> { ... });
 *
 * Behaviour:
 *  - Typing "cancel" (case-insensitive) silently cancels the prompt. No
 *    callback fires.
 *  - The chat message is consumed (cancelled) — other players don't see it.
 *  - If the player disconnects or doesn't type within {@code TIMEOUT_TICKS},
 *    the prompt expires silently.
 *  - Only one prompt at a time per player; a new prompt cancels the old one.
 *
 * This is a drop-in replacement for {@link AnvilInput#open} for everywhere
 * the rewards / template editors used to open an anvil to capture numbers.
 * Chat input is simpler (no inventory juggling, no confirm slot), works on
 * Bedrock proxies that don't render anvil-rename text well, and the player
 * sees a clear prompt explaining what they're entering.
 */
public final class ChatInput implements Listener {

    /** Auto-expire prompts after 60 seconds so they don't pile up forever. */
    private static final long TIMEOUT_TICKS = 20L * 60L;

    private static ChatInput INSTANCE;

    public static void register(AbyssPlugin plugin) {
        if (INSTANCE != null) return;
        INSTANCE = new ChatInput(plugin);
        Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
    }

    /**
     * Prompt the player to type a value in chat. Closes their current inventory
     * (so they can see the chat box). Fires {@code onConfirm} on the main thread
     * when they reply, or silently cancels if they type {@code cancel}.
     */
    public static void prompt(AbyssPlugin plugin, Player p,
                              String title, String currentValue,
                              Consumer<String> onConfirm) {
        if (INSTANCE == null) register(plugin);
        INSTANCE.promptInternal(plugin, p, title, currentValue, onConfirm);
    }

    private final AbyssPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private ChatInput(AbyssPlugin plugin) { this.plugin = plugin; }

    private static class Session {
        final Consumer<String> onConfirm;
        final int expireTaskId;
        Session(Consumer<String> onConfirm, int expireTaskId) {
            this.onConfirm = onConfirm;
            this.expireTaskId = expireTaskId;
        }
    }

    private void promptInternal(AbyssPlugin plugin, Player p,
                                String title, String currentValue,
                                Consumer<String> onConfirm) {
        // Cancel any previous prompt for this player
        cancelExisting(p.getUniqueId());

        // Close inventory on the main thread so the player can actually see chat.
        // If prompt() was called from a click handler we're already on main,
        // but runTask is safe either way.
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            p.sendMessage(Text.color("&8&m                                              "));
            p.sendMessage(Text.color("&5&l✦ " + title));
            if (currentValue != null && !currentValue.isBlank()) {
                p.sendMessage(Text.color("&7Current value: &f" + currentValue));
            }
            p.sendMessage(Text.color("&7Type the new value in chat, or &fcancel &7to abort."));
            p.sendMessage(Text.color("&8&m                                              "));
        });

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session expired = sessions.remove(p.getUniqueId());
            if (expired != null && p.isOnline()) {
                p.sendMessage(Text.color("&7Input prompt expired."));
            }
        }, TIMEOUT_TICKS).getTaskId();

        sessions.put(p.getUniqueId(), new Session(onConfirm, taskId));
    }

    private void cancelExisting(UUID uuid) {
        Session prev = sessions.remove(uuid);
        if (prev != null) {
            try { Bukkit.getScheduler().cancelTask(prev.expireTaskId); }
            catch (Throwable ignored) {}
        }
    }

    /**
     * Chat events fire async. We need to (a) detect a pending session, (b)
     * cancel the event so the message isn't broadcast, (c) hop back to the
     * main thread to invoke the callback (which usually touches inventories
     * and template state — both must be on main).
     *
     * Priority LOWEST so chat-formatting plugins don't see the message first
     * and re-broadcast it.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;

        // Consume the message so it doesn't appear in chat for anyone
        e.setCancelled(true);
        // Also clear recipients defensively (some chat plugins re-send to a copied set)
        e.getRecipients().clear();

        String text = e.getMessage();
        sessions.remove(p.getUniqueId());
        try { Bukkit.getScheduler().cancelTask(s.expireTaskId); } catch (Throwable ignored) {}

        if (text == null) text = "";
        final String trimmed = text.trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (trimmed.equalsIgnoreCase("cancel")) {
                p.sendMessage(Text.color("&7Input cancelled."));
                return;
            }
            try { s.onConfirm.accept(trimmed); }
            catch (Throwable t) { t.printStackTrace(); }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancelExisting(e.getPlayer().getUniqueId());
    }
}
