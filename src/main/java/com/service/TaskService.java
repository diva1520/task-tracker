package com.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {

	private final TaskRepository taskRepository;
	private final JwtUtil jwtUtil;
	private final UserRepository userRepository;
	private final TaskDetailRepository taskDetailRepo;
	private final EmailService mailService;
	private final com.repo.WorkLogRepository workLogRepo;

	public TaskService(EmailService mailService, TaskRepository taskRepository, JwtUtil jwtUtil,
			UserRepository userRepository, TaskDetailRepository taskDetailRepo,
			com.repo.WorkLogRepository workLogRepo) {
		this.taskRepository = taskRepository;
		this.jwtUtil = jwtUtil;
		this.userRepository = userRepository;
		this.taskDetailRepo = taskDetailRepo;
		this.mailService = mailService;
		this.workLogRepo = workLogRepo;
	}

	public List<TaskResponse> getTasks(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, List<Long> userIds) {
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
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return ResponseEntity.status(401).body("Missing or invalid Authorization header");
		}
		String token = authHeader.substring(7);
		Long userId = jwtUtil.extractUserId(token);

		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		Task task = new Task();
		task.setTitle(request.getTitle());
		task.setDescription(request.getDescription());
		task.setCreatedAt(LocalDate.now());
		task.setUser(user);
		task.setStatus(Status.TO_DO);
		task.setDueDate(request.getDueDate());

		if (taskRepository.save(task) != null) {
			return ResponseEntity.ok("Task added successfully");
		} else {
			return ResponseEntity.status(500).body("Failed to add task");
		}
	}

	public ResponseEntity<?> editTaskDetails(Long taskId, String authHeader, TaskRequest request) {
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return ResponseEntity.status(401).body("Missing or invalid Authorization header");
		}
		String token = authHeader.substring(7);
		Long loggedInUserId = jwtUtil.extractUserId(token);
		String role = jwtUtil.extractRole(token);

		Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));

		// Security Check: USER can edit only their own tasks
		if ("ROLE_USER".equals(role) && !task.getUser().getId().equals(loggedInUserId)) {
			return ResponseEntity.status(403).body("You are not allowed to edit this task");
		}

		// Update fields if provided
		if (request.getTitle() != null)
			task.setTitle(request.getTitle());
		if (request.getDescription() != null)
			task.setDescription(request.getDescription());
		if (request.getComment() != null)
			task.setComment(request.getComment());

		// Handle status change logic
		if (request.getStatus() != null && request.getStatus() != task.getStatus()) {
			return handleStatusChange(task, request, role, loggedInUserId);
		}

		taskRepository.save(task);
		return ResponseEntity.ok("Task updated successfully");
	}

	private ResponseEntity<?> handleStatusChange(Task task, TaskRequest request, String role, Long loggedInUserId) {
		Status oldStatus = task.getStatus();
		Status newStatus = request.getStatus();

		if ("ROLE_USER".equals(role)) {
			// Rules for USER
			if ((oldStatus == Status.TO_DO || oldStatus == Status.REASSIGN) && newStatus == Status.IN_PROGRESS) {
				if (request.getComment() == null || request.getComment().isBlank()) {
					return ResponseEntity.badRequest().body("Comment is mandatory when moving to IN_PROGRESS");
				}
				startTaskDetail(task, Status.IN_PROGRESS, request.getComment());
			} else if (oldStatus == Status.IN_PROGRESS && newStatus == Status.REVIEW) {
				if (request.getComment() == null || request.getComment().isBlank()) {
					return ResponseEntity.badRequest().body("Comment is mandatory when moving to REVIEW");
				}
				stopTaskDetail(task, request.getComment());
				task.setStatus(Status.REVIEW);
			} else {
				return ResponseEntity.status(403).body("User not allowed to perform this status transition");
			}
		} else if ("ROLE_ADMIN".equals(role)) {
			// Rules for ADMIN
			if (newStatus == Status.REASSIGN) {
				if (oldStatus != Status.REVIEW) {
					return ResponseEntity.status(403).body("Only tasks in REVIEW status can be reassigned");
				}

				if (taskDetailRepo.existsByTask_IdAndStatus(task.getId(), Status.REASSIGN)) {
					return ResponseEntity.badRequest()
							.body("Task has already been reassigned once. Cannot reassign again.");
				}

				if (request.getComment() == null || request.getComment().isBlank()) {
					return ResponseEntity.badRequest().body("Comment is mandatory for reassigning");
				}
				if (request.getUserId() == null) {
					return ResponseEntity.badRequest().body("New User ID is required for reassignment");
				}
				User newUser = userRepository.findById(request.getUserId())
						.orElseThrow(() -> new RuntimeException("Target user not found"));
				if ("ROLE_ADMIN".equals(newUser.getRole())) {
					return ResponseEntity.badRequest().body("Tasks cannot be reassigned to Admins");
				}
				task.setUser(newUser);
				task.setAssignedBy(loggedInUserId); // Update assignedBy to the admin who reassigned it
				startTaskDetail(task, Status.REASSIGN, request.getComment()); // Save history
				task.setStatus(Status.REASSIGN); // Set to REASSIGN instead of TO_DO
			} else if (oldStatus == Status.REVIEW && newStatus == Status.COMPLETED) {
				stopTaskDetail(task, request.getComment());
				task.setCompletedAt(LocalDate.now());
				task.setStatus(Status.COMPLETED);
				calculateDelay(task);
			} else {
				return ResponseEntity.status(403).body("Admin not allowed to perform this status transition");
			}
		}

		taskRepository.save(task);
		notifyParties(task, oldStatus, newStatus, loggedInUserId); // Notify BOTH User and Admin
		return ResponseEntity.ok("Task status updated to " + task.getStatus());
	}

	private void startTaskDetail(Task task, Status status, String comment) {
		TaskDetail detail = new TaskDetail();
		detail.setTask(task);
		detail.setStatus(status);
		detail.setStartedAt(LocalDateTime.now());
		detail.setComment(comment);
		taskDetailRepo.save(detail);
		task.setStatus(status);
	}

	private void stopTaskDetail(Task task, String comment) {
		List<TaskDetail> details = taskDetailRepo.findLatestInProgress(task.getId());
		if (!details.isEmpty()) {
			TaskDetail inProgress = details.get(0);
			if (inProgress.getEndedAt() == null) {
				inProgress.setEndedAt(LocalDateTime.now());
				if (comment != null) {
					inProgress.setComment(comment);
				}
				taskDetailRepo.save(inProgress);

				long minutes = Math.max(1,
						Duration.between(inProgress.getStartedAt(), inProgress.getEndedAt()).toMinutes());
				task.setTotalWorkedMinutes(
						(task.getTotalWorkedMinutes() != null ? task.getTotalWorkedMinutes() : 0) + minutes);
			}
		}
	}

	private void calculateDelay(Task task) {
		if (task.getDueDate() != null && task.getCompletedAt() != null) {
			if (!task.getCompletedAt().isAfter(task.getDueDate())) {
				task.setCompletedOnTime(true);
				task.setDelayMinutes(0L);
			} else {
				task.setCompletedOnTime(false);
				long delay = Duration.between(task.getDueDate().atStartOfDay(), task.getCompletedAt().atStartOfDay())
						.toMinutes();
				task.setDelayMinutes(delay);
			}
		}
	}

	private void notifyParties(Task task, Status oldStatus, Status newStatus, Long loggedInUserId) {
		if (task.getAssignedBy() == null)
			return;

		User actor = userRepository.findById(loggedInUserId).orElse(null);
		String actorName = actor != null ? actor.getUsername() : "System";

		userRepository.findById(task.getAssignedBy()).ifPresent(admin -> {
			String subject = "🔄 Task Alert: " + task.getTitle() + " [" + newStatus + "]";
			String statusUpdateMsg = "The task status has been updated.";

			if (newStatus == Status.REASSIGN) {
				statusUpdateMsg = "The task has been reassigned to <strong>" + task.getUser().getUsername()
						+ "</strong>.";
			} else if (newStatus == Status.COMPLETED) {
				statusUpdateMsg = "The task has been successfully completed.";
			} else if (newStatus == Status.REVIEW) {
				statusUpdateMsg = "The task has been submitted for review.";
			} else if (newStatus == Status.IN_PROGRESS) {
				statusUpdateMsg = "Work has started on the task.";
			}

			String emailBody = "<html><body style='font-family: Arial; line-height:1.6;'>"
					+ "<h2 style='color:#E67E22;'>Task Update: " + task.getTitle() + "</h2>"
					+ "<p>Hi,</p>"
					+ "<p>" + statusUpdateMsg + "</p>"
					+ "<table style='border-collapse: collapse; width:100%;'>"
					+ row("Task Title", task.getTitle())
					+ row("Description", task.getDescription() != null ? task.getDescription() : "-")
					+ row("Due Date", task.getDueDate() != null ? task.getDueDate().toString() : "-")
					+ row("Status Change", oldStatus + " ➡️ " + newStatus)
					+ row("Updated By", actorName)
					+ row("Comment", task.getComment() != null ? task.getComment() : "-")
					+ row("Updated On", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")))
					+ "</table>"
					+ "<p style='margin-top:20px;'>Please log in to the portal for more details.</p>"
					+ "<p>Thanks,<br/>Task Management Team</p>"
					+ "</body></html>";

			// Send to Admin
			mailService.sendMail(admin.getEmail(), subject, emailBody);
			// Send to User
			mailService.sendMail(task.getUser().getEmail(), subject, emailBody);
		});
	}

	public List<TaskResponse> viewTasks(Authentication authentication) {
		CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
		List<Task> tasks = taskRepository.fetchTasks(user.getUserId());
		return mapToTaskResponses(tasks);
	}

	private List<TaskResponse> mapToTaskResponses(List<Task> tasks) {
		return tasks.stream().map(TaskResponse::fromEntity).toList();
	}

	public ResponseEntity<?> logWork(String authHeader, com.dto.WorkLogRequest request) {
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return ResponseEntity.status(401).body("Missing or invalid Authorization header");
		}
		String token = authHeader.substring(7);
		Long userId = jwtUtil.extractUserId(token);
		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		Task task = taskRepository.findById(request.getTaskId())
				.orElseThrow(() -> new RuntimeException("Task not found"));

		if (!task.getUser().getId().equals(userId)) {
			return ResponseEntity.status(403).body("You can only log work for your own tasks");
		}

		com.entity.WorkLog log = new com.entity.WorkLog();
		log.setTask(task);
		log.setUser(user);
		log.setStartTime(request.getStartTime());
		log.setEndTime(request.getEndTime());

		if (request.getStartTime() != null && request.getEndTime() != null) {
			long minutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
			log.setDurationMinutes(Math.max(0, minutes));

			// Update total worked minutes on task
			task.setTotalWorkedMinutes(
					(task.getTotalWorkedMinutes() == null ? 0 : task.getTotalWorkedMinutes()) + minutes);
			taskRepository.save(task);
		}

		log.setComment(request.getComment());

		workLogRepo.save(log);

		if (request.getStatus() != null && request.getStatus() != task.getStatus()) {
			TaskRequest tr = new TaskRequest();
			tr.setStatus(request.getStatus());
			tr.setComment(request.getComment());
			return handleStatusChange(task, tr, user.getRole(), userId);
		}

		return ResponseEntity.ok("Work logged successfully");
	}

	private String row(String key, String value) {
		return "<tr><td style='border:1px solid #ddd;padding:8px;'><b>" + key + "</b></td>"
				+ "<td style='border:1px solid #ddd;padding:8px;'>" + value + "</td></tr>";
	}
}
