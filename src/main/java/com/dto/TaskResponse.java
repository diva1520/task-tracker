package com.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.entity.Task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskResponse {

    private Long id;
    private String userName;
    private Long userId;
    private String title;
    private String description;
    private LocalDate createdAt;
    private com.entity.Status status;
    private LocalDate completedAt;
    private LocalDate dueDate;
    private String comment;
    private boolean reassigned;

    public static TaskResponse fromEntity(Task task) {
        TaskResponse dto = new TaskResponse();
        dto.id = task.getId();
        dto.title = task.getTitle();
        dto.description = task.getDescription();
        dto.createdAt = task.getCreatedAt();
        dto.userName = task.getUser().getUsername();
        dto.userId = task.getUser().getId();
        dto.status = task.getStatus();
        dto.completedAt = task.getCompletedAt();
        dto.dueDate = task.getDueDate();
        dto.comment = task.getComment();
        if (task.getHistory() != null) {
            dto.reassigned = task.getHistory().stream()
                    .anyMatch(td -> td.getStatus() == com.entity.Status.REASSIGN);
        }
        return dto;
    }
}
