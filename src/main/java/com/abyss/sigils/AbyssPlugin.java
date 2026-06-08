package com.abyss.sigils;

import com.abyss.sigils.commands.AbyssCommand;
import com.abyss.sigils.dungeon.DungeonManager;
import com.abyss.sigils.dungeon.PortalListener;
import com.abyss.sigils.dungeon.RewardChestManager;
import com.abyss.sigils.dungeon.TemplateRegistry;
import com.abyss.sigils.dungeon.UpgradeGUI;
import com.abyss.sigils.gui.AnvilInput;
import com.abyss.sigils.gui.ChatInput;
import com.abyss.sigils.gui.EditorGUI;
import com.abyss.sigils.gui.EditorWandListener;
import com.abyss.sigils.gui.MarkerVisualizer;
import com.abyss.sigils.gui.RewardsGUI;
import com.abyss.sigils.gui.SigilCreatorGUI;
import com.abyss.sigils.integration.MMOItemsHook;
import com.abyss.sigils.integration.MythicHook;
import com.abyss.sigils.sigils.GatheringListener;
import com.abyss.sigils.sigils.SigilItem;
import com.abyss.sigils.sigils.SigilRegistry;
import com.abyss.sigils.sigils.SigilStatApplier;
import com.abyss.sigils.socket.BookListener;
import com.abyss.sigils.socket.PlayerSigilStore;
import com.abyss.sigils.socket.SocketGUI;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbyssPlugin extends JavaPlugin {

    private static AbyssPlugin instance;
    public static AbyssPlugin get() { return instance; }

    private SigilRegistry sigils;
    private com.abyss.sigils.sigils.BookTiers bookTiers;
    private PlayerSigilStore store;
    private SocketGUI socketGUI;
    private SigilStatApplier statApplier;
    private MMOItemsHook mmoItemsHook;
    private TemplateRegistry templates;
    private DungeonManager dungeonManager;
    private com.abyss.sigils.dungeon.RitualManager ritualManager;
    private RewardChestManager rewardChests;
    private UpgradeGUI upgradeGUI;
    private com.abyss.sigils.dungeon.MapRefreshListener mapRefreshListener;
    private com.abyss.sigils.dungeon.PortalHologram portalHologram;
    private com.abyss.sigils.dungeon.PortalEntryGUI portalEntryGUI;
    private com.abyss.sigils.gui.SigilCreatorGUI sigilCreatorGUI;
    private MythicHook mythicHook;
    private com.abyss.sigils.integration.MythicDropWriter mythicDropWriter;
    private com.abyss.sigils.integration.MobDropStore mobDrops;
    private EditorWandListener editorWandListener;
    private com.abyss.sigils.gui.EditorMarkers editorMarkers;
    private MarkerVisualizer markerVisualizer;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        sigils = new SigilRegistry(this);
        sigils.load();

        bookTiers = new com.abyss.sigils.sigils.BookTiers(this);
        // The store has to hold enough slots for the MAX possible book tier
        // even when the player's current book is a lower tier — otherwise
        // upgrading the book wouldn't recover previously-stored sigils.
        int maxTier = bookTiers.maxTier();
        int smallSlots = bookTiers.smallSlots(maxTier);
        int bigSlots   = bookTiers.bigSlots(maxTier) + bookTiers.grandSlots(maxTier);
        store = new PlayerSigilStore(this, smallSlots, bigSlots);

        // MMOItems hook before stat applier (applier asks it for refreshes)
        mmoItemsHook = new MMOItemsHook(this);
        mmoItemsHook.initIfPresent();

        socketGUI = new SocketGUI(this, store);
        Bukkit.getPluginManager().registerEvents(socketGUI, this);

        statApplier = new SigilStatApplier(this, store, sigils);
        Bukkit.getPluginManager().registerEvents(statApplier, this);

        Bukkit.getPluginManager().registerEvents(new GatheringListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BookListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BookOnJoinListener(this), this);

        sigilCreatorGUI = new com.abyss.sigils.gui.SigilCreatorGUI(this);
        Bukkit.getPluginManager().registerEvents(sigilCreatorGUI, this);

        templates = new TemplateRegistry(this);
        templates.loadAll();

        // Plugin-side mob drops (abyss items dropped by MythicMobs on death) —
        // stored in our own mobdrops.yml, never in MythicMobs' files.
        mobDrops = new com.abyss.sigils.integration.MobDropStore(this);
        mobDrops.load();

        dungeonManager = new DungeonManager(this);
        Bukkit.getPluginManager().registerEvents(dungeonManager, this);

        // Altar of Souls ritual runtime (armor stands, souls, soul shop).
        ritualManager = new com.abyss.sigils.dungeon.RitualManager(this);
        Bukkit.getPluginManager().registerEvents(ritualManager, this);
        com.abyss.sigils.gui.RitualShopGUI.register(this);
        // Lock down building inside play instances (abyss_inst_*).
        Bukkit.getPluginManager().registerEvents(
                new com.abyss.sigils.dungeon.DungeonProtectionListener(this), this);
        // Delete any abyss_inst_* worlds left over from a crash or hard stop.
        dungeonManager.cleanupOrphanInstances();
        rewardChests = new RewardChestManager(this);
        Bukkit.getPluginManager().registerEvents(rewardChests, this);

        // PoE-style: apply currency items to maps to roll modifiers onto them.
        Bukkit.getPluginManager().registerEvents(
                new com.abyss.sigils.dungeon.CurrencyApplyListener(this), this);
        upgradeGUI = new UpgradeGUI(this);
        Bukkit.getPluginManager().registerEvents(upgradeGUI, this);

        mapRefreshListener = new com.abyss.sigils.dungeon.MapRefreshListener(this);
        Bukkit.getPluginManager().registerEvents(mapRefreshListener, this);

        portalHologram = new com.abyss.sigils.dungeon.PortalHologram(this);
        Bukkit.getPluginManager().registerEvents(portalHologram, this);
        // Try to spawn it now; if chunks aren't loaded yet, onChunkLoad picks it up.
        portalHologram.refreshFromConfig();

        portalEntryGUI = new com.abyss.sigils.dungeon.PortalEntryGUI(this);
        Bukkit.getPluginManager().registerEvents(portalEntryGUI, this);

        EditorGUI.register(this);
        AnvilInput.register(this);
        ChatInput.register(this);
        RewardsGUI.register(this);
        com.abyss.sigils.gui.RitualRewardsGUI.register(this);

        editorWandListener = new EditorWandListener(this);
        Bukkit.getPluginManager().registerEvents(editorWandListener, this);

        editorMarkers = new com.abyss.sigils.gui.EditorMarkers(this);
        Bukkit.getPluginManager().registerEvents(editorMarkers, this);

        markerVisualizer = new MarkerVisualizer(this);
        Bukkit.getPluginManager().registerEvents(markerVisualizer, this);

        PortalListener portal = new PortalListener(this);
        Bukkit.getPluginManager().registerEvents(portal, this);

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            mythicHook = new MythicHook(this, sigils);
            mythicHook.register();
            Bukkit.getPluginManager().registerEvents(mythicHook, this);
            mythicDropWriter = new com.abyss.sigils.integration.MythicDropWriter(this);
        } else {
            getLogger().warning("MythicMobs not found — dungeon/item-type hooks disabled.");
        }

        AbyssCommand cmd = new AbyssCommand(this);
        if (getCommand("abyss") != null) {
            getCommand("abyss").setExecutor(cmd);
            getCommand("abyss").setTabCompleter(cmd);
        }
        if (getCommand("sigils") != null) getCommand("sigils").setExecutor(cmd);

        Bukkit.getOnlinePlayers().forEach(statApplier::refresh);
        getLogger().info("AbyssSigils enabled.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
        if (dungeonManager != null) dungeonManager.shutdown();
        if (mmoItemsHook != null && mmoItemsHook.available()) {
            Bukkit.getOnlinePlayers().forEach(mmoItemsHook::clearFor);
        }
        getLogger().info("AbyssSigils disabled.");
    }

    public SigilRegistry sigils() { return sigils; }
    public com.abyss.sigils.sigils.BookTiers bookTiers() { return bookTiers; }
    public PlayerSigilStore store() { return store; }
    public SocketGUI socketGUI() { return socketGUI; }
    public SigilStatApplier statApplier() { return statApplier; }
    public MMOItemsHook mmoItemsHook() { return mmoItemsHook; }
    public TemplateRegistry templates() { return templates; }
    public DungeonManager dungeonManager() { return dungeonManager; }
    public com.abyss.sigils.dungeon.RitualManager ritualManager() { return ritualManager; }
    public RewardChestManager rewardChests() { return rewardChests; }
    public UpgradeGUI upgradeGUI() { return upgradeGUI; }
    public com.abyss.sigils.dungeon.MapRefreshListener mapRefreshListener() { return mapRefreshListener; }
    public com.abyss.sigils.dungeon.PortalHologram portalHologram() { return portalHologram; }
    public com.abyss.sigils.dungeon.PortalEntryGUI portalEntryGUI() { return portalEntryGUI; }
    public com.abyss.sigils.gui.SigilCreatorGUI sigilCreatorGUI() { return sigilCreatorGUI; }
    public com.abyss.sigils.integration.MythicDropWriter mythicDropWriter() { return mythicDropWriter; }
    public com.abyss.sigils.integration.MobDropStore mobDrops() { return mobDrops; }
    public EditorWandListener editorWandListener() { return editorWandListener; }
    public com.abyss.sigils.gui.EditorMarkers editorMarkers() { return editorMarkers; }
    public MarkerVisualizer markerVisualizer() { return markerVisualizer; }

    /** Gives new players the Book of Sigils on first join (if configured). */
    public static class BookOnJoinListener implements Listener {
        private final AbyssPlugin plugin;
        private final NamespacedKey GIVEN_KEY;
        public BookOnJoinListener(AbyssPlugin plugin) {
            this.plugin = plugin;
            this.GIVEN_KEY = new NamespacedKey(plugin, "book_given");
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            if (!plugin.getConfig().getBoolean("socket.give-book-on-join", true)) return;
            Player p = e.getPlayer();
            var pdc = p.getPersistentDataContainer();
            if (pdc.has(GIVEN_KEY, PersistentDataType.BYTE)) return;
            pdc.set(GIVEN_KEY, PersistentDataType.BYTE, (byte) 1);
            p.getInventory().addItem(SigilItem.createBook());
        }
    }
}
