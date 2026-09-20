package com.kabadiwala.backend.classification;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TaxonomyMappingService {

    private final Map<String, String> mlToWorkerCategoryMap = new HashMap<>();

    public TaxonomyMappingService() {
        // Default adapter mapping from current ML classes to 14 worker-facing categories
        mlToWorkerCategoryMap.put("PCB", "PCB");
        mlToWorkerCategoryMap.put("BATTERY", "BATTERY");
        mlToWorkerCategoryMap.put("POWER-ADAPTER", "CHARGER_ADAPTER");
        mlToWorkerCategoryMap.put("POWER_ADAPTER", "CHARGER_ADAPTER");
        mlToWorkerCategoryMap.put("CHARGER", "CHARGER_ADAPTER");

        mlToWorkerCategoryMap.put("HDD", "STORAGE_DEVICE");
        mlToWorkerCategoryMap.put("SSD", "STORAGE_DEVICE");
        mlToWorkerCategoryMap.put("HARD-DRIVE", "STORAGE_DEVICE");

        mlToWorkerCategoryMap.put("SMARTPHONE", "PHONE_SMALL_ELECTRONICS");
        mlToWorkerCategoryMap.put("BAR-PHONE", "PHONE_SMALL_ELECTRONICS");
        mlToWorkerCategoryMap.put("PHONE", "PHONE_SMALL_ELECTRONICS");

        mlToWorkerCategoryMap.put("LAPTOP", "OTHER_EWASTE");
        mlToWorkerCategoryMap.put("COMPUTER-KEYBOARD", "PLASTIC_EWASTE");
        mlToWorkerCategoryMap.put("COMPUTER-MOUSE", "PLASTIC_EWASTE");
        mlToWorkerCategoryMap.put("ROUTER", "OTHER_EWASTE");

        mlToWorkerCategoryMap.put("CRT-MONITOR", "DISPLAY_SCREEN");
        mlToWorkerCategoryMap.put("CRT-TV", "DISPLAY_SCREEN");
        mlToWorkerCategoryMap.put("FLAT-PANEL-MONITOR", "DISPLAY_SCREEN");
        mlToWorkerCategoryMap.put("FLAT-PANEL-TV", "DISPLAY_SCREEN");
        mlToWorkerCategoryMap.put("DISPLAY", "DISPLAY_SCREEN");

        mlToWorkerCategoryMap.put("CABLE", "CABLE_WIRE");
        mlToWorkerCategoryMap.put("WIRE", "CABLE_WIRE");

        mlToWorkerCategoryMap.put("NON-E-WASTE", "NON_EWASTE");
        mlToWorkerCategoryMap.put("NON_E_WASTE", "NON_EWASTE");
        mlToWorkerCategoryMap.put("NOT-E-WASTE", "NON_EWASTE");
    }

    /**
     * Maps an arbitrary ML prediction class string to a suggested worker-facing category code.
     */
    public String suggestWorkerCategory(String mlPredictedClass) {
        if (mlPredictedClass == null || mlPredictedClass.isBlank()) {
            return "OTHER_EWASTE";
        }

        String normalized = mlPredictedClass.trim().toUpperCase().replace("_", "-");
        if (mlToWorkerCategoryMap.containsKey(normalized)) {
            return mlToWorkerCategoryMap.get(normalized);
        }

        String underNormalized = mlPredictedClass.trim().toUpperCase().replace("-", "_");
        if (mlToWorkerCategoryMap.containsKey(underNormalized)) {
            return mlToWorkerCategoryMap.get(underNormalized);
        }

        return "OTHER_EWASTE";
    }
}
