package ru.Fronzter.MindAc.data;

import lombok.Value;

@Value
public class PlayerStats {
    int totalViolations;
    double averageProbability;
    long lastViolationTimestamp;
}