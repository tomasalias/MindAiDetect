package ru.Fronzter.MindAc.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationManager {

    private final MindAI plugin;
    private final Map<UUID, Double> violationLevels = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final double vlIncrement;
    private final double violationThreshold;
    private final double decayAmount;

    public ViolationManager(MindAI plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("ml-check.enabled", true);
        this.vlIncrement = plugin.getConfig().getDouble("ml-check.vl-increment", 1.0);
        this.violationThreshold = plugin.getConfig().getDouble("ml-check.violation-threshold", 7.0);

        double windowSeconds = plugin.getConfig().getDouble("ml-check.window", 600);
        if (windowSeconds > 0) {
            double checksPerWindow = windowSeconds / 60.0;
            this.decayAmount = this.vlIncrement / checksPerWindow;
        } else {
            this.decayAmount = 0;
        }

        if (enabled && this.decayAmount > 0) {
            startDecayTask();
        }
    }

    public void handleViolation(PlayerEntity entity, double probability) {
        if (!enabled) return;

        UUID uuid = entity.getUUID();
        double currentVL = violationLevels.getOrDefault(uuid, 0.0);
        double newVL = currentVL + vlIncrement;

        violationLevels.put(uuid, newVL);

        plugin.getDatabaseService().logViolationAsync(uuid, entity.getName(), probability);
        sendAlert(entity, probability, newVL);

        if (newVL >= violationThreshold) {
            violationLevels.remove(uuid);
            punishPlayer(entity);
        }
    }

    private void startDecayTask() {
        long decayIntervalTicks = 20L * 60;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (UUID uuid : violationLevels.keySet()) {
                double currentVL = violationLevels.get(uuid);
                double newVL = currentVL - decayAmount;

                if (newVL <= 0) {
                    violationLevels.remove(uuid);
                } else {
                    violationLevels.put(uuid, newVL);
                }
            }
        }, decayIntervalTicks, decayIntervalTicks);
    }

    private void punishPlayer(PlayerEntity entity) {
        String mode = plugin.getConfig().getString("auto-punishment.mode", "INSTANT").toUpperCase();
        String commandToExecute = null;

        switch (mode) {
            case "INSTANT":
                commandToExecute = plugin.getConfig().getString("auto-punishment.command");
                break;
            case "ANIMATED":
                commandToExecute = "mindai punish " + entity.getName();
                break;
            case "ALERTS_ONLY":
            default:
                return;
        }

        if (commandToExecute == null || commandToExecute.isEmpty()) {
            return;
        }

        String finalCommand = commandToExecute.replace("%player%", entity.getName());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }

    private void sendAlert(PlayerEntity entity, double probability, double currentVl) {
        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) return;
        String message = plugin.getConfig().getString("alerts.message");
        if (message == null || message.isEmpty()) return;

        String formattedProb = String.format("%.2f%%", probability * 100.0D);
        String vlString = String.format("%.1f/%.0f", currentVl, violationThreshold);

        String finalMessage = ChatColor.translateAlternateColorCodes('&',
                message.replace("%player%", entity.getName())
                        .replace("%probability%", formattedProb)
                        .replace("%vl%", vlString));

        String permission = plugin.getConfig().getString("alerts.permission", "mindai.alerts");
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(permission) && plugin.areAlertsEnabledFor(admin.getUniqueId())) {
                admin.sendMessage(finalMessage);
            }
        }
    }
}
