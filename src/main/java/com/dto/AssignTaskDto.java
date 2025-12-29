package com.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignTaskDto {
	
    private Long userId;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    
}
