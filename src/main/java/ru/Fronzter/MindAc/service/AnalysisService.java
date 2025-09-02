package ru.Fronzter.MindAc.service;

import ru.Fronzter.MindAc.entity.PlayerEntity;

public class AnalysisService {

    public static void analyze(final PlayerEntity entity) {
        // Delegate to GeminiService for AI analysis
        GeminiService.analyzeWithGemini(entity);
    }
}