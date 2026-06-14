package com.abyss.sigils.npc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Backs the clickable [Back]/[Next]/[Topics] buttons in the tutorial guide.
 * Players never type this — the buttons run it. Only the page navigation is
 * exposed, so there's nothing to abuse beyond re-reading text.
 */
public final class TutorialGuideCommand implements CommandExecutor {

    private final TutorialGuide guide;

    public TutorialGuideCommand(TutorialGuide guide) {
        this.guide = guide;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length == 0) { guide.open(p, null); return true; }
        switch (args[0].toLowerCase()) {
            case "next" -> guide.next(p);
            case "prev", "back" -> guide.prev(p);
            case "topics" -> guide.topics(p);
            case "page" -> {
                int idx = 0;
                if (args.length > 1) {
                    try { idx = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }
                guide.show(p, idx);
            }
            default -> guide.open(p, null);
        }
        return true;
    }
}
