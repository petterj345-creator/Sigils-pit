package com.abyss.sigils.hideout;

import com.abyss.sigils.AbyssPlugin;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.UUID;

/**
 * Building rules inside hideouts (abyss_hideout_&lt;owner-uuid&gt;):
 *   - NOBODY freely builds — the layout comes from the starter template and
 *     stays fixed. The only blocks anyone may place or break are the two Abyss
 *     fixtures (the Abyss Portal and the Abyss Stash), and only the OWNER may
 *     touch even those. So the owner can set up / move / remove their portal and
 *     stash, but can't mine or pillar the hideout itself.
 *   - visitors may not touch a single block.
 *
 * Placing is gated on the item in hand being a fixture item; breaking is gated
 * on the block being one of the owner's REGISTERED fixtures.
 *
 * The break handler runs at LOW — earlier than {@link HideoutFixtureListener}
 * (NORMAL, ignoreCancelled) — so the fixture is still registered when we check
 * it; if we cancel here, the fixture listener's clear/drop is skipped. Place and
 * bucket handlers stay at HIGHEST (no such ordering dependency) so they hold
 * even if another plugin tries to allow the action.
 */
public final class HideoutProtectionListener implements Listener {

    private final AbyssPlugin plugin;

    public HideoutProtectionListener(AbyssPlugin plugin) { this.plugin = plugin; }

    /** A protected hideout — a player's world, but NOT the admin editor template. */
    private boolean isProtected(World w) {
        return HideoutManager.isHideoutWorld(w) && !w.getName().equals(HideoutTemplate.WORLD);
    }

    /** True when {@code p} owns hideout world {@code w}. */
    private boolean isOwner(Player p, World w) {
        UUID owner = HideoutManager.ownerOf(w);
        return owner != null && owner.equals(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        World w = e.getBlock().getWorld();
        if (!isProtected(w)) return;
        // Owner may break ONLY their registered fixtures (portal/stash); the
        // world itself can't be dug. Everyone else can't break anything.
        HideoutFixtures fx = plugin.hideoutFixtures();
        boolean fixture = fx.isPortal(e.getBlock()) || fx.isStash(e.getBlock());
        if (!(isOwner(e.getPlayer(), w) && fixture)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        World w = e.getBlock().getWorld();
        if (!isProtected(w)) return;
        // Owner may place ONLY the Abyss fixture items; no general building.
        boolean fixtureItem = HideoutItems.typeOf(e.getItemInHand()) != null;
        if (!(isOwner(e.getPlayer(), w) && fixtureItem)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (isProtected(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (isProtected(e.getBlock().getWorld())) e.setCancelled(true);
    }
}
