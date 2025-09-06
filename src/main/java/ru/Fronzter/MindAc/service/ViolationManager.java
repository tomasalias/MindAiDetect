package ru.Fronzter.MindAc.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.PlayerEntity;

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
        if (!enabled) {
            plugin.getLogger().info("ViolationManager is disabled - skipping violation for " + entity.getName());
            return;
        }

        UUID uuid = entity.getUUID();
        double currentVL = violationLevels.getOrDefault(uuid, 0.0);
        double newVL = currentVL + vlIncrement;

        violationLevels.put(uuid, newVL);

        plugin.getLogger().info("Violation recorded for " + entity.getName() + 
                               " - Probability: " + String.format("%.2f", probability) + 
                               ", VL: " + String.format("%.1f", newVL) + "/" + violationThreshold);

        plugin.getDatabaseService().logViolationAsync(uuid, entity.getName(), probability);
        sendAlert(entity, probability, newVL);

        if (newVL >= violationThreshold) {
            plugin.getLogger().warning("VL threshold reached for " + entity.getName() + " - initiating punishment");
            violationLevels.remove(uuid);
            punishPlayer(entity);
        } else {
            plugin.getLogger().info("VL threshold not reached yet for " + entity.getName() + 
                                   " (current: " + String.format("%.1f", newVL) + 
                                   ", required: " + violationThreshold + ")");
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
        String commandToExecute;

        plugin.getLogger().info("Punishing player " + entity.getName() + " using mode: " + mode);

        switch (mode) {
            case "INSTANT":
                commandToExecute = plugin.getConfig().getString("auto-punishment.command");
                plugin.getLogger().info("Using INSTANT punishment with command: " + commandToExecute);
                break;
            case "ANIMATED":
                commandToExecute = "mindai punish " + entity.getName();
                plugin.getLogger().info("Using ANIMATED punishment");
                break;
            case "ALERTS_ONLY":
                plugin.getLogger().info("ALERTS_ONLY mode - no punishment executed");
                return;
            default:
                plugin.getLogger().warning("Unknown punishment mode: " + mode + " - no action taken");
                return;
        }

        if (commandToExecute == null || commandToExecute.isEmpty()) {
            plugin.getLogger().warning("No punishment command configured - player will not be punished");
            return;
        }

        String finalCommand = commandToExecute.replace("%player%", entity.getName());
        plugin.getLogger().info("Executing punishment command: " + finalCommand);
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            plugin.getLogger().info("Punishment command executed with result: " + result);
        });
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
