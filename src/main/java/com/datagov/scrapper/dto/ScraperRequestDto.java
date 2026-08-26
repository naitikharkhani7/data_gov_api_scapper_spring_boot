package com.datagov.scrapper.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScraperRequestDto {
    private Integer totalLimit;        // e.g. 50, 100, 500, null for all
    private Integer batchSize;         // e.g. 10, 20, 50, 100 (default 10)
    private Integer rateLimitPerMin;   // e.g. 30, 60, 120 (default 30)
    private Integer customDelayMs;     // optional custom delay between batches in ms
    private String sector;             // sector filter (optional)
    private String searchQuery;        // search query (optional)
    private String sortBy;             // created, updated, desc/asc (default created:desc)
    private Boolean fetchSwagger;      // whether to also fetch Swagger schema for each API
}
