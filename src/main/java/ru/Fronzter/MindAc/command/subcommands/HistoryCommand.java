package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;
import ru.Fronzter.MindAc.data.ViolationRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryCommand extends SubCommand {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public HistoryCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getDescription() {
        return "Показать историю нарушений игрока";
    }

    @Override
    public String getUsage() {
        return "/mindai history <игрок>";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.WHITE + "Использование: " + getUsage());
            return;
        }

        String playerName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.WHITE + "Игрок с ником '" + playerName + "' никогда не играл на сервере.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Загрузка истории для " + target.getName() + "...");
        plugin.getDatabaseService().getPlayerHistoryAsync(target.getUniqueId(), history -> {
            if (history.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + "У игрока " + target.getName() + " нет зафиксированных нарушений.");
                return;
            }
            sender.sendMessage(ChatColor.GRAY + ">><< " + ChatColor.AQUA + "История нарушений для " + target.getName() + ChatColor.GRAY + " <<>>");
            for (ViolationRecord record : history) {
                String formattedDate = dateFormat.format(new Date(record.getTimestamp()));
                String formattedProb = String.format("%.2f%%", record.getProbability() * 100.0D);
                sender.sendMessage(ChatColor.WHITE + formattedDate + ChatColor.GRAY + " - " + ChatColor.GOLD + formattedProb);
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