package com.datagov.scrapper.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScraperStatusDto {
    private String state; // IDLE, RUNNING, PAUSED, STOPPED, COMPLETED, ERROR
    private int totalTarget;
    private int totalFoundOnServer;
    private int totalScrapedInCurrentJob;
    private int totalSavedInDb;
    private int currentOffset;
    private int progressPercent;
    private double currentSpeedReqPerMin;
    private String currentAction;
    private String message;
    private List<String> recentLogs;
    private ScraperRequestDto activeConfig;
}
