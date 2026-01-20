package com.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class WorkLogDto {
    private Long id;
    private String taskTitle;
    private Long taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMinutes;
    private String comment;
}
