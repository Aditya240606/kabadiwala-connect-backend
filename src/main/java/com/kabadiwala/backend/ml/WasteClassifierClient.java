package com.kabadiwala.backend.ml;

public interface WasteClassifierClient {
    MlPredictionResponse predict(byte[] imageBytes, String filename, String contentType);

    default MlPredictionResponse predictWithUrl(String imageUrl) {
        return MlPredictionResponse.fallbackManual();
    }
}
