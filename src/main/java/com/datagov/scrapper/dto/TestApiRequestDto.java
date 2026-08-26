package com.datagov.scrapper.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestApiRequestDto {
    private String resourceId;
    private String apiUrl;
    private String apiKey;
    private String format;       // json, xml, csv
    private Integer offset;
    private Integer limit;
    private Map<String, String> customFilters;
    private Map<String, String> customHeaders;
}
