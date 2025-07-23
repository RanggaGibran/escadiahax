package id.rnggagib.model;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a player's anti-cheat data
 */
public class PlayerData {
    private final UUID uuid;
    private final String playerName;
    
    // Mining statistics
    private final Map<Material, Integer> minedBlocks;
    private final List<MiningSession> miningSessions;
    
    // Suspicious activity tracking
    private int suspicionScore;
    private LocalDateTime lastScoreUpdate;
    private final List<String> suspicionReasons;
    private boolean checked; // Flag to mark if a player has been checked by staff
    
    // XRay detection metrics
    private int diamondsMinedInLastHour;
    private int emeraldsMinedInLastHour;
    private int ancientDebrisMinedInLastHour;
    private int totalBlocksMinedInLastHour;
    private LocalDateTime lastHourReset;
    
    public PlayerData(Player player) {
        this.uuid = player.getUniqueId();
        this.playerName = player.getName();
        this.minedBlocks = new HashMap<>();
        this.miningSessions = new ArrayList<>();
        this.suspicionScore = 0;
        this.lastScoreUpdate = LocalDateTime.now();
        this.suspicionReasons = new ArrayList<>();
        this.checked = false;
        
        this.diamondsMinedInLastHour = 0;
        this.emeraldsMinedInLastHour = 0;
        this.ancientDebrisMinedInLastHour = 0;
        this.totalBlocksMinedInLastHour = 0;
        this.lastHourReset = LocalDateTime.now();
    }
    
    /**
     * Records a mined block and updates statistics
     * 
     * @param block The block that was mined
     * @param timestamp The time the block was mined
     */
    public void recordMinedBlock(Block block, LocalDateTime timestamp) {
        Material material = block.getType();
        
        // Update total mined blocks for this type
        minedBlocks.put(material, minedBlocks.getOrDefault(material, 0) + 1);
        
        // Update hourly statistics for valuable blocks
        updateHourlyStats(material);
        
        // Check if this is part of an existing mining session or start a new one
        MiningSession currentSession = findOrCreateMiningSession(block.getLocation().getBlockX(), 
                                                           block.getLocation().getBlockY(), 
                                                           block.getLocation().getBlockZ(), 
                                                           timestamp);
        
        // Add the block to the current session
        currentSession.addBlock(block, timestamp);
    }
    
    /**
     * Finds an existing mining session or creates a new one
     * 
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @param timestamp The timestamp
     * @return The mining session
     */
    private MiningSession findOrCreateMiningSession(int x, int y, int z, LocalDateTime timestamp) {
        // Find the most recent session that's close to these coordinates and recent enough
        for (MiningSession session : miningSessions) {
            if (session.isNearby(x, y, z, 10) && session.isRecent(timestamp, 60)) {
                return session;
            }
        }
        
        // Create a new session
        MiningSession newSession = new MiningSession(x, y, z, timestamp);
        miningSessions.add(newSession);
        return newSession;
    }
    
    /**
     * Updates hourly mining statistics
     * 
     * @param material The material that was mined
     */
    private void updateHourlyStats(Material material) {
        // Check if we need to reset the hourly counter
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(lastHourReset.plusHours(1))) {
            resetHourlyStats();
        }
        
        // Update counters based on material
        totalBlocksMinedInLastHour++;
        
        if (material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE) {
            diamondsMinedInLastHour++;
        } else if (material == Material.EMERALD_ORE || material == Material.DEEPSLATE_EMERALD_ORE) {
            emeraldsMinedInLastHour++;
        } else if (material == Material.ANCIENT_DEBRIS) {
            ancientDebrisMinedInLastHour++;
        }
    }
    
    /**
     * Resets hourly mining statistics
     */
    private void resetHourlyStats() {
        diamondsMinedInLastHour = 0;
        emeraldsMinedInLastHour = 0;
        ancientDebrisMinedInLastHour = 0;
        totalBlocksMinedInLastHour = 0;
        lastHourReset = LocalDateTime.now();
    }
    
    /**
     * Evaluates the player's tool for suspicious enchantments
     * 
     * @param tool The tool to evaluate
     * @return True if the tool has suspicious enchantments
     */
    public boolean hasSuspiciousEnchantments(ItemStack tool) {
        if (tool == null) return false;
        
        // Check for unreasonably high enchantment levels
        return tool.getEnchantments().values().stream().anyMatch(level -> level > 5);
    }
    
    /**
     * Checks for suspicious mining patterns
     * 
     * @return True if suspicious patterns are detected
     */
    public boolean hasSuspiciousMiningPatterns() {
        // If there aren't enough mining sessions to analyze, return false
        if (miningSessions.size() < 3) return false;
        
        // Check for straight-line mining to valuable blocks
        return miningSessions.stream()
                .anyMatch(session -> session.hasDirectPathToValuableOres());
    }
    
    /**
     * Increases the player's suspicion score
     * 
     * @param amount The amount to increase by
     * @param reason The reason for increasing
     */
    public void increaseSuspicionScore(int amount, String reason) {
        suspicionScore += amount;
        suspicionReasons.add(reason + " (" + LocalDateTime.now() + ")");
        lastScoreUpdate = LocalDateTime.now();
    }
    
    /**
     * Decreases the suspicion score over time
     */
    public void decaySuspicionScore() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(lastScoreUpdate.plusHours(1)) && suspicionScore > 0) {
            suspicionScore = Math.max(0, suspicionScore - 5);
            lastScoreUpdate = now;
        }
    }
    
    /**
     * Marks the player as checked by staff
     */
    public void markAsChecked() {
        this.checked = true;
        // Add a reason indicating the player was checked by staff
        suspicionReasons.add("Checked by staff (" + LocalDateTime.now() + ")");
    }
    
    /**
     * Checks if the player has been marked as checked by staff
     * 
     * @return True if the player has been checked
     */
    public boolean isChecked() {
        return checked;
    }
    
    /**
     * Resets the checked status of the player
     */
    public void resetCheckedStatus() {
        this.checked = false;
    }
    
    /**
     * Saves player data
     */
    public void saveData() {
        // In a real implementation, this would save to a database or file
    }
    
    // Getters
    
    public UUID getUuid() {
        return uuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public int getSuspicionScore() {
        return suspicionScore;
    }
    
    public List<String> getSuspicionReasons() {
        return new ArrayList<>(suspicionReasons);
    }
    
    public int getDiamondsMinedInLastHour() {
        return diamondsMinedInLastHour;
    }
    
    public int getEmeraldsMinedInLastHour() {
        return emeraldsMinedInLastHour;
    }
    
    public int getAncientDebrisMinedInLastHour() {
        return ancientDebrisMinedInLastHour;
    }
    
    public int getTotalBlocksMinedInLastHour() {
        return totalBlocksMinedInLastHour;
    }
    
    public Map<Material, Integer> getMinedBlocks() {
        return new HashMap<>(minedBlocks);
    }
}
