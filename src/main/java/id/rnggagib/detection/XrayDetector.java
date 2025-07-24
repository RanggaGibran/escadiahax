package id.rnggagib.detection;

import id.rnggagib.Plugin;
import id.rnggagib.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detects xray-like behavior and updates player suspicion scores
 */
public class XrayDetector {
    private final Plugin plugin;
    
    // Block faces to check for exposure
    private static final BlockFace[] FACES = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, 
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };
    
    // Materials that are typically found in caves (non-solid blocks)
    private static final Set<Material> CAVE_MATERIALS = new HashSet<>(Arrays.asList(
        Material.AIR, Material.CAVE_AIR, Material.WATER, Material.LAVA, 
        Material.POINTED_DRIPSTONE, Material.GLOW_LICHEN, Material.MOSSY_COBBLESTONE,
        Material.VINE, Material.COBWEB, Material.GRAVEL, Material.TUFF, Material.CALCITE,
        Material.AMETHYST_CLUSTER, Material.SMALL_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD,
        Material.LARGE_AMETHYST_BUD, Material.SMOOTH_BASALT, Material.DRIPSTONE_BLOCK
    ));
    
    // Track recent ore blocks for vein detection
    private final Map<UUID, Map<Material, List<Block>>> recentOreBlocks = new HashMap<>();
    // Track mining speed for unnatural mining patterns
    private final Map<UUID, List<LocalDateTime>> miningTimestamps = new HashMap<>();
    
    public XrayDetector(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Analyzes a block break event for suspicious behavior
     * 
     * @param player The player who broke the block
     * @param block The block that was broken
     */
    public void analyzeBlockBreak(Player player, Block block) {
        // Get the player's data
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        
        // Record this block
        playerData.recordMinedBlock(block, LocalDateTime.now());
        
        // Track mining timestamps for speed analysis
        UUID playerId = player.getUniqueId();
        miningTimestamps.computeIfAbsent(playerId, k -> new ArrayList<>())
                         .add(LocalDateTime.now());
        
        // Clean up old timestamps (keep only last 5 minutes)
        cleanupOldTimestamps(playerId);
        
        // Check if this block type is valuable
        if (isValuableOre(block.getType())) {
            // Add to recent ore blocks for vein detection
            trackOreForVeinDetection(playerId, block);
            
            // Analyze this valuable block
            analyzeValuableBlockMine(player, block, playerData);
        }
        
        // Decay the suspicion score over time
        playerData.decaySuspicionScore();
    }
    
    /**
     * Analyzes when a valuable block is mined
     * 
     * @param player The player who mined the block
     * @param block The block that was mined
     * @param playerData The player's data
     */
    private void analyzeValuableBlockMine(Player player, Block block, PlayerData playerData) {
        // Check the tool being used
        ItemStack tool = player.getInventory().getItemInMainHand();
        UUID playerId = player.getUniqueId();
        
        // Get mining statistics
        int diamondsMined = playerData.getDiamondsMinedInLastHour();
        int emeraldsMined = playerData.getEmeraldsMinedInLastHour();
        int debrisMined = playerData.getAncientDebrisMinedInLastHour();
        int totalMined = playerData.getTotalBlocksMinedInLastHour();
        
        // ===== ADVANCED CONTEXT DETECTION =====
        
        // 1. Check for Fortune enchantment
        int fortuneLevel = 0;
        if (tool != null && tool.getItemMeta() != null) {
            fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        }
        
        // 2. Check if mining in a cave (advanced detection)
        boolean inCave = isInNaturalCave(block);
        
        // 3. Check surrounding environment (exposed faces)
        int exposedFaces = countExposedFaces(block);
        boolean hasExposedFaces = exposedFaces > 1;
        
        // 4. Check for nearby fluids (lava/water)
        boolean nearFluids = false;
        int fluidCheckRadius = plugin.getConfig().getInt("xray-detection.advanced.context-detection.lava-water-detection-radius", 5);
        if (plugin.getConfig().getBoolean("xray-detection.advanced.context-detection.enabled", true)) {
            nearFluids = hasNearbyFluids(block, fluidCheckRadius);
        }
        
        // 5. Check for nearby players (cooperative mining)
        boolean cooperativeMining = false;
        int cooperativeRadius = plugin.getConfig().getInt("xray-detection.advanced.context-detection.cooperative-mining-radius", 20);
        if (plugin.getConfig().getBoolean("xray-detection.advanced.context-detection.enabled", true)) {
            cooperativeMining = hasNearbyPlayers(player, cooperativeRadius);
        }
        
        // 6. Check if part of a natural vein
        boolean partOfVein = isPartOfNaturalVein(playerId, block);
        
        // 7. Check if part of an ore cluster
        boolean partOfCluster = isPartOfOreCluster(block);
        
        // 8. Check mining speed for suspicious patterns
        boolean unnaturalSpeed = hasUnnaturalMiningSpeed(playerId);
        
        // 9. Check for natural mining patterns (branching vs. direct)
        double naturalMiningScore = getNaturalMiningScore(playerId);
        
        // ===== CALCULATE ADJUSTED SUSPICION =====
        
        // Base ratio calculation
        double valuableRatio = (double)(diamondsMined + emeraldsMined + debrisMined) / Math.max(1, totalMined);
        
        // Adjust ratio based on Fortune level
        double fortuneMultiplier = 1.0;
        if (plugin.getConfig().getBoolean("xray-detection.advanced.fortune-allowance.enabled", true) && fortuneLevel > 0) {
            double multiplierPerLevel = plugin.getConfig().getDouble("xray-detection.advanced.fortune-allowance.multiplier", 1.5);
            fortuneMultiplier = 1.0 + (fortuneLevel * (multiplierPerLevel - 1.0));
        }
        
        // Environmental context modifiers
        double environmentModifier = 1.0;
        if (inCave) environmentModifier *= 0.7;  // 30% reduction if in a cave
        if (hasExposedFaces) environmentModifier *= (1.0 - (exposedFaces * 0.1)); // Up to 60% reduction for fully exposed
        if (nearFluids) environmentModifier *= 0.8;  // 20% reduction if near fluids
        if (cooperativeMining) environmentModifier *= 0.9;  // 10% reduction if mining with others
        if (partOfVein) environmentModifier *= 0.6;  // 40% reduction if part of a natural vein
        if (partOfCluster) environmentModifier *= 0.7;  // 30% reduction if part of a natural ore cluster
        environmentModifier *= (1.0 - (naturalMiningScore * 0.5)); // Up to 50% reduction for natural mining patterns
        
        // Calculate adjusted threshold based on context
        double adjustedSuspiciousRatioThreshold = 0.1 * fortuneMultiplier / environmentModifier;
        
        // ===== APPLY SUSPICION RULES =====
        
        // Log detection details for debugging
        if (plugin.getConfig().getBoolean("replay.debug-mode", false)) {
            plugin.getLogger().info(String.format(
                "[XrayDetector] Player %s: valuable ratio %.2f, adjusted threshold %.2f (fortune %d, " +
                "cave %b, exposed %d, fluids %b, cooperative %b, vein %b, cluster %b, speed %b, natural %.2f)",
                player.getName(), valuableRatio, adjustedSuspiciousRatioThreshold,
                fortuneLevel, inCave, exposedFaces, nearFluids, cooperativeMining, 
                partOfVein, partOfCluster, unnaturalSpeed, naturalMiningScore
            ));
        }
        
        // Score based on the adjusted ratio
        if (valuableRatio > adjustedSuspiciousRatioThreshold) {
            int score = plugin.getConfig().getInt("xray-detection.weights.valuable-ore-ratio", 10);
            String reason = String.format("High valuable ore ratio: %.2f%% (threshold: %.2f%%)", 
                    valuableRatio * 100, adjustedSuspiciousRatioThreshold * 100);
            playerData.increaseSuspicionScore(score, reason);
        }
        
        // Check for suspicious enchantments
        if (hasSuspiciousEnchantments(tool)) {
            int score = plugin.getConfig().getInt("xray-detection.weights.suspicious-enchants", 5);
            playerData.increaseSuspicionScore(score, "Suspicious tool enchantments");
        }
        
        // Check for impossible enchantment levels
        if (hasImpossibleEnchantments(tool)) {
            int score = plugin.getConfig().getInt("xray-detection.weights.impossible-enchants", 10);
            playerData.increaseSuspicionScore(score, "Impossible enchantment levels detected");
        }
        
        // Check for suspicious mining patterns
        if (playerData.hasSuspiciousMiningPatterns()) {
            int score = plugin.getConfig().getInt("xray-detection.weights.mining-patterns", 20);
            playerData.increaseSuspicionScore(score, "Direct paths to valuable ores");
        }
        
        // Check for mining directly to valuable ores with minimal surrounding blocks
        if (isRareValuableOre(block.getType()) && totalMined < 10 && !hasExposedFaces && !inCave && !partOfVein) {
            int score = plugin.getConfig().getInt("xray-detection.weights.direct-mining", 15);
            playerData.increaseSuspicionScore(score, "Mining directly to valuable ores with minimal surrounding blocks");
        }
        
        // Check for unnatural mining speed
        if (unnaturalSpeed && isRareValuableOre(block.getType())) {
            int score = plugin.getConfig().getInt("xray-detection.weights.unnatural-speed", 15);
            playerData.increaseSuspicionScore(score, "Unnaturally fast mining of valuable ores");
        }
        
        // Notify staff if suspicion is high (based on config)
        int minScoreForNotification = plugin.getConfigManager().getMinSuspicionScoreForNotification();
        if (playerData.getSuspicionScore() > minScoreForNotification) {
            // Check notification cooldown to prevent spam
            if (plugin.getConfigManager().canNotifyAboutPlayer(player.getUniqueId())) {
                notifyStaff(player, playerData);
                plugin.getConfigManager().recordNotification(player.getUniqueId());
                
                // Finalize the replay recording if enabled
                if (plugin.isReplayEnabled()) {
                    // Get the latest reason
                    String reason = "Suspicious behavior";
                    List<String> reasons = playerData.getSuspicionReasons();
                    if (!reasons.isEmpty()) {
                        reason = reasons.get(reasons.size() - 1);
                    }
                    
                    plugin.getReplayManager().finalizeRecording(
                        player.getUniqueId(), 
                        reason, 
                        playerData.getSuspicionScore()
                    );
                }
            }
        }
    }
    /**
     * Notifies staff members about a suspicious player
     * 
     * @param player The suspicious player
     * @param playerData The player's data
     */
    private void notifyStaff(Player player, PlayerData playerData) {
        // If notifications are disabled in config, don't send any
        if (!plugin.getConfigManager().isEnableNotifications()) {
            return;
        }
        
        // Get the formatted notification message
        String message = plugin.getConfigManager().getFormattedNotification(
                player.getName(), playerData.getSuspicionScore());
        
        // Log to console if enabled
        if (plugin.getConfigManager().isLogToConsole()) {
            plugin.getLogger().info("Suspicious player detected: " + player.getName() + 
                    " (Score: " + playerData.getSuspicionScore() + ")");
        }
        
        // Send to staff with permission
        plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("escadiahax.notify"))
            .forEach(staff -> {
                staff.sendMessage(message);
                
                // Send details if enabled
                if (plugin.getConfigManager().isShowDetailsInNotification()) {
                    // Get the most recent reason
                    List<String> reasons = playerData.getSuspicionReasons();
                    if (!reasons.isEmpty()) {
                        String latestReason = reasons.get(reasons.size() - 1);
                        staff.sendMessage("§7Latest reason: §c" + latestReason);
                    }
                }
            });
    }
    
    /**
     * Checks if a tool has suspicious enchantments (but still within Minecraft's limits)
     * 
     * @param tool The tool to check
     * @return True if the tool has suspicious enchantments
     */
    private boolean hasSuspiciousEnchantments(ItemStack tool) {
        if (tool == null || tool.getItemMeta() == null) return false;
        
        // Check for incompatible enchantments
        boolean hasSilkTouch = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        boolean hasFortune = tool.getEnchantmentLevel(Enchantment.FORTUNE) > 0;
        
        // In vanilla, Fortune and Silk Touch are incompatible
        return hasSilkTouch && hasFortune;
    }
    
    /**
     * Checks if a tool has impossible enchantments (beyond Minecraft's limits)
     * 
     * @param tool The tool to check
     * @return True if the tool has impossible enchantments
     */
    private boolean hasImpossibleEnchantments(ItemStack tool) {
        if (tool == null || tool.getItemMeta() == null) return false;
        
        // Check for enchantment levels beyond vanilla limits
        return tool.getEnchantmentLevel(Enchantment.FORTUNE) > 3 ||
               tool.getEnchantmentLevel(Enchantment.EFFICIENCY) > 5 ||
               tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 1 ||
               tool.getEnchantmentLevel(Enchantment.UNBREAKING) > 3;
    }
    
    /**
     * Counts how many sides of a block are exposed (not surrounded by solid blocks)
     * More exposed faces indicate the block is in a cave or natural formation
     * 
     * @param block The block to check
     * @return The number of exposed faces
     */
    private int countExposedFaces(Block block) {
        int exposedFaces = 0;
        
        for (BlockFace face : FACES) {
            Block adjacent = block.getRelative(face);
            if (CAVE_MATERIALS.contains(adjacent.getType())) {
                exposedFaces++;
            }
        }
        
        return exposedFaces;
    }
    
    /**
     * Checks if there are fluid blocks (lava/water) nearby
     * Presence of fluids indicates natural cave systems
     * 
     * @param block The block to check around
     * @param radius The radius to check
     * @return True if fluids are detected nearby
     */
    private boolean hasNearbyFluids(Block block, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block checkBlock = block.getRelative(x, y, z);
                    Material type = checkBlock.getType();
                    if (type == Material.LAVA || type == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Checks if there are other players nearby (indicates cooperative mining)
     * 
     * @param player The player to check around
     * @param radius The radius to check
     * @return True if other players are nearby
     */
    private boolean hasNearbyPlayers(Player player, int radius) {
        Location playerLoc = player.getLocation();
        return player.getWorld().getPlayers().stream()
            .filter(p -> !p.equals(player))
            .anyMatch(p -> p.getLocation().distance(playerLoc) <= radius);
    }
    
    /**
     * Checks if a material is a valuable ore
     * 
     * @param material The material to check
     * @return True if the material is a valuable ore
     */
    private boolean isValuableOre(Material material) {
        return material == Material.DIAMOND_ORE ||
               material == Material.DEEPSLATE_DIAMOND_ORE ||
               material == Material.EMERALD_ORE ||
               material == Material.DEEPSLATE_EMERALD_ORE ||
               material == Material.ANCIENT_DEBRIS;
    }
    
    /**
     * Checks if a material is an especially rare and valuable ore
     * 
     * @param material The material to check
     * @return True if the material is a rare valuable ore
     */
    private boolean isRareValuableOre(Material material) {
        return material == Material.DIAMOND_ORE ||
               material == Material.DEEPSLATE_DIAMOND_ORE ||
               material == Material.ANCIENT_DEBRIS;
    }
    
    /**
     * Tracks an ore block for vein detection
     * 
     * @param playerId The player's UUID
     * @param block The ore block
     */
    private void trackOreForVeinDetection(UUID playerId, Block block) {
        // Initialize data structure if needed
        recentOreBlocks.computeIfAbsent(playerId, k -> new HashMap<>())
                       .computeIfAbsent(block.getType(), k -> new ArrayList<>())
                       .add(block);
        
        // Clean up old blocks (keep only last 2 minutes worth)
        cleanupOldOreBlocks(playerId);
    }
    
    /**
     * Cleans up old ore blocks from tracking
     * 
     * @param playerId The player's UUID
     */
    private void cleanupOldOreBlocks(UUID playerId) {
        Map<Material, List<Block>> oreMap = recentOreBlocks.get(playerId);
        if (oreMap == null) return;
        
        // Limit each ore type list to 50 most recent blocks (arbitrary limit to prevent memory issues)
        oreMap.values().forEach(list -> {
            if (list.size() > 50) {
                list.subList(0, list.size() - 50).clear();
            }
        });
    }
    
    /**
     * Cleans up old mining timestamps
     * 
     * @param playerId The player's UUID
     */
    private void cleanupOldTimestamps(UUID playerId) {
        List<LocalDateTime> timestamps = miningTimestamps.get(playerId);
        if (timestamps == null) return;
        
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        timestamps.removeIf(time -> time.isBefore(cutoff));
    }
    
    /**
     * Detects if the given block is likely part of a natural ore vein
     * 
     * @param playerId The player's UUID
     * @param block The block to check
     * @return True if the block appears to be part of a natural vein
     */
    private boolean isPartOfNaturalVein(UUID playerId, Block block) {
        Map<Material, List<Block>> oreMap = recentOreBlocks.get(playerId);
        if (oreMap == null) return false;
        
        List<Block> recentSameTypeOres = oreMap.get(block.getType());
        if (recentSameTypeOres == null || recentSameTypeOres.isEmpty()) return false;
        
        // Check if any recent ore of the same type is nearby (part of same vein)
        for (Block recentOre : recentSameTypeOres) {
            if (isBlockNearby(block, recentOre, 3)) { // 3 block radius is reasonable for ore veins
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if two blocks are within a specified distance of each other
     * 
     * @param block1 The first block
     * @param block2 The second block
     * @param distance The maximum distance
     * @return True if the blocks are within the specified distance
     */
    private boolean isBlockNearby(Block block1, Block block2, int distance) {
        return block1.getWorld().equals(block2.getWorld()) &&
               Math.abs(block1.getX() - block2.getX()) <= distance &&
               Math.abs(block1.getY() - block2.getY()) <= distance &&
               Math.abs(block1.getZ() - block2.getZ()) <= distance;
    }
    
    /**
     * Detects if a block is in a natural cave system based on a more sophisticated check
     * 
     * @param block The block to check
     * @return True if the block appears to be in a natural cave system
     */
    private boolean isInNaturalCave(Block block) {
        // Initial quick Y-level check
        if (block.getY() > plugin.getConfig().getInt("xray-detection.advanced.cave-y-level", 50)) {
            return false;
        }
        
        // Check for a significant number of air blocks in the area
        int airBlocks = 0;
        int totalChecked = 0;
        
        // Check a 5x5x5 area around the block
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip the block itself
                    
                    Block checkBlock = block.getRelative(x, y, z);
                    totalChecked++;
                    
                    if (CAVE_MATERIALS.contains(checkBlock.getType())) {
                        airBlocks++;
                    }
                }
            }
        }
        
        // Calculate the percentage of air blocks
        double airPercentage = (double) airBlocks / totalChecked;
        
        // If more than 40% of surrounding blocks are air/cave materials, it's likely in a cave
        return airPercentage > 0.4;
    }
    
    /**
     * Checks if the block is part of an ore cluster (multiple ore blocks close together)
     * Natural diamond generation often creates small clusters of 1-8 ores
     * 
     * @param block The block to check
     * @return True if the block appears to be part of a natural ore cluster
     */
    private boolean isPartOfOreCluster(Block block) {
        Material type = block.getType();
        if (!isValuableOre(type)) return false;
        
        // Count nearby blocks of the same type
        int sameTypeCount = 0;
        
        // Check a 3x3x3 area around the block
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip the block itself
                    
                    Block checkBlock = block.getRelative(x, y, z);
                    if (checkBlock.getType() == type) {
                        sameTypeCount++;
                    }
                }
            }
        }
        
        // If there are at least 2 ore blocks of the same type nearby, it's likely a natural cluster
        return sameTypeCount >= 1;
    }
    
    /**
     * Analyzes mining speed for unnatural patterns
     * 
     * @param playerId The player's UUID
     * @return True if mining speed appears unnatural
     */
    private boolean hasUnnaturalMiningSpeed(UUID playerId) {
        List<LocalDateTime> timestamps = miningTimestamps.get(playerId);
        if (timestamps == null || timestamps.size() < 10) return false;
        
        // Get the 10 most recent timestamps
        List<LocalDateTime> recent = timestamps.subList(Math.max(0, timestamps.size() - 10), timestamps.size());
        
        // Calculate average time between breaks
        double totalSeconds = 0;
        for (int i = 1; i < recent.size(); i++) {
            totalSeconds += ChronoUnit.MILLIS.between(recent.get(i-1), recent.get(i)) / 1000.0;
        }
        double averageSeconds = totalSeconds / (recent.size() - 1);
        
        // If average time between breaks is too small, it might be unnatural
        // This threshold may need adjustment based on server performance and desired sensitivity
        return averageSeconds < 0.2; // 200ms threshold
    }
    
    /**
     * Checks if the player's mining pattern resembles natural exploration vs. direct targeting
     * Natural mining has lots of branching and turns as players explore caves
     * 
     * @param playerId The player's UUID
     * @return A score from 0-1 indicating how natural the mining pattern appears (higher = more natural)
     */
    private double getNaturalMiningScore(UUID playerId) {
        Map<Material, List<Block>> oreMap = recentOreBlocks.get(playerId);
        if (oreMap == null) return 1.0; // Default to natural if no data
        
        // Get all recent ore blocks regardless of type
        List<Block> allOres = new ArrayList<>();
        oreMap.values().forEach(allOres::addAll);
        
        // Not enough data to analyze
        if (allOres.size() < 5) return 1.0;
        
        // Count how many ores have multiple neighbors (suggests branching pattern)
        int oresWithMultipleNeighbors = 0;
        for (Block ore : allOres) {
            int neighbors = 0;
            for (Block other : allOres) {
                if (ore != other && isBlockNearby(ore, other, 4)) {
                    neighbors++;
                }
            }
            if (neighbors >= 2) {
                oresWithMultipleNeighbors++;
            }
        }
        
        // Calculate ratio of ores with multiple neighbors
        double branchingRatio = (double) oresWithMultipleNeighbors / allOres.size();
        
        // Higher ratio means more natural mining pattern
        return branchingRatio;
    }
}
