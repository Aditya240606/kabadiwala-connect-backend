package com.kabadiwala.backend.ml;

import com.kabadiwala.backend.common.MlServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FastApiClientTest {

    @Test
    @DisplayName("FastApiClient: When disabled, returns manual fallback without calling network")
    void testDisabledClientFallback() {
        FastApiClient client = new FastApiClient(
                "http://localhost:9999",
                "/api/v1/predict",
                1000,
                1000,
                false // disabled
        );

        MlPredictionResponse response = client.predict(new byte[]{1, 2}, "sample.jpg", "image/jpeg");

        assertNotNull(response);
        assertNull(response.predictedClass());
        assertEquals(BigDecimal.ZERO, response.confidence());
        assertEquals("manual-fallback", response.modelName());
        assertTrue(response.needsConfirmation());
    }

    @Test
    @DisplayName("FastApiClient: Empty image bytes throws IllegalArgumentException")
    void testEmptyPayloadThrows() {
        FastApiClient client = new FastApiClient(
                "http://localhost:9999",
                "/api/v1/predict",
                1000,
                1000,
                true
        );

        assertThrows(IllegalArgumentException.class, () ->
                client.predict(new byte[]{}, "test.jpg", "image/jpeg"));

        assertThrows(IllegalArgumentException.class, () ->
                client.predict(null, "test.jpg", "image/jpeg"));
    }

    @Test
    @DisplayName("FastApiClient: Unreachable ML service throws MlServiceException")
    void testUnreachableServiceThrowsMlServiceException() {
        // Point to an invalid local port that refuses connection immediately
        FastApiClient client = new FastApiClient(
                "http://localhost:59999",
                "/api/v1/predict",
                300,
                300,
                true
        );

        assertThrows(MlServiceException.class, () ->
                client.predict(new byte[]{1, 2, 3}, "test.jpg", "image/jpeg"));
    }

    @Test
    @DisplayName("FastApiClient: predictWithUrl when disabled returns manual fallback")
    void testDisabledClientPredictWithUrlFallback() {
        FastApiClient client = new FastApiClient(
                "http://localhost:9999",
                "/api/v1/predict",
                1000,
                1000,
                false
        );

        MlPredictionResponse response = client.predictWithUrl("https://example.com/lot.jpg");
        assertNotNull(response);
        assertNull(response.predictedClass());
        assertEquals("manual-fallback", response.modelName());
    }

    @Test
    @DisplayName("FastApiClient: Empty image URL throws IllegalArgumentException")
    void testEmptyImageUrlThrows() {
        FastApiClient client = new FastApiClient(
                "http://localhost:9999",
                "/api/v1/predict",
                1000,
                1000,
                true
        );

        assertThrows(IllegalArgumentException.class, () -> client.predictWithUrl(""));
        assertThrows(IllegalArgumentException.class, () -> client.predictWithUrl("   "));
        assertThrows(IllegalArgumentException.class, () -> client.predictWithUrl(null));
    }

    @Test
    @DisplayName("FastApiClient: Unreachable ML service throws MlServiceException on URL predict")
    void testUnreachableServicePredictWithUrlThrows() {
        FastApiClient client = new FastApiClient(
                "http://localhost:59999",
                "/api/v1/predict",
                300,
                300,
                true
        );

        assertThrows(MlServiceException.class, () ->
                client.predictWithUrl("https://example.com/lot.jpg"));
    }
}
