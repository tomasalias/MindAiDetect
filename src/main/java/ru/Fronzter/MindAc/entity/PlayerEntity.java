package ru.Fronzter.MindAc.entity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class PlayerEntity {
    private final UUID uuid;
    private final String name;
    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;
    private final List<Frame> mlFrames = new LinkedList<>();
    private List<Frame> lastAnalyzedFrames = null;
    private boolean isProcessingFlag = false;

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
    public List<Frame> getLastAnalyzedFrames() { return this.lastAnalyzedFrames; }
    public void setLastAnalyzedFrames(List<Frame> frames) { this.lastAnalyzedFrames = new ArrayList<>(frames); }

    public boolean isProcessingFlag() { return isProcessingFlag; }
    public void setProcessingFlag(boolean processingFlag) { isProcessingFlag = processingFlag; }
}