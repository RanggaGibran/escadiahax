package id.rnggagib.detection;

import id.rnggagib.Plugin;
import id.rnggagib.model.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detects xray-like behavior and updates player suspicion scores
 */
public class XrayDetector {
    private final Plugin plugin;
    
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
        
        // Check if this block type is valuable
        if (isValuableOre(block.getType())) {
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
        
        // Get mining statistics
        int diamondsMined = playerData.getDiamondsMinedInLastHour();
        int emeraldsMined = playerData.getEmeraldsMinedInLastHour();
        int debrisMined = playerData.getAncientDebrisMinedInLastHour();
        int totalMined = playerData.getTotalBlocksMinedInLastHour();
        
        // Calculate a ratio of valuable ores to total blocks
        double valuableRatio = (double)(diamondsMined + emeraldsMined + debrisMined) / Math.max(1, totalMined);
        
        // Check for suspicious patterns
        boolean hasSuspiciousEnchants = playerData.hasSuspiciousEnchantments(tool);
        boolean hasSuspiciousMiningPatterns = playerData.hasSuspiciousMiningPatterns();
        
        // Score based on the ratio
        if (valuableRatio > 0.1) { // More than 10% valuable ores
            playerData.increaseSuspicionScore(10, "High valuable ore ratio: " + String.format("%.2f", valuableRatio * 100) + "%");
        }
        
        // Score based on enchantments
        if (hasSuspiciousEnchants) {
            playerData.increaseSuspicionScore(5, "Suspicious tool enchantments");
        }
        
        // Score based on mining patterns
        if (hasSuspiciousMiningPatterns) {
            playerData.increaseSuspicionScore(20, "Direct paths to valuable ores");
        }
        
        // Check for Fortune enchantment level
        int fortuneLevel = 0;
        if (tool != null && tool.getItemMeta() != null) {
            try {
                fortuneLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
            } catch (Exception e) {
                // Enchantment might not exist in this version
            }
        }
        if (fortuneLevel > 3) {
            playerData.increaseSuspicionScore(10, "Fortune level higher than vanilla maximum: " + fortuneLevel);
        }
        
        // Check if mining directly to diamonds (no nearby stone mined)
        if (isRareValuableOre(block.getType()) && totalMined < 10) {
            playerData.increaseSuspicionScore(15, "Mining directly to valuable ores with minimal surrounding blocks");
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
}
