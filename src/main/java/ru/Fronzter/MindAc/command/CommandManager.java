package ru.Fronzter.MindAc.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.subcommands.*;

import java.util.*;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandManager(MindAI plugin) {

        registerSubCommand(new ReloadCommand(plugin));
        registerSubCommand(new AlertsCommand(plugin));
        registerSubCommand(new HistoryCommand(plugin));
        registerSubCommand(new StatsCommand(plugin));
        registerSubCommand(new PunishCommand(plugin));
    }

    private void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mindai.admin")) {
            sender.sendMessage(ChatColor.WHITE + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.WHITE + "Unknown subcommand. Use /mindai for help.");
            return true;
        }

        String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
        subCommand.execute(sender, subCommandArgs);

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + ">>> MindAI Help <<<");
        for (SubCommand subCommand : subCommands.values()) {
            sender.sendMessage(ChatColor.GOLD + subCommand.getUsage() + ChatColor.WHITE + " - " + subCommand.getDescription());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mindai.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], subCommands.keySet(), new ArrayList<>());
        }

        if (args.length > 1) {
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                String[] subCommandArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.onTabComplete(sender, subCommandArgs);
            }
        }

        return Collections.emptyList();
    }
}