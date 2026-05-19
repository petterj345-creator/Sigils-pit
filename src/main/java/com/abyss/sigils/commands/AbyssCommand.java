package com.abyss.sigils.commands;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.dungeon.DungeonTemplate;
import com.abyss.sigils.gui.TemplateEditorGUI;
import com.abyss.sigils.sigils.SigilDefinition;
import com.abyss.sigils.sigils.SigilInstance;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.util.Text;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.*;

/**
 * /abyss + /sigils command dispatcher.
 *
 * Most admin work happens via the GUI (open with /abyss edit &lt;name&gt;).
 * Commands kept here:
 *   Player: sigils, leave, enterabyss
 *   Admin:  create, edit, delete, list, reload, givesigil, givedust
 */
public final class AbyssCommand implements CommandExecutor, TabCompleter {

    private final AbyssPlugin plugin;
    public AbyssCommand(AbyssPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sigils")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            plugin.socketGUI().openFor(p);
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help"       -> sendHelp(sender);
            case "sigils"     -> { if (sender instanceof Player p) plugin.socketGUI().openFor(p); }
            case "leave"      -> { if (sender instanceof Player p) plugin.dungeonManager().leave(p); }
            case "enterabyss" -> { if (sender instanceof Player p) plugin.dungeonManager().start(List.of(p)); }
            case "create"     -> handleCreate(sender, args);
            case "edit"       -> handleEdit(sender, args);
            case "delete"     -> handleDelete(sender, args);
            case "list"       -> handleList(sender);
            case "givesigil"  -> handleGiveSigil(sender, args);
            case "givedust"   -> handleGiveDust(sender, args);
            case "givebook"   -> handleGiveBook(sender, args);
            case "givemap"    -> handleGiveMap(sender, args);
            case "sigil"      -> handleSigilSubcommand(sender, args);
            case "mythicdrops" -> handleMythicDrops(sender);
            case "markers"    -> handleMarkers(sender);
            case "setportal"  -> handleSetPortal(sender);
            case "removeportal","unsetportal" -> handleRemovePortal(sender);
            case "reload"     -> {
                if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return true; }
                plugin.reloadConfig();
                plugin.sigils().load();
                plugin.templates().loadAll();
                // Push fresh metadata to every player so renamed/edited
                // templates show up immediately on their existing maps.
                if (plugin.mapRefreshListener() != null) {
                    plugin.mapRefreshListener().refreshAllOnline();
                }
                if (plugin.portalHologram() != null) {
                    plugin.portalHologram().refreshFromConfig();
                }
                sender.sendMessage(Text.color("&aAbyssSigils reloaded."));
            }
            default -> sender.sendMessage(Text.color("&cUnknown subcommand. Try /abyss help"));
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }
        if (args.length < 2) { p.sendMessage(Text.color("&7Usage: &f/abyss create <name>")); return; }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]+")) {
            p.sendMessage(Text.color("&cName must be lowercase letters, digits, or underscores."));
            return;
        }
        try {
            DungeonTemplate t = plugin.templates().create(name);
            p.sendMessage(Text.color("&aCreated template &f" + name + "&a. Opening editor..."));
            World w = Bukkit.getWorld(t.worldName());
            if (w != null) {
                int y = w.getHighestBlockYAt(0, 0) + 1;
                p.teleport(new Location(w, 0.5, y, 0.5));
                p.setGameMode(GameMode.CREATIVE);
                plugin.editorWandListener().giveWand(p);
            }
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> TemplateEditorGUI.openFor(plugin, p, t), 5L);
        } catch (IOException ex) {
            p.sendMessage(Text.color("&cCouldn't create: " + ex.getMessage()));
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }
        if (args.length < 2) { p.sendMessage(Text.color("&7Usage: &f/abyss edit <name>")); return; }
        DungeonTemplate t = plugin.templates().get(args[1]);
        if (t == null) { p.sendMessage(Text.color("&cUnknown template.")); return; }
        // Load world + tp + give wand + open editor
        org.bukkit.World w = plugin.templates().loadWorld(t);
        if (w == null) { p.sendMessage(Text.color("&cTemplate world missing on disk.")); return; }
        int y = w.getHighestBlockYAt(0, 0) + 1;
        p.teleport(new org.bukkit.Location(w, 0.5, y, 0.5));
        plugin.editorWandListener().giveWand(p);
        com.abyss.sigils.gui.TemplateEditorGUI.openFor(plugin, p, t);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Text.color("&7Usage: &f/abyss delete <name> confirm"));
            sender.sendMessage(Text.color("&cThis permanently deletes the template world and config."));
            return;
        }
        DungeonTemplate t = plugin.templates().get(args[1]);
        if (t == null) { sender.sendMessage(Text.color("&cUnknown template.")); return; }
        try {
            plugin.templates().delete(t);
            sender.sendMessage(Text.color("&aDeleted template " + t.name() + "."));
        } catch (IOException ex) {
            sender.sendMessage(Text.color("&cFailed: " + ex.getMessage()));
        }
    }

    private void handleList(CommandSender sender) {
        Collection<DungeonTemplate> all = plugin.templates().all();
        if (all.isEmpty()) { sender.sendMessage(Text.color("&7No templates yet. /abyss create <name>")); return; }
        sender.sendMessage(Text.color("&5&lTemplates:"));
        for (DungeonTemplate t : all) {
            String state = t.isPlayable() ? "&a✓" : "&c✗ " + t.validationError();
            sender.sendMessage(Text.color("  &f" + t.name() + " &8(&f" + t.mode() + "&8) " + state));
        }
    }

    private void handleGiveSigil(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (args.length < 3) {
            sender.sendMessage(Text.color("&7Usage: &f/abyss givesigil <player> <sigilId|random> [tier]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Text.color("&cUnknown player.")); return; }
        SigilDefinition def = args[2].equalsIgnoreCase("random")
                ? plugin.sigils().random() : plugin.sigils().get(args[2]);
        if (def == null) { sender.sendMessage(Text.color("&cUnknown sigil id.")); return; }
        int tier = 1;
        if (args.length >= 4) try { tier = Math.max(1, Math.min(def.maxTier(), Integer.parseInt(args[3]))); } catch (NumberFormatException ignored) {}
        ItemStack item = SigilItem.toItem(new SigilInstance(def.id(), tier));
        for (ItemStack o : target.getInventory().addItem(item).values())
            target.getWorld().dropItemNaturally(target.getLocation(), o);
        sender.sendMessage(Text.color("&aGave " + target.getName() + " a T" + tier + " " + def.id() + "."));
    }

    private void handleGiveDust(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (args.length < 3) { sender.sendMessage(Text.color("&7Usage: &f/abyss givedust <player> <amount>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Text.color("&cUnknown player.")); return; }
        int amount; try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException e) { return; }
        target.getInventory().addItem(SigilItem.createDust(amount));
        sender.sendMessage(Text.color("&aGave " + target.getName() + " " + amount + " Sigil Dust."));
    }

    private void handleGiveBook(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (args.length < 2) {
            sender.sendMessage(Text.color("&7Usage: &f/abyss givebook <player> [tier]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Text.color("&cUnknown player.")); return; }
        int tier = 1;
        if (args.length >= 3) {
            try { tier = Integer.parseInt(args[2]); }
            catch (NumberFormatException ex) {
                sender.sendMessage(Text.color("&cTier must be a number."));
                return;
            }
            int max = plugin.bookTiers().maxTier();
            if (tier < 1) tier = 1;
            if (tier > max) tier = max;
        }
        target.getInventory().addItem(SigilItem.createBook(tier));
        sender.sendMessage(Text.color("&aGave " + target.getName() + " a Book of Sigils (T" + tier + ")."));
    }

    /**
     * /abyss givemap &lt;template&gt; [player] [amount]
     *
     * If [player] is omitted, gives to the sender (must be a player). Amount
     * defaults to 1. Maps stack as paper items but the PDC ensures they keep
     * pointing to the right template.
     */
    private void handleGiveMap(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (args.length < 2) {
            sender.sendMessage(Text.color("&7Usage: &f/abyss givemap <template> [player] [amount]"));
            return;
        }
        String tplName = args[1].toLowerCase(Locale.ROOT);
        com.abyss.sigils.dungeon.DungeonTemplate tpl = plugin.templates().get(tplName);
        if (tpl == null) {
            sender.sendMessage(Text.color("&cUnknown template: " + tplName));
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { sender.sendMessage(Text.color("&cUnknown player.")); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Text.color("&cMust specify a player from console."));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
            catch (NumberFormatException ex) {
                sender.sendMessage(Text.color("&cAmount must be a number."));
                return;
            }
        }
        org.bukkit.inventory.ItemStack map = com.abyss.sigils.dungeon.DungeonMap.create(tpl);
        map.setAmount(amount);
        var overflow = target.getInventory().addItem(map);
        for (var o : overflow.values()) target.getWorld().dropItemNaturally(target.getLocation(), o);
        sender.sendMessage(Text.color("&aGave " + target.getName() + " " + amount + "x Abyss Map (" + tplName + ")."));
    }

    private void handleSigilSubcommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }
        if (args.length < 2) {
            sender.sendMessage(Text.color("&7Usage: &f/abyss sigil <list|create|edit <id>>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> com.abyss.sigils.gui.SigilListGUI.openFor(plugin, p);
            case "create" -> {
                String id = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "new_" + System.currentTimeMillis() % 100000;
                if (!id.matches("[a-z0-9_]+")) {
                    p.sendMessage(Text.color("&cInvalid id. Use lowercase letters/digits/underscores."));
                    return;
                }
                com.abyss.sigils.gui.SigilCreatorGUI.openFor(plugin, p, new com.abyss.sigils.sigils.SigilDraft(id));
            }
            case "edit" -> {
                if (args.length < 3) { p.sendMessage(Text.color("&7Usage: &f/abyss sigil edit <id>")); return; }
                com.abyss.sigils.sigils.SigilDefinition def = plugin.sigils().get(args[2]);
                if (def == null) { p.sendMessage(Text.color("&cUnknown sigil.")); return; }
                com.abyss.sigils.gui.SigilCreatorGUI.openFor(plugin, p,
                        com.abyss.sigils.sigils.SigilDraft.from(def));
            }
            default -> p.sendMessage(Text.color("&cUnknown. Try /abyss sigil list|create|edit <id>"));
        }
    }

    private void handleMythicDrops(CommandSender sender) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }
        if (plugin.mythicDropWriter() == null) {
            p.sendMessage(Text.color("&cMythicMobs isn't installed."));
            return;
        }
        com.abyss.sigils.gui.MythicDropsGUI.openFor(plugin, p);
    }

    /** Manually refresh the visual editor markers in the current template world. */
    private void handleMarkers(CommandSender sender) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }
        String wname = p.getWorld().getName();
        if (!wname.startsWith("abyss_tpl_")) {
            p.sendMessage(Text.color("&cYou're not in an editor world."));
            return;
        }
        DungeonTemplate t = plugin.templates().get(wname.substring("abyss_tpl_".length()));
        if (t == null) { p.sendMessage(Text.color("&cTemplate not loaded.")); return; }
        plugin.editorMarkers().refreshFor(t);
        p.sendMessage(Text.color("&aMarkers refreshed."));
    }

    /**
     * /abyss setportal — sets the overworld portal location to the block the
     * sender is looking at (up to 8 blocks away). Saves config, refreshes the
     * floating hologram, and reports the new coords. Also auto-detects the
     * block material so the player doesn't have to edit portal.block-type
     * separately.
     */
    private void handleSetPortal(CommandSender sender) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return; }

        org.bukkit.block.Block target = p.getTargetBlockExact(8);
        if (target == null || target.getType().isAir()) {
            p.sendMessage(Text.color("&cLook at a block within 8 blocks to use as the portal."));
            return;
        }

        plugin.getConfig().set("portal.world", target.getWorld().getName());
        plugin.getConfig().set("portal.x", target.getX());
        plugin.getConfig().set("portal.y", target.getY());
        plugin.getConfig().set("portal.z", target.getZ());
        plugin.getConfig().set("portal.block-type", target.getType().name());
        plugin.saveConfig();

        // Re-spawn the hologram at the new spot
        if (plugin.portalHologram() != null) {
            plugin.portalHologram().refreshFromConfig();
        }

        p.sendMessage(Text.color("&5&lAbyss Portal &aset to &f"
                + target.getType().name() + " &7at &f"
                + target.getX() + ", " + target.getY() + ", " + target.getZ()
                + " &7in &f" + target.getWorld().getName()));
        p.sendMessage(Text.color("&7Right-click the block to enter The Abyss."));
    }

    /**
     * /abyss removeportal — clears the portal configuration so no block in
     * the world is treated as a portal. Also despawns the floating hologram
     * and stops the particle effect. The world's block is left untouched
     * (we never owned it; admins can break it themselves if they want).
     */
    private void handleRemovePortal(CommandSender sender) {
        if (!sender.hasPermission("abyss.admin")) { noPerm(sender); return; }
        if (!plugin.getConfig().isSet("portal.x")) {
            sender.sendMessage(Text.color("&7No portal is currently configured."));
            return;
        }
        // Capture old coords for the confirmation message
        String oldWorld = plugin.getConfig().getString("portal.world", "?");
        int ox = plugin.getConfig().getInt("portal.x");
        int oy = plugin.getConfig().getInt("portal.y");
        int oz = plugin.getConfig().getInt("portal.z");

        plugin.getConfig().set("portal.x", null);
        plugin.getConfig().set("portal.y", null);
        plugin.getConfig().set("portal.z", null);
        // Keep portal.world and portal.block-type so the keys still exist as
        // defaults if the admin later runs /abyss setportal. Wipe them too if
        // you want a totally clean slate.
        plugin.saveConfig();

        // Refresh the hologram system — with no coords set, it removes its
        // entities from all worlds and shuts down its particle task.
        if (plugin.portalHologram() != null) {
            plugin.portalHologram().refreshFromConfig();
        }

        sender.sendMessage(Text.color("&aPortal removed &7(was at &f"
                + ox + ", " + oy + ", " + oz + " &7in &f" + oldWorld + "&7)."));
        sender.sendMessage(Text.color("&8The block itself wasn't broken — remove it manually if you want."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.color("&5&lAbyss commands:"));
        sender.sendMessage(Text.color("  &f/sigils &7- open your socket menu"));
        sender.sendMessage(Text.color("  &f/abyss leave &7- exit a dungeon"));
        if (sender.hasPermission("abyss.admin")) {
            sender.sendMessage(Text.color("&7Admin:"));
            sender.sendMessage(Text.color("  &f/abyss create <name> &8— new template + open editor"));
            sender.sendMessage(Text.color("  &f/abyss edit <name> &8— open editor for existing template"));
            sender.sendMessage(Text.color("  &f/abyss list &8— list all templates"));
            sender.sendMessage(Text.color("  &f/abyss delete <name> confirm &8— wipe a template"));
            sender.sendMessage(Text.color("  &f/abyss givesigil <p> <id|random> [tier]"));
            sender.sendMessage(Text.color("  &f/abyss givedust <p> <n>"));
            sender.sendMessage(Text.color("  &f/abyss givebook <p> [tier]"));
            sender.sendMessage(Text.color("  &f/abyss givemap <template> [p] [n]"));
            sender.sendMessage(Text.color("  &f/abyss sigil list|create [id]|edit <id> &8— sigil creator"));
            sender.sendMessage(Text.color("  &f/abyss mythicdrops &8— add sigil drops to mythic mobs"));
            sender.sendMessage(Text.color("  &f/abyss setportal &8— set portal block to the one you're looking at"));
            sender.sendMessage(Text.color("  &f/abyss removeportal &8— clear the portal so no block opens a dungeon"));
            sender.sendMessage(Text.color("  &f/abyss reload"));
        }
    }

    private void noPerm(CommandSender s) { s.sendMessage(Text.color("&cNo permission.")); }

    private static final List<String> SUBS = List.of(
            "help","sigils","leave","enterabyss",
            "create","edit","delete","list",
            "givesigil","givedust","givebook","givemap","reload",
            "sigil","mythicdrops","markers","setportal","removeportal"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sigils")) return List.of();
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "edit","delete" -> plugin.templates().all().stream()
                        .map(DungeonTemplate::name)
                        .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                case "givemap" -> plugin.templates().all().stream()
                        .map(DungeonTemplate::name)
                        .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                case "givesigil","givedust","givebook" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).filter(s -> s.startsWith(args[1])).toList();
                case "sigil" -> java.util.stream.Stream.of("list","create","edit")
                        .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String sub2 = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("sigil") && sub2.equals("edit")) {
                return plugin.sigils().all().stream()
                        .map(SigilDefinition::id)
                        .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
            if (sub.equals("givesigil")) {
                List<String> out = new ArrayList<>();
                out.add("random");
                out.addAll(plugin.sigils().all().stream().map(SigilDefinition::id).toList());
                return out.stream().filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
        }
        return List.of();
    }
}
