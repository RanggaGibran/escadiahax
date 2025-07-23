package id.rnggagib.listeners;

import id.rnggagib.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Listens for block break events to detect xray
 */
public class BlockBreakListener implements Listener {
    private final Plugin plugin;
    
    public BlockBreakListener(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handles block break events
     * 
     * @param event The block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        // Skip players with bypass permission
        if (player.hasPermission("escadiahax.bypass")) {
            return;
        }
        
        // Analyze this block break for xray patterns
        plugin.getXrayDetector().analyzeBlockBreak(player, event.getBlock());
        
        // Record this block break for replay functionality
        if (plugin.isReplayEnabled()) {
            plugin.getReplayManager().recordBlockBreak(player, event.getBlock());
        }
    }
}
