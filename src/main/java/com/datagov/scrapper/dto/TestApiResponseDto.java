package com.datagov.scrapper.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestApiResponseDto {
    private boolean success;
    private int statusCode;
    private long executionTimeMs;
    private String rawUrl;
    private String curlCommand;
    private String contentType;
    private String responseBody;
    private Map<String, String> responseHeaders;
    private String errorMessage;
}
