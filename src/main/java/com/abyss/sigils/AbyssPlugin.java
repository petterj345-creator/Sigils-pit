package com.abyss.sigils;

import com.abyss.sigils.commands.AbyssCommand;
import com.abyss.sigils.dungeon.DungeonManager;
import com.abyss.sigils.dungeon.PortalListener;
import com.abyss.sigils.dungeon.RewardChestManager;
import com.abyss.sigils.dungeon.TemplateRegistry;
import com.abyss.sigils.dungeon.UpgradeGUI;
import com.abyss.sigils.gui.AnvilInput;
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
    private PlayerSigilStore store;
    private SocketGUI socketGUI;
    private SigilStatApplier statApplier;
    private MMOItemsHook mmoItemsHook;
    private TemplateRegistry templates;
    private DungeonManager dungeonManager;
    private RewardChestManager rewardChests;
    private UpgradeGUI upgradeGUI;
    private MythicHook mythicHook;
    private com.abyss.sigils.integration.MythicDropWriter mythicDropWriter;
    private EditorWandListener editorWandListener;
    private com.abyss.sigils.gui.EditorMarkers editorMarkers;
    private MarkerVisualizer markerVisualizer;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        sigils = new SigilRegistry(this);
        sigils.load();

        int smallSlots = getConfig().getInt("socket.small-slots", 10);
        int bigSlots   = getConfig().getInt("socket.big-slots", 3);
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

        templates = new TemplateRegistry(this);
        templates.loadAll();

        dungeonManager = new DungeonManager(this);
        Bukkit.getPluginManager().registerEvents(dungeonManager, this);
        rewardChests = new RewardChestManager(this);
        Bukkit.getPluginManager().registerEvents(rewardChests, this);
        upgradeGUI = new UpgradeGUI(this);
        Bukkit.getPluginManager().registerEvents(upgradeGUI, this);

        EditorGUI.register(this);
        AnvilInput.register(this);
        ChatInput.register(this);
        RewardsGUI.register(this);
        SigilCreatorGUI.register(this);

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
    public PlayerSigilStore store() { return store; }
    public SocketGUI socketGUI() { return socketGUI; }
    public SigilStatApplier statApplier() { return statApplier; }
    public MMOItemsHook mmoItemsHook() { return mmoItemsHook; }
    public TemplateRegistry templates() { return templates; }
    public DungeonManager dungeonManager() { return dungeonManager; }
    public RewardChestManager rewardChests() { return rewardChests; }
    public UpgradeGUI upgradeGUI() { return upgradeGUI; }
    public com.abyss.sigils.integration.MythicDropWriter mythicDropWriter() { return mythicDropWriter; }
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
