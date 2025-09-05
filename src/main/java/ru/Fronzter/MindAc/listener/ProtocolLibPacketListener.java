package ru.Fronzter.MindAc.listener;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;

import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;
import ru.Fronzter.MindAc.service.GeminiService;

public class ProtocolLibPacketListener extends PacketAdapter {

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;
    private final java.util.Map<java.util.UUID, Long> lastAnalysisTime = new java.util.concurrent.ConcurrentHashMap<>();

    public ProtocolLibPacketListener(MindAI plugin) {
        super(plugin, ListenerPriority.NORMAL, 
              PacketType.Play.Client.POSITION_LOOK, 
              PacketType.Play.Client.LOOK,
              PacketType.Play.Client.USE_ENTITY);
        
        this.isMlCheckEnabled = plugin.getConfig().getBoolean("ml-check.enabled", false);
        this.framesToAnalyze = plugin.getConfig().getInt("ml-check.frames-to-analyze", 150);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (!isMlCheckEnabled) return;

        Player player = event.getPlayer();
        if (player == null) return;

        PlayerEntity entity = PlayerRegistry.getPlayer(player.getUniqueId());
        if (entity == null) return;

        PacketType packetType = event.getPacketType();

        // Handle movement packets (rotation tracking)
        if (packetType == PacketType.Play.Client.POSITION_LOOK || 
            packetType == PacketType.Play.Client.LOOK) {
            
            try {
                float yaw = event.getPacket().getFloat().read(0);
                float pitch = event.getPacket().getFloat().read(1);

                Frame frame = new Frame(
                    yaw - entity.getLastYaw(),
                    pitch - entity.getLastPitch()
                );

                List<Frame> frames = entity.getFrames();
                frames.add(frame);

                while (frames.size() > framesToAnalyze) {
                    frames.remove(0);
                }

                entity.setLastYaw(yaw);
                entity.setLastPitch(pitch);
            } catch (Exception e) {
                // Silently handle packet reading errors for movement packets
            }
        }

        // Handle entity interaction packets (attack detection)
        if (packetType == PacketType.Play.Client.USE_ENTITY) {
            try {
                // Safely read the entity ID
                int entityId = event.getPacket().getIntegers().read(0);
                
                // Schedule the entity lookup on the main thread to avoid async issues
                org.bukkit.Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> {
                    try {
                        // Check if there are any EntityUseAction fields available
                        boolean isAttack = false;
                        if (event.getPacket().getEntityUseActions().size() > 0) {
                            EnumWrappers.EntityUseAction action = event.getPacket()
                                .getEntityUseActions()
                                .read(0);
                            isAttack = (action == EnumWrappers.EntityUseAction.ATTACK);
                        } else {
                            // Fallback: assume any USE_ENTITY packet on a player is potentially an attack
                            isAttack = true;
                        }

                        if (isAttack) {
                            Entity target = null;
                            // Now safe to call getEntities() from main thread
                            for (Entity e : player.getWorld().getEntities()) {
                                if (e.getEntityId() == entityId) {
                                    target = e;
                                    break;
                                }
                            }

                            if (target instanceof Player && entity.getFrames().size() >= framesToAnalyze) {
                                // Rate limiting: only analyze once every 5 seconds per player
                                long currentTime = System.currentTimeMillis();
                                Long lastTime = lastAnalysisTime.get(player.getUniqueId());
                                
                                if (lastTime == null || (currentTime - lastTime) > 5000) {
                                    lastAnalysisTime.put(player.getUniqueId(), currentTime);
                                    
                                    // Check if there's meaningful movement variance (not just noise)
                                    if (hasSignificantMovement(entity.getFrames())) {
                                        GeminiService.analyzeWithGemini(entity);
                                    }
                                    entity.getFrames().clear();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Silently handle any errors in the main thread task
                    }
                });
            } catch (Exception e) {
                // Silently handle any packet reading errors
                // This prevents the plugin from crashing due to packet structure changes
            }
        }
    }
    
    private boolean hasSignificantMovement(List<Frame> frames) {
        if (frames.size() < 10) return false;
        
        // Calculate variance in movement to detect if there's meaningful data
        double totalYawVariance = 0;
        double totalPitchVariance = 0;
        
        for (Frame frame : frames) {
            totalYawVariance += Math.abs(frame.getX()); // yaw delta
            totalPitchVariance += Math.abs(frame.getY()); // pitch delta
        }
        
        double avgYawVariance = totalYawVariance / frames.size();
        double avgPitchVariance = totalPitchVariance / frames.size();
        
        // Only analyze if there's significant movement (not just tiny mouse jitter)
        return avgYawVariance > 0.5 || avgPitchVariance > 0.5;
    }
}
