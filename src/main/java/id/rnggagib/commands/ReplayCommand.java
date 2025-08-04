package id.rnggagib.commands;

import id.rnggagib.Plugin;
import id.rnggagib.replay.ReplaySession;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command for controlling replay functionality
 */
public class ReplayCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
    
    public ReplayCommand(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Executes the replay command
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if the player has permission
        if (!sender.hasPermission("escadiahax.replay")) {
            sender.sendMessage("§c[EscadiaHax] §fYou don't have permission to use this command.");
            return true;
        }
        
        // Check if the replay manager is enabled
        if (!plugin.isReplayEnabled()) {
            sender.sendMessage("§c[EscadiaHax] §fReplay functionality is disabled. ProtocolLib is required.");
            return true;
        }
        
        // Check if args are provided
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        // Handle different subcommands
        switch (args[0].toLowerCase()) {
            case "list":
                handleList(sender, args);
                break;
                
            case "view":
                handleView(sender, args);
                break;
                
            case "stop":
                handleStop(sender);
                break;
                
            case "control":
                handleControl(sender, args);
                break;
                
            case "help":
                showHelp(sender);
                break;
                
            default:
                sender.sendMessage("§c[EscadiaHax] §fUnknown subcommand. Use §e/replay help§f for help.");
                break;
        }
        
        return true;
    }
    
    /**
     * Handles the list subcommand
     */
    private void handleList(CommandSender sender, String[] args) {
        int maxResults = 10;
        if (args.length > 1) {
            try {
                maxResults = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                if (args[1].equalsIgnoreCase("all")) {
                    maxResults = 100;
                } else {
                    // Try to get by player name
                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                    if (targetPlayer != null) {
                        listPlayerSessions(sender, targetPlayer.getUniqueId());
                        return;
                    }
                }
            }
        }
        
        // List recent suspicious sessions
        List<ReplaySession> sessions = plugin.getReplayManager().getRecentSuspiciousSessions(maxResults);
        
        if (sessions.isEmpty()) {
            sender.sendMessage("§c[EscadiaHax] §fNo suspicious mining sessions found.");
            return;
        }
        
        sender.sendMessage("§e===== Recent Suspicious Mining Sessions =====");
        
        for (ReplaySession session : sessions) {
            String timeStr = session.getStartTime().format(DATE_FORMAT);
            sender.sendMessage(String.format(
                "§f[§e%s§f] §e%s §f- Score: §c%d §f- ID: §7%s",
                timeStr,
                session.getPlayerName(),
                session.getSuspicionScore(),
                session.getSessionId().toString().substring(0, 8)
            ));
        }
        
        sender.sendMessage("§fUse §e/replay view <id>§f to watch a replay.");
    }
    
    /**
     * Lists sessions for a specific player
     */
    private void listPlayerSessions(CommandSender sender, UUID playerId) {
        List<ReplaySession> sessions = plugin.getReplayManager().getPlayerSessions(playerId);
        
        if (sessions.isEmpty()) {
            sender.sendMessage("§c[EscadiaHax] §fNo recorded sessions for this player.");
            return;
        }
        
        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : "Unknown";
        
        sender.sendMessage("§e===== Mining Sessions for " + playerName + " =====");
        
        for (ReplaySession session : sessions) {
            String timeStr = session.getStartTime().format(DATE_FORMAT);
            sender.sendMessage(String.format(
                "§f[§e%s§f] Duration: §e%ds §f- Score: §c%d §f- ID: §7%s",
                timeStr,
                session.getDurationSeconds(),
                session.getSuspicionScore(),
                session.getSessionId().toString().substring(0, 8)
            ));
        }
        
        sender.sendMessage("§fUse §e/replay view <id>§f to watch a replay.");
    }
    
    /**
     * Handles the view subcommand
     */
    private void handleView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[EscadiaHax] §fThis command can only be used by players.");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c[EscadiaHax] §fUsage: §e/replay view <id>");
            return;
        }
        
        Player player = (Player) sender;
        
        // Check if already viewing
        if (plugin.getReplayManager().isViewing(player.getUniqueId())) {
            sender.sendMessage("§c[EscadiaHax] §fYou are already viewing a replay. Use §e/replay stop§f to stop it.");
            return;
        }
        
        // Try to find the session
        String idStr = args[1];
        UUID sessionId = null;
        
        try {
            // Try to match partial ID
            List<ReplaySession> allSessions = new ArrayList<>();
            for (List<ReplaySession> sessions : plugin.getReplayManager().getAllSessions().values()) {
                allSessions.addAll(sessions);
            }
            
            for (ReplaySession session : allSessions) {
                String shortId = session.getSessionId().toString().substring(0, 8);
                if (shortId.startsWith(idStr)) {
                    sessionId = session.getSessionId();
                    break;
                }
            }
            
            // If still not found, try full UUID
            if (sessionId == null) {
                sessionId = UUID.fromString(idStr);
            }
        } catch (Exception e) {
            sender.sendMessage("§c[EscadiaHax] §fInvalid session ID '" + idStr + "'. Use §e/replay list§f to see available sessions.");
            return;
        }
        
        if (sessionId == null) {
            sender.sendMessage("§c[EscadiaHax] §fCould not find session with ID '" + idStr + "'. Use §e/replay list§f to see available sessions.");
            return;
        }
        
        // Start the replay
        boolean success = plugin.getReplayManager().startReplay(player, sessionId);
        
        if (!success) {
            sender.sendMessage("§c[EscadiaHax] §fFailed to start replay with ID '" + idStr + "'. The session might not exist or have no events.");
        }
    }
    
    /**
     * Handles the stop subcommand
     */
    private void handleStop(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[EscadiaHax] §fThis command can only be used by players.");
            return;
        }
        
        Player player = (Player) sender;
        
        // Check if viewing
        if (!plugin.getReplayManager().isViewing(player.getUniqueId())) {
            sender.sendMessage("§c[EscadiaHax] §fYou are not viewing a replay.");
            return;
        }
        
        // Stop the replay
        plugin.getReplayManager().stopReplay(player);
    }
    
    /**
     * Handles the control subcommand
     */
    private void handleControl(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[EscadiaHax] §fThis command can only be used by players.");
            return;
        }
        
        Player player = (Player) sender;
        
        // Check if viewing
        if (!plugin.getReplayManager().isViewing(player.getUniqueId())) {
            sender.sendMessage("§c[EscadiaHax] §fYou are not viewing a replay.");
            return;
        }
        
        if (args.length < 2) {
            showControlHelp(sender);
            return;
        }
        
        String controlAction = args[1].toLowerCase();
        
        switch (controlAction) {
            case "pause":
                boolean isPaused = plugin.getReplayManager().togglePause(player);
                sender.sendMessage("§a[EscadiaHax] §fReplay " + (isPaused ? "§cpaused" : "§aresumed"));
                break;
                
            case "pov":
                boolean isPOVMode = plugin.getReplayManager().togglePOVMode(player);
                sender.sendMessage("§a[EscadiaHax] §fSwitched to " + (isPOVMode ? "§ePOV" : "§eobserver") + "§f mode");
                break;
                
            case "speed":
                if (args.length < 3) {
                    sender.sendMessage("§c[EscadiaHax] §fUsage: §e/replay control speed <value>");
                    return;
                }
                
                try {
                    double speed = Double.parseDouble(args[2]);
                    plugin.getReplayManager().setPlaybackSpeed(player, speed);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c[EscadiaHax] §fInvalid speed value. Use a number between 0.25 and 10.");
                }
                break;
                
            case "forward":
                int forwardSeconds = 10;
                if (args.length >= 3) {
                    try {
                        forwardSeconds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c[EscadiaHax] §fInvalid seconds value. Using default of 10 seconds.");
                    }
                }
                plugin.getReplayManager().jumpForward(player, forwardSeconds);
                break;
                
            case "backward":
                int backwardSeconds = 10;
                if (args.length >= 3) {
                    try {
                        backwardSeconds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c[EscadiaHax] §fInvalid seconds value. Using default of 10 seconds.");
                    }
                }
                plugin.getReplayManager().jumpBackward(player, backwardSeconds);
                break;
                
            default:
                showControlHelp(sender);
                break;
        }
    }
    
    /**
     * Shows help for replay commands
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§e===== EscadiaHax Replay Commands =====");
        sender.sendMessage("§e/replay list [count|player] §f- List recent suspicious sessions");
        sender.sendMessage("§e/replay view <id> §f- View a replay session");
        sender.sendMessage("§e/replay stop §f- Stop the current replay");
        sender.sendMessage("§e/replay control §f- Control the current replay");
        sender.sendMessage("§e/replay help §f- Show this help message");
    }
    
    /**
     * Shows help for replay control commands
     */
    private void showControlHelp(CommandSender sender) {
        sender.sendMessage("§e===== Replay Control Commands =====");
        sender.sendMessage("§e/replay control pause §f- Pause/resume the replay");
        sender.sendMessage("§e/replay control pov §f- Toggle between POV and observer view modes");
        sender.sendMessage("§e/replay control speed <value> §f- Set playback speed (0.25-10)");
        sender.sendMessage("§e/replay control forward [seconds] §f- Skip forward");
        sender.sendMessage("§e/replay control backward [seconds] §f- Skip backward");
    }
    
    /**
     * Tab completer for the replay command
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        try {
            if (!sender.hasPermission("escadiahax.replay")) {
                return completions;
            }
            
            if (!plugin.isReplayEnabled()) {
                return completions;
            }
            
            if (args.length == 1) {
                completions.add("list");
                completions.add("view");
                completions.add("stop");
                completions.add("control");
                completions.add("help");
            } else if (args.length == 2) {
                switch (args[0].toLowerCase()) {
                    case "list":
                        completions.add("all");
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            completions.add(player.getName());
                        }
                        break;
                        
                    case "control":
                        completions.add("pause");
                        completions.add("pov");
                        completions.add("speed");
                        completions.add("forward");
                        completions.add("backward");
                        break;
                        
                    case "view":
                        // Add player names instead of session IDs for safety
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            completions.add(player.getName());
                        }
                        break;
                }
            } else if (args.length == 3) {
                if (args[0].toLowerCase().equals("control")) {
                    switch (args[1].toLowerCase()) {
                        case "speed":
                            completions.add("0.5");
                            completions.add("1.0");
                            completions.add("2.0");
                            break;
                            
                        case "forward":
                        case "backward":
                            completions.add("5");
                            completions.add("10");
                            completions.add("30");
                            break;
                    }
                }
            }
            
            // Filter based on the current input
            if (args.length > 0) {
                String lastArg = args[args.length - 1].toLowerCase();
                completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in tab completion: " + e.getMessage());
        }
        
        return completions;
    }
}
