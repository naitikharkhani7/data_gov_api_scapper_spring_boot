package com.datagov.scrapper.service;

import com.datagov.scrapper.dto.TestApiRequestDto;
import com.datagov.scrapper.dto.TestApiResponseDto;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ApiTesterService {

    public static final String DEFAULT_SAMPLE_KEY = "579b464db66ec23bdd000001cdd3946e44ce4aad7209ff7b23ac571b";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String buildCurlCommand(TestApiRequestDto request) {
        String url = buildFullUrl(request);
        StringBuilder curl = new StringBuilder();
        curl.append("curl -X GET '").append(url).append("' \\\n");
        curl.append("  -H 'Accept: ");
        if ("xml".equalsIgnoreCase(request.getFormat())) {
            curl.append("application/xml");
        } else if ("csv".equalsIgnoreCase(request.getFormat())) {
            curl.append("text/csv");
        } else {
            curl.append("application/json");
        }
        curl.append("' \\\n");
        curl.append("  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'");

        if (request.getCustomHeaders() != null) {
            for (Map.Entry<String, String> header : request.getCustomHeaders().entrySet()) {
                if (header.getKey() != null && !header.getKey().isBlank()) {
                    curl.append(" \\\n  -H '").append(header.getKey()).append(": ").append(header.getValue()).append("'");
                }
            }
        }
        return curl.toString();
    }

    public String buildFullUrl(TestApiRequestDto request) {
        String resourceId = request.getResourceId();
        if (resourceId == null || resourceId.isBlank()) {
            resourceId = "14613c4e-5ab0-4705-b440-e4e49ae345de";
        }

        String apiKey = request.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = DEFAULT_SAMPLE_KEY;
        }

        String format = request.getFormat() != null ? request.getFormat().toLowerCase() : "json";
        int offset = request.getOffset() != null ? request.getOffset() : 0;
        int limit = request.getLimit() != null ? request.getLimit() : 10;

        StringBuilder url = new StringBuilder("https://api.data.gov.in/resource/");
        url.append(resourceId)
           .append("?api-key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8))
           .append("&format=").append(format)
           .append("&offset=").append(offset)
           .append("&limit=").append(limit);

        if (request.getCustomFilters() != null) {
            for (Map.Entry<String, String> entry : request.getCustomFilters().entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                    url.append("&filters[").append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                       .append("]=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                }
            }
        }
        return url.toString();
    }

    public TestApiResponseDto executeTestCall(TestApiRequestDto request) {
        String fullUrl = buildFullUrl(request);
        String curlCmd = buildCurlCommand(request);
        long start = System.currentTimeMillis();

        try {
            HttpRequest.Builder httpReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(20))
                    .GET();

            if ("xml".equalsIgnoreCase(request.getFormat())) {
                httpReqBuilder.header("Accept", "application/xml");
            } else if ("csv".equalsIgnoreCase(request.getFormat())) {
                httpReqBuilder.header("Accept", "text/csv");
            } else {
                httpReqBuilder.header("Accept", "application/json");
            }
            httpReqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            if (request.getCustomHeaders() != null) {
                for (Map.Entry<String, String> header : request.getCustomHeaders().entrySet()) {
                    if (header.getKey() != null && !header.getKey().isBlank()) {
                        httpReqBuilder.header(header.getKey(), header.getValue());
                    }
                }
            }

            HttpResponse<String> response = httpClient.send(httpReqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long executionTimeMs = System.currentTimeMillis() - start;

            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((k, v) -> {
                if (!v.isEmpty()) responseHeaders.put(k, v.get(0));
            });

            return TestApiResponseDto.builder()
                    .success(response.statusCode() >= 200 && response.statusCode() < 300)
                    .statusCode(response.statusCode())
                    .executionTimeMs(executionTimeMs)
                    .rawUrl(fullUrl)
                    .curlCommand(curlCmd)
                    .contentType(response.headers().firstValue("content-type").orElse("text/plain"))
                    .responseBody(response.body())
                    .responseHeaders(responseHeaders)
                    .build();

        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - start;
            return TestApiResponseDto.builder()
                    .success(false)
                    .statusCode(500)
                    .executionTimeMs(executionTimeMs)
                    .rawUrl(fullUrl)
                    .curlCommand(curlCmd)
                    .errorMessage("Request failed: " + e.getMessage())
                    .build();
        }
    }
}
