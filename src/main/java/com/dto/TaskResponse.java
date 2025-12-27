package com.dto;

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
    private String title;
    private String description;
    private LocalDateTime createdAt;

    
    public static TaskResponse fromEntity(Task task) {
        TaskResponse dto = new TaskResponse();
        dto.id = task.getId();
        dto.title = task.getTitle();
        dto.description = task.getDescription();
        dto.createdAt = task.getCreatedAt();
       // dto.userId = task.getUser().getId();
        dto.userName = task.getUser().getUsername();
        return dto;
    }
}
