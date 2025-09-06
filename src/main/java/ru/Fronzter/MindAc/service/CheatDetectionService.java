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
        
        // Create a thread-safe copy of the frames list to avoid ConcurrentModificationException
        List<Frame> frames;
        synchronized (entity.getFrames()) {
            frames = new java.util.ArrayList<>(entity.getFrames());
        }
        
        if (frames.isEmpty()) return;
        
        // Perform various heuristic checks before sending to Gemini
        boolean shouldAnalyze = false;
        int detectionScore = 0;
        
        // Check for robotic movement patterns
        if (detectRoboticMovement(frames)) {
            shouldAnalyze = true;
            detectionScore++;
            plugin.getLogger().info("Detected robotic movement pattern for player: " + entity.getName());
        }
        
        // Check for impossible precision
        if (detectImpossiblePrecision(frames)) {
            shouldAnalyze = true;
            detectionScore++;
            plugin.getLogger().info("Detected impossible precision for player: " + entity.getName());
        }
        
        // Check for snap-to-target behavior
        if (detectSnapToTarget(frames)) {
            shouldAnalyze = true;
            detectionScore++;
            plugin.getLogger().info("Detected snap-to-target behavior for player: " + entity.getName());
        }
        
        // Check for consistent aim patterns
        if (detectConsistentAiming(frames)) {
            shouldAnalyze = true;
            detectionScore++;
            plugin.getLogger().info("Detected consistent aiming pattern for player: " + entity.getName());
        }
        
        // Check for Kill Aura patterns (rapid target switching)
        if (detectKillAura(frames)) {
            shouldAnalyze = true;
            detectionScore += 2; // Kill Aura is more serious, give higher score
            plugin.getLogger().info("Detected Kill Aura pattern for player: " + entity.getName());
        }
        
        if (shouldAnalyze) {
            lastAnalysisTime.put(playerId, currentTime);
            
            // Increment suspicious movement count
            int suspCount = suspiciousMovementCount.getOrDefault(playerId, 0) + 1;
            suspiciousMovementCount.put(playerId, suspCount);
            
            // If we have multiple detection types in a single analysis, increase urgency
            if (detectionScore >= 2) {
                plugin.getLogger().warning("Multiple cheat patterns detected simultaneously for " + entity.getName() + 
                                         " (patterns: " + detectionScore + ", total count: " + suspCount + ")");
                
                // Immediate action for obvious multiple patterns (more conservative)
                if (detectionScore >= 3 || (suspCount >= 3 && detectionScore >= 2)) {
                    plugin.getLogger().warning("Taking immediate action against " + entity.getName() + " for obvious cheating");
                    plugin.getViolationManager().handleViolation(entity, 0.95);
                    suspiciousMovementCount.remove(playerId);
                    return; // Exit early after punishment
                }
            }
            
            // Special case: Kill Aura detection gets faster response
            if (detectionScore >= 2 && suspCount >= 2) {
                // Check if Kill Aura was detected
                for (int i = Math.max(0, frames.size() - 50); i < frames.size(); i++) {
                    // If we detected Kill Aura pattern recently, act faster
                    if (detectKillAura(frames.subList(Math.max(0, i - 20), Math.min(frames.size(), i + 20)))) {
                        plugin.getLogger().warning("Kill Aura detected - taking immediate action against " + entity.getName());
                        plugin.getViolationManager().handleViolation(entity, 0.92);
                        suspiciousMovementCount.remove(playerId);
                        return;
                    }
                }
            }
            
            // If multiple suspicious patterns detected, analyze with Gemini
            if (suspCount >= 2) {
                plugin.getLogger().info("Analyzing player " + entity.getName() + " with Gemini AI (suspicious count: " + suspCount + ")");
                GeminiService.analyzeWithGemini(entity);
                // Don't reset count immediately - let it decay naturally
            }
            
            // If we have overwhelming evidence (5+ detections), take immediate action
            if (suspCount >= 5) {
                plugin.getLogger().warning("Player " + entity.getName() + " shows consistent cheating patterns - taking immediate action");
                // Trigger immediate punishment through violation manager
                plugin.getViolationManager().handleViolation(entity, 0.95); // High confidence
                suspiciousMovementCount.remove(playerId); // Reset after punishment
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
        if (frames.size() < 25) return false;
        
        // Check for perfectly consistent movement deltas (robotic behavior)
        double yawVariance = calculateVariance(frames, true);
        double pitchVariance = calculateVariance(frames, false);
        
        // Calculate movement consistency - check if movements are too regular
        double avgYawDelta = frames.stream().mapToDouble(Frame::getX).average().orElse(0);
        double avgPitchDelta = frames.stream().mapToDouble(Frame::getY).average().orElse(0);
        
        int consistentFrames = 0;
        int significantMovements = 0;
        
        for (Frame frame : frames) {
            if (Math.abs(frame.getX()) > 0.1 || Math.abs(frame.getY()) > 0.1) {
                significantMovements++;
                
                // Check if movement is very close to the average (too consistent)
                if (Math.abs(frame.getX() - avgYawDelta) < 0.05 && Math.abs(frame.getY() - avgPitchDelta) < 0.05) {
                    consistentFrames++;
                }
            }
        }
        
        // If we have significant movement but extremely low variance, it's suspicious
        boolean hasMovement = significantMovements > 10;
        boolean tooLowVariance = hasMovement && (yawVariance < 0.005 || pitchVariance < 0.005);
        boolean tooConsistent = significantMovements > 0 && (consistentFrames / (double) significantMovements) > 0.8;
        
        return tooLowVariance && tooConsistent;
    }
    
    private boolean detectImpossiblePrecision(List<Frame> frames) {
        if (frames.size() < 20) return false;
        
        // Check for movements that are too precise for human control
        int preciseMovements = 0;
        int totalSignificantMovements = 0;
        
        for (Frame frame : frames) {
            // Only check frames with significant movement to avoid false positives from small movements
            if (Math.abs(frame.getX()) > 0.1 || Math.abs(frame.getY()) > 0.1) {
                totalSignificantMovements++;
                // Check for movements that are precise to multiple decimal places (bot-like)
                if (isPreciseFloat(frame.getX()) && isPreciseFloat(frame.getY())) {
                    preciseMovements++;
                }
            }
        }
        
        // Need significant movements to analyze and high percentage of precise movements
        if (totalSignificantMovements < 10) return false;
        
        // If more than 50% of significant movements are impossibly precise
        return (preciseMovements / (double) totalSignificantMovements) > 0.5;
    }
    
    private boolean detectSnapToTarget(List<Frame> frames) {
        if (frames.size() < 15) return false;
        
        // Look for consistent snap patterns - multiple occurrences indicate aimbot
        int snapCount = 0;
        int consecutiveSnaps = 0;
        
        for (int i = 1; i < frames.size() - 2; i++) {
            Frame current = frames.get(i);
            Frame next = frames.get(i + 1);
            Frame nextNext = frames.get(i + 2);
            
            double currentMagnitude = Math.sqrt(current.getX() * current.getX() + current.getY() * current.getY());
            double nextMagnitude = Math.sqrt(next.getX() * next.getX() + next.getY() * next.getY());
            double nextNextMagnitude = Math.sqrt(nextNext.getX() * nextNext.getX() + nextNext.getY() * nextNext.getY());
            
            // Large movement followed by very small adjustments (snap + micro-adjustments)
            if (currentMagnitude > 4.0 && nextMagnitude < 0.5 && nextNextMagnitude < 0.3) {
                snapCount++;
                consecutiveSnaps++;
                
                // If we see 3+ consecutive snap patterns, it's very suspicious
                if (consecutiveSnaps >= 3) {
                    return true;
                }
            } else {
                consecutiveSnaps = 0;
            }
        }
        
        // Need multiple snap patterns to be confident it's not just good aim
        return snapCount >= 4;
    }
    
    private boolean detectConsistentAiming(List<Frame> frames) {
        if (frames.size() < 30) return false;
        
        // Check for unnaturally consistent aiming patterns
        double avgYawDelta = frames.stream().mapToDouble(Frame::getX).average().orElse(0);
        double avgPitchDelta = frames.stream().mapToDouble(Frame::getY).average().orElse(0);
        
        int consistentFrames = 0;
        int significantMovements = 0;
        
        for (Frame frame : frames) {
            // Only check significant movements
            if (Math.abs(frame.getX()) > 0.1 || Math.abs(frame.getY()) > 0.1) {
                significantMovements++;
                
                // Check if movement is very close to the average (too consistent)
                if (Math.abs(frame.getX() - avgYawDelta) < 0.05 && Math.abs(frame.getY() - avgPitchDelta) < 0.05) {
                    consistentFrames++;
                }
            }
        }
        
        // Need a good amount of significant movements to analyze
        if (significantMovements < 15) return false;
        
        // If more than 85% of significant frames are very consistent, it's suspicious
        return (consistentFrames / (double) significantMovements) > 0.85;
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
        // Check if the float has more than 4 decimal places of precision AND follows suspicious patterns
        // This is unusual for human mouse movement
        String str = Float.toString(Math.abs(value));
        int decimalIndex = str.indexOf('.');
        if (decimalIndex == -1) return false;
        
        String fractionalPart = str.substring(decimalIndex + 1);
        
        // Must have high precision AND not be a natural rounding artifact
        boolean highPrecision = fractionalPart.length() > 4;
        boolean notNaturalRounding = !fractionalPart.matches(".*[1-9]0+$") && !fractionalPart.endsWith("5");
        
        // Additional check: look for patterns that indicate bot-generated values
        boolean suspiciousPattern = fractionalPart.matches(".*([0-9])\\1{2,}.*"); // Repeating digits
        
        return highPrecision && (notNaturalRounding || suspiciousPattern);
    }
    
    private boolean detectKillAura(List<Frame> frames) {
        if (frames.size() < 20) return false;
        
        // Kill Aura typically shows rapid, mechanical target switching patterns
        int rapidDirectionChanges = 0;
        int mechanicalMovements = 0;
        
        for (int i = 2; i < frames.size(); i++) {
            Frame prev = frames.get(i - 2);
            Frame current = frames.get(i - 1);
            Frame next = frames.get(i);
            
            // Check for rapid direction changes (typical of Kill Aura target switching)
            double prevAngle = Math.atan2(prev.getY(), prev.getX());
            double currentAngle = Math.atan2(current.getY(), current.getX());
            double nextAngle = Math.atan2(next.getY(), next.getX());
            
            // Calculate angular differences
            double angleDiff1 = Math.abs(currentAngle - prevAngle);
            double angleDiff2 = Math.abs(nextAngle - currentAngle);
            
            // Normalize angle differences to [0, π]
            angleDiff1 = Math.min(angleDiff1, 2 * Math.PI - angleDiff1);
            angleDiff2 = Math.min(angleDiff2, 2 * Math.PI - angleDiff2);
            
            // Rapid direction changes (> 90 degrees in short time)
            if (angleDiff1 > Math.PI / 2 || angleDiff2 > Math.PI / 2) {
                rapidDirectionChanges++;
            }
            
            // Check for mechanical movements (perfect angles, e.g., exactly 90°, 180°, 45°)
            double[] mechanicalAngles = {0, Math.PI/4, Math.PI/2, 3*Math.PI/4, Math.PI, 5*Math.PI/4, 3*Math.PI/2, 7*Math.PI/4};
            for (double mechanicalAngle : mechanicalAngles) {
                if (Math.abs(currentAngle - mechanicalAngle) < 0.05) { // Within ~3 degrees
                    mechanicalMovements++;
                    break;
                }
            }
        }
        
        // Kill Aura indicators:
        // 1. High frequency of rapid direction changes
        // 2. Mechanical/perfect angle movements
        boolean hasRapidChanges = (rapidDirectionChanges / (double) frames.size()) > 0.3;
        boolean hasMechanicalMovements = (mechanicalMovements / (double) frames.size()) > 0.2;
        
        return hasRapidChanges && hasMechanicalMovements;
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
        // Create a thread-safe copy of the frames list
        List<Frame> frames;
        synchronized (entity.getFrames()) {
            frames = new java.util.ArrayList<>(entity.getFrames());
        }
        
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
