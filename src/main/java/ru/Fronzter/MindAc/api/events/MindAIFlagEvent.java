package ru.Fronzter.MindAc.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class MindAIFlagEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double probability;
    private final int currentViolations;
    private final int violationThreshold;
    private boolean cancelled;

    public MindAIFlagEvent(boolean isAsync, Player player, double probability, int currentViolations, int violationThreshold) {
        super(isAsync);
        this.player = player;
        this.probability = probability;
        this.currentViolations = currentViolations;
        this.violationThreshold = violationThreshold;
        this.cancelled = false;
    }

    public Player getPlayer() {
        return player;
    }

    public double getProbability() {
        return probability;
    }

    public int getCurrentViolations() {
        return currentViolations;
    }

    public int getViolationThreshold() {
        return violationThreshold;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return handlers;
    }
}