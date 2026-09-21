package com.kabadiwala.backend.classification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaxonomyMappingServiceTest {

    private TaxonomyMappingService mappingService;

    private static final Set<String> CANONICAL_CATEGORIES = Set.of(
            "PCB",
            "CABLE_WIRE",
            "BATTERY",
            "MOTOR_MECHANICAL",
            "DISPLAY_SCREEN",
            "STORAGE_DEVICE",
            "CHARGER_ADAPTER",
            "PHONE_SMALL_ELECTRONICS",
            "ELECTRONIC_COMPONENTS",
            "PLASTIC_EWASTE",
            "METAL_SCRAP",
            "OTHER_EWASTE",
            "MIXED_EWASTE",
            "NON_EWASTE"
    );

    @BeforeEach
    void setUp() {
        mappingService = new TaxonomyMappingService();
    }

    @ParameterizedTest(name = "Model class ''{0}'' maps to category ''{1}''")
    @CsvSource({
            "PCB, PCB",
            "Battery, BATTERY",
            "Smartphone, PHONE_SMALL_ELECTRONICS",
            "Flat-Panel-Monitor, DISPLAY_SCREEN",
            "Flat-Panel-TV, DISPLAY_SCREEN",
            "CRT-Monitor, DISPLAY_SCREEN",
            "CRT-TV, DISPLAY_SCREEN",
            "HDD, STORAGE_DEVICE",
            "SSD, STORAGE_DEVICE",
            "Laptop, OTHER_EWASTE",
            "Computer-Keyboard, PLASTIC_EWASTE",
            "Computer-Mouse, PLASTIC_EWASTE",
            "Non-E-Waste, NON_EWASTE",
            "Power-Adapter, CHARGER_ADAPTER",
            "Charger, CHARGER_ADAPTER",
            "Cable, CABLE_WIRE",
            "Wire, CABLE_WIRE",
            "Router, OTHER_EWASTE"
    })
    @DisplayName("Verify ML model class mappings to canonical backend category codes")
    void testModelClassMappingToCanonicalTaxonomy(String modelClass, String expectedCategory) {
        String mappedCategory = mappingService.suggestWorkerCategory(modelClass);
        assertEquals(expectedCategory, mappedCategory);
        assertTrue(CANONICAL_CATEGORIES.contains(mappedCategory),
                "Mapped category must belong to the 14 canonical categories: " + mappedCategory);
    }

    @Test
    @DisplayName("Verify edge cases: null, empty, unknown class fallback to OTHER_EWASTE")
    void testEdgeCasesAndFallbacks() {
        assertEquals("OTHER_EWASTE", mappingService.suggestWorkerCategory(null));
        assertEquals("OTHER_EWASTE", mappingService.suggestWorkerCategory(""));
        assertEquals("OTHER_EWASTE", mappingService.suggestWorkerCategory("   "));
        assertEquals("OTHER_EWASTE", mappingService.suggestWorkerCategory("UnknownDeviceXYZ"));
    }

    @Test
    @DisplayName("Verify case-insensitivity and delimiter flexibility (hyphen vs underscore)")
    void testCaseAndDelimiterFlexibility() {
        assertEquals("CHARGER_ADAPTER", mappingService.suggestWorkerCategory("power_adapter"));
        assertEquals("CHARGER_ADAPTER", mappingService.suggestWorkerCategory("POWER-ADAPTER"));
        assertEquals("PLASTIC_EWASTE", mappingService.suggestWorkerCategory("computer_mouse"));
        assertEquals("PLASTIC_EWASTE", mappingService.suggestWorkerCategory("computer-mouse"));
        assertEquals("NON_EWASTE", mappingService.suggestWorkerCategory("non_e_waste"));
        assertEquals("NON_EWASTE", mappingService.suggestWorkerCategory("not-e-waste"));
    }
}
