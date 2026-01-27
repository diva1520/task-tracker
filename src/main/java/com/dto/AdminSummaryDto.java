package com.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminSummaryDto {
    private long totalUsers;
    private long activeUsers;
    private long usersWithoutTasks;

    private List<Long> totalUserIds;
    private List<Long> activeUserIds;
    private List<Long> noTaskUserIds;

    // Task Stats
    private long totalTasks;
    private long todoTasks;
    private long inProgressTasks;
    private long reviewTasks;
    private long completedTasks;

    // Leave Stats

    // User IDs filtered by task status
    private List<Long> todoUserIds;
    private List<Long> inProgressUserIds;
    private List<Long> reviewUserIds;
    private List<Long> completedUserIds;

    private long pendingLeaveRequests;
}
