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
    private String publicServerIp = null;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        String apiKey = getConfig().getString("api-key", "");
        if (apiKey.isEmpty() || apiKey.equals("CHANGE-ME-TO-YOUR-API-KEY")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        discoverPublicIpAndValidateLicense(apiKey);
    }

    private void discoverPublicIpAndValidateLicense(final String apiKey) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Request ipRequest = new Request.Builder().url("https://api.ipify.org").build();
                try (Response ipResponse = LazyHolder.CLIENT.newCall(ipRequest).execute()) {
                    if (ipResponse.isSuccessful() && ipResponse.body() != null) {
                        this.publicServerIp = ipResponse.body().string().trim();
                        validateLicense(apiKey, this.publicServerIp);
                    } else {
                        disablePluginWithMessage("Ошибка определения IP.");
                    }
                }
            } catch (Exception e) {
                disablePluginWithMessage("Проверьте сетевое соединение сервера.");
            }
        });
    }

    private void validateLicense(String apiKey, String serverIp) {
        String licenseUrl = getConfig().getString("api-endpoints.license-server", "") + "/validate";
        if (licenseUrl.isEmpty() || licenseUrl.equals("/validate")) {
            disablePluginWithMessage("URL сервера лицензий не указан в config.yml!");
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("license_key", apiKey);
            data.put("server_ip", serverIp);

            String json = LazyHolder.JSON_ADAPTER.toJson(data);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(licenseUrl).post(body).build();

            try (Response response = LazyHolder.CLIENT.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Map<String, Object> result = LazyHolder.RESPONSE_ADAPTER.fromJson(responseBody);
                    if (result != null && (Boolean) result.getOrDefault("valid", false)) {
                        isLicenseValid = true;
                        Bukkit.getScheduler().runTask(this, this::initializePluginServices);
                    } else {
                        disablePluginWithMessage("Лицензия недействительна.");
                    }
                } else {
                    disablePluginWithMessage("Сервер лицензий вернул ошибку.");
                }
            }
        } catch (Exception e) {
            disablePluginWithMessage("Ошибка при подключении к серверу лицензий.");
        }
    }

    private void disablePluginWithMessage(String message) {
        Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
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
        } catch (NoClassDefFoundError ignored) {}
    }

    public static MindAI getInstance() { return instance; }
    public static boolean isLicenseActive() { return isLicenseValid; }
    public String getPublicServerIp() { return this.publicServerIp; }

    private static class LazyHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient();
        private static final Moshi MOSHI = new Moshi.Builder().build();
        private static final Type MAP_STRING_STRING_TYPE = Types.newParameterizedType(Map.class, String.class, String.class);
        private static final JsonAdapter<Map<String, String>> JSON_ADAPTER = MOSHI.adapter(MAP_STRING_STRING_TYPE);
        private static final Type MAP_STRING_OBJECT_TYPE = Types.newParameterizedType(Map.class, String.class, Object.class);
        private static final JsonAdapter<Map<String, Object>> RESPONSE_ADAPTER = MOSHI.adapter(MAP_STRING_OBJECT_TYPE);
    }
}
