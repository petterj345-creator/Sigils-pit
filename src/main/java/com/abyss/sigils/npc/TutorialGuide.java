package com.abyss.sigils.npc;

import com.abyss.sigils.AbyssPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the paginated endgame guide read off a tutorial NPC. Pages, the title
 * template and the footer all come from the {@code tutorial:} section of
 * config.yml and are read fresh each time, so {@code /abyss reload} picks up
 * edits with no restart.
 *
 * Navigation is driven both by clicking the NPC ({@link TutorialNpcListener})
 * and by the clickable chat buttons, which run {@code /sigilguide ...}
 * ({@link TutorialGuideCommand}). Each reader's current page is remembered so a
 * right-click resumes where they left off.
 */
public final class TutorialGuide {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final AbyssPlugin plugin;
    private final Map<UUID, Integer> page = new HashMap<>();
    private final Map<UUID, String> lastNpc = new HashMap<>();

    public TutorialGuide(AbyssPlugin plugin) {
        this.plugin = plugin;
    }

    private record Page(String title, List<String> lines) {}

    private List<Page> loadPages() {
        List<Page> out = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("tutorial.pages")) {
            Object title = m.get("title");
            Object lines = m.get("lines");
            List<String> body = new ArrayList<>();
            if (lines instanceof List<?> list) {
                for (Object o : list) body.add(String.valueOf(o));
            }
            out.add(new Page(title == null ? "" : String.valueOf(title), body));
        }
        return out;
    }

    /** Open at the reader's remembered page (or the first one). */
    public void open(Player p, String npcName) {
        if (npcName != null && !npcName.isBlank()) lastNpc.put(p.getUniqueId(), npcName);
        show(p, page.getOrDefault(p.getUniqueId(), 0));
    }

    public void next(Player p) { show(p, page.getOrDefault(p.getUniqueId(), 0) + 1); }
    public void prev(Player p) { show(p, page.getOrDefault(p.getUniqueId(), 0) - 1); }

    /** Render a specific page, clamping out-of-range indices. */
    public void show(Player p, int index) {
        List<Page> pages = loadPages();
        if (pages.isEmpty()) {
            p.sendMessage(Component.text("The guide has nothing to say yet."));
            return;
        }
        int total = pages.size();
        int idx = Math.max(0, Math.min(index, total - 1));
        page.put(p.getUniqueId(), idx);
        Page pg = pages.get(idx);

        String npc = lastNpc.getOrDefault(p.getUniqueId(), "Loremaster");
        String title = plugin.getConfig()
                .getString("tutorial.title", "&5&l✦ {npc} &8— &5&lEndgame Codex &7({n}/{total})")
                .replace("{npc}", npc)
                .replace("{n}", String.valueOf(idx + 1))
                .replace("{total}", String.valueOf(total));

        p.sendMessage(Component.empty());
        p.sendMessage(LEGACY.deserialize(title));
        if (!pg.title().isBlank()) p.sendMessage(LEGACY.deserialize("&8» " + pg.title()));
        p.sendMessage(Component.empty());
        for (String line : pg.lines()) p.sendMessage(LEGACY.deserialize(line));
        p.sendMessage(Component.empty());
        p.sendMessage(navRow(idx, total));

        String footer = plugin.getConfig().getString("tutorial.footer", "");
        if (footer != null && !footer.isBlank()) p.sendMessage(LEGACY.deserialize(footer));
    }

    /** A clickable index of every page. */
    public void topics(Player p) {
        List<Page> pages = loadPages();
        if (pages.isEmpty()) {
            p.sendMessage(Component.text("The guide has nothing to say yet."));
            return;
        }
        p.sendMessage(Component.empty());
        p.sendMessage(LEGACY.deserialize("&5&l✦ Topics"));
        for (int i = 0; i < pages.size(); i++) {
            String label = "&8 » &f" + (pages.get(i).title().isBlank()
                    ? "Page " + (i + 1) : pages.get(i).title());
            p.sendMessage(button(label, "/sigilguide page " + i, "&7Read this section"));
        }
    }

    private Component navRow(int idx, int total) {
        var row = Component.text();
        row.append(idx > 0
                ? button("&a[◀ Back]", "/sigilguide prev", "&7Previous page")
                : LEGACY.deserialize("&8[◀ Back]"));
        row.append(LEGACY.deserialize("  &8• &7Page &f" + (idx + 1) + "&7/&f" + total + " &8•  "));
        row.append(idx < total - 1
                ? button("&a[Next ▶]", "/sigilguide next", "&7Next page")
                : LEGACY.deserialize("&8[Next ▶]"));
        row.append(LEGACY.deserialize("    "));
        row.append(button("&b[≡ Topics]", "/sigilguide topics", "&7Jump to a topic"));
        return row.build();
    }

    private Component button(String label, String command, String hover) {
        return LEGACY.deserialize(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize(hover)));
    }
}
