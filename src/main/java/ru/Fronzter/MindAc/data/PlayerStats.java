package ru.Fronzter.MindAc.data;

public final class PlayerStats {
    private final int totalViolations;
    private final double averageProbability;
    private final long lastViolationTimestamp;

    public PlayerStats(int totalViolations, double averageProbability, long lastViolationTimestamp) {
        this.totalViolations = totalViolations;
        this.averageProbability = averageProbability;
        this.lastViolationTimestamp = lastViolationTimestamp;
    }

    public int getTotalViolations() {
        return totalViolations;
    }

    public double getAverageProbability() {
        return averageProbability;
    }

    public long getLastViolationTimestamp() {
        return lastViolationTimestamp;
    }
}