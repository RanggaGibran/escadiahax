package id.rnggagib.commands;

import id.rnggagib.Plugin;
import id.rnggagib.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Main command for the EscadiaHax plugin
 */
public class EscadiaHaxCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    
    public EscadiaHaxCommand(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Executes the escadiahax command
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("escadiahax.admin.reload")) {
                    sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission to reload the configuration.", NamedTextColor.WHITE)));
                    return true;
                }
                
                plugin.getConfigManager().loadConfig();
                
                // Reload other managers if needed
                if (plugin.isReplayEnabled()) {
                    plugin.getReplayManager().loadConfig();
                }
                
                sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.GREEN)
                        .append(Component.text("Configuration reloaded successfully!", NamedTextColor.WHITE)));
                return true;
                
            case "help":
                showHelp(sender);
                return true;
                
            case "version":
                sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.GREEN)
                        .append(Component.text("Version: ", NamedTextColor.WHITE))
                        .append(Component.text(plugin.getPluginMeta().getVersion(), NamedTextColor.YELLOW)));
                return true;
                
            case "reset-checked":
                if (!sender.hasPermission("escadiahax.admin.reset")) {
                    sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission to reset checked status.", NamedTextColor.WHITE)));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.RED)
                            .append(Component.text("Usage: /escadiahax reset-checked <player|all>", NamedTextColor.WHITE)));
                    return true;
                }
                
                String target = args[1].toLowerCase();
                if (target.equals("all")) {
                    // Reset all checked players
                    int count = resetAllCheckedPlayers();
                    sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.GREEN)
                            .append(Component.text("Reset checked status for " + count + " players.", NamedTextColor.WHITE)));
                } else {
                    // Reset a specific player
                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer == null) {
                        sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.RED)
                                .append(Component.text("Player not found or not online.", NamedTextColor.WHITE)));
                        return true;
                    }
                    
                    boolean success = resetPlayerCheckedStatus(targetPlayer);
                    if (success) {
                        sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.GREEN)
                                .append(Component.text("Reset checked status for " + targetPlayer.getName() + ".", NamedTextColor.WHITE)));
                    } else {
                        sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.RED)
                                .append(Component.text("Failed to reset checked status for " + targetPlayer.getName() + ".", NamedTextColor.WHITE)));
                    }
                }
                return true;
                
            default:
                sender.sendMessage(Component.text("[EscadiaHax] ", NamedTextColor.RED)
                        .append(Component.text("Unknown command. Type /escadiahax help for help.", NamedTextColor.WHITE)));
                return true;
        }
    }
    
    /**
     * Shows the help message
     * 
     * @param sender The command sender
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("===== EscadiaHax Commands =====", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/sus", NamedTextColor.YELLOW)
                .append(Component.text(" - Open the suspicious players GUI", NamedTextColor.WHITE)));
        
        if (sender.hasPermission("escadiahax.admin.reload")) {
            sender.sendMessage(Component.text("/escadiahax reload", NamedTextColor.YELLOW)
                    .append(Component.text(" - Reload the configuration", NamedTextColor.WHITE)));
        }
        
        if (sender.hasPermission("escadiahax.admin.reset")) {
            sender.sendMessage(Component.text("/escadiahax reset-checked <player|all>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Reset checked status for player(s)", NamedTextColor.WHITE)));
        }
        
        sender.sendMessage(Component.text("/escadiahax version", NamedTextColor.YELLOW)
                .append(Component.text(" - Show the plugin version", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/escadiahax help", NamedTextColor.YELLOW)
                .append(Component.text(" - Show this help message", NamedTextColor.WHITE)));
                
        // Add replay commands if available
        if (plugin.isReplayEnabled() && sender.hasPermission("escadiahax.replay")) {
            sender.sendMessage(Component.text("", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("===== Replay Commands =====", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/replay list", NamedTextColor.YELLOW)
                    .append(Component.text(" - List suspicious player mining sessions", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/replay view <id>", NamedTextColor.YELLOW)
                    .append(Component.text(" - View a replay session", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/replay help", NamedTextColor.YELLOW)
                    .append(Component.text(" - Show replay help", NamedTextColor.WHITE)));
        }
    }
    
    /**
     * Tab completer for the escadiahax command
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("help");
            completions.add("version");
            
            if (sender.hasPermission("escadiahax.admin.reload")) {
                completions.add("reload");
            }
            
            if (sender.hasPermission("escadiahax.admin.reset")) {
                completions.add("reset-checked");
            }
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset-checked")) {
            completions.add("all");
            // Add online player names
            Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .toList();
        }
        
        return completions;
    }
    
    /**
     * Resets the checked status for a specific player
     * 
     * @param player The player to reset
     * @return True if successful
     */
    private boolean resetPlayerCheckedStatus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        if (playerData != null) {
            playerData.resetCheckedStatus();
            return true;
        }
        return false;
    }
    
    /**
     * Resets the checked status for all players
     * 
     * @return The number of players reset
     */
    private int resetAllCheckedPlayers() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
            if (playerData != null && playerData.isChecked()) {
                playerData.resetCheckedStatus();
                count++;
            }
        }
        return count;
    }
}
