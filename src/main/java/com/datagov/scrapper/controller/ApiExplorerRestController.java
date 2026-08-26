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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExplorerRestController.class);

    private volatile long cachedTotalCount = -1;
    private volatile long lastCountCheckTime = 0;

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
            @RequestParam(required = false) String sector,
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
        boolean hasSector = (sector != null && !sector.isBlank() && !"ALL".equalsIgnoreCase(sector));

        log.info("[TIMER] ⏱️ Request received: page={}, size={}, search='{}', sector='{}'", page, size, search, sector);

        List<Long> ids;
        long totalElements;

        int offset = page * size;
        long qStart = System.currentTimeMillis();

        if (hasSearch || hasSector) {
            String s = hasSearch ? search.trim() : null;
            String sec = hasSector ? sector.trim() : null;
            ids = apiResourceRepository.searchPagedIds(s, sec, size, offset);
            long qEnd = System.currentTimeMillis();
            totalElements = apiResourceRepository.countFiltered(s, sec);
            long cEnd = System.currentTimeMillis();
            log.info("[TIMER] 🔍 Filtered IDs lookup: {} ids (took {} ms), count query (took {} ms)", ids.size(), (qEnd - qStart), (cEnd - qEnd));
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
