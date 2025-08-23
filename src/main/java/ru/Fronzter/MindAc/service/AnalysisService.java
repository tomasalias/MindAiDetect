package ru.Fronzter.MindAc.service;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.util.MoshiFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AnalysisService {

    public static void analyze(final PlayerEntity entity) {
        if (entity.getFrames().isEmpty()) {
            return;
        }
        entity.setLastAnalyzedFrames(entity.getFrames());

        String json;
        try {
            Map<String, Object> dataToSend = new HashMap<>();
            dataToSend.put("license_key", MindAI.getInstance().getConfig().getString("api-key"));
            dataToSend.put("name", entity.getName());
            dataToSend.put("frames", entity.getLastAnalyzedFrames());

            Moshi moshi = MoshiFactory.getInstance();
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
            json = adapter.toJson(dataToSend);
        } catch (Exception e) {
            MindAI.getInstance().getLogger().warning("Ошибка при сериализации данных для анализа: " + e.getMessage());
            return;
        }

        String url = MindAI.getInstance().getConfig().getString("api-endpoints.analysis-server", "") + "/predict";

        if (url.isEmpty() || url.equals("/predict")) {
            return;
        }

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        LazyHolder.CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                MindAI.getInstance().getLogger().warning("Не удалось отправить данные на анализ: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        MindAI.getInstance().getLogger().warning("Получен некорректный ответ от сервера анализа: " + response.code());
                        return;
                    }
                    String bodyString = responseBody.string();
                    Map<String, Double> result = LazyHolder.MAP_ADAPTER.fromJson(bodyString);
                    if (result != null && result.containsKey("cheat_probability")) {
                        double probability = result.get("cheat_probability");
                        Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> handleResult(entity, probability));
                    }
                } catch (Exception ex) {
                    MindAI.getInstance().getLogger().severe("Ошибка при обработке ответа от сервера анализа: " + ex.getMessage());
                }
            }
        });
    }

    private static void handleResult(PlayerEntity entity, double probability) {
        double classificationThreshold = MindAI.getInstance().getConfig().getDouble("ml-check.classification-threshold");
        if (probability > classificationThreshold) {
            sendAlert(entity, probability);
            executeViolationCommand(entity, probability);
            entity.getViolationLogs().put(System.currentTimeMillis(), 1);
        }
        long windowMillis = MindAI.getInstance().getConfig().getLong("ml-check.window", 600) * 1000L;
        entity.getViolationLogs().entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getKey() > windowMillis);
        int totalViolations = entity.getViolationLogs().values().stream().mapToInt(Integer::intValue).sum();
        double violationThreshold = MindAI.getInstance().getConfig().getDouble("ml-check.violation-threshold");
        if (totalViolations >= violationThreshold) {
            punishPlayer(entity);
            entity.getViolationLogs().clear();
        }
    }

    private static void sendAlert(PlayerEntity entity, double probability) {
        if (!MindAI.getInstance().getConfig().getBoolean("alerts.enabled", true)) {
            return;
        }
        String message = MindAI.getInstance().getConfig().getString("alerts.message");
        if (message == null || message.isEmpty()) return;

        String formattedProb = String.format("%.2f%%", probability * 100.0D);

        String finalMessage = ChatColor.translateAlternateColorCodes('&', message.replace("%player%", entity.getName()).replace("%probability%", formattedProb));
        String permission = MindAI.getInstance().getConfig().getString("alerts.permission", "mindai.alerts");
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(permission)) {
                admin.sendMessage(finalMessage);
            }
        }
    }

    private static void executeViolationCommand(PlayerEntity entity, double probability) {
        String command = MindAI.getInstance().getConfig().getString("alerts.violation-command", "");
        if (command.isEmpty()) return;

        String formattedProb = String.format("%.2f%%", probability * 100.0D);

        String finalCommand = ChatColor.translateAlternateColorCodes('&', command.replace("%player%", entity.getName()).replace("%probability%", formattedProb));
        Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }

    private static void punishPlayer(PlayerEntity entity) {
        String command = MindAI.getInstance().getConfig().getString("punishment");
        if (command == null || command.isEmpty()) return;
        String finalCommand = command.replace("%player%", entity.getName());
        Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }

    private static class LazyHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient();
        private static final Moshi MOSHI = MoshiFactory.getInstance();
        private static final Type MAP_TYPE = Types.newParameterizedType(Map.class, String.class, Double.class);
        private static final JsonAdapter<Map<String, Double>> MAP_ADAPTER = MOSHI.adapter(MAP_TYPE);
    }
}