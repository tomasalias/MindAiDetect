package ru.Fronzter.MindAc.service;

import okhttp3.*;
import org.bukkit.Bukkit;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class GeminiService {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    
    public static void analyzeWithGemini(final PlayerEntity entity) {
        String apiKey = MindAI.getInstance().getConfig().getString("gemini-api-key", "");
        if (apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            return;
        }

        if (entity.getFrames().isEmpty()) {
            return;
        }

        entity.setLastAnalyzedFrames(entity.getFrames());

        String model = MindAI.getInstance().getConfig().getString("gemini-settings.model", "gemini-1.5-flash");
        double temperature = MindAI.getInstance().getConfig().getDouble("gemini-settings.temperature", 0.3);
        int maxTokens = MindAI.getInstance().getConfig().getInt("gemini-settings.max-tokens", 100);

        // Prepare the analysis prompt
        String prompt = createAnalysisPrompt(entity.getName(), entity.getLastAnalyzedFrames());

        try {
            // Create a simplified JSON manually since we removed Moshi
            String json = createGeminiRequestJson(prompt, temperature, maxTokens);

            String url = GEMINI_API_URL + model + ":generateContent?key=" + apiKey;
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            MindAI.getInstance().getHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Handle failure silently
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            return;
                        }
                        
                        String bodyString = responseBody.string();
                        double probability = parseCheatProbabilityFromJson(bodyString);
                        
                        Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> {
                            handleAnalysisResult(entity, probability);
                        });
                    } catch (Exception ignored) {
                        // Handle parsing errors silently
                    }
                }
            });

        } catch (Exception e) {
            // Handle errors silently
        }
    }

    private static String createGeminiRequestJson(String prompt, double temperature, int maxTokens) {
        // Manually create JSON to avoid Moshi dependency issues
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"generationConfig\":{");
        json.append("\"temperature\":").append(temperature).append(",");
        json.append("\"maxOutputTokens\":").append(maxTokens);
        json.append("},");
        json.append("\"contents\":[{");
        json.append("\"parts\":[{");
        json.append("\"text\":\"").append(prompt.replace("\"", "\\\"")).append("\"");
        json.append("}]");
        json.append("}]");
        json.append("}");
        return json.toString();
    }

    private static String createAnalysisPrompt(String playerName, List<Frame> frames) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following Minecraft player movement data for potential cheating behavior. ");
        prompt.append("Player: ").append(playerName).append("\\n");
        prompt.append("Movement frames (yaw_delta, pitch_delta):\\n");
        
        // Convert frames to a readable format
        String frameData = frames.stream()
                .map(frame -> String.format("(%.2f, %.2f)", frame.getX(), frame.getY()))
                .collect(Collectors.joining(", "));
        
        prompt.append(frameData).append("\\n\\n");
        prompt.append("Based on this movement data, analyze for signs of aimbot, killaura, or other cheating software. ");
        prompt.append("Look for unnatural movement patterns, perfect accuracy, or robotic behavior. ");
        prompt.append("Respond with only a probability between 0.0 and 1.0, where 0.0 means definitely not cheating ");
        prompt.append("and 1.0 means definitely cheating. Format: PROBABILITY: X.XX");
        
        return prompt.toString();
    }

    private static double parseCheatProbabilityFromJson(String jsonResponse) {
        try {
            // Simple JSON parsing without external libraries
            // Look for text content in the response
            int textIndex = jsonResponse.indexOf("\"text\":");
            if (textIndex != -1) {
                int startQuote = jsonResponse.indexOf("\"", textIndex + 7);
                int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                if (startQuote != -1 && endQuote != -1) {
                    String textContent = jsonResponse.substring(startQuote + 1, endQuote);
                    return parseCheatProbability(textContent);
                }
            }
        } catch (Exception e) {
            // Handle parsing errors silently
        }
        return 0.0;
    }

    private static double parseCheatProbability(String geminiResponse) {
        try {
            // Look for "PROBABILITY: X.XX" pattern in the response
            String[] lines = geminiResponse.split("\\\\n");
            for (String line : lines) {
                if (line.toUpperCase().contains("PROBABILITY:")) {
                    String probabilityStr = line.substring(line.indexOf(":") + 1).trim();
                    return Math.max(0.0, Math.min(1.0, Double.parseDouble(probabilityStr)));
                }
            }
            
            // Fallback: try to extract any decimal number between 0 and 1
            String[] words = geminiResponse.split("\\\\s+");
            for (String word : words) {
                try {
                    double value = Double.parseDouble(word);
                    if (value >= 0.0 && value <= 1.0) {
                        return value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            // If parsing fails, return low probability
        }
        
        return 0.0; // Default to no detection if parsing fails
    }

    private static void handleAnalysisResult(PlayerEntity entity, double probability) {
        MindAI plugin = MindAI.getInstance();
        double classificationThreshold = plugin.getConfig().getDouble("ml-check.classification-threshold");

        if (probability <= classificationThreshold) {
            return;
        }

        plugin.getViolationManager().handleViolation(entity, probability);
    }
}
