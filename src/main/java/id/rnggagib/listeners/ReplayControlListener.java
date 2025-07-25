package id.rnggagib.listeners;

import id.rnggagib.Plugin;
import id.rnggagib.replay.ReplayManager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles interactions with replay control items
 */
public class ReplayControlListener implements Listener {
    private final Plugin plugin;
    
    public ReplayControlListener(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Check if replay is active for this player
        ReplayManager replayManager = plugin.getReplayManager();
        if (replayManager == null || !replayManager.isViewingReplay(player)) {
            return;
        }
        
        // Only handle right-click actions (AIR or BLOCK)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        // Get the item in the player's hand
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        
        // Always cancel the event to prevent normal interactions
        event.setCancelled(true);
        
        // Check which control item was used
        Material material = item.getType();
        boolean isShiftClick = player.isSneaking();
        
        switch (material) {
            case CLOCK:
                // Pause/Resume
                replayManager.togglePause(player);
                break;
                
            case SPECTRAL_ARROW:
                // Skip Forward
                int forwardSeconds = isShiftClick ? 15 : 5;
                replayManager.  skipForward(player, forwardSeconds);
                break;
                
            case ARROW:
                // Skip Backward
                int backwardSeconds = isShiftClick ? 15 : 5;
                replayManager.skipBackward(player, backwardSeconds);
                break;
                
            case ENDER_EYE:
                // Toggle POV
                replayManager.togglePOV(player);
                break;
                
            case BARRIER:
                // Exit Replay
                replayManager.stopReplay(player);
                break;
                
            default:
                // Not a control item
                break;
        }
    }
}
