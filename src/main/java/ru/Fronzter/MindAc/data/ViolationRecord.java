package ru.Fronzter.MindAc.data;

public final class ViolationRecord {
    private final String playerName;
    private final double probability;
    private final long timestamp;

    public ViolationRecord(String playerName, double probability, long timestamp) {
        this.playerName = playerName;
        this.probability = probability;
        this.timestamp = timestamp;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getProbability() {
        return probability;
    }

    public long getTimestamp() {
        return timestamp;
    }
}