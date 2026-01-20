package com.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class WeeklyLogRequest {
    private LocalDate weekStartDate;
    private List<TaskLogDto> taskLogs;

    @Data
    public static class TaskLogDto {
        private Long taskId;
        private List<DailyEntryDto> entries;
    }

    @Data
    public static class DailyEntryDto {
        private LocalDate date;
        private Double hours;
        private String comment; // Optional: Daily comment
    }
}
