package ru.Fronzter.MindAc.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;
import ru.Fronzter.MindAc.service.GeminiService;

import java.util.List;

public class MovementListener implements Listener {

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;

    public MovementListener() {
        this.isMlCheckEnabled = MindAI.getInstance().getConfig().getBoolean("ml-check.enabled", false);
        this.framesToAnalyze = MindAI.getInstance().getConfig().getInt("ml-check.frames-to-analyze", 150);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isMlCheckEnabled) return;

        Player player = event.getPlayer();
        if (player == null) return;

        PlayerEntity entity = PlayerRegistry.getPlayer(player.getUniqueId());
        if (entity == null) return;

        // Only track rotation changes
        if (event.getFrom().getYaw() != event.getTo().getYaw() || 
            event.getFrom().getPitch() != event.getTo().getPitch()) {

            float yawDelta = event.getTo().getYaw() - entity.getLastYaw();
            float pitchDelta = event.getTo().getPitch() - entity.getLastPitch();

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

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isMlCheckEnabled) return;

        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            PlayerEntity entity = PlayerRegistry.getPlayer(attacker.getUniqueId());
            
            if (entity != null && entity.getFrames().size() >= framesToAnalyze) {
                GeminiService.analyzeWithGemini(entity);
                entity.getFrames().clear();
            }
        }
    }
}
