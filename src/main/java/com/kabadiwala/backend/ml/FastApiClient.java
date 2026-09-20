package com.kabadiwala.backend.ml;

import com.kabadiwala.backend.common.MlServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class FastApiClient implements WasteClassifierClient {

    private static final Logger logger = LoggerFactory.getLogger(FastApiClient.class);

    private final RestClient restClient;
    private final String predictEndpoint;
    private final boolean mlEnabled;

    public FastApiClient(
            @Value("${kabadiwala.ml.service-url:http://localhost:8000}") String serviceUrl,
            @Value("${kabadiwala.ml.predict-endpoint:/api/v1/predict}") String predictEndpoint,
            @Value("${kabadiwala.ml.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${kabadiwala.ml.read-timeout-ms:8000}") int readTimeout,
            @Value("${kabadiwala.ml.enabled:true}") boolean mlEnabled
    ) {
        this.predictEndpoint = predictEndpoint;
        this.mlEnabled = mlEnabled;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(serviceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public MlPredictionResponse predict(byte[] imageBytes, String filename, String contentType) {
        if (!mlEnabled) {
            logger.info("ML inference service is disabled by configuration; falling back to manual entry");
            return MlPredictionResponse.fallbackManual();
        }

        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Image payload must not be empty");
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "waste_sample.jpg";
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType != null ? contentType : MediaType.IMAGE_JPEG_VALUE));
            HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(resource, headers);
            body.add("image", fileEntity);

            logger.debug("Sending prediction request to FastAPI ML endpoint: {}", predictEndpoint);
            MlPredictionResponse response = restClient.post()
                    .uri(predictEndpoint)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(MlPredictionResponse.class);

            if (response == null) {
                throw new MlServiceException("FastAPI ML service returned empty prediction response");
            }

            logger.info("FastAPI inference successful: predictedClass={}, confidence={}, model={}:{}",
                    response.predictedClass(), response.confidence(), response.modelName(), response.modelVersion());
            return response;

        } catch (Exception ex) {
            logger.error("FastAPI ML prediction request failed: {}", ex.getMessage());
            throw new MlServiceException("Failed to obtain prediction from ML inference service: " + ex.getMessage(), ex);
        }
    }

    @Override
    public MlPredictionResponse predictWithUrl(String imageUrl) {
        if (!mlEnabled) {
            logger.info("ML inference service is disabled by configuration; falling back to manual entry");
            return MlPredictionResponse.fallbackManual();
        }

        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("Image URL must not be empty");
        }

        try {
            logger.debug("Sending JSON prediction request to FastAPI ML endpoint: {}", predictEndpoint);
            java.util.Map<String, String> payload = java.util.Map.of("image_url", imageUrl);

            MlPredictionResponse response = restClient.post()
                    .uri(predictEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MlPredictionResponse.class);

            if (response == null) {
                throw new MlServiceException("FastAPI ML service returned empty prediction response");
            }

            logger.info("FastAPI JSON inference successful: predictedClass={}, confidence={}, model={}:{}",
                    response.predictedClass(), response.confidence(), response.modelName(), response.modelVersion());
            return response;

        } catch (Exception ex) {
            logger.error("FastAPI ML JSON prediction request failed: {}", ex.getMessage());
            throw new MlServiceException("Failed to obtain prediction from ML inference service: " + ex.getMessage(), ex);
        }
    }
}
