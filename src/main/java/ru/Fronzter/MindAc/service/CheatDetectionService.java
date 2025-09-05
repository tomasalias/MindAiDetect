package ru.Fronzter.MindAc.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;

public class CheatDetectionService {
    
    private final MindAI plugin;
    private final Map<UUID, Long> lastAnalysisTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> suspiciousMovementCount = new ConcurrentHashMap<>();
    private final Map<UUID, Double> averageMovementVariance = new ConcurrentHashMap<>();
    
    public CheatDetectionService(MindAI plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Analyzes player movement patterns for potential aimbot/triggerbot usage
     */
    public void analyzeMovementPattern(PlayerEntity entity, String trigger) {
        if (!plugin.getConfig().getBoolean("ml-check.enabled", false)) return;
        
        UUID playerId = entity.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastAnalysisTime.get(playerId);
        
        // Rate limiting - analyze at most once every 2 seconds
        if (lastTime != null && (currentTime - lastTime) < 2000) {
            return;
        }
        
        List<Frame> frames = entity.getFrames();
        if (frames.isEmpty()) return;
        
        // Perform various heuristic checks before sending to Gemini
        boolean shouldAnalyze = false;
        
        // Check for robotic movement patterns
        if (detectRoboticMovement(frames)) {
            shouldAnalyze = true;
            plugin.getLogger().info("Detected robotic movement pattern for player: " + entity.getName());
        }
        
        // Check for impossible precision
        if (detectImpossiblePrecision(frames)) {
            shouldAnalyze = true;
            plugin.getLogger().info("Detected impossible precision for player: " + entity.getName());
        }
        
        // Check for snap-to-target behavior
        if (detectSnapToTarget(frames)) {
            shouldAnalyze = true;
            plugin.getLogger().info("Detected snap-to-target behavior for player: " + entity.getName());
        }
        
        // Check for consistent aim patterns
        if (detectConsistentAiming(frames)) {
            shouldAnalyze = true;
            plugin.getLogger().info("Detected consistent aiming pattern for player: " + entity.getName());
        }
        
        if (shouldAnalyze) {
            lastAnalysisTime.put(playerId, currentTime);
            
            // Increment suspicious movement count
            int suspCount = suspiciousMovementCount.getOrDefault(playerId, 0) + 1;
            suspiciousMovementCount.put(playerId, suspCount);
            
            // If multiple suspicious patterns detected, analyze with Gemini
            if (suspCount >= 2) {
                GeminiService.analyzeWithGemini(entity);
                suspiciousMovementCount.remove(playerId);
            }
            
            // Log the detection for debugging
            plugin.getLogger().info("Suspicious " + trigger + " detected for " + entity.getName() + 
                                  " (Count: " + suspCount + ")");
        }
        
        // Clean up old data every 30 seconds
        if (currentTime % 30000 < 1000) {
            cleanupOldData(currentTime);
        }
    }
    
    private boolean detectRoboticMovement(List<Frame> frames) {
        if (frames.size() < 20) return false;
        
        // Check for perfectly consistent movement deltas (robotic behavior)
        double yawVariance = calculateVariance(frames, true);
        double pitchVariance = calculateVariance(frames, false);
        
        // If variance is extremely low but movement is present, it's suspicious
        boolean hasMovement = frames.stream().anyMatch(f -> Math.abs(f.getX()) > 0.1 || Math.abs(f.getY()) > 0.1);
        return hasMovement && (yawVariance < 0.01 || pitchVariance < 0.01);
    }
    
    private boolean detectImpossiblePrecision(List<Frame> frames) {
        if (frames.size() < 15) return false;
        
        // Check for movements that are too precise for human control
        int preciseMovements = 0;
        for (Frame frame : frames) {
            // Check for movements that are precise to multiple decimal places (bot-like)
            if (isPreciseFloat(frame.getX()) || isPreciseFloat(frame.getY())) {
                preciseMovements++;
            }
        }
        
        // If more than 60% of movements are impossibly precise
        return (preciseMovements / (double) frames.size()) > 0.6;
    }
    
    private boolean detectSnapToTarget(List<Frame> frames) {
        if (frames.size() < 10) return false;
        
        // Look for sudden large movements followed by precise small adjustments
        for (int i = 1; i < frames.size() - 1; i++) {
            Frame current = frames.get(i);
            Frame next = frames.get(i + 1);
            
            double currentMagnitude = Math.sqrt(current.getX() * current.getX() + current.getY() * current.getY());
            double nextMagnitude = Math.sqrt(next.getX() * next.getX() + next.getY() * next.getY());
            
            // Large movement followed by very small adjustment (snap + micro-adjustment)
            if (currentMagnitude > 5.0 && nextMagnitude < 0.5) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean detectConsistentAiming(List<Frame> frames) {
        if (frames.size() < 25) return false;
        
        // Check for unnaturally consistent aiming patterns
        double avgYawDelta = frames.stream().mapToDouble(Frame::getX).average().orElse(0);
        double avgPitchDelta = frames.stream().mapToDouble(Frame::getY).average().orElse(0);
        
        int consistentFrames = 0;
        for (Frame frame : frames) {
            // Check if movement is very close to the average (too consistent)
            if (Math.abs(frame.getX() - avgYawDelta) < 0.1 && Math.abs(frame.getY() - avgPitchDelta) < 0.1) {
                consistentFrames++;
            }
        }
        
        // If more than 70% of frames are very consistent, it's suspicious
        return (consistentFrames / (double) frames.size()) > 0.7;
    }
    
    private double calculateVariance(List<Frame> frames, boolean useYaw) {
        double sum = 0;
        double sumSquares = 0;
        int count = frames.size();
        
        for (Frame frame : frames) {
            double value = useYaw ? frame.getX() : frame.getY();
            sum += value;
            sumSquares += value * value;
        }
        
        double mean = sum / count;
        return (sumSquares / count) - (mean * mean);
    }
    
    private boolean isPreciseFloat(float value) {
        // Check if the float has more than 3 decimal places of precision
        // This is unusual for human mouse movement
        String str = Float.toString(Math.abs(value));
        int decimalIndex = str.indexOf('.');
        if (decimalIndex == -1) return false;
        
        String fractionalPart = str.substring(decimalIndex + 1);
        return fractionalPart.length() > 3 && !fractionalPart.matches(".*[1-9]0+$");
    }
    
    private void cleanupOldData(long currentTime) {
        // Remove analysis timestamps older than 5 minutes
        lastAnalysisTime.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue()) > 300000);
        
        // Decay suspicious movement counts
        suspiciousMovementCount.replaceAll((key, value) -> Math.max(0, value - 1));
        suspiciousMovementCount.entrySet().removeIf(entry -> entry.getValue() <= 0);
        
        // Clean up variance data
        averageMovementVariance.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }
    
    /**
     * Quick heuristic check for obvious cheating patterns before expensive AI analysis
     */
    public boolean isObviousCheating(PlayerEntity entity) {
        List<Frame> frames = entity.getFrames();
        if (frames.size() < 10) return false;
        
        // Check for perfect aim lock (zero variance in multiple frames)
        int zeroVarianceCount = 0;
        for (Frame frame : frames) {
            if (Math.abs(frame.getX()) < 0.001 && Math.abs(frame.getY()) < 0.001) {
                zeroVarianceCount++;
            }
        }
        
        // If more than 80% of frames have zero variance while player should be moving
        return (zeroVarianceCount / (double) frames.size()) > 0.8;
    }
}
