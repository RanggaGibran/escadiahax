package id.rnggagib;

import id.rnggagib.commands.SusCommand;
import id.rnggagib.commands.EscadiaHaxCommand;
import id.rnggagib.commands.ReplayCommand;
import id.rnggagib.detection.XrayDetector;
import id.rnggagib.gui.SusGUI;
import id.rnggagib.listeners.BlockBreakListener;
import id.rnggagib.listeners.PlayerListener;
import id.rnggagib.manager.ConfigManager;
import id.rnggagib.manager.PlayerDataManager;
import id.rnggagib.manager.VanishManager;
import id.rnggagib.replay.ReplayManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * EscadiaHax - A professional anti-cheat plugin with advanced detection capabilities
 */
public class Plugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("EscadiaHax");
    private static Plugin instance;
    
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private XrayDetector xrayDetector;
    private VanishManager vanishManager;
    private SusGUI susGUI;
    private ReplayManager replayManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize config manager first
        this.configManager = new ConfigManager(this);
        
        // Initialize managers
        this.playerDataManager = new PlayerDataManager(this);
        this.xrayDetector = new XrayDetector(this);
        this.vanishManager = new VanishManager(this);
        this.susGUI = new SusGUI(this);
        
        // Initialize replay manager if ProtocolLib is present
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            this.replayManager = new ReplayManager(this);
            LOGGER.info("ProtocolLib found, replay functionality enabled!");
        } else {
            LOGGER.warning("ProtocolLib not found, replay functionality disabled!");
        }
        
        // Register listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockBreakListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(this.susGUI, this);
        
        // Register commands
        getCommand("sus").setExecutor(new SusCommand(this));
        getCommand("escadiahax").setExecutor(new EscadiaHaxCommand(this));
        
        // Register replay command if replay is enabled
        if (this.replayManager != null) {
            getCommand("replay").setExecutor(new ReplayCommand(this));
        }
        
        // Register replay control listener if replay is enabled
        if (this.replayManager != null) {
            pm.registerEvents(new id.rnggagib.listeners.ReplayControlListener(this), this);
            LOGGER.info("Registered replay control listener for enhanced replay controls");
        }
        
        LOGGER.info("EscadiaHax has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Save data and clean up
        if (playerDataManager != null) {
            playerDataManager.saveAllData();
        }
        
        LOGGER.info("EscadiaHax has been disabled.");
    }
    
    /**
     * Get the plugin instance
     * 
     * @return The plugin instance
     */
    public static Plugin getInstance() {
        return instance;
    }
    
    /**
     * Get the player data manager
     * 
     * @return The player data manager
     */
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    /**
     * Get the xray detector
     * 
     * @return The xray detector
     */
    public XrayDetector getXrayDetector() {
        return xrayDetector;
    }
    
    /**
     * Get the vanish manager
     * 
     * @return The vanish manager
     */
    public VanishManager getVanishManager() {
        return vanishManager;
    }
    
    /**
     * Get the suspicious players GUI
     * 
     * @return The suspicious players GUI
     */
    public SusGUI getSusGUI() {
        return susGUI;
    }
    
    /**
     * Get the config manager
     * 
     * @return The config manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Get the replay manager
     * 
     * @return The replay manager
     */
    public ReplayManager getReplayManager() {
        return replayManager;
    }
    
    /**
     * Logs a message with the specified level
     * Only logs if the level is at or below the configured debug level
     * 
     * @param message The message to log
     * @param component The component that's logging (XrayDetector, ReplayManager, etc.)
     * @param level The debug level (0=essential, 1=important, 2=detailed)
     */
    public void logDebug(String message, String component, int level) {
        // If not in debug mode or level is too high, don't log
        String configPath = component.toLowerCase() + ".debug.debug-level";
        int configuredLevel = getConfig().getInt(configPath, 0);
        
        if (configuredLevel >= level) {
            getLogger().info("[" + component + "] " + message);
        }
    }
    
    /**
     * Check if replay functionality is enabled
     * 
     * @return True if replay is enabled
     */
    public boolean isReplayEnabled() {
        return replayManager != null && getConfig().getBoolean("replay.enabled", true);
    }
}
