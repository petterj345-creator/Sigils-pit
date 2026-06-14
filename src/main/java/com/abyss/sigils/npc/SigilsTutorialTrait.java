package com.abyss.sigils.npc;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;

/**
 * Marker Citizens trait. Adding it to an NPC ({@code /trait sigils}) turns that
 * NPC into the endgame tutorial guide: right-clicking it opens the paginated
 * codex defined under {@code tutorial:} in config.yml.
 *
 * The trait holds no state of its own — all click handling lives in
 * {@link TutorialNpcListener} (which checks {@code npc.hasTrait(...)}) and all
 * page state lives in {@link TutorialGuide}. We keep it a pure marker so it has
 * nothing to persist and survives Citizens save/load untouched.
 */
@TraitName("sigils")
public class SigilsTutorialTrait extends Trait {
    public SigilsTutorialTrait() {
        super("sigils");
    }
}
