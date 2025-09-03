package ru.Fronzter.MindAc.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class MindAIFlagEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final double probability;
    private final double currentViolations;
    private final double violationThreshold;

    private boolean cancelled;

    public MindAIFlagEvent(boolean isAsync, Player player, double probability, double currentViolations, double violationThreshold) {
        super(isAsync);
        this.player = player;
        this.probability = probability;
        this.currentViolations = currentViolations;
        this.violationThreshold = violationThreshold;
        this.cancelled = false;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}