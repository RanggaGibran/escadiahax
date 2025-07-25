package id.rnggagib.replay;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import id.rnggagib.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages recording and playing back mining replays
 */
public class ReplayManager {
    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    
    // Maps player UUIDs to their current replay sessions
    private final Map<UUID, ReplaySession> activeRecordings;
    
    // Stored replay sessions for each player
    private final Map<UUID, List<ReplaySession>> playerSessions;
    
    // Currently active replay viewers
    private final Map<UUID, ReplayViewer> activeViewers;
    
    // Configuration
    private boolean enabled;
    private int maxDuration;
    private int maxSessionsPerPlayer;
    private int storageTime;
    private List<Material> highlightBlocks;
    private boolean debugMode;
    
    public ReplayManager(Plugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.activeRecordings = new ConcurrentHashMap<>();
        this.playerSessions = new ConcurrentHashMap<>();
        this.activeViewers = new ConcurrentHashMap<>();
        
        loadConfig();
        
        // Schedule cleanup task
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupOldReplays, 20L * 60L, 20L * 60L * 10L); // Run every 10 minutes
    }
    
    /**
     * Loads the configuration for the replay system
     */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("replay.enabled", true);
        this.maxDuration = plugin.getConfig().getInt("replay.max-duration", 180);
        this.maxSessionsPerPlayer = plugin.getConfig().getInt("replay.max-sessions-per-player", 5);
        this.storageTime = plugin.getConfig().getInt("replay.storage-time", 60);
        this.debugMode = plugin.getConfig().getBoolean("replay.debug-mode", false);
        
        // Load highlight blocks
        this.highlightBlocks = new ArrayList<>();
        List<String> blockNames = plugin.getConfig().getStringList("replay.highlight-blocks");
        for (String blockName : blockNames) {
            try {
                Material material = Material.valueOf(blockName);
                highlightBlocks.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in replay.highlight-blocks: " + blockName);
            }
        }
    }
    
    /**
     * Helper method for conditional debug logging
     * @param message The message to log
     * @param level The debug level (0=essential, 1=important, 2=detailed)
     */
    private void debug(String message, int level) {
        boolean logMinimal = plugin.getConfig().getBoolean("replay.log-minimal", true);
        
        // If log-minimal is true, only log level 0 (essential) messages
        if (logMinimal && level > 0) {
            return;
        }
        
        // Otherwise, respect the configured debug level
        if (debugMode && plugin.getConfig().getInt("replay.debug-level", 0) >= level) {
            plugin.getLogger().info("[ReplayDebug] " + message);
        }
    }
    
    /**
     * Helper method for conditional debug logging (default level 1)
     * @param message The message to log
     */
    private void debug(String message) {
        debug(message, 1);
    }
    
    /**
     * Records a block break event for potential replay
     * 
     * @param player The player who broke the block
     * @param block The block that was broken
     */
    public void recordBlockBreak(Player player, Block block) {
        if (!enabled) return;
        
        UUID playerId = player.getUniqueId();
        
        // First check if there's an active recording already in progress
        ReplaySession session = activeRecordings.get(playerId);
        
        // If no active recording, check for a recent session that can be continued
        if (session == null) {
            List<ReplaySession> existingSessions = playerSessions.getOrDefault(playerId, new ArrayList<>());
            
            if (!existingSessions.isEmpty()) {
                // Get the most recent session
                ReplaySession recentSession = existingSessions.stream()
                    .sorted(Comparator.comparing(ReplaySession::getStartTime).reversed())
                    .findFirst().orElse(null);
                
                // Only continue if the session is recent (within last 60 seconds) and not already at limit
                if (recentSession != null && 
                    recentSession.getDurationSeconds() < 60 && 
                    recentSession.getStartTime().plusSeconds(60).isAfter(LocalDateTime.now())) {
                    
                    debug("Retrieving recent session to continue recording for " + player.getName() + 
                          " (Session ID: " + recentSession.getSessionId() + ", Duration so far: " + 
                          recentSession.getDurationSeconds() + "s)");
                    
                    // Move the session back to active recordings
                    session = recentSession;
                    activeRecordings.put(playerId, session);
                    playerSessions.get(playerId).remove(recentSession);
                }
            }
            
            // If still no session found, create a new one
            if (session == null) {
                session = new ReplaySession(playerId, player.getName());
                activeRecordings.put(playerId, session);
                debug("Creating new replay recording session for " + player.getName() + 
                      " (Session ID: " + session.getSessionId() + ")", 0); // Essential log - made level 0
            }
        }
        
        // Check if session already reached the 60-second time limit
        long durationSeconds = session.getDurationSeconds();
        if (durationSeconds >= 60) {
            debug("Session already reached 60-second limit, finalizing and not adding new events for " + 
                  player.getName() + " (Session ID: " + session.getSessionId() + ")", 1);
            
            // Finalize this session if it's still active
            if (activeRecordings.containsKey(playerId)) {
                finalizeRecording(playerId, "Session time limit reached", session.getSuspicionScore());
            }
            return;
        }
        
        // Add the mining event to the session
        MiningEvent event = new MiningEvent(player, block.getLocation(), block.getType());
        session.addEvent(event);
        
        // This is a common event that happens frequently - use detailed logging (level 2)
        debug("Recorded block break: " + block.getType() + " at " + block.getLocation().getBlockX() + 
              "," + block.getLocation().getBlockY() + "," + block.getLocation().getBlockZ() + 
              " (Session ID: " + session.getSessionId() + ", Duration: " + session.getDurationSeconds() + "s)", 2);
        
        // Check if this event pushed us over the 60-second limit
        // Only finalize if we've actually exceeded the limit
        if (session.getDurationSeconds() >= 60) {
            debug("Session reached 60-second limit after adding event, finalizing recording for " + 
                  player.getName() + " (Session ID: " + session.getSessionId() + ")", 1);
            finalizeRecording(playerId, "Session time limit reached", session.getSuspicionScore());
        }
    }
    
    /**
     * Finalizes a recording session and stores it
     * 
     * @param playerId The player ID
     * @param reason The reason for suspicion
     * @param score The suspicion score
     * @return The finalized session, or null if no valid session was found
     */
    public ReplaySession finalizeRecording(UUID playerId, String reason, int score) {
        ReplaySession session = activeRecordings.remove(playerId);
        if (session == null) {
            debug("No active recording found to finalize for player ID: " + playerId, 1);
            return null;
        }
        
        if (session.getEvents().isEmpty()) {
            debug("Skipped finalizing empty session for player ID: " + playerId, 1);
            return null;
        }
        
        // Set the suspicion details
        if (reason != null) {
            session.setSuspicionReason(reason);
        }
        session.setSuspicionScore(score);
        
        // Store the session
        playerSessions.computeIfAbsent(playerId, id -> new ArrayList<>()).add(session);
        
        debug("Finalized recording for player ID: " + playerId + 
              " (Session ID: " + session.getSessionId() + 
              ", Event count: " + session.getEvents().size() + 
              ", Duration: " + session.getDurationSeconds() + "s)", 0); // Essential log - made level 0
        
        // Ensure we don't exceed the max sessions per player
        List<ReplaySession> sessions = playerSessions.get(playerId);
        if (sessions.size() > maxSessionsPerPlayer) {
            // Sort by time and remove the oldest
            sessions.sort(Comparator.comparing(ReplaySession::getStartTime));
            ReplaySession removed = sessions.remove(0);
            debug("Removed oldest session to maintain max sessions limit for player ID: " + playerId +
                  " (Removed session ID: " + removed.getSessionId() + ")", 1);
        }
        
        return session;
    }
    
    /**
     * Starts a replay for a staff member
     * 
     * @param viewer The staff member viewing the replay
     * @param sessionId The session ID to replay
     * @return True if the replay was started successfully
     */
    public boolean startReplay(Player viewer, UUID sessionId) {
        if (!enabled) return false;
        
        // Find the requested session
        ReplaySession session = null;
        UUID ownerPlayerId = null;
        
        // First check if this is an active session that hasn't been finalized
        for (Map.Entry<UUID, ReplaySession> entry : activeRecordings.entrySet()) {
            if (entry.getValue().getSessionId().equals(sessionId)) {
                session = entry.getValue();
                ownerPlayerId = entry.getKey();
                debug("Found active recording session: " + sessionId + " for player: " + session.getPlayerName());
                break;
            }
        }
        
        // If not active, look in finalized sessions
        if (session == null) {
            for (Map.Entry<UUID, List<ReplaySession>> entry : playerSessions.entrySet()) {
                for (ReplaySession s : entry.getValue()) {
                    if (s.getSessionId().equals(sessionId)) {
                        session = s;
                        ownerPlayerId = entry.getKey();
                        debug("Found finalized session: " + sessionId + " for player: " + session.getPlayerName());
                        break;
                    }
                }
                if (session != null) break;
            }
        }
        
        if (session == null) {
            viewer.sendMessage("§c[EscadiaHax] §fReplay session not found: " + sessionId);
            debug("Session not found: " + sessionId);
            return false;
        }
        
        // Check if it's an active recording that hasn't been finalized yet
        if (activeRecordings.containsKey(ownerPlayerId) && 
            activeRecordings.get(ownerPlayerId).getSessionId().equals(sessionId)) {
            // Finalize this recording before allowing replay
            ReplaySession finalizedSession = finalizeRecording(ownerPlayerId, "Manually replayed", session.getSuspicionScore());
            
            if (finalizedSession != null) {
                session = finalizedSession;
                debug("Finalized active recording " + sessionId + " to allow immediate replay. Event count: " + 
                      session.getEvents().size());
            } else {
                debug("Failed to finalize session " + sessionId + " - this should not happen");
            }
        }
        
        // Create a replay viewer
        ReplayViewer replayViewer = new ReplayViewer(viewer, session);
        activeViewers.put(viewer.getUniqueId(), replayViewer);
        
        // Log detailed session info before starting
        debug("Starting replay of session " + sessionId + " for viewer " + viewer.getName() +
              ". Events: " + session.getEvents().size() + 
              ", Duration: " + session.getDurationSeconds() + "s");
        
        // Start the replay
        replayViewer.start();
        
        // Inform the viewer about session details
        long duration = session.getDurationSeconds();
        int eventCount = session.getEvents().size();
        
        viewer.sendMessage("§a[EscadiaHax] §fStarting replay of §e" + session.getPlayerName() + 
                "§f's mining session. Duration: §e" + duration + "s§f, Events: §e" + eventCount + "§f.");
        
        if (duration < 60) {
            viewer.sendMessage("§7[EscadiaHax] §fThis is a partial session (ended before the 60-second limit).");
        }
        
        return true;
    }
    
    /**
     * Stops a replay for a staff member
     * 
     * @param viewer The staff member viewing the replay
     */
    public void stopReplay(Player viewer) {
        ReplayViewer replayViewer = activeViewers.remove(viewer.getUniqueId());
        if (replayViewer != null) {
            replayViewer.stop();
        }
    }
    
    /**
     * Cleans up old replay sessions
     */
    private void cleanupOldReplays() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(storageTime);
        
        for (Map.Entry<UUID, List<ReplaySession>> entry : playerSessions.entrySet()) {
            entry.getValue().removeIf(session -> session.getEndTime().isBefore(cutoff));
        }
        
        // Remove empty player entries
        playerSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    
    /**
     * Gets the list of sessions for a player
     * 
     * @param playerId The player ID
     * @return The list of replay sessions
     */
    public List<ReplaySession> getPlayerSessions(UUID playerId) {
        return playerSessions.getOrDefault(playerId, new ArrayList<>());
    }
    
    /**
     * Gets the list of all recent suspicious sessions
     * 
     * @param maxResults The maximum number of results to return
     * @return The list of recent suspicious sessions
     */
    public List<ReplaySession> getRecentSuspiciousSessions(int maxResults) {
        List<ReplaySession> allSessions = new ArrayList<>();
        
        for (List<ReplaySession> sessions : playerSessions.values()) {
            allSessions.addAll(sessions);
        }
        
        return allSessions.stream()
                .filter(session -> session.getSuspicionScore() > 0)
                .sorted(Comparator.comparing(ReplaySession::getEndTime).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Inner class to handle replay viewing
     */
    private class ReplayViewer {
        private final Player viewer;
        private final ReplaySession session;
        private BukkitTask replayTask;
        private final List<Integer> entityIds = new ArrayList<>();
        private final Map<Location, Material> originalBlocks = new HashMap<>();
        private final Set<Location> highlightedBlocks = new HashSet<>();
        private int currentEventIndex = 0;
        private double playbackSpeed = 1.0;
        private boolean isPaused = false;
        private boolean povMode = true; // Default to POV mode
        private Location lastPlayerLocation = null; // Store the last location for relative movement
        private ItemStack[] savedInventory = null;
        
        public ReplayViewer(Player viewer, ReplaySession session) {
            this.viewer = viewer;
            this.session = session;
        }
        
        /**
         * Starts the replay
         */
        public void start() {
            debug("Starting replay session for " + viewer.getName() + 
                  " (Session ID: " + session.getSessionId() + 
                  ", Event count: " + session.getEvents().size() + 
                  ", Duration: " + session.getDurationSeconds() + "s)");
            
            // Ensure we have at least one event
            if (session.getEvents().isEmpty()) {
                viewer.sendMessage("§c[EscadiaHax] §fThis replay session contains no events to show.");
                debug("Cannot start replay - session has no events");
                return;
            }
            
            // Reset the replay viewer state
            currentEventIndex = 0;
            lastPlayerLocation = null;
            removeEntities();
            restoreBlocks();
            
            // Save inventory and give replay control tools
            giveReplayControlTools();
            
            // Display information about the replay
            viewer.sendMessage("§a[EscadiaHax] §fStarting replay of §e" + session.getPlayerName() + 
                               "§f's mining session (Duration: §e" + session.getDurationSeconds() + "s§f)");
            viewer.sendMessage("§a[EscadiaHax] §fUse the items in your hotbar to control the replay.");
            
            // Set the viewer to adventure mode initially so they can use the tools
            viewer.setGameMode(org.bukkit.GameMode.ADVENTURE);
            
            // Add night vision for better visibility in dark areas
            viewer.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.NIGHT_VISION, 
                20 * 60 * 10, 0, false, false));
                
            // Get the first event and position the viewer
            MiningEvent firstEvent = session.getEvents().get(0);
            
            // In POV mode, teleport directly to player location
            if (povMode) {
                Location playerLoc = firstEvent.getPlayerLocation().clone();
                // Set the view to match the player's view
                playerLoc.setYaw(firstEvent.getPlayerYaw());
                playerLoc.setPitch(firstEvent.getPlayerPitch());
                viewer.teleport(playerLoc);
                viewer.sendMessage("§a[EscadiaHax] §fViewing in POV mode. Type §e/replay control pov§f to toggle view mode.");
                debug("Started in POV mode at position " + 
                      playerLoc.getX() + "," + playerLoc.getY() + "," + playerLoc.getZ());
            } else {
                // In observer mode, teleport to a good vantage point
                Location observerLoc = firstEvent.getPlayerLocation().clone().add(3, 2, 3);
                // Make the observer look at the player's position
                observerLoc.setDirection(firstEvent.getPlayerLocation().toVector().subtract(observerLoc.toVector()));
                viewer.teleport(observerLoc);
                viewer.sendMessage("§a[EscadiaHax] §fViewing in observer mode. Type §e/replay control pov§f to toggle view mode.");
                debug("Started in observer mode at position " + 
                      observerLoc.getX() + "," + observerLoc.getY() + "," + observerLoc.getZ());
                
                // Spawn the fake player immediately for observer mode
                spawnFakePlayer(firstEvent);
            }
            
            // Start the replay task - runs every 5 ticks (1/4 second) at normal speed
            // This creates smoother movement while still being performant
            replayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::playNextEvent, 5L, 5L);
            debug("Started replay task with period: 5 ticks");
            
            // Additional instructions for the viewer
            viewer.sendMessage("§a[EscadiaHax] §fControls: §e/replay control pause§f to pause/resume");
            viewer.sendMessage("§a[EscadiaHax] §fControls: §e/replay control speed <value>§f to change speed");
            viewer.sendMessage("§a[EscadiaHax] §fControls: §e/replay stop§f to exit replay mode");
        }
        
        /**
         * Stops the replay
         */
        public void stop() {
            if (replayTask != null) {
                replayTask.cancel();
                replayTask = null;
            }
            
            // Restore blocks
            restoreBlocks();
            
            // Remove entities
            removeEntities();
            
            // Restore game mode
            viewer.setGameMode(org.bukkit.GameMode.CREATIVE);
            
            // Restore inventory
            restoreInventory();
            
            viewer.sendMessage("§a[EscadiaHax] §fReplay ended.");
        }
        
        /**
         * Plays the next event in the replay sequence
         */
        private void playNextEvent() {
            if (isPaused) return;
            
            List<MiningEvent> events = session.getEvents();
            if (events.isEmpty()) {
                debug("No events in session to play");
                viewer.sendMessage("§a[EscadiaHax] §fReplay complete. No events to show.");
                stopReplay(viewer);
                return;
            }
            
            if (currentEventIndex >= events.size()) {
                // End of replay
                debug("Reached end of replay at index " + currentEventIndex + " (total events: " + events.size() + ")");
                viewer.sendMessage("§a[EscadiaHax] §fReplay complete.");
                stopReplay(viewer);
                return;
            }
            
            // Get current event
            MiningEvent event = events.get(currentEventIndex);
                
            // Debug info about current event
            debug("Playing event " + (currentEventIndex + 1) + " of " + events.size() +
                " at " + event.getLocation().getBlockX() + "," + event.getLocation().getBlockY() + "," + 
                event.getLocation().getBlockZ() + " (" + event.getBlockType() + ")");
            debug("Player position: " + event.getPlayerLocation().getX() + "," + 
                event.getPlayerLocation().getY() + "," + event.getPlayerLocation().getZ() + 
                " Yaw: " + event.getPlayerYaw() + " Pitch: " + event.getPlayerPitch());
            
            // If we're in POV mode, update the viewer's position to match player's view
            if (povMode) {
                // Create location with player's position and view angles
                Location playerLoc = event.getPlayerLocation().clone();
                playerLoc.setYaw(event.getPlayerYaw());
                playerLoc.setPitch(event.getPlayerPitch());
                
                // Teleport the viewer to see from player's POV
                viewer.teleport(playerLoc);
                debug("POV teleport to: " + playerLoc.getX() + "," + playerLoc.getY() + "," + playerLoc.getZ());
            } else {
                // In observer mode, create a fake player to represent the miner
                if (currentEventIndex == 0 || entityIds.isEmpty()) {
                    // Spawn fake player at the start or if it doesn't exist
                    spawnFakePlayer(event);
                    debug("Spawned fake player at event " + currentEventIndex);
                } else {
                    // Update fake player position using the proper relative movement method
                    updateFakePlayerPosition(event);
                    debug("Updated fake player position at event " + currentEventIndex);
                }
            }
            
            // First, restore/show the block to be mined in its original form
            restoreBlockBeforeMining(event);
            
            // Then, after a delay, show the mining animation and block break
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Show mining animation if not in POV mode
                if (!povMode) {
                    showMiningAnimation(event);
                }
                
                // After a short delay, highlight the block (replacing it with air to show it's been mined)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    highlightBlock(event);
                }, 5L); // 5 ticks (0.25 seconds) after mining animation
            }, 10L); // 10 ticks (0.5 seconds) after block is restored
            
            // Advance to next event based on playback speed
            if (playbackSpeed >= 1.0) {
                // For speeds >= 1, advance by the number of events that should occur at this speed
                // For example, at 2x speed, we should skip an event to move twice as fast
                int increment = (int)Math.ceil(playbackSpeed);
                currentEventIndex += increment;
                debug("Advanced by " + increment + " events to index " + currentEventIndex + 
                      " (speed: " + playbackSpeed + "x)");
            } else {
                // For slower speeds, we want to advance the index more gradually
                // At 0.5x speed, we should advance every other tick
                double advanceProbability = playbackSpeed;
                if (Math.random() < advanceProbability) {
                    currentEventIndex++;
                    debug("Advanced to next event (slow speed: " + playbackSpeed + "x)");
                } else {
                    debug("Holding at current event " + currentEventIndex + " due to slow speed: " + playbackSpeed + "x");
                }
            }
        }
        
        /**
         * Spawns a fake entity to represent the player in the replay
         * 
         * @param event The mining event with player information
         */
        private void spawnFakePlayer(MiningEvent event) {
            try {
                // Clean up previous entities if they exist
                if (!entityIds.isEmpty()) {
                    removeEntities();
                    entityIds.clear();
                }
                
                // Create a new entity ID
                int entityId = generateEntityId();
                entityIds.add(entityId);
                UUID entityUuid = UUID.randomUUID();
                
                // Debug info
                debug("Spawning fake entity: ID=" + entityId + 
                    ", Player=" + event.getPlayerName());
                
                // Get player location information
                Location loc = event.getPlayerLocation();
                float yaw = event.getPlayerYaw();
                float pitch = event.getPlayerPitch();
                
                debug("Spawn location: " + loc.getX() + "," + loc.getY() + "," + loc.getZ() + 
                      " (World: " + (loc.getWorld() != null ? loc.getWorld().getName() : "null") + ")");
                
                // 1. Create entity spawn packet - Using ArmorStand for maximum compatibility
                PacketContainer spawnPacket = null;
                try {
                    spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
                    
                    // Set entity ID and UUID
                    spawnPacket.getIntegers().write(0, entityId);
                    spawnPacket.getUUIDs().write(0, entityUuid);
                    
                    // Set entity type to armor stand (this will be invisible by default in replay)
                    spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
                    
                    // Set position - use exact coordinates
                    spawnPacket.getDoubles()
                        .write(0, loc.getX())
                        .write(1, loc.getY() - 0.25) // Adjusted offset Y to make armor stand appear at right height
                        .write(2, loc.getZ());
                    
                    // Try to set velocity to 0 if the packet supports it
                    try {
                        if (spawnPacket.getIntegers().size() > 3) {
                            spawnPacket.getIntegers()
                                .write(1, 0) // Velocity X
                                .write(2, 0) // Velocity Y
                                .write(3, 0); // Velocity Z
                        }
                    } catch (Exception velocityError) {
                        // Ignore velocity errors, not critical
                        debug("Note: Velocity fields not supported in this version", 1);
                    }
                    
                    // Send the spawn packet
                    protocolManager.sendServerPacket(viewer, spawnPacket);
                    debug("Sent spawn packet with entity ID " + entityId, 1);
                    
                    // Store initial location for relative movement calculations
                    lastPlayerLocation = loc.clone();
                    debug("Set lastPlayerLocation: " + lastPlayerLocation.getX() + "," + 
                          lastPlayerLocation.getY() + "," + lastPlayerLocation.getZ(), 2);
                } catch (Exception spawnError) {
                    plugin.getLogger().severe("Error spawning entity: " + spawnError.getMessage());
                    spawnError.printStackTrace();
                    return; // Stop if we can't spawn the entity
                }
                
                // 2. Set entity metadata (compatible with Paper 1.21.4)
                try {
                    // Skip metadata packet entirely for Paper 1.21.4+
                    // This avoids the ClassCastException issue with entity metadata
                    // ClassCastException: class net.minecraft.network.syncher.SynchedEntityData$DataItem cannot be cast 
                    // to class net.minecraft.network.syncher.SynchedEntityData$DataValue
                    debug("Skipping metadata packet for Paper 1.21.4+ compatibility");
                    
                    // If you need to set metadata in the future, you'll need to use
                    // the newer Paper 1.21.4 API for metadata rather than the old approach
                    // Or consider using Bukkit API directly instead of ProtocolLib for metadata
                } catch (Exception metaError) {
                    plugin.getLogger().warning("Error handling entity metadata: " + metaError.getMessage());
                    metaError.printStackTrace();
                }
                
                // 3. Set equipment (give it a diamond pickaxe)
                try {
                    PacketContainer equipmentPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
                    equipmentPacket.getIntegers().write(0, entityId);
                    
                    // Create a single equipment pair (mainhand -> diamond pickaxe)
                    com.comphenix.protocol.wrappers.Pair<com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot, org.bukkit.inventory.ItemStack> equipment =
                        new com.comphenix.protocol.wrappers.Pair<>(
                            com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot.MAINHAND,
                            new org.bukkit.inventory.ItemStack(Material.DIAMOND_PICKAXE)
                        );
                    
                    List<com.comphenix.protocol.wrappers.Pair<com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot, org.bukkit.inventory.ItemStack>> equipmentList = 
                        new ArrayList<>();
                    equipmentList.add(equipment);
                    
                    // Write the equipment list
                    equipmentPacket.getSlotStackPairLists().write(0, equipmentList);
                    
                    // Send equipment packet
                    protocolManager.sendServerPacket(viewer, equipmentPacket);
                    debug("Sent equipment packet for entity " + entityId);
                } catch (Exception equipError) {
                    plugin.getLogger().warning("Error setting entity equipment: " + equipError.getMessage());
                    equipError.printStackTrace();
                }
                
                // 4. Set initial rotation with look packet
                try {
                    PacketContainer lookPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
                    lookPacket.getIntegers().write(0, entityId);
                    
                    // Convert degrees to byte values (0-255)
                    byte yawByte = (byte)(yaw * 256.0F / 360.0F);
                    byte pitchByte = (byte)(pitch * 256.0F / 360.0F);
                    
                    lookPacket.getBytes()
                        .write(0, yawByte)
                        .write(1, pitchByte);
                    
                    // Set on ground flag
                    lookPacket.getBooleans().write(0, true);
                    
                    // Send look packet
                    protocolManager.sendServerPacket(viewer, lookPacket);
                    debug("Sent look packet for entity " + entityId);
                } catch (Exception lookError) {
                    plugin.getLogger().warning("Error setting entity look: " + lookError.getMessage());
                    lookError.printStackTrace();
                }
                
                // 5. Set head rotation
                try {
                    PacketContainer headPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                    headPacket.getIntegers().write(0, entityId);
                    
                    // Head rotation uses byte value
                    byte yawByte = (byte)(yaw * 256.0F / 360.0F);
                    headPacket.getBytes().write(0, yawByte);
                    
                    // Send head rotation packet
                    protocolManager.sendServerPacket(viewer, headPacket);
                    debug("Sent head rotation packet for entity " + entityId);
                } catch (Exception headError) {
                    plugin.getLogger().warning("Error setting entity head rotation: " + headError.getMessage());
                    headError.printStackTrace();
                }
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error in spawnFakePlayer: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        /**
         * Updates the position of the fake player using proper relative movement
         * 
         * @param event The mining event containing player position information
         */
        private void updateFakePlayerPosition(MiningEvent event) {
            if (entityIds.isEmpty()) return;
            
            try {
                int entityId = entityIds.get(0);
                Location currentLoc = event.getPlayerLocation();
                
                // Debug log current location
                debug("Player position: ID=" + entityId + 
                    ", X=" + currentLoc.getX() + ", Y=" + currentLoc.getY() + 
                    ", Z=" + currentLoc.getZ() + ", Yaw=" + event.getPlayerYaw() + 
                    ", Pitch=" + event.getPlayerPitch());
                
                // If we have a previous location, calculate a proper delta move
                if (lastPlayerLocation != null && lastPlayerLocation.getWorld().equals(currentLoc.getWorld())) {
                    // Calculate the difference in position
                    double deltaX = currentLoc.getX() - lastPlayerLocation.getX();
                    double deltaY = currentLoc.getY() - lastPlayerLocation.getY();
                    double deltaZ = currentLoc.getZ() - lastPlayerLocation.getZ();
                    
                    // Convert to Minecraft's fixed point representation (1/8 of a block per unit)
                    // In Minecraft packets, position deltas are in 1/32 of a block
                    short dx = (short)(deltaX * 32);
                    short dy = (short)(deltaY * 32);
                    short dz = (short)(deltaZ * 32);
                    
                    // Check if we need to move
                    boolean needsMove = Math.abs(dx) > 0 || Math.abs(dy) > 0 || Math.abs(dz) > 0;
                    
                    // Check if yaw/pitch changed
                    float oldYaw = lastPlayerLocation.getYaw();
                    float oldPitch = lastPlayerLocation.getPitch();
                    float newYaw = event.getPlayerYaw();
                    float newPitch = event.getPlayerPitch();
                    boolean rotationChanged = Math.abs(oldYaw - newYaw) > 1 || Math.abs(oldPitch - newPitch) > 1;
                    
                    // If there's a significant movement, send a relative move packet
                    if (needsMove || rotationChanged) {
                        // Convert to proper packet values
                        byte yawByte = (byte)(newYaw * 256.0F / 360.0F);
                        byte pitchByte = (byte)(newPitch * 256.0F / 360.0F);
                        
                        try {
                            // In Paper 1.21.1, we should use REL_ENTITY_MOVE_LOOK if both position and rotation changed
                            // or REL_ENTITY_MOVE if only position changed
                            PacketContainer packet;
                            
                            if (needsMove && rotationChanged) {
                                // Both position and rotation changed
                                packet = protocolManager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
                                packet.getIntegers().write(0, entityId);
                                packet.getShorts()
                                    .write(0, dx)
                                    .write(1, dy)
                                    .write(2, dz);
                                packet.getBytes()
                                    .write(0, yawByte)
                                    .write(1, pitchByte);
                                packet.getBooleans().write(0, true); // onGround
                                
                                debug("Sending REL_ENTITY_MOVE_LOOK: " +
                                    "dx=" + dx + ", dy=" + dy + ", dz=" + dz + 
                                    ", yaw=" + yawByte + ", pitch=" + pitchByte);
                            } 
                            else if (needsMove) {
                                // Only position changed
                                packet = protocolManager.createPacket(PacketType.Play.Server.REL_ENTITY_MOVE);
                                packet.getIntegers().write(0, entityId);
                                packet.getShorts()
                                    .write(0, dx)
                                    .write(1, dy)
                                    .write(2, dz);
                                packet.getBooleans().write(0, true); // onGround
                                
                                debug("Sending REL_ENTITY_MOVE: " +
                                    "dx=" + dx + ", dy=" + dy + ", dz=" + dz);
                            }
                            else {
                                // Only rotation changed
                                packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
                                packet.getIntegers().write(0, entityId);
                                packet.getBytes()
                                    .write(0, yawByte)
                                    .write(1, pitchByte);
                                packet.getBooleans().write(0, true); // onGround
                                
                                debug("Sending ENTITY_LOOK: " +
                                    "yaw=" + yawByte + ", pitch=" + pitchByte);
                            }
                            
                            // Send the packet
                            protocolManager.sendServerPacket(viewer, packet);
                        } catch (Exception moveError) {
                            plugin.getLogger().warning("Error sending movement packet: " + moveError.getMessage());
                        }
                        
                        // Update head rotation if yaw changed
                        if (rotationChanged) {
                            try {
                                PacketContainer headPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                                headPacket.getIntegers().write(0, entityId);
                                headPacket.getBytes().write(0, yawByte);
                                protocolManager.sendServerPacket(viewer, headPacket);
                            } catch (Exception headError) {
                                plugin.getLogger().warning("Error sending head rotation packet: " + headError.getMessage());
                            }
                        }
                    }
                } else if (lastPlayerLocation == null) {
                    // First update, no previous location
                    debug("First position update, setting initial position");
                }
                
                // Update the last known location
                lastPlayerLocation = currentLoc.clone();
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error updating fake player position: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        /**
         * Highlights a block in the replay
         * 
         * @param event The mining event
         */
        private void highlightBlock(MiningEvent event) {
            Location loc = event.getLocation();
            
            // Avoid re-highlighting the same block
            if (highlightedBlocks.contains(loc)) {
                return;
            }
            
            // Store the original block
            World world = loc.getWorld();
            if (world == null) return;
            
            Block block = world.getBlockAt(loc);
            originalBlocks.put(loc, block.getType());
            highlightedBlocks.add(loc);
            
            // Send block change packet
            PacketContainer blockChange = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            blockChange.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            
            // Always display the actual block type - never substitute with glowstone
            Material displayMaterial = event.getBlockType();
            
            // Add a glowing effect to the block if it's valuable (but keep its original appearance)
            // In a future enhancement, we could use light level packets or entity effects for highlighting
            // without changing the block type
            
            // For now, we'll prioritize showing the actual material
            blockChange.getBlockData().write(0, 
                com.comphenix.protocol.wrappers.WrappedBlockData.createData(displayMaterial));
            
            try {
                protocolManager.sendServerPacket(viewer, blockChange);
                debug("Set block at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + 
                      " to " + displayMaterial + " (original type: " + event.getBlockType() + ")");
            } catch (Exception e) {
                plugin.getLogger().severe("Error highlighting block: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        /**
         * Shows a mining animation for the entity
         * 
         * @param event The mining event
         */
        private void showMiningAnimation(MiningEvent event) {
            if (entityIds.isEmpty()) return;
            
            try {
                int entityId = entityIds.get(0);
                
                // Create animation packet for 1.21.1
                PacketContainer animationPacket = protocolManager.createPacket(PacketType.Play.Server.ANIMATION);
                
                // Set the entity ID
                animationPacket.getIntegers().write(0, entityId);
                
                // In 1.21.1, animation type should be in the second integer field
                // 0 = SWING_MAIN_HAND, 1 = HURT, 2 = WAKE_UP, 3 = SWING_OFF_HAND, 4 = CRITICAL_EFFECT, 5 = MAGIC_CRITICAL_EFFECT
                try {
                    if (animationPacket.getIntegers().size() > 1) {
                        animationPacket.getIntegers().write(1, 0); // SWING_MAIN_HAND
                        debug("Set animation type to SWING_MAIN_HAND (0)");
                    }
                } catch (Exception animTypeError) {
                    // This is a fallback - in some versions, animation type isn't specified in a second integer
                    // The animation is implicitly SWING_MAIN_HAND when only entityId is provided
                    debug("Using default animation type (older protocol style)");
                }
                
                // Send the animation packet
                protocolManager.sendServerPacket(viewer, animationPacket);
                debug("Sent mining animation packet for entity " + entityId);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error showing mining animation: " + e.getMessage());
            }
        }
        
        /**
         * Restores a block to its original state before showing the mining animation
         * 
         * @param event The mining event
         */
        private void restoreBlockBeforeMining(MiningEvent event) {
            Location loc = event.getLocation();
            
            // Don't do anything if the world doesn't exist
            if (loc.getWorld() == null) {
                debug("Cannot restore block: world is null", 1);
                return;
            }
            
            try {
                // Create a block change packet
                PacketContainer blockChange = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
                
                // Set block position
                blockChange.getBlockPositionModifier().write(0, 
                    new com.comphenix.protocol.wrappers.BlockPosition(
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                
                // Set the block data to the original type
                Material originalType = event.getBlockType();
                
                // Store original block before highlighting
                if (!originalBlocks.containsKey(loc)) {
                    originalBlocks.put(loc, originalType);
                }
                
                // Set the block to the original material
                blockChange.getBlockData().write(0, 
                    com.comphenix.protocol.wrappers.WrappedBlockData.createData(originalType));
                
                // Send the packet
                protocolManager.sendServerPacket(viewer, blockChange);
                debug("Restored block at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + 
                      " to original type: " + originalType, 2);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error restoring block: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        /**
         * Restores all modified blocks
         */
        private void restoreBlocks() {
            for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                Material originalType = entry.getValue();
                
                PacketContainer blockChange = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
                blockChange.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                
                blockChange.getBlockData().write(0, 
                    com.comphenix.protocol.wrappers.WrappedBlockData.createData(originalType));
                
                try {
                    protocolManager.sendServerPacket(viewer, blockChange);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error restoring block: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            originalBlocks.clear();
            highlightedBlocks.clear();
        }
        
        /**
         * Removes all fake entities
         */
        private void removeEntities() {
            if (entityIds.isEmpty()) return;
            
            try {
                // Create a list of entity IDs to destroy
                List<Integer> idsToRemove = new ArrayList<>(entityIds);
                
                // Debug info
                debug("Removing entities: " + idsToRemove);
                
                // In Paper 1.21.1, ENTITY_DESTROY takes an int[] or List<Integer>
                PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                
                // Try different packet structure options for maximum compatibility
                boolean packetSent = false;
                
                // Method 1: Using int lists - this is the correct way for 1.21.1
                try {
                    if (destroyPacket.getIntLists().size() > 0) {
                        destroyPacket.getIntLists().write(0, idsToRemove);
                        protocolManager.sendServerPacket(viewer, destroyPacket);
                        packetSent = true;
                        debug("Removed entities using intLists method");
                    }
                } catch (Exception method1Error) {
                    plugin.getLogger().warning("Error removing entities with intLists: " + method1Error.getMessage());
                }
                
                // Method 2: Using integer arrays (older versions)
                if (!packetSent) {
                    try {
                        destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                        destroyPacket.getIntegerArrays().write(0, idsToRemove.stream().mapToInt(Integer::intValue).toArray());
                        protocolManager.sendServerPacket(viewer, destroyPacket);
                        packetSent = true;
                        debug("Removed entities using integerArrays method");
                    } catch (Exception method2Error) {
                        plugin.getLogger().warning("Error removing entities with integerArrays: " + method2Error.getMessage());
                    }
                }
                
                // Method 3: One by one removal as a last resort
                if (!packetSent) {
                    debug("Using one-by-one entity removal as fallback");
                    for (int entityId : idsToRemove) {
                        try {
                            PacketContainer singleDestroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                            
                            // Try int lists first (1.21.1 style)
                            if (singleDestroyPacket.getIntLists().size() > 0) {
                                List<Integer> singleId = Collections.singletonList(entityId);
                                singleDestroyPacket.getIntLists().write(0, singleId);
                            } 
                            // Fall back to int arrays (older versions)
                            else if (singleDestroyPacket.getIntegerArrays().size() > 0) {
                                singleDestroyPacket.getIntegerArrays().write(0, new int[] { entityId });
                            }
                            
                            protocolManager.sendServerPacket(viewer, singleDestroyPacket);
                            debug("Removed entity " + entityId + " individually");
                        } catch (Exception singleError) {
                            plugin.getLogger().warning("Failed to remove entity " + entityId + ": " + singleError.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error removing entities: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Clear the entity IDs list
            entityIds.clear();
        }
        
        /**
         * Generates a unique entity ID
         * 
         * @return A unique entity ID
         */
        private int generateEntityId() {
            return -new Random().nextInt(1000000) - 1;
        }
        
        /**
         * Sets the playback speed
         * 
         * @param speed The playback speed
         */
        public void setPlaybackSpeed(double speed) {
            this.playbackSpeed = Math.max(0.25, Math.min(10.0, speed));
            viewer.sendMessage("§a[EscadiaHax] §fPlayback speed set to §e" + playbackSpeed + "x");
        }
        
        /**
         * Toggles pause state
         * 
         * @return The new pause state
         */
        public boolean togglePause() {
            isPaused = !isPaused;
            viewer.sendMessage("§a[EscadiaHax] §fReplay " + (isPaused ? "§cpaused" : "§aresumed"));
            return isPaused;
        }
        
        /**
         * Toggles POV mode (first-person vs observer mode)
         * 
         * @return The new POV mode state
         */
        public boolean togglePOVMode() {
            povMode = !povMode;
            
            // If we're currently playing, update the view immediately
            if (currentEventIndex < session.getEvents().size()) {
                MiningEvent currentEvent = session.getEvents().get(currentEventIndex);
                
                if (povMode) {
                    // Remove fake player when switching to POV mode
                    removeEntities();
                    entityIds.clear();
                    
                    // Update player's view to match the miner's POV
                    Location playerLoc = currentEvent.getPlayerLocation().clone();
                    playerLoc.setYaw(currentEvent.getPlayerYaw());
                    playerLoc.setPitch(currentEvent.getPlayerPitch());
                    viewer.teleport(playerLoc);
                    
                    debug("Switched to POV mode at index " + currentEventIndex);
                } else {
                    // Move viewer to a position where they can observe the player
                    Location observerLoc = currentEvent.getPlayerLocation().clone().add(3, 2, 3);
                    // Make the observer look at the player
                    observerLoc.setDirection(currentEvent.getPlayerLocation().toVector().subtract(observerLoc.toVector()));
                    viewer.teleport(observerLoc);
                    
                    // Spawn fake player when switching to observer mode
                    spawnFakePlayer(currentEvent);
                    
                    debug("Switched to observer mode at index " + currentEventIndex);
                }
            }
            
            viewer.sendMessage("§a[EscadiaHax] §fSwitched to " + (povMode ? "§ePOV" : "§eobserver") + "§f mode");
            return povMode;
        }
        
        /**
         * Toggles POV mode (first-person vs observer mode)
         * Alias for togglePOVMode() to match the ReplayManager method
         * 
         * @return The new POV mode state
         */
        public boolean togglePOV() {
            return togglePOVMode();
        }
        
        /**
         * Skips forward in the replay
         * Alias for jumpForward() to match the ReplayManager method
         * 
         * @param seconds The number of seconds to skip forward
         */
        public void skipForward(int seconds) {
            jumpForward(seconds);
        }
        
        /**
         * Skips backward in the replay
         * Alias for jumpBackward() to match the ReplayManager method
         * 
         * @param seconds The number of seconds to skip backward
         */
        public void skipBackward(int seconds) {
            jumpBackward(seconds);
        }
        
        /**
         * Jumps forward in the replay
         * 
         * @param seconds The number of seconds to jump forward
         */
        public void jumpForward(int seconds) {
            List<MiningEvent> events = session.getEvents();
            if (events.isEmpty() || currentEventIndex >= events.size() - 1) return;
            
            LocalDateTime currentTime = events.get(currentEventIndex).getTimestamp();
            LocalDateTime targetTime = currentTime.plusSeconds(seconds);
            
            // Find the closest event after the target time
            int newIndex = currentEventIndex;
            for (int i = currentEventIndex + 1; i < events.size(); i++) {
                if (events.get(i).getTimestamp().isAfter(targetTime) || 
                    events.get(i).getTimestamp().equals(targetTime)) {
                    newIndex = i;
                    break;
                }
                // If we reach the end without finding a time after target,
                // just use the last event
                if (i == events.size() - 1) {
                    newIndex = i;
                }
            }
            
            // If we found a new valid index, jump to it
            if (newIndex > currentEventIndex) {
                currentEventIndex = newIndex;
                
                // Update viewer position immediately
                MiningEvent jumpEvent = events.get(currentEventIndex);
                
                if (povMode) {
                    // Update POV
                    Location playerLoc = jumpEvent.getPlayerLocation().clone();
                    playerLoc.setYaw(jumpEvent.getPlayerYaw());
                    playerLoc.setPitch(jumpEvent.getPlayerPitch());
                    viewer.teleport(playerLoc);
                } else {
                    // For observer mode, respawn the fake player at the new position
                    removeEntities();
                    entityIds.clear();
                    spawnFakePlayer(jumpEvent);
                    
                    // Move observer to a good viewing position
                    Location observerLoc = jumpEvent.getPlayerLocation().clone().add(3, 2, 3);
                    observerLoc.setDirection(jumpEvent.getPlayerLocation().toVector().subtract(observerLoc.toVector()));
                    viewer.teleport(observerLoc);
                }
                
                // Clear highlighted blocks to avoid confusion
                restoreBlocks();
                
                viewer.sendMessage("§a[EscadiaHax] §fJumped forward §e" + seconds + " seconds");
            } else {
                viewer.sendMessage("§a[EscadiaHax] §fCannot jump forward further");
            }
        }
        
        /**
         * Jumps backward in the replay
         * 
         * @param seconds The number of seconds to jump backward
         */
        public void jumpBackward(int seconds) {
            List<MiningEvent> events = session.getEvents();
            if (events.isEmpty() || currentEventIndex <= 0) return;
            
            LocalDateTime currentTime = events.get(currentEventIndex).getTimestamp();
            LocalDateTime targetTime = currentTime.minusSeconds(seconds);
            
            // Find the closest event before the target time
            int newIndex = 0;
            for (int i = currentEventIndex - 1; i >= 0; i--) {
                if (events.get(i).getTimestamp().isBefore(targetTime) || 
                    events.get(i).getTimestamp().equals(targetTime)) {
                    newIndex = i;
                    break;
                }
            }
            
            // If we found a new valid index, jump to it
            if (newIndex < currentEventIndex) {
                currentEventIndex = newIndex;
                
                // Update viewer position immediately
                MiningEvent jumpEvent = events.get(currentEventIndex);
                
                if (povMode) {
                    // Update POV
                    Location playerLoc = jumpEvent.getPlayerLocation().clone();
                    playerLoc.setYaw(jumpEvent.getPlayerYaw());
                    playerLoc.setPitch(jumpEvent.getPlayerPitch());
                    viewer.teleport(playerLoc);
                } else {
                    // For observer mode, respawn the fake player at the new position
                    removeEntities();
                    entityIds.clear();
                    spawnFakePlayer(jumpEvent);
                    
                    // Move observer to a good viewing position
                    Location observerLoc = jumpEvent.getPlayerLocation().clone().add(3, 2, 3);
                    observerLoc.setDirection(jumpEvent.getPlayerLocation().toVector().subtract(observerLoc.toVector()));
                    viewer.teleport(observerLoc);
                }
                
                // Clear highlighted blocks to avoid confusion
                restoreBlocks();
                
                viewer.sendMessage("§a[EscadiaHax] §fJumped backward §e" + seconds + " seconds");
            } else {
                viewer.sendMessage("§a[EscadiaHax] §fCannot jump backward further");
            }
        }
        
        /**
         * Gives replay control tools to the player
         */
        private void giveReplayControlTools() {
            // Save inventory if not already saved
            if (savedInventory == null) {
                savedInventory = viewer.getInventory().getContents().clone();
            }
            
            ItemStack pauseItem = new ItemStack(Material.CLOCK);
            ItemMeta pauseMeta = pauseItem.getItemMeta();
            if (pauseMeta != null) {
                pauseMeta.displayName(net.kyori.adventure.text.Component.text("Pause/Resume", 
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW, 
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
                List<net.kyori.adventure.text.Component> pauseLore = new ArrayList<>();
                pauseLore.add(net.kyori.adventure.text.Component.text("Right-click to pause/resume the replay", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                pauseMeta.lore(pauseLore);
                pauseItem.setItemMeta(pauseMeta);
            }
            
            ItemStack forwardItem = new ItemStack(Material.SPECTRAL_ARROW);
            ItemMeta forwardMeta = forwardItem.getItemMeta();
            if (forwardMeta != null) {
                forwardMeta.displayName(net.kyori.adventure.text.Component.text("Skip Forward", 
                    net.kyori.adventure.text.format.NamedTextColor.GREEN, 
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
                List<net.kyori.adventure.text.Component> forwardLore = new ArrayList<>();
                forwardLore.add(net.kyori.adventure.text.Component.text("Right-click to skip forward 5 seconds", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                forwardLore.add(net.kyori.adventure.text.Component.text("Shift-right-click to skip forward 15 seconds", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                forwardMeta.lore(forwardLore);
                forwardItem.setItemMeta(forwardMeta);
            }
            
            ItemStack backwardItem = new ItemStack(Material.ARROW);
            ItemMeta backwardMeta = backwardItem.getItemMeta();
            if (backwardMeta != null) {
                backwardMeta.displayName(net.kyori.adventure.text.Component.text("Skip Backward", 
                    net.kyori.adventure.text.format.NamedTextColor.RED, 
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
                List<net.kyori.adventure.text.Component> backwardLore = new ArrayList<>();
                backwardLore.add(net.kyori.adventure.text.Component.text("Right-click to skip backward 5 seconds", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                backwardLore.add(net.kyori.adventure.text.Component.text("Shift-right-click to skip backward 15 seconds", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                backwardMeta.lore(backwardLore);
                backwardItem.setItemMeta(backwardMeta);
            }
            
            ItemStack povItem = new ItemStack(Material.ENDER_EYE);
            ItemMeta povMeta = povItem.getItemMeta();
            if (povMeta != null) {
                povMeta.displayName(net.kyori.adventure.text.Component.text("Toggle POV", 
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE, 
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
                List<net.kyori.adventure.text.Component> povLore = new ArrayList<>();
                povLore.add(net.kyori.adventure.text.Component.text("Right-click to toggle between", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                povLore.add(net.kyori.adventure.text.Component.text("POV mode and observer mode", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                povMeta.lore(povLore);
                povItem.setItemMeta(povMeta);
            }
            
            ItemStack exitItem = new ItemStack(Material.BARRIER);
            ItemMeta exitMeta = exitItem.getItemMeta();
            if (exitMeta != null) {
                exitMeta.displayName(net.kyori.adventure.text.Component.text("Exit Replay", 
                    net.kyori.adventure.text.format.NamedTextColor.DARK_RED, 
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
                List<net.kyori.adventure.text.Component> exitLore = new ArrayList<>();
                exitLore.add(net.kyori.adventure.text.Component.text("Right-click to exit the replay", 
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                exitMeta.lore(exitLore);
                exitItem.setItemMeta(exitMeta);
            }
            
            // Give the items to the player
            viewer.getInventory().clear();
            viewer.getInventory().setItem(0, pauseItem);
            viewer.getInventory().setItem(1, backwardItem);
            viewer.getInventory().setItem(2, forwardItem);
            viewer.getInventory().setItem(7, povItem);
            viewer.getInventory().setItem(8, exitItem);
            
            viewer.updateInventory();
        }
        
        /**
         * Restores the player's saved inventory
         */
        private void restoreInventory() {
            if (savedInventory != null) {
                viewer.getInventory().setContents(savedInventory);
                viewer.updateInventory();
                savedInventory = null;
                debug("Restored inventory for " + viewer.getName(), 1);
            }
        }
    }
    
    /**
     * Gets a player's active replay viewer
     * 
     * @param playerId The player ID
     * @return The replay viewer, or null if the player is not viewing a replay
     */
    public ReplayViewer getReplayViewer(UUID playerId) {
        return activeViewers.get(playerId);
    }
    
    /**
     * Sets the playback speed for a viewer
     * 
     * @param viewer The viewer
     * @param speed The playback speed
     * @return True if successful
     */
    public boolean setPlaybackSpeed(Player viewer, double speed) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            replayViewer.setPlaybackSpeed(speed);
            return true;
        }
        return false;
    }
    
    /**
     * Toggles pause for a viewer
     * 
     * @param viewer The viewer
     * @return True if now paused, false if resumed
     */
    public boolean togglePause(Player viewer) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            return replayViewer.togglePause();
        }
        return false;
    }
    
    /**
     * Jumps forward in the replay
     * 
     * @param viewer The viewer
     * @param seconds The number of seconds to jump forward
     * @return True if successful
     */
    public boolean jumpForward(Player viewer, int seconds) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            replayViewer.jumpForward(seconds);
            return true;
        }
        return false;
    }
    
    /**
     * Jumps backward in the replay
     * 
     * @param viewer The viewer
     * @param seconds The number of seconds to jump backward
     * @return True if successful
     */
    public boolean jumpBackward(Player viewer, int seconds) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            replayViewer.jumpBackward(seconds);
            return true;
        }
        return false;
    }
    
    /**
     * Toggles POV mode for a viewer
     * 
     * @param viewer The viewer
     * @return True if now in POV mode, false if in observer mode
     */
    public boolean togglePOVMode(Player viewer) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            return replayViewer.togglePOVMode();
        }
        return false;
    }
    
    /**
     * Checks if a player is currently viewing a replay
     * 
     * @param playerId The player ID
     * @return True if the player is viewing a replay
     */
    public boolean isViewing(UUID playerId) {
        return activeViewers.containsKey(playerId);
    }
    
    /**
     * Checks if a player is currently viewing a replay
     * 
     * @param player The player to check
     * @return True if the player is viewing a replay
     */
    public boolean isViewingReplay(Player player) {
        return activeViewers.containsKey(player.getUniqueId());
    }
    
    /**
     * Toggle between first-person and observer mode for a replay viewer
     * 
     * @param viewer The player viewing the replay
     * @return True if now in POV mode, false if in observer mode
     */
    public boolean togglePOV(Player viewer) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            return replayViewer.togglePOV();
        }
        return false;
    }
    
    /**
     * Skip forward in the replay
     * 
     * @param viewer The player viewing the replay
     * @param seconds Number of seconds to skip forward
     */
    public void skipForward(Player viewer, int seconds) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            replayViewer.skipForward(seconds);
        }
    }
    
    /**
     * Skip backward in the replay
     * 
     * @param viewer The player viewing the replay
     * @param seconds Number of seconds to skip backward
     */
    public void skipBackward(Player viewer, int seconds) {
        ReplayViewer replayViewer = activeViewers.get(viewer.getUniqueId());
        if (replayViewer != null) {
            replayViewer.skipBackward(seconds);
        }
    }
    
    /**
     * Gets all recorded sessions
     * 
     * @return A map of player IDs to their sessions
     */
    public Map<UUID, List<ReplaySession>> getAllSessions() {
        return playerSessions;
    }
}
