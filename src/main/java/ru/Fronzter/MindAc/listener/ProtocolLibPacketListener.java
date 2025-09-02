package ru.Fronzter.MindAc.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;
import ru.Fronzter.MindAc.service.GeminiService;

import java.util.List;

public class ProtocolLibPacketListener extends PacketAdapter {

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;

    public ProtocolLibPacketListener(MindAI plugin) {
        super(plugin, ListenerPriority.NORMAL, 
              PacketType.Play.Client.POSITION, 
              PacketType.Play.Client.POSITION_LOOK, 
              PacketType.Play.Client.LOOK,
              PacketType.Play.Client.FLYING,
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
        }

        // Handle entity interaction packets (attack detection)
        if (packetType == PacketType.Play.Client.USE_ENTITY) {
            EnumWrappers.EntityUseAction action = event.getPacket()
                .getEntityUseActions()
                .read(0);

            if (action == EnumWrappers.EntityUseAction.ATTACK) {
                int entityId = event.getPacket().getIntegers().read(0);
                
                Entity target = null;
                for (Entity e : player.getWorld().getEntities()) {
                    if (e.getEntityId() == entityId) {
                        target = e;
                        break;
                    }
                }

                if (target instanceof Player) {
                    if (entity.getFrames().size() >= framesToAnalyze) {
                        GeminiService.analyzeWithGemini(entity);
                        entity.getFrames().clear();
                    }
                }
            }
        }
    }
}
