package id.rnggagib.replay;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single mining event for replay functionality
 */
public class MiningEvent {
    private final UUID playerId;
    private final String playerName;
    private final Location location;
    private final Material blockType;
    private final LocalDateTime timestamp;
    private final Location playerLocation;
    private final float playerYaw;
    private final float playerPitch;
    
    public MiningEvent(Player player, Location blockLocation, Material blockType) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.location = blockLocation.clone();
        this.blockType = blockType;
        this.timestamp = LocalDateTime.now();
        this.playerLocation = player.getLocation().clone();
        this.playerYaw = player.getLocation().getYaw();
        this.playerPitch = player.getLocation().getPitch();
    }
    
    /**
     * Gets the player ID
     * 
     * @return The player ID
     */
    public UUID getPlayerId() {
        return playerId;
    }
    
    /**
     * Gets the player name
     * 
     * @return The player name
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * Gets the block location
     * 
     * @return The block location
     */
    public Location getLocation() {
        return location.clone();
    }
    
    /**
     * Gets the block type
     * 
     * @return The block type
     */
    public Material getBlockType() {
        return blockType;
    }
    
    /**
     * Gets the timestamp
     * 
     * @return The timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the player location
     * 
     * @return The player location
     */
    public Location getPlayerLocation() {
        return playerLocation.clone();
    }
    
    /**
     * Gets the player yaw
     * 
     * @return The player yaw
     */
    public float getPlayerYaw() {
        return playerYaw;
    }
    
    /**
     * Gets the player pitch
     * 
     * @return The player pitch
     */
    public float getPlayerPitch() {
        return playerPitch;
    }
}
