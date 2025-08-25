package ru.Fronzter.MindAc.command;

import org.bukkit.command.CommandSender;
import ru.Fronzter.MindAc.MindAI;
import java.util.Collections;
import java.util.List;

public abstract class SubCommand {

    protected final MindAI plugin;

    public SubCommand(MindAI plugin) {
        this.plugin = plugin;
    }

    public abstract String getName();
    public abstract String getDescription();
    public abstract String getUsage();
    public abstract void execute(CommandSender sender, String[] args);

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}