package ru.Fronzter.MindAc;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.Fronzter.MindAc.command.CommandManager;
import ru.Fronzter.MindAc.listener.ConnectionListener;
import ru.Fronzter.MindAc.listener.MovementListener;
import ru.Fronzter.MindAc.listener.ProtocolLibPacketListener;
import ru.Fronzter.MindAc.service.CheatDetectionService;
import ru.Fronzter.MindAc.service.DatabaseService;
import ru.Fronzter.MindAc.service.HeartbeatService;
import ru.Fronzter.MindAc.service.ViolationManager;

public final class MindAI extends JavaPlugin {
    private static MindAI instance;
    private static boolean isApiKeyValid = false;
    private int heartbeatTaskID = -1;
    private final Set<UUID> alertsDisabledAdmins = ConcurrentHashMap.newKeySet();
    private DatabaseService databaseService;
    private OkHttpClient httpClient;
    private ViolationManager violationManager;
    private ProtocolManager protocolManager;
    private CheatDetectionService cheatDetectionService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!checkProtocolLib()) {
            getLogger().severe("ProtocolLib not found! This plugin requires ProtocolLib to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize ProtocolLib
        initializeProtocolLib();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        databaseService = new DatabaseService(this);
        databaseService.init();

        this.violationManager = new ViolationManager(this);
        this.cheatDetectionService = new CheatDetectionService(this);

        String apiKey = getConfig().getString("gemini-api-key", "");
        if (apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            if (getConfig().getBoolean("license-validation.enabled", false)) {
                getLogger().severe("Invalid Gemini API key! Please set a valid API key in config.yml");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            validateGeminiApiKey(apiKey);
        }

        initializePluginServices();

        CommandManager commandManager = new CommandManager(this);
        getCommand("mindai").setExecutor(commandManager);
        getCommand("mindai").setTabCompleter(commandManager);
    }

    private boolean checkProtocolLib() {
        return getServer().getPluginManager().getPlugin("ProtocolLib") != null;
    }

    private void initializeProtocolLib() {
        if (checkProtocolLib()) {
            try {
                this.protocolManager = ProtocolLibrary.getProtocolManager();
                getLogger().info("ProtocolLib found and initialized successfully!");
            } catch (Exception e) {
                getLogger().warning("Failed to initialize ProtocolLib: " + e.getMessage());
                this.protocolManager = null;
            }
        } else {
            this.protocolManager = null;
            getLogger().warning("ProtocolLib not found - packet monitoring will be limited");
        }
    }

    private void validateGeminiApiKey(final String apiKey) {
        if (!getConfig().getBoolean("license-validation.enabled", false)) {
            isApiKeyValid = true;
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // Simple test request to validate API key
                String testJson = "{\"contents\":[{\"parts\":[{\"text\":\"test\"}]}]}";
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
                
                RequestBody body = RequestBody.create(testJson, MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();
                
                try (Response response = this.httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        isApiKeyValid = true;
                        getLogger().info("Gemini API key validated successfully!");
                    } else {
                        getLogger().severe("Invalid Gemini API key! Response code: " + response.code());
                        disablePlugin();
                    }
                }
            } catch (Exception e) {
                getLogger().severe("Failed to validate Gemini API key: " + e.getMessage());
                disablePlugin();
            }
        });
    }

    private void disablePlugin() {
        Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
    }

    private void initializePluginServices() {
        // Register ProtocolLib packet listener for comprehensive packet monitoring
        if (protocolManager != null) {
            try {
                protocolManager.addPacketListener(new ProtocolLibPacketListener(this));
                getLogger().info("ProtocolLib packet listener registered successfully!");
                getLogger().info("Plugin will use packet-level monitoring for enhanced detection");
            } catch (Exception e) {
                getLogger().warning("Failed to register ProtocolLib packet listener: " + e.getMessage());
                getLogger().info("Falling back to Bukkit event listeners only");
            }
        } else {
            getLogger().info("ProtocolLib not available - using Bukkit event listeners only");
        }
        
        // Register Bukkit event listeners as backup/supplement
        getServer().getPluginManager().registerEvents(new ConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new MovementListener(), this);
        
        // Log version compatibility info
        String serverVersion = getServer().getVersion();
        getLogger().info("Server version detected: " + serverVersion);
        getLogger().info("MindAI initialized with comprehensive monitoring system");
        
        // Start heartbeat service (optional)
        long interval = 20L * 60 * 5; // 5 minutes
        heartbeatTaskID = Bukkit.getScheduler().runTaskTimerAsynchronously(this, HeartbeatService::sendHeartbeat, 100L, interval).getTaskId();
    }

    @Override
    public void onDisable() {
        if (databaseService != null) {
            databaseService.close();
        }
        if (heartbeatTaskID != -1) {
            Bukkit.getScheduler().cancelTask(heartbeatTaskID);
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabledAdmins.contains(uuid)) {
            alertsDisabledAdmins.remove(uuid);
            return true;
        } else {
            alertsDisabledAdmins.add(uuid);
            return false;
        }
    }

    public boolean areAlertsEnabledFor(UUID uuid) {
        return !alertsDisabledAdmins.contains(uuid);
    }

    public static MindAI getInstance() { return instance; }
    public static boolean isLicenseActive() { return isApiKeyValid; }
    public DatabaseService getDatabaseService() { return databaseService; }
    public OkHttpClient getHttpClient() { return this.httpClient; }
    public ViolationManager getViolationManager() { return this.violationManager; }
    public CheatDetectionService getCheatDetectionService() { return this.cheatDetectionService; }
}