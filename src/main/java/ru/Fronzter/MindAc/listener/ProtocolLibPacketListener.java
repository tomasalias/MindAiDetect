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

public class ProtocolLibPacketListener extends PacketAdapter {

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;
    private final java.util.Map<java.util.UUID, Long> lastAnalysisTime = new java.util.concurrent.ConcurrentHashMap<>();

    public ProtocolLibPacketListener(MindAI plugin) {
        super(plugin, ListenerPriority.NORMAL, 
              // Movement and rotation packets
              PacketType.Play.Client.POSITION_LOOK, 
              PacketType.Play.Client.LOOK,
              PacketType.Play.Client.POSITION,
              // Combat packets
              PacketType.Play.Client.USE_ENTITY,
              PacketType.Play.Client.ARM_ANIMATION,
              // Additional packets for comprehensive monitoring
              PacketType.Play.Client.BLOCK_DIG,
              PacketType.Play.Client.USE_ITEM);
        
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

        // Handle movement packets (rotation tracking) - most important for aimbot detection
        if (packetType == PacketType.Play.Client.POSITION_LOOK || 
            packetType == PacketType.Play.Client.LOOK ||
            packetType == PacketType.Play.Client.POSITION) {
            
            handleMovementPacket(event, entity);
        }

        // Handle entity interaction packets (attack detection)
        else if (packetType == PacketType.Play.Client.USE_ENTITY) {
            handleUseEntityPacket(event, player, entity);
        }

        // Handle arm animation packets (swing detection)
        else if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
            handleArmAnimationPacket(event, player, entity);
        }

        // Handle block dig packets (mining speed analysis)
        else if (packetType == PacketType.Play.Client.BLOCK_DIG) {
            handleBlockDigPacket(event, player, entity);
        }

        // Handle item use packets
        else if (packetType == PacketType.Play.Client.USE_ITEM) {
            handleUseItemPacket(event, player, entity);
        }
    }

    private void handleMovementPacket(PacketEvent event, PlayerEntity entity) {
        try {
            float yaw = 0, pitch = 0;
            PacketType packetType = event.getPacketType();
            
            // Extract yaw and pitch based on packet type
            if (packetType == PacketType.Play.Client.POSITION_LOOK) {
                yaw = event.getPacket().getFloat().read(0);
                pitch = event.getPacket().getFloat().read(1);
            } else if (packetType == PacketType.Play.Client.LOOK) {
                yaw = event.getPacket().getFloat().read(0);
                pitch = event.getPacket().getFloat().read(1);
            } else if (packetType == PacketType.Play.Client.POSITION) {
                // Position packets don't contain rotation data, skip
                return;
            }

            // Calculate rotation deltas
            float yawDelta = yaw - entity.getLastYaw();
            float pitchDelta = pitch - entity.getLastPitch();

            // Normalize yaw delta to handle wrapping around 360/-180
            while (yawDelta > 180) yawDelta -= 360;
            while (yawDelta < -180) yawDelta += 360;

            // Only track significant movements to reduce noise
            if (Math.abs(yawDelta) > 0.01 || Math.abs(pitchDelta) > 0.01) {
                Frame frame = new Frame(yawDelta, pitchDelta);
                List<Frame> frames = entity.getFrames();
                synchronized (frames) {
                    frames.add(frame);

                    while (frames.size() > framesToAnalyze) {
                        frames.remove(0);
                    }
                }

                entity.setLastYaw(yaw);
                entity.setLastPitch(pitch);
            }
        } catch (Exception e) {
            // Silently handle packet reading errors for movement packets
        }
    }

    private void handleUseEntityPacket(PacketEvent event, Player player, PlayerEntity entity) {
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
                        // Find the target entity
                        for (Entity e : player.getWorld().getEntities()) {
                            if (e.getEntityId() == entityId) {
                                target = e;
                                break;
                            }
                        }

                        if (target instanceof Player) {
                            // Check frame count in a thread-safe way
                            int frameCount;
                            synchronized (entity.getFrames()) {
                                frameCount = entity.getFrames().size();
                            }
                            
                            if (frameCount >= framesToAnalyze) {
                                // Rate limiting: only analyze once every 3 seconds per player
                                long currentTime = System.currentTimeMillis();
                                Long lastTime = lastAnalysisTime.get(player.getUniqueId());
                                
                                if (lastTime == null || (currentTime - lastTime) > 3000) {
                                    lastAnalysisTime.put(player.getUniqueId(), currentTime);
                                    
                                    // Use the new CheatDetectionService for comprehensive analysis
                                    MindAI.getInstance().getCheatDetectionService().analyzeMovementPattern(entity, "combat");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Silently handle any errors in the main thread task
                }
            });
        } catch (Exception e) {
            // Silently handle any packet reading errors
        }
    }

    private void handleArmAnimationPacket(PacketEvent event, Player player, PlayerEntity entity) {
        // Track swing patterns - could indicate auto-clicker or combat hacks
        // This is a simple implementation, can be expanded for more sophisticated detection
        try {
            // You can add swing timing analysis here if needed
            // For now, we just log that a swing occurred
        } catch (Exception e) {
            // Handle errors silently
        }
    }

    private void handleBlockDigPacket(PacketEvent event, Player player, PlayerEntity entity) {
        // Track mining patterns - could indicate X-ray or fast break hacks
        try {
            // You can add block breaking analysis here
            // For now, we just acknowledge the packet
        } catch (Exception e) {
            // Handle errors silently
        }
    }

    private void handleUseItemPacket(PacketEvent event, Player player, PlayerEntity entity) {
        // Track item usage patterns
        try {
            // You can add item usage analysis here
            // For now, we just acknowledge the packet
        } catch (Exception e) {
            // Handle errors silently
        }
    }
}
