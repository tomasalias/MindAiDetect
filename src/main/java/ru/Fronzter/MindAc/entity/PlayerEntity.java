package ru.Fronzter.MindAc.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerEntity {
    private final UUID uuid;
    private final String name;
    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;

    private final List<Frame> mlFrames = new CopyOnWriteArrayList<>();
    private final Map<Long, Integer> violationLogs = new ConcurrentHashMap<>();
    private List<Frame> lastAnalyzedFrames = null;
    private int combatRecordingTicks = 0;

    public PlayerEntity(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUUID() { return this.uuid; }
    public String getName() { return this.name; }
    public float getLastYaw() { return this.lastYaw; }
    public void setLastYaw(float lastYaw) { this.lastYaw = lastYaw; }
    public float getLastPitch() { return this.lastPitch; }
    public void setLastPitch(float lastPitch) { this.lastPitch = lastPitch; }
    public List<Frame> getFrames() { return this.mlFrames; }
    public Map<Long, Integer> getViolationLogs() { return this.violationLogs; }
    public List<Frame> getLastAnalyzedFrames() { return this.lastAnalyzedFrames; }
    public void setLastAnalyzedFrames(List<Frame> frames) { this.lastAnalyzedFrames = new ArrayList<>(frames); }
    public int getCombatRecordingTicks() { return this.combatRecordingTicks; }
    public void setCombatRecordingTicks(int ticks) { this.combatRecordingTicks = ticks; }
}