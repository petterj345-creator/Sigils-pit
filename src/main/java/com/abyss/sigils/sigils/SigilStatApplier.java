package com.abyss.sigils.sigils;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.socket.PlayerSigilStore;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Computes a player's total stats from socketed sigils (both small + big),
 * applies attribute modifiers (HP, speed), and hooks combat events for
 * percentage damage / defense / crit / lifesteal / thorns.
 *
 * MMOItems stats and gathering stats are handled in separate listeners
 * (MMOItemsHook and GatheringListener) but reuse this applier's totalStat().
 */
public final class SigilStatApplier implements Listener {

    private final AbyssPlugin plugin;
    private final PlayerSigilStore store;
    private final SigilRegistry registry;
    private final Random random = new Random();

    private static final UUID HEALTH_MOD_ID = UUID.fromString("a1f9c1ec-7a47-4e2a-bb31-5e9a0d000001");
    private static final UUID SPEED_MOD_ID  = UUID.fromString("a1f9c1ec-7a47-4e2a-bb31-5e9a0d000002");

    private static final Attribute MAX_HEALTH_ATTR = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
    private static final Attribute SPEED_ATTR      = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("movement_speed"));

    public SigilStatApplier(AbyssPlugin plugin, PlayerSigilStore store, SigilRegistry registry) {
        this.plugin = plugin;
        this.store = store;
        this.registry = registry;
    }

    /** Total value of a given stat across all socketed sigils (small + big) + their sub-stats. */
    public double totalStat(Player p, SigilStat stat) {
        double total = 0;
        for (SigilInstance inst : store.allSocketed(p.getUniqueId())) {
            if (inst == null) continue;
            SigilDefinition def = registry.get(inst.definitionId());
            if (def == null) continue;
            if (def.stat() == stat) total += def.valueAtTier(inst.tier());
            Double sub = inst.subStats().get(stat);
            if (sub != null) total += sub;
        }
        return total;
    }

    /** Convenience for damage % (combines normal + big variants). */
    public double totalDamagePercent(Player p) {
        return totalStat(p, SigilStat.DAMAGE_PERCENT) + totalStat(p, SigilStat.DAMAGE_PERCENT_BIG);
    }

    public void refresh(Player p) {
        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance hp = p.getAttribute(MAX_HEALTH_ATTR);
            if (hp != null) {
                removeMod(hp, HEALTH_MOD_ID);
                double bonus = totalStat(p, SigilStat.MAX_HEALTH);
                if (bonus > 0) {
                    hp.addModifier(new AttributeModifier(HEALTH_MOD_ID,
                            "abyss_sigil_hp", bonus, AttributeModifier.Operation.ADD_NUMBER));
                }
            }
        }
        if (SPEED_ATTR != null) {
            AttributeInstance speed = p.getAttribute(SPEED_ATTR);
            if (speed != null) {
                removeMod(speed, SPEED_MOD_ID);
                double pct = totalStat(p, SigilStat.SPEED_PERCENT);
                if (pct > 0) {
                    speed.addModifier(new AttributeModifier(SPEED_MOD_ID,
                            "abyss_sigil_speed", pct / 100.0, AttributeModifier.Operation.ADD_SCALAR));
                }
            }
        }
        plugin.mmoItemsHook().applyTo(p, this);
    }

    private void removeMod(AttributeInstance attr, UUID id) {
        for (AttributeModifier m : new ArrayList<>(attr.getModifiers())) {
            if (m.getUniqueId().equals(id)) attr.removeModifier(m);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { refresh(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance hp = p.getAttribute(MAX_HEALTH_ATTR);
            if (hp != null) removeMod(hp, HEALTH_MOD_ID);
        }
        if (SPEED_ATTR != null) {
            AttributeInstance speed = p.getAttribute(SPEED_ATTR);
            if (speed != null) removeMod(speed, SPEED_MOD_ID);
        }
        plugin.mmoItemsHook().clearFor(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player attacker) {
            double dmgPct = totalDamagePercent(attacker);
            double critChance = totalStat(attacker, SigilStat.CRIT_CHANCE);
            double extraCritDmg = totalStat(attacker, SigilStat.EXTRA_CRIT_DAMAGE);
            double base = e.getDamage();
            if (dmgPct > 0) base *= (1.0 + dmgPct / 100.0);
            boolean crit = critChance > 0 && random.nextDouble() * 100.0 < critChance;
            if (crit) {
                base *= (1.5 + extraCritDmg / 100.0);
                attacker.sendActionBar(net.kyori.adventure.text.Component.text("§6✦ Critical!"));
            }
            // Lifesteal
            double lifesteal = totalStat(attacker, SigilStat.LIFESTEAL_PERCENT);
            if (lifesteal > 0) {
                double heal = base * lifesteal / 100.0;
                AttributeInstance hp = attacker.getAttribute(MAX_HEALTH_ATTR);
                double max = hp == null ? 20 : hp.getValue();
                attacker.setHealth(Math.min(max, attacker.getHealth() + heal));
            }
            e.setDamage(base);
        }
        if (e.getEntity() instanceof Player victim) {
            double defPct = totalStat(victim, SigilStat.DEFENSE_PERCENT);
            if (defPct > 0) {
                e.setDamage(e.getDamage() * Math.max(0.05, 1.0 - defPct / 100.0));
            }
            // Thorns
            double thorns = totalStat(victim, SigilStat.THORNS_PERCENT);
            if (thorns > 0 && e.getDamager() instanceof LivingEntity attacker) {
                double reflected = e.getDamage() * thorns / 100.0;
                attacker.damage(reflected, victim);
            }
        }
    }
}
