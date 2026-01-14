package com.dto;

import java.util.Map;

import lombok.Data;

@Data
public class UserTaskStatsDto {
    private Long userId;
    private String username;
    private int totalTasks;
    private int pendingTasks; // TO_DO, IN_PROGRESS, REVIEW
    private int completedTasks;
    private Map<String, Integer> statusBreakdown;
}
