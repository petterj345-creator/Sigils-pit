package com.abyss.sigils.npc;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Bridges Citizens NPC clicks to the {@link TutorialGuide}. Any NPC carrying the
 * {@link SigilsTutorialTrait} ({@code /trait sigils}) becomes a reader:
 * right-click opens / advances the guide, left-click pages back. The buttons in
 * chat do the same via {@code /sigilguide}.
 */
public final class TutorialNpcListener implements Listener {

    private final TutorialGuide guide;

    public TutorialNpcListener(TutorialGuide guide) {
        this.guide = guide;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent e) {
        if (!e.getNPC().hasTrait(SigilsTutorialTrait.class)) return;
        guide.open(e.getClicker(), e.getNPC().getName());
    }

    @EventHandler
    public void onLeftClick(NPCLeftClickEvent e) {
        if (!e.getNPC().hasTrait(SigilsTutorialTrait.class)) return;
        guide.prev(e.getClicker());
    }
}
