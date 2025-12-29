package com.dto;


import java.time.LocalDate;
import java.time.LocalDateTime;

import com.entity.Status;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskRequest {

    private String title;
    private String description;
    private LocalDate dueDate;
    private Status status;
   

}
