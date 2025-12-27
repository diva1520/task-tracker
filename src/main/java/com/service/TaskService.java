package com.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dto.TaskResponse;
import com.entity.Task;
import com.repo.TaskRepository;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponse> getTasks(
            LocalDate fromDate,
            LocalDate toDate,
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
}
