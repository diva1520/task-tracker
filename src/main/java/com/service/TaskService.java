package com.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.dto.TaskRequest;
import com.dto.TaskResponse;
import com.entity.Status;
import com.entity.Task;
import com.entity.TaskDetail;
import com.entity.User;
import com.repo.TaskDetailRepository;
import com.repo.TaskRepository;
import com.repo.UserRepository;
import com.security.CustomUserDetails;
import com.util.JwtUtil;

import jakarta.validation.constraints.NotNull;

@Service
public class TaskService {

	private final TaskRepository taskRepository;

	private final JwtUtil jwtUtil;

	private final UserRepository userRepository;

	private final TaskDetailRepository taskDetailRepo;

	public TaskService(TaskRepository taskRepository, JwtUtil jwtUtil, UserRepository userRepository,
			TaskDetailRepository taskDetailRepo) {
		this.taskRepository = taskRepository;
		this.jwtUtil = jwtUtil;
		this.userRepository = userRepository;
		this.taskDetailRepo = taskDetailRepo;
	}

	public List<TaskResponse> getTasks(@NotNull @NotNull LocalDate fromDate, @NotNull @NotNull LocalDate toDate,
			List<Long> userIds) {

		List<Task> tasks;

		if (userIds == null || userIds.isEmpty()) {
			tasks = taskRepository.findByCreatedAtBetween(fromDate, toDate);
		} else {
			tasks = taskRepository.findByUserIdInAndCreatedAtBetween(userIds, fromDate, toDate);
		}

		return tasks.stream().map(TaskResponse::fromEntity).toList();
	}

	public List<TaskResponse> getTasks() {

		List<Task> tasks = taskRepository.findAll();

		return tasks.stream().map(TaskResponse::fromEntity).toList();
	}

	public ResponseEntity<?> addTask(String authHeader, TaskRequest request) {
		String token = authHeader.substring(7);
		Long userId = jwtUtil.extractUserId(token);

		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		Task task = new Task();
		task.setTitle(request.getTitle());
		task.setDescription(request.getDescription());
		task.setCreatedAt(LocalDate.now());
		task.setUser(user);

		// 🔥 IMPORTANT DEFAULTS
		task.setStatus(Status.TO_DO); // ← THIS FIXES NULL
		task.setDueDate(request.getDueDate()); // if coming from UI

		if (taskRepository.save(task) != null) {
			return ResponseEntity.ok("Task added successfully");
		} else {
			return ResponseEntity.status(500).body("Failed to add task");
		}
	}

	public ResponseEntity<?> editTaskDetails(Long taskId, String authHeader, TaskRequest request) {
		String token = authHeader.substring(7);
		Long loggedInUserId = jwtUtil.extractUserId(token);

		Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));

		if (!task.getUser().getId().equals(loggedInUserId)) {
			return ResponseEntity.status(403).body("You are not allowed to edit this task");
		}

		if (request.getTitle() != null) {
			task.setTitle(request.getTitle());
		}

		if (request.getDescription() != null) {
			task.setDescription(request.getDescription());
		}

		if (request.getStatus() != null) {

			Status oldStatus = task.getStatus();
			Status newStatus = request.getStatus();

			// TO_DO → IN_PROGRESS
			if (oldStatus == Status.TO_DO && newStatus == Status.IN_PROGRESS) {

				TaskDetail detail = new TaskDetail();
				detail.setTask(task);
				detail.setStatus(Status.IN_PROGRESS);
				detail.setStartedAt(LocalDate.now());
				taskDetailRepo.save(detail);

				task.setStatus(Status.IN_PROGRESS);
			}

			// IN_PROGRESS → COMPLETED
			else if (oldStatus == Status.IN_PROGRESS && newStatus == Status.COMPLETED) {

				TaskDetail last = taskDetailRepo.findLatestInProgress(task.getId());

				if (last == null) {
					return ResponseEntity.badRequest().body("Task not in IN_PROGRESS state");
				}

				LocalDate completedTime = LocalDate.now();
				last.setEndedAt(completedTime);
				last.setStatus(Status.COMPLETED);
				taskDetailRepo.save(last);

				// worked time
				Duration worked = Duration.between(last.getStartedAt(), last.getEndedAt());

				task.setTotalWorkedMinutes(worked.toMinutes());
				task.setCompletedAt(completedTime);

				// 🔥 DUE DATE CHECK
				if (task.getDueDate() != null) {

					if (!completedTime.isAfter(task.getDueDate())) {
						task.setCompletedOnTime(true);
						task.setDelayMinutes(0L);
					} else {
						task.setCompletedOnTime(false);

						Duration delay = Duration.between(task.getDueDate(), completedTime);
						task.setDelayMinutes(delay.toMinutes());
					}
				}

				task.setStatus(Status.COMPLETED);
			}

			// direct TO_DO
			else if (newStatus == Status.TO_DO) {
				task.setStatus(Status.TO_DO);
			}
		}
		if (task.getStatus() == Status.COMPLETED && request.getStatus() == Status.IN_PROGRESS) {
			return ResponseEntity.ok("This Task Already Completed");
		}

		if (taskRepository.save(task) != null) {
			return ResponseEntity.ok("Task updated successfully");
		} else {
			return ResponseEntity.status(500).body("Failed to update task");
		}
	}

	public List<TaskResponse> viewTasks(Authentication authentication) {
		CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

		List<Task> tasks = taskRepository.fetchTasks(user.getUserId());

		List<TaskResponse> taskResponses = mapToTaskResponses(tasks);

		System.out.println("Tasks for user " + user.getUsername() + ": " + taskResponses);

		return taskResponses;
	}

	private List<TaskResponse> mapToTaskResponses(List<Task> tasks) {
		List<TaskResponse> tsk = new java.util.ArrayList<>();
		for (Task task : tasks) {
			tsk.add(new TaskResponse(task.getId(), task.getUser().getUsername(), task.getUser().getId(),
					task.getTitle(), task.getDescription(), task.getCreatedAt(), task.getStatus(),
					task.getCompletedAt(), task.getDueDate()));
		}

		return tsk;
	}
}
