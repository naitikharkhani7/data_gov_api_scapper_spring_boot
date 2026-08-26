package com.datagov.scrapper.controller;

import com.datagov.scrapper.dto.ScraperRequestDto;
import com.datagov.scrapper.dto.ScraperStatusDto;
import com.datagov.scrapper.dto.SectorDto;
import com.datagov.scrapper.service.DataGovScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScraperApiController {

    private final DataGovScraperService scraperService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startScraper(@RequestBody(required = false) ScraperRequestDto request) {
        if (request == null) request = new ScraperRequestDto();
        boolean started = scraperService.startScraping(request);
        return ResponseEntity.ok(Map.of(
                "success", started,
                "message", started ? "Scraper job started successfully" : "Scraper is already running",
                "status", scraperService.getStatus()
        ));
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseScraper() {
        scraperService.pauseScraping();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Scraper paused",
                "status", scraperService.getStatus()
        ));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeScraper() {
        scraperService.resumeScraping();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Scraper resumed",
                "status", scraperService.getStatus()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopScraper() {
        scraperService.stopScraping();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Scraper stopped",
                "status", scraperService.getStatus()
        ));
    }

    @PostMapping("/restart")
    public ResponseEntity<Map<String, Object>> restartScraper(@RequestBody(required = false) ScraperRequestDto request) {
        boolean restarted = scraperService.restartScraping(request);
        return ResponseEntity.ok(Map.of(
                "success", restarted,
                "message", "Scraper restarted",
                "status", scraperService.getStatus()
        ));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearData() {
        scraperService.clearAllData();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Database and logs cleared successfully",
                "status", scraperService.getStatus()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<ScraperStatusDto> getStatus() {
        return ResponseEntity.ok(scraperService.getStatus());
    }

    @GetMapping("/sectors")
    public ResponseEntity<List<SectorDto>> getSectors() {
        return ResponseEntity.ok(scraperService.getSectors());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress() {
        SseEmitter emitter = new SseEmitter(0L); // Infinite timeout
        scraperService.registerEmitter(emitter);
        return emitter;
    }
}
