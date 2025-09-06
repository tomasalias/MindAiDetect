package ru.Fronzter.MindAc.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public final class PlayerEntity {
    private final UUID uuid;
    private final String name;

    private volatile float lastYaw = 0.0F;
    private volatile float lastPitch = 0.0F;
    private final List<Frame> mlFrames = Collections.synchronizedList(new LinkedList<>());
    private volatile List<Frame> lastAnalyzedFrames = null;

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