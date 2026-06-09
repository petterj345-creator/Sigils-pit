package com.abyss.sigils.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Soft integration with MMOCore's party system, via reflection — mirroring the
 * MMOItemsHook approach so the plugin still compiles and runs without MMOCore
 * installed (no compile-time dependency).
 *
 * {@link #onlinePartyMembers(Player)} returns the online members of a player's
 * MMOCore party; if MMOCore is absent, the player has no party, or the API
 * shape differs from what we expect, it safely returns just the player.
 *
 * Reflected MMOCore API (5.x / MMOCore-API):
 *   PlayerData.get(UUID) -> PlayerData
 *   MMOCore.plugin.partyModule.getParty(PlayerData) -> AbstractParty (nullable)
 *   AbstractParty.getOnlineMembers() (fallback getMembers()) -> Iterable<PlayerData>
 *   PlayerData.getPlayer() -> Player
 */
public final class MMOCorePartyHook {

    private MMOCorePartyHook() {}

    private static Boolean present;

    public static boolean available() {
        if (present == null) present = Bukkit.getPluginManager().getPlugin("MMOCore") != null;
        return present;
    }

    /** Online members of this player's MMOCore party — always includes the player. */
    public static List<Player> onlinePartyMembers(Player p) {
        List<Player> solo = new ArrayList<>();
        solo.add(p);
        if (!available()) return solo;
        try {
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Object data = playerDataClass.getMethod("get", UUID.class).invoke(null, p.getUniqueId());
            if (data == null) return solo;

            Object mmocore = Class.forName("net.Indyuce.mmocore.MMOCore").getField("plugin").get(null);
            Object partyModule = mmocore.getClass().getField("partyModule").get(mmocore);
            if (partyModule == null) return solo;

            Object party = partyModule.getClass().getMethod("getParty", playerDataClass).invoke(partyModule, data);
            if (party == null) return solo; // not in a party

            Object members;
            try { members = party.getClass().getMethod("getOnlineMembers").invoke(party); }
            catch (NoSuchMethodException nsme) { members = party.getClass().getMethod("getMembers").invoke(party); }
            if (!(members instanceof Iterable<?> it)) return solo;

            List<Player> out = new ArrayList<>();
            for (Object member : it) {
                Object pl = member.getClass().getMethod("getPlayer").invoke(member);
                if (pl instanceof Player player && player.isOnline()) out.add(player);
            }
            if (!out.contains(p)) out.add(p);
            return out.isEmpty() ? solo : out;
        } catch (Throwable t) {
            // Unknown API shape / not installed properly — behave as solo.
            return solo;
        }
    }
}
