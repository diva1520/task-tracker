package com.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class WorkLogRequest {
    private Long taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String comment;
    private com.entity.Status status;
}
