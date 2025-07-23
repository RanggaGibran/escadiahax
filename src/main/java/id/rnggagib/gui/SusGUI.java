package id.rnggagib.gui;

import id.rnggagib.Plugin;
import id.rnggagib.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for displaying suspicious players
 */
public class SusGUI implements Listener {
    private final Plugin plugin;
    private final Map<UUID, Inventory> openInventories;
    private final Map<UUID, Map<Integer, UUID>> playerSlotMap;
    private static final int VANISH_BUTTON_SLOT = 8; // Position for the vanish toggle button
    
    public SusGUI(Plugin plugin) {
        this.plugin = plugin;
        this.openInventories = new HashMap<>();
        this.playerSlotMap = new HashMap<>();
    }
    
    /**
     * Opens the suspicious players GUI for a player
     * 
     * @param player The player to open the GUI for
     */
    public void openSusGUI(Player player) {
        // Get suspicious players
        Map<UUID, PlayerData> suspiciousPlayers = plugin.getPlayerDataManager().getSuspiciousPlayers();
        
        // Create inventory
        Inventory inventory = createInventory(player, suspiciousPlayers);
        
        // Open inventory
        player.openInventory(inventory);
        
        // Store opened inventory
        openInventories.put(player.getUniqueId(), inventory);
    }
    
    /**
     * Creates the suspicious players inventory
     * 
     * @param player The player to create the inventory for
     * @param suspiciousPlayers The suspicious players to display
     * @return The created inventory
     */
    private Inventory createInventory(Player player, Map<UUID, PlayerData> suspiciousPlayers) {
        int size = (int) Math.ceil(suspiciousPlayers.size() / 9.0) * 9;
        size = Math.max(9, Math.min(54, size)); // Minimum size 9, maximum size 54
        
        // Get title from config, or use default
        String guiTitle = "§c§lSuspicious Players";
        try {
            guiTitle = plugin.getServer().getPluginManager()
                .getPlugin("EscadiaHax").getConfig().getString("gui.title", guiTitle);
        } catch (Exception e) {
            // Use default title
        }
        
        Inventory inventory = Bukkit.createInventory(null, size, net.kyori.adventure.text.Component.text("Suspicious Players", net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD));
        
        // Store player slot mapping
        Map<Integer, UUID> slotMap = new HashMap<>();
        playerSlotMap.put(player.getUniqueId(), slotMap);
        
        // Add suspicious player heads
        int slot = 0;
        for (Map.Entry<UUID, PlayerData> entry : suspiciousPlayers.entrySet()) {
            UUID suspiciousUUID = entry.getKey();
            PlayerData playerData = entry.getValue();
            
            // Check if this player is online
            Player suspiciousPlayer = Bukkit.getPlayer(suspiciousUUID);
            if (suspiciousPlayer == null) continue; // Skip offline players
            
            // Create player head item
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(suspiciousPlayer);
            meta.displayName(net.kyori.adventure.text.Component.text(playerData.getPlayerName(), net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            
            // Add lore with suspicious info
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text("Suspicion Score: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(net.kyori.adventure.text.Component.text(playerData.getSuspicionScore(), net.kyori.adventure.text.format.NamedTextColor.RED)));
            lore.add(net.kyori.adventure.text.Component.text("Diamonds Mined (Last Hour): ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(net.kyori.adventure.text.Component.text(playerData.getDiamondsMinedInLastHour(), net.kyori.adventure.text.format.NamedTextColor.AQUA)));
            lore.add(net.kyori.adventure.text.Component.text("Emeralds Mined (Last Hour): ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(net.kyori.adventure.text.Component.text(playerData.getEmeraldsMinedInLastHour(), net.kyori.adventure.text.format.NamedTextColor.GREEN)));
            lore.add(net.kyori.adventure.text.Component.text("Ancient Debris Mined (Last Hour): ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(net.kyori.adventure.text.Component.text(playerData.getAncientDebrisMinedInLastHour(), net.kyori.adventure.text.format.NamedTextColor.GOLD)));
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Latest Reasons:", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            
            // Add reasons to the lore (up to 3)
            List<String> reasons = playerData.getSuspicionReasons();
            for (int i = Math.max(0, reasons.size() - 3); i < reasons.size(); i++) {
                lore.add(net.kyori.adventure.text.Component.text("- ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .append(net.kyori.adventure.text.Component.text(reasons.get(i), net.kyori.adventure.text.format.NamedTextColor.RED)));
            }
            
            lore.add(net.kyori.adventure.text.Component.empty());
            lore.add(net.kyori.adventure.text.Component.text("Click to teleport and monitor", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            lore.add(net.kyori.adventure.text.Component.text("Press Q or right-click to mark as checked", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            
            meta.lore(lore);
            head.setItemMeta(meta);
            
            inventory.setItem(slot, head);
            slotMap.put(slot, suspiciousUUID);
            slot++;
            
            if (slot >= size) break; // Stop if we've filled the inventory
        }
        
        // Add vanish toggle button
        addVanishToggleButton(inventory, player);
        
        // Fill empty slots with glass
        for (int i = slot; i < size; i++) {
            // Skip the vanish button slot
            if (i == VANISH_BUTTON_SLOT) continue;
            
            ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            meta.displayName(net.kyori.adventure.text.Component.space());
            glass.setItemMeta(meta);
            inventory.setItem(i, glass);
        }
        
        return inventory;
    }
    
    /**
     * Adds the vanish toggle button to the inventory
     * 
     * @param inventory The inventory to add the button to
     * @param player The player viewing the inventory
     */
    private void addVanishToggleButton(Inventory inventory, Player player) {
        boolean isVanished = plugin.getVanishManager().isVanished(player);
        
        // Create vanish toggle button
        Material buttonMaterial = isVanished ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack vanishButton = new ItemStack(buttonMaterial);
        ItemMeta meta = vanishButton.getItemMeta();
        
        // Set display name based on current state
        if (isVanished) {
            meta.displayName(net.kyori.adventure.text.Component.text("Currently Vanished", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        } else {
            meta.displayName(net.kyori.adventure.text.Component.text("Currently Visible", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        
        // Add lore with instructions
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Click to toggle vanish state", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        
        meta.lore(lore);
        vanishButton.setItemMeta(meta);
        
        // Add the button to the inventory
        inventory.setItem(VANISH_BUTTON_SLOT, vanishButton);
    }
    
    /**
     * Updates the suspicious players GUI for all open inventories
     */
    public void updateAllGUIs() {
        for (UUID uuid : openInventories.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                updateGUI(player);
            }
        }
    }
    
    /**
     * Updates the suspicious players GUI for a player
     * 
     * @param player The player to update the GUI for
     */
    private void updateGUI(Player player) {
        // Get suspicious players
        Map<UUID, PlayerData> suspiciousPlayers = plugin.getPlayerDataManager().getSuspiciousPlayers();
        
        // Create new inventory
        Inventory inventory = createInventory(player, suspiciousPlayers);
        
        // Replace the old inventory
        player.openInventory(inventory);
        openInventories.put(player.getUniqueId(), inventory);
    }
    
    /**
     * Handles clicks in the suspicious players GUI
     * 
     * @param event The click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        
        // Check if this is one of our inventories
        if (clickedInventory != null && openInventories.containsValue(clickedInventory)) {
            event.setCancelled(true); // Prevent item taking
            
            // Get the clicked slot
            int slot = event.getSlot();
            
            // Check if vanish toggle button was clicked
            if (slot == VANISH_BUTTON_SLOT) {
                // Toggle vanish state
                plugin.getVanishManager().toggleVanish(player);
                
                // Update GUI to reflect new state
                updateGUI(player);
                return;
            }
            
            // Get the player slot map
            Map<Integer, UUID> slotMap = playerSlotMap.get(player.getUniqueId());
            if (slotMap != null && slotMap.containsKey(slot)) {
                // Get the suspicious player UUID
                UUID suspiciousUUID = slotMap.get(slot);
                Player suspiciousPlayer = Bukkit.getPlayer(suspiciousUUID);
                
                // Handle Drop/Q key or right-click as "mark as checked"
                if (event.getAction() == InventoryAction.DROP_ONE_SLOT || 
                    event.getAction() == InventoryAction.DROP_ALL_SLOT ||
                    event.getClick() == ClickType.RIGHT) {
                    
                    // Mark the player as checked
                    plugin.getPlayerDataManager().markPlayerAsChecked(suspiciousUUID);
                    
                    // Send notification message
                    String playerName = suspiciousPlayer != null ? suspiciousPlayer.getName() : "Player";
                    String message = "§a[EscadiaHax] §fMarked §e" + playerName + "§f as checked. Removed from suspicious players list.";
                    
                    // Try to get custom message from config
                    try {
                        String configMessage = plugin.getServer().getPluginManager()
                            .getPlugin("EscadiaHax").getConfig().getString("gui.checked-message", message);
                        if (configMessage != null) {
                            message = configMessage.replace("%player%", playerName);
                        }
                    } catch (Exception e) {
                        // Use default message
                    }
                    
                    player.sendMessage(message);
                    
                    // Update the GUI to remove this player
                    updateGUI(player);
                    return;
                }
                
                // Handle normal left-click as teleport
                if (suspiciousPlayer != null && suspiciousPlayer.isOnline()) {
                    // Vanish and teleport
                    plugin.getVanishManager().teleportToPlayer(player, suspiciousPlayer);
                    
                    // Close inventory
                    player.closeInventory();
                } else {
                    player.sendMessage("§c[EscadiaHax] §fThat player is no longer online.");
                    updateGUI(player);
                }
            }
        }
    }
}
