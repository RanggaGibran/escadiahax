package id.rnggagib.manager;

import id.rnggagib.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the plugin configuration
 */
public class ConfigManager {
    private final Plugin plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Notification cooldown map to prevent spam
    private final Map<UUID, Long> lastNotificationTimes = new HashMap<>();
    
    // Default values
    private int minSuspicionScoreForNotification = 50;
    private int notificationCooldownSeconds = 60;
    private boolean enableNotifications = true;
    private boolean logToConsole = true;
    private boolean showDetailsInNotification = true;
    private String notificationFormat = "§c[EscadiaHax] §fPlayer §e%player% §fis showing suspicious behavior (Score: §c%score%§f)";
    
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * Loads or reloads the configuration file
     */
    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        loadValues();
    }
    
    /**
     * Loads values from the configuration file
     */
    private void loadValues() {
        minSuspicionScoreForNotification = config.getInt("xray-detection.min-suspicion-score-for-notification", 50);
        notificationCooldownSeconds = config.getInt("notifications.cooldown-seconds", 60);
        enableNotifications = config.getBoolean("notifications.enabled", true);
        logToConsole = config.getBoolean("notifications.log-to-console", true);
        showDetailsInNotification = config.getBoolean("notifications.show-details", true);
        notificationFormat = config.getString("notifications.format", notificationFormat);
    }
    
    /**
     * Saves the configuration file
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config to " + configFile);
            e.printStackTrace();
        }
    }
    
    /**
     * Checks if a player can be notified about (not on cooldown)
     * 
     * @param playerId The UUID of the player to check
     * @return True if the player can be notified
     */
    public boolean canNotifyAboutPlayer(UUID playerId) {
        if (!enableNotifications) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        Long lastNotification = lastNotificationTimes.get(playerId);
        
        if (lastNotification == null) {
            return true;
        }
        
        long timeDifference = currentTime - lastNotification;
        long cooldownMillis = notificationCooldownSeconds * 1000L;
        
        return timeDifference >= cooldownMillis;
    }
    
    /**
     * Records a notification for a player
     * 
     * @param playerId The UUID of the player
     */
    public void recordNotification(UUID playerId) {
        lastNotificationTimes.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * Gets the formatted notification message
     * 
     * @param playerName The name of the player
     * @param score The suspicion score
     * @return The formatted notification message
     */
    public String getFormattedNotification(String playerName, int score) {
        return notificationFormat
                .replace("%player%", playerName)
                .replace("%score%", String.valueOf(score));
    }
    
    // Getters
    
    public int getMinSuspicionScoreForNotification() {
        return minSuspicionScoreForNotification;
    }
    
    public boolean isEnableNotifications() {
        return enableNotifications;
    }
    
    public boolean isLogToConsole() {
        return logToConsole;
    }
    
    public boolean isShowDetailsInNotification() {
        return showDetailsInNotification;
    }
}
