package com.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dto.TaskResponse;
import com.entity.Task;
import com.repo.TaskRepository;

import jakarta.validation.constraints.NotNull;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponse> getTasks(
    		@NotNull @NotNull LocalDate fromDate,
    		@NotNull @NotNull LocalDate toDate,
            List<Long> userIds
    ) {

        List<Task> tasks;

        if (userIds == null || userIds.isEmpty()) {
             tasks = taskRepository
                    .findByCreatedAtBetween(fromDate, toDate);
        } else {
             tasks = taskRepository
                    .findByUserIdInAndCreatedAtBetween(
                            userIds, fromDate, toDate);
        }

        return tasks.stream()
                .map(TaskResponse::fromEntity)
                .toList();
    }
    
    public List<TaskResponse> getTasks() {

        List<Task> tasks= taskRepository.findAll();

       
        return tasks.stream()
                .map(TaskResponse::fromEntity)
                .toList();
    }
}
