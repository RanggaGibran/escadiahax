package id.rnggagib.manager;

import id.rnggagib.Plugin;
import id.rnggagib.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages player data for all players on the server
 */
public class PlayerDataManager {
    private final Plugin plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    
    public PlayerDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
        
        // Load data for online players
        Bukkit.getOnlinePlayers().forEach(this::loadPlayerData);
    }
    
    /**
     * Gets player data for a player. Creates new data if it doesn't exist.
     * 
     * @param player The player to get data for
     * @return The player's data
     */
    public PlayerData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerData(player));
    }
    
    /**
     * Loads player data for a player
     * 
     * @param player The player to load data for
     */
    public void loadPlayerData(Player player) {
        playerDataMap.put(player.getUniqueId(), new PlayerData(player));
    }
    
    /**
     * Removes player data for a player
     * 
     * @param player The player to remove data for
     */
    public void removePlayerData(Player player) {
        playerDataMap.remove(player.getUniqueId());
    }
    
    /**
     * Saves all player data
     */
    public void saveAllData() {
        // In a more advanced implementation, we would save this data to a database or file
        playerDataMap.values().forEach(PlayerData::saveData);
    }
    
    /**
     * Gets all suspicious players
     * 
     * @return A map of suspicious players and their data
     */
    public Map<UUID, PlayerData> getSuspiciousPlayers() {
        // Get the minimum score from the config
        int minScore = 30; // Default value
        
        if (plugin.getConfigManager() != null) {
            try {
                minScore = plugin.getServer().getPluginManager()
                    .getPlugin("EscadiaHax").getConfig().getInt("gui.min-score", 30);
            } catch (Exception e) {
                // Use default value
            }
        }
        
        final int suspicionThreshold = minScore;
        
        return playerDataMap.entrySet().stream()
                .filter(entry -> entry.getValue().getSuspicionScore() > suspicionThreshold)
                .filter(entry -> !entry.getValue().isChecked()) // Filter out players that have been checked
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    /**
     * Marks a player as checked by staff
     * 
     * @param playerId The UUID of the player to mark as checked
     * @return True if the player was found and marked, false otherwise
     */
    public boolean markPlayerAsChecked(UUID playerId) {
        PlayerData data = playerDataMap.get(playerId);
        if (data != null) {
            data.markAsChecked();
            return true;
        }
        return false;
    }
}
