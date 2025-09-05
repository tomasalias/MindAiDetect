package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;

public class AlertsCommand extends SubCommand {

    public AlertsCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "alerts";
    }

    @Override
    public String getDescription() {
        return "Toggle alerts for yourself";
    }

    @Override
    public String getUsage() {
        return "/mindai alerts";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.WHITE + "This command can only be used by a player. (CONSOLE NOT ALLOWED)");
            return;
        }
        Player player = (Player) sender;
        boolean newState = plugin.toggleAlerts(player.getUniqueId());
        if (newState) {
            player.sendMessage(ChatColor.GREEN + "MindAI alerts are now enabled.");
        } else {
            player.sendMessage(ChatColor.RED + "MindAI alerts are now disabled.");
        }
    }
}