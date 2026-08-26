package com.datagov.scrapper.controller;

import com.datagov.scrapper.dto.TestApiRequestDto;
import com.datagov.scrapper.dto.TestApiResponseDto;
import com.datagov.scrapper.model.ApiResourceEntity;
import com.datagov.scrapper.repository.ApiResourceRepository;
import com.datagov.scrapper.service.ApiTesterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiExplorerRestController {

    private final ApiResourceRepository apiResourceRepository;
    private final ApiTesterService apiTesterService;
    private final ObjectMapper objectMapper;
    private final com.datagov.scrapper.service.IndexSearchService indexSearchService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExplorerRestController.class);

    private volatile long cachedTotalCount = -1;
    private volatile long lastCountCheckTime = 0;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> sectorCountCache = new java.util.concurrent.ConcurrentHashMap<>();

    private long getSectorCountCached(String sector) {
        return sectorCountCache.computeIfAbsent(sector.toLowerCase().trim(), s -> apiResourceRepository.countBySector(sector));
    }

    private long getTotalCountCached() {
        long now = System.currentTimeMillis();
        if (cachedTotalCount == -1 || now - lastCountCheckTime > 15000) {
            long cStart = System.currentTimeMillis();
            cachedTotalCount = apiResourceRepository.count();
            lastCountCheckTime = now;
            log.info("[TIMER] 📊 Count Query refreshed: {} total records in {} ms", cachedTotalCount, (System.currentTimeMillis() - cStart));
        }
        return cachedTotalCount;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> listResources(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String orgType,
            @RequestParam(required = false) String year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "27") int size,
            @RequestParam(defaultValue = "id,desc") String sort
    ) {
        long reqStartTime = System.currentTimeMillis();

        if (size <= 0) size = 27;
        if (page < 0) page = 0;

        String[] sortParts = sort.split(",");
        String sortProp = sortParts[0];
        Sort.Direction sortDir = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;

        boolean hasSearch = (search != null && !search.isBlank());
        boolean hasState = (state != null && !state.isBlank() && !"ALL".equalsIgnoreCase(state));
        boolean hasSector = (sector != null && !sector.isBlank() && !"ALL".equalsIgnoreCase(sector));
        boolean hasOrgType = (orgType != null && !orgType.isBlank() && !"ALL".equalsIgnoreCase(orgType));
        boolean hasYear = (year != null && !year.isBlank() && !"ALL".equalsIgnoreCase(year));

        log.info("[TIMER] ⏱️ Request: page={}, size={}, search='{}', state='{}', sector='{}', orgType='{}', year='{}'",
                page, size, search, state, sector, orgType, year);

        List<Long> ids = null;
        long totalElements = 0;

        int offset = page * size;
        long qStart = System.currentTimeMillis();

        if (hasSearch || hasState || hasSector || hasOrgType || hasYear) {
            String s = hasSearch ? search.trim() : null;
            String st = hasState ? state.trim() : null;
            String sec = hasSector ? sector.trim() : null;
            String ot = hasOrgType ? orgType.trim() : null;
            String yr = hasYear ? year.trim() : null;

            // DSA Path 1: Fast Multi-Dimensional In-Memory Inverted Index (O(1) / O(N) in RAM)
            if (indexSearchService.isReady()) {
                com.datagov.scrapper.service.IndexSearchService.PagedIndexResult res = indexSearchService.searchAndFilter(s, st, sec, ot, yr, page, size);
                if (res != null) {
                    ids = res.ids();
                    totalElements = res.totalCount();
                    long qEnd = System.currentTimeMillis();
                    log.info("[TIMER] ⚡ In-Memory DSA Index Lookup: {} ids (took {} ms), Total Matched: {}", ids.size(), (qEnd - qStart), totalElements);
                }
            }

            // Fallback to DB if index is not ready
            if (ids == null) {
                if (hasSearch && hasSector) {
                    ids = apiResourceRepository.searchPagedIds(s, sec, size, offset);
                    totalElements = apiResourceRepository.countFiltered(s, sec);
                } else if (hasSector) {
                    ids = apiResourceRepository.findPagedIdsBySector(sec, size, offset);
                    totalElements = getSectorCountCached(sec);
                } else {
                    ids = apiResourceRepository.searchPagedIds(s, null, size, offset);
                    totalElements = apiResourceRepository.countFiltered(s, null);
                }
                long qEnd = System.currentTimeMillis();
                log.info("[TIMER] 🔍 DB Fallback Query: {} ids (took {} ms)", ids.size(), (qEnd - qStart));
            }
        } else {
            Long maxId = apiResourceRepository.findMaxId();
            if (maxId == null || maxId <= 0) {
                ids = Collections.emptyList();
            } else {
                long targetMax = maxId - offset;
                long targetMin = Math.max(1, targetMax - size + 1);
                ids = apiResourceRepository.findIdsInRange(targetMax, targetMin, size);
                if (ids.size() < size && targetMax > 0) {
                    ids = apiResourceRepository.findIdsFromMax(targetMax, size);
                }
            }
            long qEnd = System.currentTimeMillis();
            totalElements = getTotalCountCached();
            log.info("[TIMER] ⚡ Keyset B-Tree Range lookup: {} ids (took {} ms)", ids.size(), (qEnd - qStart));
        }

        List<ApiResourceEntity> content;
        if (ids.isEmpty()) {
            content = Collections.emptyList();
        } else {
            long fetchStart = System.currentTimeMillis();
            List<ApiResourceEntity> rawList = apiResourceRepository.findAllByIdInList(ids);
            Map<Long, ApiResourceEntity> map = rawList.stream().collect(Collectors.toMap(ApiResourceEntity::getId, e -> e, (a, b) -> a));
            content = ids.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
            log.info("[TIMER] 📦 Hydrated {} full entity records in {} ms", content.size(), (System.currentTimeMillis() - fetchStart));
        }

        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        long totalReqTime = System.currentTimeMillis() - reqStartTime;
        log.info("[TIMER] 🚀 Complete API response prepared in {} ms (Total elements: {}, Total pages: {})", totalReqTime, totalElements, totalPages);

        return ResponseEntity.ok(Map.of(
                "content", content,
                "page", page,
                "size", size,
                "totalElements", totalElements,
                "totalPages", totalPages,
                "first", page == 0,
                "last", page >= totalPages - 1,
                "executionTimeMs", totalReqTime
        ));
    }

    @GetMapping("/find/auto-discover")
    public ResponseEntity<?> autoDiscover(@RequestParam String query) {
        try {
            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
            }

            String url = "https://api.data.gov.in/lists?format=json&notfilters[source]=visualize.data.gov.in&filters[active]=1&limit=5&query=" +
                    java.net.URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(java.time.Duration.ofSeconds(12))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body());
                com.fasterxml.jackson.databind.JsonNode records = root.path("records");
                return ResponseEntity.ok(Map.of("records", records, "total", root.path("total").asInt(0)));
            } else {
                return ResponseEntity.status(resp.statusCode()).body(Map.of("error", "Remote catalog returned " + resp.statusCode()));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<?> getResourceById(@PathVariable Long id) {
        Optional<ApiResourceEntity> res = apiResourceRepository.findById(id);
        return res.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-uuid/{resourceId}")
    public ResponseEntity<?> getResourceByUuid(@PathVariable String resourceId) {
        Optional<ApiResourceEntity> res = apiResourceRepository.findByResourceId(resourceId);
        return res.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportToJson() {
        try {
            List<ApiResourceEntity> allRecords = apiResourceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(allRecords);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data_gov_apis.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(("Error generating JSON: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportToCsv() {
        try {
            List<ApiResourceEntity> allRecords = apiResourceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8);

            // CSV Header
            writer.println("ID,Resource_UUID,Title,Description,API_Endpoint,Sectors,Organizations,Created_Date,Updated_Date");

            for (ApiResourceEntity r : allRecords) {
                writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
                        escapeCsv(r.getId() != null ? r.getId().toString() : ""),
                        escapeCsv(r.getResourceId()),
                        escapeCsv(r.getTitle()),
                        escapeCsv(r.getDescription()),
                        escapeCsv(r.getApiUrl()),
                        escapeCsv(r.getSectors()),
                        escapeCsv(r.getOrganizations()),
                        escapeCsv(r.getCreatedDate()),
                        escapeCsv(r.getUpdatedDate())
                ));
            }
            writer.flush();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data_gov_apis.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(("Error generating CSV: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ");
    }

    @PostMapping("/test-call")
    public ResponseEntity<TestApiResponseDto> testApi(@RequestBody TestApiRequestDto request) {
        TestApiResponseDto response = apiTesterService.executeTestCall(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-curl")
    public ResponseEntity<Map<String, String>> generateCurl(@RequestBody TestApiRequestDto request) {
        String curl = apiTesterService.buildCurlCommand(request);
        String url = apiTesterService.buildFullUrl(request);
        return ResponseEntity.ok(Map.of("curl", curl, "url", url));
    }
}
