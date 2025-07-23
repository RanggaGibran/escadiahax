package id.rnggagib.manager;

import id.rnggagib.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages player vanish state for staff monitoring
 */
public class VanishManager {
    private final Plugin plugin;
    private final Set<UUID> vanishedPlayers;
    private final Set<UUID> previousGameModes;
    
    public VanishManager(Plugin plugin) {
        this.plugin = plugin;
        this.vanishedPlayers = new HashSet<>();
        this.previousGameModes = new HashSet<>();
        
        // Schedule task to maintain vanish effects
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateVanishEffects, 20L, 20L);
    }
    
    /**
     * Toggles a player's vanish state
     * 
     * @param player The player to toggle vanish for
     * @return True if the player is now vanished, false if not
     */
    public boolean toggleVanish(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (vanishedPlayers.contains(uuid)) {
            // Unvanish the player
            vanishedPlayers.remove(uuid);
            
            // Show the player to everyone
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.showPlayer(plugin, player);
            }
            
            // Remove effects
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            
            // Restore previous game mode
            if (!previousGameModes.contains(uuid)) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            
            player.sendMessage("§a[EscadiaHax] §fYou are now §avisible§f to other players.");
            return false;
        } else {
            // Vanish the player
            vanishedPlayers.add(uuid);
            
            // Hide the player from everyone without permission
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.hasPermission("escadiahax.vanish.see")) {
                    onlinePlayer.hidePlayer(plugin, player);
                }
            }
            
            // Add effects
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            
            // If in survival, change to spectator
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                previousGameModes.add(uuid);
                player.setGameMode(GameMode.SPECTATOR);
            }
            
            player.sendMessage("§a[EscadiaHax] §fYou are now §cinvisible§f to other players.");
            return true;
        }
    }
    
    /**
     * Teleports a vanished staff member to a player for monitoring
     * 
     * @param staff The staff member
     * @param target The target player
     */
    public void teleportToPlayer(Player staff, Player target) {
        // Make sure the staff member is vanished
        if (!isVanished(staff)) {
            toggleVanish(staff);
        }
        
        // Teleport to the target
        staff.teleport(target);
        staff.sendMessage("§a[EscadiaHax] §fTeleported to §e" + target.getName() + "§f for monitoring.");
    }
    
    /**
     * Checks if a player is vanished
     * 
     * @param player The player to check
     * @return True if the player is vanished
     */
    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }
    
    /**
     * Updates vanish effects for all vanished players
     */
    private void updateVanishEffects() {
        for (UUID uuid : vanishedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Ensure night vision stays active
                if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                }
                
                // Ensure player is still hidden from those without permission
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.hasPermission("escadiahax.vanish.see") && onlinePlayer.canSee(player)) {
                        onlinePlayer.hidePlayer(plugin, player);
                    }
                }
            }
        }
    }
}
