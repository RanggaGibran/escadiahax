package id.rnggagib.replay;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete replay session of mining events
 */
public class ReplaySession {
    private final UUID playerId;
    private final String playerName;
    private final UUID sessionId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private final List<MiningEvent> events;
    private String suspicionReason;
    private int suspicionScore;
    
    public ReplaySession(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.sessionId = UUID.randomUUID();
        this.startTime = LocalDateTime.now();
        this.events = new ArrayList<>();
    }
    
    /**
     * Adds a mining event to this session
     * 
     * @param event The mining event to add
     */
    public void addEvent(MiningEvent event) {
        events.add(event);
        endTime = LocalDateTime.now();
    }
    
    /**
     * Sets the suspicion reason for this session
     * 
     * @param reason The suspicion reason
     */
    public void setSuspicionReason(String reason) {
        this.suspicionReason = reason;
    }
    
    /**
     * Sets the suspicion score for this session
     * 
     * @param score The suspicion score
     */
    public void setSuspicionScore(int score) {
        this.suspicionScore = score;
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
     * Gets the session ID
     * 
     * @return The session ID
     */
    public UUID getSessionId() {
        return sessionId;
    }
    
    /**
     * Gets the start time
     * 
     * @return The start time
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    /**
     * Gets the end time
     * 
     * @return The end time
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    /**
     * Gets the mining events
     * 
     * @return The mining events
     */
    public List<MiningEvent> getEvents() {
        return new ArrayList<>(events);
    }
    
    /**
     * Gets the suspicion reason
     * 
     * @return The suspicion reason
     */
    public String getSuspicionReason() {
        return suspicionReason;
    }
    
    /**
     * Gets the suspicion score
     * 
     * @return The suspicion score
     */
    public int getSuspicionScore() {
        return suspicionScore;
    }
    
    /**
     * Gets the duration of this session in seconds
     * 
     * @return The duration in seconds
     */
    public long getDurationSeconds() {
        if (endTime != null) {
            // If the session is finalized, use the recorded end time
            return java.time.Duration.between(startTime, endTime).getSeconds();
        } else if (!events.isEmpty()) {
            // If the session is still active, use the last event's time
            MiningEvent lastEvent = events.get(events.size() - 1);
            return java.time.Duration.between(startTime, lastEvent.getTimestamp()).getSeconds();
        } else {
            // Empty session
            return 0;
        }
    }
}
