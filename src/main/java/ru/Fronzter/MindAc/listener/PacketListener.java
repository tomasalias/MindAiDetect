package ru.Fronzter.MindAc.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;
import ru.Fronzter.MindAc.service.AnalysisService;

public class PacketListener extends PacketListenerAbstract {
    private static final int COMBAT_ANALYSIS_DURATION_TICKS = 200;

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;

    public PacketListener() {
        this.isMlCheckEnabled = MindAI.getInstance().getConfig().getBoolean("ml-check.enabled", false);
        this.framesToAnalyze = MindAI.getInstance().getConfig().getInt("ml-check.frames-to-analyze", 150);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isMlCheckEnabled) return;

        User user = event.getUser();
        if (user == null || user.getUUID() == null) return;

        PlayerEntity entity = PlayerRegistry.getPlayer(user.getUUID());
        if (entity == null) return;

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                Player bukkitPlayer = Bukkit.getPlayer(user.getUUID());
                if (bukkitPlayer != null) {
                    Entity target = bukkitPlayer.getWorld().getEntities().stream()
                            .filter(e -> e.getEntityId() == interact.getEntityId())
                            .findFirst().orElse(null);

                    if (target instanceof Player) {
                        entity.setCombatRecordingTicks(COMBAT_ANALYSIS_DURATION_TICKS);
                    }
                }
            }
        }

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);

            if (entity.getCombatRecordingTicks() > 0) {
                entity.setCombatRecordingTicks(entity.getCombatRecordingTicks() - 1);
            }

            if (flying.hasRotationChanged()) {
                Frame frame = new Frame(
                        flying.getLocation().getYaw() - entity.getLastYaw(),
                        flying.getLocation().getPitch() - entity.getLastPitch()
                );

                if (entity.getCombatRecordingTicks() > 0) {
                    entity.getFrames().add(frame);

                    if (entity.getFrames().size() >= framesToAnalyze) {
                        AnalysisService.analyze(entity);
                        entity.getFrames().clear();
                    }
                }
                else if (!entity.getFrames().isEmpty()) {
                    entity.getFrames().clear();
                }
            }
            updateLastLocation(entity, flying.getLocation());
        }
    }

    private void updateLastLocation(PlayerEntity entity, com.github.retrooper.packetevents.protocol.world.Location location) {
        entity.setLastYaw(location.getYaw());
        entity.setLastPitch(location.getPitch());
    }
}