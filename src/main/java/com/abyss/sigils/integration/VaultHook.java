package com.abyss.sigils.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Optional Vault integration. Loaded reflectively so the plugin still works
 * without Vault on the classpath.
 *
 * Usage: VaultHook.deposit(player, amount) — returns true if deposit succeeded.
 */
public final class VaultHook {

    private static boolean attempted = false;
    private static Object econ;          // net.milkbowl.vault.economy.Economy
    private static Method depositMethod; // depositPlayer(OfflinePlayer, double)
    private static Method formatMethod;  // format(double)

    private VaultHook() {}

    private static synchronized void init() {
        if (attempted) return;
        attempted = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp == null) return;
            econ = rsp.getProvider();
            depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            formatMethod = economyClass.getMethod("format", double.class);
        } catch (Throwable t) {
            econ = null;
        }
    }

    public static boolean available() {
        init();
        return econ != null;
    }

    /** Deposit an amount into the player's Vault balance. Returns true on success. */
    public static boolean deposit(OfflinePlayer p, double amount) {
        init();
        if (econ == null || amount <= 0) return false;
        try {
            Object resp = depositMethod.invoke(econ, p, amount);
            // EconomyResponse has a public field `transactionSuccess()`; check via reflection
            if (resp == null) return false;
            Method ok = resp.getClass().getMethod("transactionSuccess");
            Object result = ok.invoke(resp);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Pretty-print currency, e.g. "$1,234.00". Falls back to "<amount> coins" without Vault. */
    public static String format(double amount) {
        init();
        if (econ == null) return amount + " coins";
        try { return (String) formatMethod.invoke(econ, amount); }
        catch (Throwable t) { return amount + " coins"; }
    }
}
