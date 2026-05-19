package com.abyss.sigils.dungeon;

import com.abyss.sigils.AbyssPlugin;
import com.abyss.sigils.util.Text;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Creates per-party instanced dungeons (cloned from a DungeonTemplate's world),
 * runs the dungeon in either MAP or WAVES mode, shows progress on a boss bar,
 * and spawns the upgrade block on boss death.
 *
 * Implements Listener so we can hook EntityDamageEvent to update boss HP on the bar.
 */
public final class DungeonManager implements Listener {

    private final AbyssPlugin plugin;
    private final Map<UUID, DungeonSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> playerToSession = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> sessionTasks = new HashMap<>();

    public DungeonManager(AbyssPlugin plugin) { this.plugin = plugin; }

    public DungeonSession sessionOf(Player p) {
        UUID sid = playerToSession.get(p.getUniqueId());
        return sid == null ? null : sessions.get(sid);
    }

    public DungeonSession sessionById(UUID id) { return sessions.get(id); }

    /** Pick a random playable template and start. */
    public void start(Collection<Player> party) {
        DungeonTemplate template = plugin.templates().randomPlayable();
        if (template == null) {
            for (Player p : party) p.sendMessage(Text.color("&cNo playable Abyss templates are configured."));
            return;
        }
        start(party, template);
    }

    public void start(Collection<Player> party, DungeonTemplate template) {
        if (party.isEmpty()) return;
        for (Player p : party) {
            if (playerToSession.containsKey(p.getUniqueId())) {
                p.sendMessage(Text.color("&cSomeone in your party is already in The Abyss."));
                return;
            }
        }
        String err = template.validationError();
        if (err != null) {
            for (Player p : party) p.sendMessage(Text.color("&cTemplate '" + template.name() + "' is invalid: " + err));
            return;
        }

        World tplWorld = Bukkit.getWorld(template.worldName());
        if (tplWorld == null) tplWorld = plugin.templates().loadWorld(template);
        if (tplWorld == null) {
            for (Player p : party) p.sendMessage(Text.color("&cTemplate world is missing on disk."));
            return;
        }

        String instanceName = "abyss_inst_" + UUID.randomUUID().toString().substring(0, 8);

        // Remove editor markers from the template world BEFORE cloning, so the
        // clone doesn't have them. We'll re-spawn them next tick if admins are
        // still inside.
        if (plugin.editorMarkers() != null) plugin.editorMarkers().clearFor(tplWorld);

        World instance;
        try { instance = cloneWorld(tplWorld, instanceName); }
        catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clone template world", ex);
            for (Player p : party) p.sendMessage(Text.color("&cCouldn't create your Abyss instance."));
            // Restore markers in template world
            if (plugin.editorMarkers() != null) plugin.editorMarkers().refreshFor(template);
            return;
        }

        // Re-spawn markers in the template world so admins editing still see them.
        if (plugin.editorMarkers() != null) plugin.editorMarkers().refreshFor(template);

        // Also defensively strip from the cloned world (in case markers had been
        // persisted to disk previously).
        if (plugin.editorMarkers() != null) plugin.editorMarkers().stripFromInstance(instance);

        DungeonSession session = new DungeonSession(instance, party);
        session.setTemplateName(template.name());
        sessions.put(session.id(), session);
        sessionTasks.put(session.id(), new ArrayList<>());

        // Apply per-template game rules
        instance.setGameRule(GameRule.KEEP_INVENTORY, template.keepInventory());

        // Progress bar
        ProgressBar bar = new ProgressBar("§5§lThe Abyss");
        session.setProgressBar(bar);

        Location spawn = bindToWorld(template.playerSpawn(), instance);
        for (Player p : party) {
            playerToSession.put(p.getUniqueId(), session.id());
            session.setLives(p.getUniqueId(), template.lives()); // 0 = unlimited
            p.teleport(spawn);
            bar.addPlayer(p);
            p.sendMessage(Text.color("&5&lThe Abyss &7welcomes you to &f" + template.name() + "&7."));
            if (template.lives() > 0) {
                p.sendMessage(Text.color("&7You have &c" + template.lives() + " ❤ &7lives."));
            } else {
                p.sendMessage(Text.color("&7Lives: &aunlimited&7."));
            }
        }

        if (template.mode() == DungeonMode.MAP) {
            session.setPhase(DungeonSession.Phase.TRASH);
            bar.setKillProgress(0, template.mobsBeforeBoss());
            startMapWaves(session, template);
        } else {
            session.setPhase(DungeonSession.Phase.WAVES);
            startNextWave(session, template);
        }
        scheduleTimeout(session, template);
    }

    // ============================================================
    // MAP mode
    // ============================================================

    private void startMapWaves(DungeonSession session, DungeonTemplate t) {
        long ticks = t.waveIntervalSeconds() * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> spawnMapTick(session, t), 40L, ticks);
        addTask(session, task);
    }

    private void spawnMapTick(DungeonSession session, DungeonTemplate t) {
        if (session.phase() != DungeonSession.Phase.TRASH) return;
        int canSpawn = Math.max(0, t.maxConcurrentMobs() - session.aliveMobs().size());
        int toSpawn = Math.min(t.mobsPerWave(), canSpawn);
        if (toSpawn == 0) return;
        if (t.spawnPoints().isEmpty()) return;

        Random rng = new Random();
        for (int i = 0; i < toSpawn; i++) {
            SpawnPoint sp = t.spawnPoints().get(rng.nextInt(t.spawnPoints().size()));
            // Per-spawn-point mob list, fallback to template default
            List<MobEntry> pool = sp.mobs().isEmpty() ? t.defaultTrashMobs() : sp.mobs();
            if (pool.isEmpty()) continue;
            MobEntry e = pool.get(rng.nextInt(pool.size()));
            Location loc = sp.boundTo(session.world());
            spawnMythic(e.mythicId(), loc, e.level())
                    .ifPresent(ent -> session.aliveMobs().add(ent.getUniqueId()));
        }
    }

    // ============================================================
    // WAVES mode
    // ============================================================

    private void startNextWave(DungeonSession session, DungeonTemplate t) {
        int nextIdx = session.currentWaveIndex() + 1;
        if (nextIdx >= t.waves().size()) {
            triggerBoss(session, t);
            return;
        }
        Wave w = t.waves().get(nextIdx);
        int totalMobs = w.totalMobs();
        session.setCurrentWave(nextIdx, totalMobs);
        session.progressBar().setWaveProgress(nextIdx + 1, t.waves().size(), 0, totalMobs);

        broadcast(session, "&5&lWave " + (nextIdx + 1) + "/" + t.waves().size());

        // Spawn all wave mobs, distributed across spawn points
        Random rng = new Random();
        List<SpawnPoint> points = t.spawnPoints();
        for (MobEntry e : w.mobs()) {
            for (int i = 0; i < e.count(); i++) {
                SpawnPoint sp = points.get(rng.nextInt(points.size()));
                spawnMythic(e.mythicId(), sp.boundTo(session.world()), e.level())
                        .ifPresent(ent -> session.aliveMobs().add(ent.getUniqueId()));
            }
        }
    }

    private void onWaveMobKilled(DungeonSession session, DungeonTemplate t) {
        session.incrementWaveKilled();
        session.progressBar().setWaveProgress(
                session.currentWaveIndex() + 1, t.waves().size(),
                session.currentWaveKilled(), session.currentWaveTotal());
        if (session.isWaveCleared()) {
            Wave w = t.waves().get(session.currentWaveIndex());
            int delay = w.delayAfterSeconds();
            broadcast(session, "&aWave cleared! Next in " + delay + "s...");
            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> startNextWave(session, t), delay * 20L);
            addTask(session, task);
        }
    }

    // ============================================================
    // Shared
    // ============================================================

    private Optional<Entity> spawnMythic(String internalName, Location loc, int level) {
        try {
            Optional<MythicMob> opt = MythicBukkit.inst().getMobManager().getMythicMob(internalName);
            if (opt.isEmpty()) {
                plugin.getLogger().warning("Unknown MythicMob: " + internalName);
                return Optional.empty();
            }
            ActiveMob am = opt.get().spawn(BukkitAdapter.adapt(loc), level);
            return Optional.of(am.getEntity().getBukkitEntity());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn MythicMob " + internalName, t);
            return Optional.empty();
        }
    }

    public void handleMythicMobDeath(MythicMobDeathEvent e) {
        Entity ent = e.getEntity();
        if (ent == null) return;
        World w = ent.getWorld();
        DungeonSession session = sessionByWorld(w);
        if (session == null) return;

        UUID entId = ent.getUniqueId();
        if (entId.equals(session.bossEntityId())) {
            onBossDeath(session, ent.getLocation());
            return;
        }
        if (!session.aliveMobs().remove(entId)) return;

        DungeonTemplate t = plugin.templates().get(session.templateName());
        if (t == null) return;

        if (t.mode() == DungeonMode.MAP) {
            session.incrementKills();
            int threshold = t.mobsBeforeBoss();
            session.progressBar().setKillProgress(session.kills(), threshold);
            if (session.kills() >= threshold && session.phase() == DungeonSession.Phase.TRASH) {
                triggerBoss(session, t);
            }
        } else if (t.mode() == DungeonMode.WAVES && session.phase() == DungeonSession.Phase.WAVES) {
            onWaveMobKilled(session, t);
        }
    }

    /** Listen to damage events to keep the boss bar HP fraction in sync. */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        DungeonSession session = sessionByWorld(le.getWorld());
        if (session == null) return;
        if (session.bossEntityId() == null || !le.getUniqueId().equals(session.bossEntityId())) return;

        // Use the post-damage HP (run next tick)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (le.isDead()) return;
            double max;
            try { max = le.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(
                    org.bukkit.NamespacedKey.minecraft("max_health"))).getValue(); }
            catch (Throwable ex) { max = 20; }
            session.progressBar().setBossHealth(le.getHealth(), max);
        });
    }

    /**
     * When a player dies inside a dungeon, decrement their lives. If they're
     * out, eliminate them (kick to exit on respawn). Otherwise they respawn
     * normally inside the dungeon.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        DungeonSession session = sessionOf(p);
        if (session == null) return;
        DungeonTemplate t = plugin.templates().get(session.templateName());
        if (t == null) return;

        // Always preserve the Book of Sigils across death — even when the
        // template has keep-inventory off. The book is core to the player's
        // identity (their socketed sigils), not a "loot" item, so losing it
        // mid-dungeon feels punishing in a bad way.
        //
        // Pull any books out of the drop list and stash them; we re-add them
        // to the player's inventory on the next tick (after Bukkit clears
        // their inventory as part of the death flow).
        java.util.List<org.bukkit.inventory.ItemStack> savedBooks = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.inventory.ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            org.bukkit.inventory.ItemStack drop = it.next();
            if (drop != null && com.abyss.sigils.sigils.SigilItem.isBook(drop)) {
                savedBooks.add(drop.clone());
                it.remove();
            }
        }
        if (!savedBooks.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                for (org.bukkit.inventory.ItemStack book : savedBooks) {
                    var overflow = p.getInventory().addItem(book);
                    for (org.bukkit.inventory.ItemStack o : overflow.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), o);
                    }
                }
            });
        }

        // Unlimited lives → just respawn (no decrement)
        if (t.lives() == 0) {
            broadcast(session, "&7" + p.getName() + " &cdied &7(unlimited lives)");
            return;
        }

        boolean stillAlive = session.decrementLife(p.getUniqueId());
        int left = session.livesOf(p.getUniqueId());
        if (stillAlive) {
            broadcast(session, "&7" + p.getName() + " &cdied &7— &c" + left + " ❤ &7left");
        } else {
            broadcast(session, "&4" + p.getName() + " has been eliminated.");
            session.eliminate(p.getUniqueId());
            // Remove from the boss bar right now AND after a short delay.
            // Doing it twice — once on death, once a tick after respawn — is
            // belt-and-braces: clients sometimes ignore boss-bar removal that
            // arrives mid-respawn-transition, leaving a ghost bar on screen.
            if (session.progressBar() != null) session.progressBar().removePlayer(p);
            // Check if everyone's out
            int active = 0;
            for (UUID uid : session.players()) if (!session.isEliminated(uid)) active++;
            if (active == 0) {
                session.setPhase(DungeonSession.Phase.FAILED);
                broadcast(session, "&c&lYour party has fallen. The Abyss claims you.");
                // Schedule cleanup after a short delay so respawn message is seen
                Bukkit.getScheduler().runTaskLater(plugin, () -> endSession(session), 60L);
            }
        }
    }

    /**
     * Respawn players inside the dungeon (if they still have lives) or at the
     * exit location (if they're eliminated).
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        DungeonSession session = sessionOf(p);
        if (session == null) return;
        DungeonTemplate t = plugin.templates().get(session.templateName());
        if (t == null) return;

        if (session.isEliminated(p.getUniqueId())) {
            // Kick out: respawn at exit + remove from session
            e.setRespawnLocation(resolveExitLocation());
            playerToSession.remove(p.getUniqueId());
            session.players().remove(p.getUniqueId());
            if (session.progressBar() != null) session.progressBar().removePlayer(p);
            // Belt-and-braces: re-issue the removal a few ticks AFTER respawn
            // actually finishes. PlayerRespawnEvent fires before the player is
            // fully respawned, so a bar.removePlayer call here can be swallowed
            // by the client transition — leaving a ghost bar on screen even
            // though the server thinks it removed it.
            final ProgressBar pb = session.progressBar();
            if (pb != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) pb.removePlayer(p);
                }, 5L);
            }
            return;
        }
        // Still in the dungeon — respawn at the template's player spawn
        e.setRespawnLocation(bindToWorld(t.playerSpawn(), session.world()));
    }

    private DungeonSession sessionByWorld(World w) {
        for (DungeonSession s : sessions.values()) if (s.world().equals(w)) return s;
        return null;
    }

    private void triggerBoss(DungeonSession session, DungeonTemplate t) {
        session.setPhase(DungeonSession.Phase.BOSS);
        broadcast(session, "&4&l" + t.bossMobId().toUpperCase() + " AWAKENS");

        // Kill any leftover trash
        for (UUID id : new HashSet<>(session.aliveMobs())) {
            Entity ent = Bukkit.getEntity(id);
            if (ent != null) ent.remove();
        }
        session.aliveMobs().clear();

        Location loc = bindToWorld(t.bossSpawn(), session.world());
        spawnMythic(t.bossMobId(), loc, t.bossLevel()).ifPresent(e -> {
            session.setBossEntityId(e.getUniqueId());
            session.progressBar().setBossPhase(t.bossMobId());
            if (e instanceof LivingEntity le) {
                double max;
                try { max = le.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(
                        org.bukkit.NamespacedKey.minecraft("max_health"))).getValue(); }
                catch (Throwable ex) { max = le.getHealth(); }
                session.progressBar().setBossHealth(le.getHealth(), max);
            }
        });
    }

    private void onBossDeath(DungeonSession session, Location at) {
        session.setPhase(DungeonSession.Phase.COMPLETE);
        broadcast(session, "&6&lThe boss falls. The forge awakens.");

        Block block = at.clone().add(0, 1, 0).getBlock();
        Material upgradeMat = Material.matchMaterial(
                plugin.getConfig().getString("upgrade-block.block-type", "LODESTONE"));
        if (upgradeMat == null) upgradeMat = Material.LODESTONE;
        block.setType(upgradeMat);
        session.setUpgradeBlock(block.getLocation());

        // Spawn the reward chest next to the upgrade block
        plugin.rewardChests().placeChest(session, at);

        // Return portal — placed FAR from the forge/chest so players can't
        // accidentally click it. We pick the first free block from a list of
        // offsets that don't overlap the chest's spawn zone (which lives at
        // bossLoc + (±1, 1, ±1)). If all are occupied, fall back to (5,1,0).
        Material returnMat = Material.matchMaterial(
                plugin.getConfig().getString("return-portal.block-type", "END_GATEWAY"));
        if (returnMat == null) returnMat = Material.END_GATEWAY;
        int[][] returnOffsets = {
                {4, 1, 0}, {-4, 1, 0}, {0, 1, 4}, {0, 1, -4},
                {5, 1, 0}, {-5, 1, 0}, {0, 1, 5}, {0, 1, -5},
                {3, 1, 3}, {-3, 1, 3}, {3, 1, -3}, {-3, 1, -3}
        };
        Block returnBlock = null;
        for (int[] o : returnOffsets) {
            Block candidate = at.clone().add(o[0], o[1], o[2]).getBlock();
            if (candidate.getType() == Material.AIR) {
                returnBlock = candidate;
                break;
            }
        }
        if (returnBlock == null) {
            // Last resort — replace whatever's there at +5 X
            returnBlock = at.clone().add(5, 1, 0).getBlock();
        }
        returnBlock.setType(returnMat);
        session.setReturnPortalBlock(returnBlock.getLocation());
        spawnReturnPortalHologram(returnBlock.getLocation());

        session.progressBar().setBossHealth(0, 1);
        broadcast(session, "&7An upgrade altar has appeared. Right-click it to forge.");
        broadcast(session, "&aA &dreturn portal &ahas appeared. Right-click it to leave.");
    }

    /**
     * Floating "↩ Return to Overworld" label above the return portal block.
     * Non-persistent so it disappears with the instance world's chunk unload.
     */
    private void spawnReturnPortalHologram(Location blockLoc) {
        Location textLoc = blockLoc.clone().add(0.5, 1.6, 0.5);
        textLoc.getWorld().spawn(textLoc, org.bukkit.entity.TextDisplay.class, e -> {
            e.setText(Text.color("&a&l↩ &f&lReturn to Overworld &a&l↩"));
            e.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setSeeThrough(true);
            e.setDefaultBackground(false);
            e.setBackgroundColor(org.bukkit.Color.fromARGB(180, 0, 30, 10));
            e.setPersistent(false);
        });
    }

    private void scheduleTimeout(DungeonSession session, DungeonTemplate t) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.phase() != DungeonSession.Phase.COMPLETE) {
                session.setPhase(DungeonSession.Phase.FAILED);
                broadcast(session, "&c&lTime has run out.");
                endSession(session);
            }
        }, t.timeLimitMinutes() * 60L * 20L);
        addTask(session, task);
    }

    public void leave(Player p) {
        DungeonSession s = sessionOf(p);
        if (s != null) {
            if (s.progressBar() != null) s.progressBar().removePlayer(p);
            teleportOut(p);
            playerToSession.remove(p.getUniqueId());
            s.players().remove(p.getUniqueId());
            if (s.players().isEmpty()) endSession(s);
            return;
        }
        // Not in an active session — are they in a template (editor) world?
        if (p.getWorld().getName().startsWith("abyss_tpl_")) {
            teleportOut(p);
            // The wand is auto-removed by EditorWandListener on world change.
            p.sendMessage(Text.color("&aLeft the template editor."));
            return;
        }
        p.sendMessage(Text.color("&7You're not in The Abyss."));
    }

    public void endSession(DungeonSession s) {
        List<BukkitTask> tasks = sessionTasks.remove(s.id());
        if (tasks != null) for (BukkitTask t : tasks) t.cancel();

        for (UUID uid : new HashSet<>(s.players())) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) teleportOut(p);
            playerToSession.remove(uid);
        }
        if (s.progressBar() != null) s.progressBar().removeAll();
        plugin.rewardChests().cleanupSession(s.id());
        sessions.remove(s.id());

        World w = s.world();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String name = w.getName();
            if (Bukkit.unloadWorld(w, false)) {
                try { deleteRecursive(w.getWorldFolder().toPath()); }
                catch (IOException e) {
                    plugin.getLogger().warning("Couldn't delete instance " + name + ": " + e.getMessage());
                }
            }
        }, 40L);
    }

    private void addTask(DungeonSession s, BukkitTask t) {
        sessionTasks.computeIfAbsent(s.id(), k -> new ArrayList<>()).add(t);
    }

    /**
     * Where to put the player when they leave a dungeon.
     *
     * Priority:
     *   1. dungeon.exit.x/y/z/world if all set and non-default
     *   2. otherwise the configured portal location +1 on Y (stand on the
     *      portal block) — that's almost always what you want, since the
     *      portal is where they came in.
     *   3. otherwise (no portal configured either) the primary world's spawn
     */
    private Location resolveExitLocation() {
        ConfigurationSection ex = plugin.getConfig().getConfigurationSection("dungeon.exit");
        if (ex != null && ex.isSet("x") && ex.isSet("y") && ex.isSet("z")) {
            World w = Bukkit.getWorld(ex.getString("world", "world"));
            if (w != null) {
                return new Location(w,
                        ex.getDouble("x"),
                        ex.getDouble("y"),
                        ex.getDouble("z"));
            }
        }
        // Fall back to standing on top of the portal block
        String pw = plugin.getConfig().getString("portal.world", null);
        if (pw != null && plugin.getConfig().isSet("portal.x")) {
            World w = Bukkit.getWorld(pw);
            if (w != null) {
                return new Location(w,
                        plugin.getConfig().getInt("portal.x") + 0.5,
                        plugin.getConfig().getInt("portal.y") + 1.0,
                        plugin.getConfig().getInt("portal.z") + 0.5);
            }
        }
        // Last resort — primary world spawn
        World fallback = Bukkit.getWorlds().get(0);
        return fallback.getSpawnLocation();
    }

    private void teleportOut(Player p) {
        p.teleport(resolveExitLocation());
    }

    private void broadcast(DungeonSession s, String msg) {
        for (UUID id : s.players()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Text.color(msg));
        }
    }

    public Collection<DungeonSession> sessions() { return sessions.values(); }

    public void shutdown() {
        for (DungeonSession s : new ArrayList<>(sessions.values())) endSession(s);
    }

    private static Location bindToWorld(Location l, World w) {
        if (l == null) return null;
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    // ----- world clone -----

    private World cloneWorld(World template, String newName) throws IOException {
        template.save();
        File src = template.getWorldFolder();
        File dst = new File(Bukkit.getWorldContainer(), newName);
        if (dst.exists()) deleteRecursive(dst.toPath());
        copyDir(src.toPath(), dst.toPath());
        new File(dst, "uid.dat").delete();
        new File(dst, "session.lock").delete();

        WorldCreator wc = new WorldCreator(newName);
        wc.environment(template.getEnvironment());
        wc.seed(template.getSeed());
        World w = Bukkit.createWorld(wc);
        if (w != null) {
            w.setAutoSave(false);
            w.setDifficulty(Difficulty.HARD);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            // KEEP_INVENTORY is set in start() based on the template's keepInventory setting.
        }
        return w;
    }

    private static void copyDir(Path src, Path dst) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(p -> {
                try {
                    Path rel = src.relativize(p);
                    String name = rel.getFileName() == null ? "" : rel.getFileName().toString();
                    if (name.equals("session.lock") || name.equals("uid.dat")) return;
                    Path target = dst.resolve(rel);
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) { throw new RuntimeException(ex); }
            });
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> stream = Files.walk(p)) {
            stream.sorted(Comparator.reverseOrder()).forEach(f -> {
                try { Files.delete(f); } catch (IOException ignored) {}
            });
        }
    }
}
