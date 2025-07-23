package id.rnggagib.model;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single mining session for a player
 */
public class MiningSession {
    private final int startX;
    private final int startY;
    private final int startZ;
    private final LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    
    private final List<MinedBlock> minedBlocks;
    
    public MiningSession(int startX, int startY, int startZ, LocalDateTime startTime) {
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.startTime = startTime;
        this.lastUpdateTime = startTime;
        this.minedBlocks = new ArrayList<>();
    }
    
    /**
     * Adds a block to this mining session
     * 
     * @param block The block that was mined
     * @param timestamp The time the block was mined
     */
    public void addBlock(Block block, LocalDateTime timestamp) {
        minedBlocks.add(new MinedBlock(
            block.getType(),
            block.getLocation().getBlockX(),
            block.getLocation().getBlockY(),
            block.getLocation().getBlockZ(),
            timestamp
        ));
        
        lastUpdateTime = timestamp;
    }
    
    /**
     * Checks if coordinates are nearby this session
     * 
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @param threshold The maximum distance to be considered nearby
     * @return True if the coordinates are nearby
     */
    public boolean isNearby(int x, int y, int z, int threshold) {
        // Calculate distance
        double distance = Math.sqrt(
            Math.pow(startX - x, 2) +
            Math.pow(startY - y, 2) +
            Math.pow(startZ - z, 2)
        );
        
        return distance <= threshold;
    }
    
    /**
     * Checks if a timestamp is recent compared to the last update
     * 
     * @param timestamp The timestamp to check
     * @param secondsThreshold The maximum seconds difference to be considered recent
     * @return True if the timestamp is recent
     */
    public boolean isRecent(LocalDateTime timestamp, int secondsThreshold) {
        long secondsDifference = ChronoUnit.SECONDS.between(lastUpdateTime, timestamp);
        return secondsDifference <= secondsThreshold;
    }
    
    /**
     * Checks if this mining session has a direct path to valuable ores
     * 
     * @return True if there's a suspicious direct path to valuable ores
     */
    public boolean hasDirectPathToValuableOres() {
        if (minedBlocks.size() < 10) return false;
        
        // Count valuable blocks
        long valuableBlockCount = minedBlocks.stream()
                .filter(block -> isValuableOre(block.material))
                .count();
        
        // Calculate ratio of valuable blocks to total blocks
        double valuableRatio = (double) valuableBlockCount / minedBlocks.size();
        
        // Check for direct paths (minimal deviation in mining)
        boolean hasDirectPath = calculateDirectPathScore() > 0.7; // 70% directness
        
        // If there's a high ratio of valuable ores and a direct path, it's suspicious
        return valuableRatio > 0.3 && hasDirectPath; // 30% of blocks are valuable
    }
    
    /**
     * Calculates how direct the mining path is (0-1 score)
     * 
     * @return A score indicating how direct the path is (higher = more direct)
     */
    private double calculateDirectPathScore() {
        if (minedBlocks.size() < 3) return 0;
        
        // Calculate the average deviation from a straight line
        double totalDeviation = 0;
        int comparisons = 0;
        
        // Use the first and last blocks to define a reference line
        MinedBlock first = minedBlocks.get(0);
        MinedBlock last = minedBlocks.get(minedBlocks.size() - 1);
        
        // Calculate the unit vector of the line
        double dx = last.x - first.x;
        double dy = last.y - first.y;
        double dz = last.z - first.z;
        double length = Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        // Avoid division by zero
        if (length < 0.001) return 0;
        
        dx /= length;
        dy /= length;
        dz /= length;
        
        // For each block, calculate its distance from the line
        for (int i = 1; i < minedBlocks.size() - 1; i++) {
            MinedBlock block = minedBlocks.get(i);
            
            // Vector from first point to this point
            double vx = block.x - first.x;
            double vy = block.y - first.y;
            double vz = block.z - first.z;
            
            // Project this vector onto the line
            double projection = vx*dx + vy*dy + vz*dz;
            
            // Calculate the projected point on the line
            double projectedX = first.x + projection * dx;
            double projectedY = first.y + projection * dy;
            double projectedZ = first.z + projection * dz;
            
            // Calculate the distance from the point to the line
            double deviation = Math.sqrt(
                Math.pow(block.x - projectedX, 2) +
                Math.pow(block.y - projectedY, 2) +
                Math.pow(block.z - projectedZ, 2)
            );
            
            totalDeviation += deviation;
            comparisons++;
        }
        
        if (comparisons == 0) return 0;
        
        double averageDeviation = totalDeviation / comparisons;
        
        // Convert to a score where 0 = high deviation, 1 = low deviation
        return Math.max(0, 1 - (averageDeviation / 5.0)); // Normalize, assuming 5 blocks is high deviation
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
     * Private class to represent a mined block
     */
    private static class MinedBlock {
        public final Material material;
        public final int x;
        public final int y;
        public final int z;
        public final LocalDateTime timestamp;
        
        public MinedBlock(Material material, int x, int y, int z, LocalDateTime timestamp) {
            this.material = material;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}
