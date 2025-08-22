package ru.Fronzter.MindAc;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.Fronzter.MindAc.listener.ConnectionListener;
import ru.Fronzter.MindAc.listener.PacketListener;
import ru.Fronzter.MindAc.service.HeartbeatService;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

public final class MindAI extends JavaPlugin {
    private static MindAI instance;
    private static boolean isLicenseValid = false;
    private int heartbeatTaskID = -1;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        String apiKey = getConfig().getString("api-key", "");
        if (apiKey.isEmpty() || apiKey.equals("CHANGE-ME-TO-YOUR-API-KEY")) {
            return;
        }

        validateLicense(apiKey);
        getLogger().info("MindAI включен.");
    }

    private void validateLicense(String apiKey) {
        String licenseUrl = getConfig().getString("api-endpoints.license-server", "") + "/validate";
        if (licenseUrl.isEmpty() || licenseUrl.equals("/validate")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("license_key", apiKey);
                String serverIp = getConfig().getString("server-ip", "");
                if (serverIp.isEmpty()) {
                    serverIp = this.getServer().getIp();
                    if (serverIp.isEmpty() || serverIp.equals("0.0.0.0")) {
                        serverIp = "127.0.0.1";
                    }
                }
                data.put("server_ip", serverIp);

                String json = LazyHolder.JSON_ADAPTER.toJson(data);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder().url(licenseUrl).post(body).build();

                Response response = LazyHolder.CLIENT.newCall(request).execute();

                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Map<String, Object> result = LazyHolder.RESPONSE_ADAPTER.fromJson(responseBody);
                    if (result != null && (Boolean) result.getOrDefault("valid", false)) {
                        isLicenseValid = true;
                        Bukkit.getScheduler().runTask(this, this::initializePluginServices);
                    }
                }
                response.close();
            } catch (Exception ignored) {
            }
        });
    }

    private void initializePluginServices() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false).reEncodeByDefault(false);
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().getEventManager().registerListeners(new ConnectionListener(), new PacketListener());
        PacketEvents.getAPI().init();

        long interval = 20L * 60 * 5;
        heartbeatTaskID = Bukkit.getScheduler().runTaskTimerAsynchronously(this, HeartbeatService::sendHeartbeat, 100L, interval).getTaskId();
    }

    @Override
    public void onDisable() {
        if (heartbeatTaskID != -1) {
            Bukkit.getScheduler().cancelTask(heartbeatTaskID);
        }
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded()) {
                PacketEvents.getAPI().terminate();
            }
        } catch (NoClassDefFoundError ignored) {
        }
        getLogger().info("MindAI выключен.");
    }

    public static MindAI getInstance() { return instance; }
    public static boolean isLicenseActive() { return isLicenseValid; }

    private static class LazyHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient();
        private static final Moshi MOSHI = new Moshi.Builder().build();
        private static final Type MAP_STRING_STRING_TYPE = Types.newParameterizedType(Map.class, String.class, String.class);
        private static final JsonAdapter<Map<String, String>> JSON_ADAPTER = MOSHI.adapter(MAP_STRING_STRING_TYPE);
        private static final Type MAP_STRING_OBJECT_TYPE = Types.newParameterizedType(Map.class, String.class, Object.class);
        private static final JsonAdapter<Map<String, Object>> RESPONSE_ADAPTER = MOSHI.adapter(MAP_STRING_OBJECT_TYPE);
    }
}
