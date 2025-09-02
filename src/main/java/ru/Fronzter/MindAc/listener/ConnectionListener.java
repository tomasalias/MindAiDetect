package ru.Fronzter.MindAc.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;

public class ConnectionListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer() != null) {
            PlayerEntity entity = new PlayerEntity(event.getPlayer().getUniqueId(), event.getPlayer().getName());
            PlayerRegistry.addPlayer(event.getPlayer().getUniqueId(), entity);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            PlayerRegistry.removePlayer(event.getPlayer().getUniqueId());
        }
    }
}