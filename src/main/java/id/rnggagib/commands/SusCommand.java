package id.rnggagib.commands;

import id.rnggagib.Plugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for opening the suspicious players GUI
 */
public class SusCommand implements CommandExecutor {
    private final Plugin plugin;
    
    public SusCommand(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Executes the sus command
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[EscadiaHax] §fThis command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check permission
        if (!player.hasPermission("escadiahax.sus")) {
            player.sendMessage("§c[EscadiaHax] §fYou don't have permission to use this command.");
            return true;
        }
        
        // Open the GUI
        plugin.getSusGUI().openSusGUI(player);
        
        return true;
    }
}
