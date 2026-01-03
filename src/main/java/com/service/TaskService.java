package com.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

		System.out.println(taskId);
		String token = authHeader.substring(7);
		Long loggedInUserId = jwtUtil.extractUserId(token);
		String role = jwtUtil.extractRole(token); // ROLE_USER / ROLE_ADMIN

		Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
		System.out.println(task.getStatus());

		// USER can edit only own task
		if ("ROLE_USER".equals(role) && !task.getUser().getId().equals(loggedInUserId)) {
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

			/* ================= USER FLOW ================= */
			if ("ROLE_USER".equals(role)) {

				// TO_DO → IN_PROGRESS
				if (oldStatus == Status.TO_DO && newStatus == Status.IN_PROGRESS) {

					TaskDetail detail = new TaskDetail();
					detail.setTask(task);
					detail.setStatus(Status.IN_PROGRESS);
					detail.setStartedAt(LocalDateTime.now());
					taskDetailRepo.save(detail);

					task.setStatus(Status.IN_PROGRESS);
				}

				// IN_PROGRESS → REVIEW
				else if (oldStatus == Status.IN_PROGRESS && newStatus == Status.REVIEW) {
					task.setStatus(Status.REVIEW);
				}

				// ❌ USER cannot complete
				else {
					return ResponseEntity.status(403).body("User not allowed to change to this status");
				}
			}

			/* ================= ADMIN FLOW ================= */
			else if ("ROLE_ADMIN".equals(role)) {

				// REVIEW → COMPLETED
				// REVIEW → COMPLETED
				if (oldStatus == Status.REVIEW && newStatus == Status.COMPLETED) {

					Optional<TaskDetail> in = taskDetailRepo.findByTask_Id(task.getId());

					TaskDetail inProgress;

					if (in.isPresent()) {
						inProgress = in.get();
					} else {
						inProgress = null;
					}

					if (inProgress == null || inProgress.getEndedAt() != null) {
						return ResponseEntity.badRequest().body("Task not in IN_PROGRESS state");
					}

					inProgress.setEndedAt(LocalDateTime.now());
					taskDetailRepo.save(inProgress);

					Duration worked = Duration.between(inProgress.getStartedAt(), inProgress.getEndedAt());

					long minutes = Math.max(1, worked.toMinutes());

					task.setTotalWorkedMinutes(minutes);
					task.setCompletedAt(LocalDate.now());
					task.setStatus(Status.COMPLETED);
				}

				else {
					return ResponseEntity.status(403).body("Admin not allowed to change to this status");
				}
			}
		}

		taskRepository.save(task);
		return ResponseEntity.ok("Task updated successfully");
	}

//	public ResponseEntity<?> editTaskDetails(Long taskId, String authHeader, TaskRequest request) {
//	    String token = authHeader.substring(7);
//	    Long loggedInUserId = jwtUtil.extractUserId(token);
//
//	    Task task = taskRepository.findById(taskId)
//	            .orElseThrow(() -> new RuntimeException("Task not found"));
//
//	    if (!task.getUser().getId().equals(loggedInUserId)) {
//	        return ResponseEntity.status(403).body("You are not allowed to edit this task");
//	    }
//
//	    if (request.getTitle() != null) {
//	        task.setTitle(request.getTitle());
//	    }
//
//	    if (request.getDescription() != null) {
//	        task.setDescription(request.getDescription());
//	    }
//
//	    if (request.getStatus() != null) {
//
//	        Status oldStatus = task.getStatus();
//	        Status newStatus = request.getStatus();
//
//	        // TO_DO → IN_PROGRESS
//	        if (oldStatus == Status.TO_DO && newStatus == Status.IN_PROGRESS) {
//	            TaskDetail detail = new TaskDetail();
//	            detail.setTask(task);
//	            detail.setStatus(Status.IN_PROGRESS);
//	            detail.setStartedAt(LocalDateTime.now());
//	            taskDetailRepo.save(detail);
//
//	            task.setStatus(Status.IN_PROGRESS);
//	        }
//
//	        // IN_PROGRESS → COMPLETED
//	        else if (oldStatus == Status.IN_PROGRESS && newStatus == Status.COMPLETED) {
//	            TaskDetail last = taskDetailRepo.findLatestInProgress(task.getId());
//	            if (last == null) {
//	                return ResponseEntity.badRequest().body("Task not in IN_PROGRESS state");
//	            }
//
//	            LocalDate completedDate = LocalDate.now();
//	            last.setEndedAt(LocalDateTime.now());
//	            last.setStatus(Status.COMPLETED);
//	            
//
//	            // ✅ Worked time in minutes
//	            Duration worked = Duration.between(
//	                    last.getStartedAt(),
//	                    last.getEndedAt()
//	            );
//                taskDetailRepo.save(last);
//	            task.setTotalWorkedMinutes(worked.toMinutes());
//	            task.setCompletedAt(completedDate);
//
//	            // 🔥 DUE DATE CHECK
//	            if (task.getDueDate() != null) {
//	                LocalDateTime dueDateTime = task.getDueDate().atStartOfDay();
//	                LocalDateTime completedDateTime = completedDate.atStartOfDay();
//
//	                if (!completedDateTime.isAfter(dueDateTime)) {
//	                    task.setCompletedOnTime(true);
//	                    task.setDelayMinutes(0L);
//	                } else {
//	                    task.setCompletedOnTime(false);
//	                    Duration delay = Duration.between(dueDateTime, completedDateTime);
//	                    task.setDelayMinutes(delay.toMinutes());
//	                }
//	            }
//
//	            task.setStatus(Status.COMPLETED);
//	            
//	            taskRepository.save(task);
//	        }
//
//	        // direct TO_DO
//	        else if (newStatus == Status.TO_DO) {
//	            task.setStatus(Status.TO_DO);
//	        }
//	    }
//
//	    if (task.getStatus() == Status.COMPLETED && request.getStatus() == Status.IN_PROGRESS) {
//	        return ResponseEntity.ok("This Task Already Completed");
//	    }
//
//	    if (taskRepository.save(task) != null) {
//	        return ResponseEntity.ok("Task updated successfully");
//	    } else {
//	        return ResponseEntity.status(500).body("Failed to update task");
//	    }
//	}

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
