package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class StatsCommand extends SubCommand {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public StatsCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public String getDescription() {
        return "Show player's violation statistics";
    }

    @Override
    public String getUsage() {
        return "/mindai stats <player>";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.WHITE + "Usage: " + getUsage());
            return;
        }
        String playerName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.WHITE + "Player with name '" + playerName + "' has never played on this server.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Loading statistics for " + target.getName() + "...");
        plugin.getDatabaseService().getPlayerStatsAsync(target.getUniqueId(), stats -> {
            sender.sendMessage(ChatColor.GRAY + ">><< " + ChatColor.AQUA + "Statistics for " + target.getName() + ChatColor.GRAY + " >><<");
            sender.sendMessage(ChatColor.GOLD + "Total violations in database: " + ChatColor.WHITE + stats.getTotalViolations());
            if (stats.getTotalViolations() > 0) {
                sender.sendMessage(ChatColor.GOLD + "Average probability: " + ChatColor.WHITE + String.format("%.2f%%", stats.getAverageProbability() * 100));
                sender.sendMessage(ChatColor.GOLD + "Last violation: " + ChatColor.WHITE + dateFormat.format(new Date(stats.getLastViolationTimestamp())));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> StringUtil.startsWithIgnoreCase(name, args[0]))
                    .collect(Collectors.toList());
        }
        return super.onTabComplete(sender, args);
    }
}