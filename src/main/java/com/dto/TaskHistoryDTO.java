package com.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskHistoryDTO {
    private String eventType; // "CREATED", "STATUS_CHANGE", "WORK_LOG", "REASSIGN"
    private String username;
    private String comment;
    private LocalDateTime timestamp;
    private String metadata; // e.g., "1h 30m" for work logs, or "TO_DO -> IN_PROGRESS" for status
}
