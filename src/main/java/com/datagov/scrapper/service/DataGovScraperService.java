package com.datagov.scrapper.service;

import com.datagov.scrapper.dto.ScraperRequestDto;
import com.datagov.scrapper.dto.ScraperStatusDto;
import com.datagov.scrapper.dto.SectorDto;
import com.datagov.scrapper.model.ApiResourceEntity;
import com.datagov.scrapper.repository.ApiResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGovScraperService {

    private final ApiResourceRepository apiResourceRepository;
    private final ObjectMapper objectMapper;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<String> logHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_LOGS = 200;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    private volatile String currentState = "IDLE"; // IDLE, RUNNING, PAUSED, STOPPED, COMPLETED, ERROR
    private volatile ScraperRequestDto activeConfig = new ScraperRequestDto();
    private final AtomicInteger totalFoundOnServer = new AtomicInteger(0);
    private final AtomicInteger totalScrapedInCurrentJob = new AtomicInteger(0);
    private final AtomicInteger currentOffset = new AtomicInteger(0);
    private volatile String currentAction = "Ready to scrape";
    private volatile String lastErrorMessage = "";
    private volatile long jobStartTime = 0;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final List<SectorDto> cachedSectors = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        // Fetch sectors asynchronously on startup
        CompletableFuture.runAsync(this::refreshSectors);
    }

    public synchronized void registerEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Send initial status
        try {
            emitter.send(SseEmitter.event().name("status").data(getStatus()));
        } catch (Exception ignored) {}
    }

    public List<SectorDto> getSectors() {
        if (cachedSectors.isEmpty()) {
            refreshSectors();
        }
        return new ArrayList<>(cachedSectors);
    }

    public synchronized void refreshSectors() {
        try {
            logInfo("Fetching available sectors taxonomy from data.gov.in...");
            String url = "https://api.data.gov.in/lists?format=json&filters[source]=data.gov.in&filters[active]=1&aggs[0][name]=sector&aggs[0][size]=1000";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode buckets = root.path("aggregations").path("sector").path("buckets");
                List<SectorDto> list = new ArrayList<>();
                if (buckets.isArray()) {
                    for (JsonNode bucket : buckets) {
                        String name = bucket.path("key").asText();
                        long count = bucket.path("doc_count").asLong(0);
                        if (!name.isBlank()) {
                            list.add(new SectorDto(name, count));
                        }
                    }
                }
                cachedSectors.clear();
                cachedSectors.addAll(list);
                logInfo("Successfully loaded " + list.size() + " sectors from data.gov.in");
            }
        } catch (Exception e) {
            logError("Failed to fetch sectors: " + e.getMessage());
        }
    }

    public synchronized boolean startScraping(ScraperRequestDto request) {
        if (isRunning.get()) {
            logWarn("Scraper is already running!");
            return false;
        }

        // Apply defaults
        if (request.getBatchSize() == null || request.getBatchSize() <= 0) request.setBatchSize(10);
        if (request.getRateLimitPerMin() == null || request.getRateLimitPerMin() <= 0) request.setRateLimitPerMin(30);
        if (request.getFetchSwagger() == null) request.setFetchSwagger(false);

        this.activeConfig = request;
        this.isRunning.set(true);
        this.isPaused.set(false);
        this.currentState = "RUNNING";
        this.totalScrapedInCurrentJob.set(0);
        this.currentOffset.set(0);
        this.jobStartTime = System.currentTimeMillis();
        this.lastErrorMessage = "";

        logInfo("Started scraper job with Limit: " + (request.getTotalLimit() != null ? request.getTotalLimit() : "ALL") +
                ", Batch Size: " + request.getBatchSize() +
                ", Rate: " + request.getRateLimitPerMin() + " req/min" +
                (request.getSector() != null && !request.getSector().isBlank() ? ", Sector: " + request.getSector() : ""));

        broadcastStatus();

        CompletableFuture.runAsync(this::runScraperLoop);
        return true;
    }

    public synchronized void pauseScraping() {
        if (isRunning.get() && !isPaused.get()) {
            isPaused.set(true);
            currentState = "PAUSED";
            logInfo("Scraper paused by user at offset " + currentOffset.get());
            broadcastStatus();
        }
    }

    public synchronized void resumeScraping() {
        if (isRunning.get() && isPaused.get()) {
            isPaused.set(false);
            currentState = "RUNNING";
            logInfo("Scraper resumed by user from offset " + currentOffset.get());
            broadcastStatus();
        }
    }

    public synchronized void stopScraping() {
        if (isRunning.get()) {
            isRunning.set(false);
            isPaused.set(false);
            currentState = "STOPPED";
            currentAction = "Stopped by user";
            logInfo("Scraper stopped. Total APIs scraped in this job: " + totalScrapedInCurrentJob.get());
            broadcastStatus();
        }
    }

    public synchronized boolean restartScraping(ScraperRequestDto request) {
        stopScraping();
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {}
        return startScraping(request != null ? request : this.activeConfig);
    }

    public synchronized void clearAllData() {
        if (isRunning.get()) {
            stopScraping();
        }
        apiResourceRepository.deleteAll();
        logHistory.clear();
        totalScrapedInCurrentJob.set(0);
        currentOffset.set(0);
        currentState = "IDLE";
        currentAction = "Database cleared";
        logInfo("Cleared all scraped APIs from database and reset logs.");
        broadcastStatus();
    }

    private void runScraperLoop() {
        try {
            int targetLimit = activeConfig.getTotalLimit() != null && activeConfig.getTotalLimit() > 0
                    ? activeConfig.getTotalLimit() : Integer.MAX_VALUE;
            int batchSize = Math.min(activeConfig.getBatchSize(), 100);
            int rateLimitPerMin = activeConfig.getRateLimitPerMin() > 0 ? activeConfig.getRateLimitPerMin() : 30;
            int delayBetweenBatchesMs = activeConfig.getCustomDelayMs() != null && activeConfig.getCustomDelayMs() > 0
                    ? activeConfig.getCustomDelayMs()
                    : (int) Math.max(200, (60000.0 / rateLimitPerMin));

            int offset = 0;

            while (isRunning.get() && totalScrapedInCurrentJob.get() < targetLimit) {
                // Check for pause
                while (isPaused.get() && isRunning.get()) {
                    currentAction = "Paused at offset " + offset;
                    broadcastStatus();
                    Thread.sleep(500);
                }
                if (!isRunning.get()) break;

                int currentBatchLimit = Math.min(batchSize, targetLimit - totalScrapedInCurrentJob.get());
                currentAction = "Fetching batch at offset " + offset + " (limit: " + currentBatchLimit + ")...";
                broadcastStatus();

                String pageUrl = buildListUrl(offset, currentBatchLimit, activeConfig.getSector(), activeConfig.getSearchQuery());

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(pageUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Origin", "https://www.data.gov.in")
                        .header("Referer", "https://www.data.gov.in/")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (httpResponse.statusCode() != 200) {
                    logWarn("HTTP error " + httpResponse.statusCode() + " while fetching offset " + offset + ". Retrying after delay...");
                    Thread.sleep(3000);
                    continue;
                }

                JsonNode root = objectMapper.readTree(httpResponse.body());
                int totalServerCount = root.path("total").asInt(0);
                totalFoundOnServer.set(totalServerCount);

                JsonNode records = root.path("records");
                if (!records.isArray() || records.isEmpty()) {
                    logInfo("No more records found at offset " + offset + ". Scraping completed!");
                    break;
                }

                int savedInBatch = 0;
                for (JsonNode item : records) {
                    if (!isRunning.get()) break;
                    if (totalScrapedInCurrentJob.get() >= targetLimit) break;

                    ApiResourceEntity entity = parseAndBuildEntity(item);
                    if (entity != null) {
                        // Deep swagger fetch if requested
                        if (Boolean.TRUE.equals(activeConfig.getFetchSwagger())) {
                            fetchSwaggerForResource(entity);
                        }

                        // Save or update existing
                        Optional<ApiResourceEntity> existing = apiResourceRepository.findByResourceId(entity.getResourceId());
                        if (existing.isPresent()) {
                            ApiResourceEntity ex = existing.get();
                            entity.setId(ex.getId());
                        }
                        entity.setScrapedAt(LocalDateTime.now());
                        apiResourceRepository.save(entity);

                        totalScrapedInCurrentJob.incrementAndGet();
                        savedInBatch++;
                    }
                }

                offset += records.size();
                currentOffset.set(offset);

                logInfo("Scraped batch: +" + savedInBatch + " APIs (Total job: " + totalScrapedInCurrentJob.get() +
                        ", Target: " + (targetLimit == Integer.MAX_VALUE ? "ALL" : targetLimit) + ", Server total: " + totalServerCount + ")");
                broadcastStatus();

                if (records.size() < currentBatchLimit || offset >= totalServerCount) {
                    logInfo("Reached end of data.gov.in catalog records.");
                    break;
                }

                // Throttle delay
                Thread.sleep(delayBetweenBatchesMs);
            }

            if (isRunning.get()) {
                currentState = "COMPLETED";
                currentAction = "Completed successfully! " + totalScrapedInCurrentJob.get() + " APIs scraped.";
                logInfo("Finished job. Total scraped: " + totalScrapedInCurrentJob.get());
            }

        } catch (InterruptedException e) {
            logWarn("Scraper interrupted.");
            currentState = "STOPPED";
        } catch (Exception e) {
            logError("Scraper encountered error: " + e.getMessage());
            lastErrorMessage = e.getMessage();
            currentState = "ERROR";
        } finally {
            isRunning.set(false);
            isPaused.set(false);
            broadcastStatus();
        }
    }

    private void fetchSwaggerForResource(ApiResourceEntity entity) {
        try {
            String swaggerUrl = "https://www.data.gov.in/backend/dataapi/v1/swagger/" + entity.getResourceId();
            HttpRequest swaggerReq = HttpRequest.newBuilder()
                    .uri(URI.create(swaggerUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(swaggerReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                entity.setSwaggerJson(resp.body());
            }
        } catch (Exception ignored) {}
    }

    private ApiResourceEntity parseAndBuildEntity(JsonNode item) {
        try {
            String resourceId = item.path("index_name").asText();
            if (resourceId == null || resourceId.isBlank()) {
                resourceId = item.path("id").asText();
            }
            if (resourceId == null || resourceId.isBlank()) {
                return null;
            }

            String title = item.path("title").asText("");
            String desc = item.path("desc").asText("");
            String source = item.path("source").asText("data.gov.in");
            String orgType = item.path("org_type").asText("");

            String orgsJson = item.has("org") ? item.get("org").toString() : "[]";
            String sectorsJson = item.has("sector") ? item.get("sector").toString() : "[]";
            String fieldsJson = item.has("field") ? item.get("field").toString() : "[]";

            String active = item.path("active").asText("1");
            String visualizable = item.path("visualizable").asText("0");
            String catalogUuid = item.path("catalog_uuid").asText("");
            String createdDate = item.path("created_date").asText("");
            String updatedDate = item.path("updated_date").asText("");

            String apiUrl = "https://api.data.gov.in/resource/" + resourceId;

            return ApiResourceEntity.builder()
                    .resourceId(resourceId)
                    .title(title)
                    .description(desc)
                    .source(source)
                    .orgType(orgType)
                    .organizations(orgsJson)
                    .sectors(sectorsJson)
                    .fieldsJson(fieldsJson)
                    .apiUrl(apiUrl)
                    .active(active)
                    .visualizable(visualizable)
                    .catalogUuid(catalogUuid)
                    .createdDate(createdDate)
                    .updatedDate(updatedDate)
                    .build();

        } catch (Exception e) {
            logError("Error parsing item: " + e.getMessage());
            return null;
        }
    }

    private String buildListUrl(int offset, int limit, String sector, String search) {
        StringBuilder sb = new StringBuilder("https://api.data.gov.in/lists?format=json");
        sb.append("&notfilters[source]=visualize.data.gov.in");
        sb.append("&filters[active]=1");
        sb.append("&limit=").append(limit);
        sb.append("&offset=").append(offset);
        sb.append("&sort[created]=desc");

        if (sector != null && !sector.isBlank() && !"ALL".equalsIgnoreCase(sector)) {
            sb.append("&filters[sector]=").append(URLEncoder.encode(sector, StandardCharsets.UTF_8));
        }
        if (search != null && !search.isBlank()) {
            sb.append("&query=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public ScraperStatusDto getStatus() {
        int totalSaved = (int) apiResourceRepository.count();
        int target = activeConfig.getTotalLimit() != null && activeConfig.getTotalLimit() > 0 ? activeConfig.getTotalLimit() : totalFoundOnServer.get();
        int progress = 0;
        if (target > 0) {
            progress = (int) Math.min(100, Math.round(((double) totalScrapedInCurrentJob.get() / target) * 100.0));
        }

        double speedReqPerMin = 0;
        if (isRunning.get() && jobStartTime > 0) {
            long elapsedSec = (System.currentTimeMillis() - jobStartTime) / 1000;
            if (elapsedSec > 0) {
                speedReqPerMin = Math.round(((double) totalScrapedInCurrentJob.get() / elapsedSec) * 60.0 * 10.0) / 10.0;
            }
        }

        return ScraperStatusDto.builder()
                .state(currentState)
                .totalTarget(target)
                .totalFoundOnServer(totalFoundOnServer.get())
                .totalScrapedInCurrentJob(totalScrapedInCurrentJob.get())
                .totalSavedInDb(totalSaved)
                .currentOffset(currentOffset.get())
                .progressPercent(progress)
                .currentSpeedReqPerMin(speedReqPerMin)
                .currentAction(currentAction)
                .message(lastErrorMessage)
                .recentLogs(new ArrayList<>(logHistory))
                .activeConfig(activeConfig)
                .build();
    }

    public void logInfo(String msg) {
        String entry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] [INFO] " + msg;
        addLog(entry);
        log.info(msg);
    }

    public void logWarn(String msg) {
        String entry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] [WARN] " + msg;
        addLog(entry);
        log.warn(msg);
    }

    public void logError(String msg) {
        String entry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] [ERROR] " + msg;
        addLog(entry);
        log.error(msg);
    }

    private void addLog(String entry) {
        if (logHistory.size() >= MAX_LOGS) {
            logHistory.remove(0);
        }
        logHistory.add(entry);
        broadcastStatus();
    }

    private void broadcastStatus() {
        CompletableFuture.runAsync(() -> {
            try {
                ScraperStatusDto status = getStatus();
                List<SseEmitter> deadEmitters = new ArrayList<>();
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.send(SseEmitter.event().name("status").data(status));
                    } catch (Throwable e) {
                        deadEmitters.add(emitter);
                        try {
                            emitter.complete();
                        } catch (Throwable ignored) {}
                    }
                }
                if (!deadEmitters.isEmpty()) {
                    emitters.removeAll(deadEmitters);
                }
            } catch (Throwable ignored) {}
        });
    }
}
