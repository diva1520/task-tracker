package com.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeeklyLogResponse {
    private LocalDate weekStartDate;
    private List<TaskLogResponseDto> taskLogs;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskLogResponseDto {
        private Long taskId;
        private String taskTitle;
        private String status;
        private List<DailyEntryResponseDto> entries;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyEntryResponseDto {
        private LocalDate date;
        private Double hours;
        private String comment;
    }
}
