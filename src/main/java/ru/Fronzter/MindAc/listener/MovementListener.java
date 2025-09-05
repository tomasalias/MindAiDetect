package ru.Fronzter.MindAc.listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;

public class MovementListener implements Listener {

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;
    private final Map<UUID, Long> lastBlockBreakTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rapidBreakCount = new ConcurrentHashMap<>();

    public MovementListener() {
        this.isMlCheckEnabled = MindAI.getInstance().getConfig().getBoolean("ml-check.enabled", false);
        this.framesToAnalyze = MindAI.getInstance().getConfig().getInt("ml-check.frames-to-analyze", 150);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isMlCheckEnabled) return;

        Player player = event.getPlayer();
        PlayerEntity entity = PlayerRegistry.getPlayer(player.getUniqueId());
        if (entity == null) return;

        // Only track rotation changes (mouse movements)
        if (event.getFrom().getYaw() != event.getTo().getYaw() || 
            event.getFrom().getPitch() != event.getTo().getPitch()) {

            float yawDelta = event.getTo().getYaw() - entity.getLastYaw();
            float pitchDelta = event.getTo().getPitch() - entity.getLastPitch();

            // Normalize yaw delta to handle wrapping around 360/-180
            while (yawDelta > 180) yawDelta -= 360;
            while (yawDelta < -180) yawDelta += 360;

            // Only track significant movements to reduce noise
            if (Math.abs(yawDelta) > 0.01 || Math.abs(pitchDelta) > 0.01) {
                Frame frame = new Frame(yawDelta, pitchDelta);

                List<Frame> frames = entity.getFrames();
                frames.add(frame);

                while (frames.size() > framesToAnalyze) {
                    frames.remove(0);
                }

                entity.setLastYaw(event.getTo().getYaw());
                entity.setLastPitch(event.getTo().getPitch());
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isMlCheckEnabled) return;

        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerEntity entity = PlayerRegistry.getPlayer(attacker.getUniqueId());
            
            if (entity != null && entity.getFrames().size() >= framesToAnalyze) {
                // Use the new CheatDetectionService for analysis
                MindAI.getInstance().getCheatDetectionService().analyzeMovementPattern(entity, "combat");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isMlCheckEnabled) return;
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Track rapid block breaking (potential x-ray or fast break)
        Long lastBreakTime = lastBlockBreakTime.get(playerId);
        if (lastBreakTime != null && (currentTime - lastBreakTime) < 100) { // Less than 100ms between breaks
            Integer count = rapidBreakCount.getOrDefault(playerId, 0);
            rapidBreakCount.put(playerId, count + 1);
            
            // If too many rapid breaks, trigger analysis
            if (count > 5) {
                PlayerEntity entity = PlayerRegistry.getPlayer(playerId);
                if (entity != null && !entity.getFrames().isEmpty()) {
                    MindAI.getInstance().getCheatDetectionService().analyzeMovementPattern(entity, "rapid_mining");
                }
                rapidBreakCount.remove(playerId);
            }
        } else {
            rapidBreakCount.put(playerId, 0);
        }
        
        lastBlockBreakTime.put(playerId, currentTime);
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        if (!isMlCheckEnabled) return;
        
        Player player = event.getPlayer();
        PlayerEntity entity = PlayerRegistry.getPlayer(player.getUniqueId());
        
        if (entity != null) {
            // Track sprint toggling patterns (could indicate speed hacks)
            // This is supplementary data for analysis
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!isMlCheckEnabled) return;
        
        Player player = event.getPlayer();
        PlayerEntity entity = PlayerRegistry.getPlayer(player.getUniqueId());
        
        if (entity != null) {
            // Track sneak toggling patterns
            // This could be useful for detecting some movement hacks
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isMlCheckEnabled) return;
        
        Player player = event.getPlayer();
        PlayerEntity entity = PlayerRegistry.getPlayer(player.getUniqueId());
        
        if (entity != null) {
            // Track interaction patterns (could indicate auto-clickers)
            // This is supplementary data for analysis
        }
    }
}
