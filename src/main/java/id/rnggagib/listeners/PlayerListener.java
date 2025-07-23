package id.rnggagib.listeners;

import id.rnggagib.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for player events
 */
public class PlayerListener implements Listener {
    private final Plugin plugin;
    
    public PlayerListener(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handles player join events
     * 
     * @param event The player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Load player data
        plugin.getPlayerDataManager().loadPlayerData(player);
        
        // Hide vanished players from this player if they don't have permission
        if (!player.hasPermission("escadiahax.vanish.see")) {
            plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> plugin.getVanishManager().isVanished(p))
                .forEach(vanished -> player.hidePlayer(plugin, vanished));
        }
    }
    
    /**
     * Handles player quit events
     * 
     * @param event The player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save and remove player data
        plugin.getPlayerDataManager().removePlayerData(player);
    }
}
