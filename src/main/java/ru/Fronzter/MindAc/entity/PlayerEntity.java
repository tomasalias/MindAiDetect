package ru.Fronzter.MindAc.entity;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Data
@RequiredArgsConstructor
public final class PlayerEntity {
    private final UUID uuid;
    private final String name;

    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;
    private final List<Frame> mlFrames = new LinkedList<>();
    private List<Frame> lastAnalyzedFrames = null;

    private volatile boolean isProcessingFlag = false;

    public UUID getUUID() {
        return this.uuid;
    }

    public void setLastAnalyzedFrames(List<Frame> frames) {
        this.lastAnalyzedFrames = new ArrayList<>(frames);
    }

    public List<Frame> getFrames() {
        return this.mlFrames;
    }
}