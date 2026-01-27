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
				// REMOVED: Restriction that oldStatus MUST be REVIEW. Admins can now reassign
				// from any state.
				// if (oldStatus != Status.REVIEW) { return ... }

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

				// REMOVED: Auto-calculation of minutes.
				// Minutes are now only added via explicit WorkLog (manual entry).
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

	public com.dto.UserTaskStatsDto getUserStats(String authHeader) {
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			throw new RuntimeException("Invalid token");
		}
		String token = authHeader.substring(7);
		Long userId = jwtUtil.extractUserId(token);
		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		com.dto.UserTaskStatsDto stats = new com.dto.UserTaskStatsDto();
		stats.setUserId(userId);
		stats.setUsername(user.getUsername());
		stats.setTotalTasks((int) taskRepository.countByUserId(userId));

		int todo = (int) taskRepository.countByUserIdAndStatus(userId, Status.TO_DO);
		int inProgress = (int) taskRepository.countByUserIdAndStatus(userId, Status.IN_PROGRESS);
		int review = (int) taskRepository.countByUserIdAndStatus(userId, Status.REVIEW);
		int completed = (int) taskRepository.countByUserIdAndStatus(userId, Status.COMPLETED);

		stats.setPendingTasks(todo + inProgress + review);
		stats.setCompletedTasks(completed);

		java.util.Map<String, Integer> breakdown = new java.util.HashMap<>();
		breakdown.put("TO_DO", todo);
		breakdown.put("IN_PROGRESS", inProgress);
		breakdown.put("REVIEW", review);
		breakdown.put("COMPLETED", completed);
		stats.setStatusBreakdown(breakdown);

		return stats;
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

		if (request.getStartTime() == null || request.getEndTime() == null) {
			return ResponseEntity.badRequest().body("Start time and End time are required");
		}
		if (!request.getEndTime().isAfter(request.getStartTime())) {
			return ResponseEntity.badRequest().body("End time must be after Start time");
		}

		// Overlap Validation
		LocalDateTime start = request.getStartTime();
		LocalDateTime end = request.getEndTime();
		LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
		LocalDateTime dayEnd = start.toLocalDate().atTime(23, 59, 59);

		List<com.entity.WorkLog> dailyLogs = workLogRepo.findByUserIdAndStartTimeBetween(userId, dayStart, dayEnd);

		for (com.entity.WorkLog existing : dailyLogs) {
			// Check if new interval overlaps with existing interval
			// Overlap logic: (StartA < EndB) and (EndA > StartB)
			if (start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime())) {
				return ResponseEntity.badRequest().body("Time overlap detected with existing log: "
						+ existing.getStartTime().toLocalTime() + " - " + existing.getEndTime().toLocalTime());
			}
		}

		long minutes = Duration.between(start, end).toMinutes();
		log.setDurationMinutes(Math.max(0, minutes));

		// Update total worked minutes on task
		task.setTotalWorkedMinutes(
				(task.getTotalWorkedMinutes() == null ? 0 : task.getTotalWorkedMinutes()) + minutes);
		taskRepository.save(task);

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

	public List<com.dto.TaskHistoryDTO> getTaskHistory(Long taskId) {
		Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
		List<com.dto.TaskHistoryDTO> history = new java.util.ArrayList<>();

		// 1. Task Creation
		com.dto.TaskHistoryDTO creation = new com.dto.TaskHistoryDTO();
		creation.setEventType("CREATED");
		if (task.getAssignedBy() != null) {
			User admin = userRepository.findById(task.getAssignedBy()).orElse(null);
			creation.setUsername(admin != null ? admin.getUsername() : "Admin");
		} else {
			creation.setUsername("System");
		}
		creation.setTimestamp(task.getCreatedAt().atStartOfDay());
		creation.setComment(task.getDescription());
		creation.setMetadata("Task Created");
		history.add(creation);

		// 2. Status History (TaskDetail)
		List<com.entity.TaskDetail> details = taskDetailRepo.findByTask_Id(taskId);
		details.sort(java.util.Comparator.comparing(com.entity.TaskDetail::getStartedAt));

		for (com.entity.TaskDetail d : details) {
			// Event: Started State
			com.dto.TaskHistoryDTO startEvent = new com.dto.TaskHistoryDTO();
			startEvent.setTimestamp(d.getStartedAt());
			startEvent.setEventType("STATUS_CHANGE");
			startEvent.setUsername(task.getUser().getUsername()); // Assuming user started it

			if (d.getStatus() == com.entity.Status.REASSIGN) {
				startEvent.setEventType("REASSIGN");
				startEvent.setUsername("Admin"); // Usually admin reassigns
				startEvent.setMetadata("Reassigned to " + task.getUser().getUsername());
				startEvent.setComment(d.getComment()); // Comment on reassign is valuable at start
			} else {
				startEvent.setMetadata("Moved to " + d.getStatus());
				if (d.getEndedAt() == null && d.getComment() != null) {
					// Only attach comment to start if it hasn't ended (active comment)
					// OR if we assume comment was for start.
					// But usually comment is overwritten on stop.
					// Let's use logic: if endedAt is null, comment is current status comment.
					startEvent.setComment(d.getComment());
				}
			}
			history.add(startEvent);

			// Event: Ended State (Transition Out)
			if (d.getEndedAt() != null) {
				com.dto.TaskHistoryDTO endEvent = new com.dto.TaskHistoryDTO();
				endEvent.setTimestamp(d.getEndedAt());
				endEvent.setEventType("STATUS_CHANGE");
				endEvent.setUsername(task.getUser().getUsername());

				// Infer transition
				if (d.getStatus() == com.entity.Status.IN_PROGRESS) {
					endEvent.setMetadata("Submitted for Review");
				} else {
					endEvent.setMetadata("Ended " + d.getStatus());
				}

				endEvent.setComment(d.getComment()); // The comment explains why it ended/moved to review
				history.add(endEvent);
			}
		}

		// 3. Work Logs
		List<com.entity.WorkLog> logs = workLogRepo.findByTaskId(taskId);
		for (com.entity.WorkLog log : logs) {
			com.dto.TaskHistoryDTO h = new com.dto.TaskHistoryDTO();
			h.setEventType("WORK_LOG");
			h.setUsername(log.getUser().getUsername());
			h.setTimestamp(log.getStartTime());
			h.setComment(log.getComment());

			long hours = log.getDurationMinutes() / 60;
			long mins = log.getDurationMinutes() % 60;
			String duration = (hours > 0 ? hours + "h " : "") + mins + "m";
			h.setMetadata("Logged " + duration);
			history.add(h);
		}

		// 4. Completion (if not covered by details)
		// If task is completed, but we might not have a TaskDetail for the transition
		// to COMPLETED
		// (e.g. from Review -> Completed, or direct Admin completion)
		if (task.getStatus() == com.entity.Status.COMPLETED && task.getCompletedAt() != null) {
			// Check if we already have an event near completion time?
			// Usually Review -> Completed doesn't create a TaskDetail.
			com.dto.TaskHistoryDTO completeEvent = new com.dto.TaskHistoryDTO();
			completeEvent.setEventType("COMPLETED");
			completeEvent.setTimestamp(task.getCompletedAt().atTime(23, 59)); // End of day fallback or modify entity to
																				// have LocalDateTime

			// Improve: If we find a 'stopTaskDetail' that ended recently, use that time?
			// But Review -> Completed updates the Task, not TaskDetail.

			if (task.getAssignedBy() != null) {
				User admin = userRepository.findById(task.getAssignedBy()).orElse(null);
				completeEvent.setUsername(admin != null ? admin.getUsername() : "Admin");
			} else {
				completeEvent.setUsername("System");
			}

			completeEvent.setMetadata("Task Completed");
			// Prevent duplicate if logic roughly matches
			history.add(completeEvent);
		}

		// Sort by timestamp DESC
		history.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

		return history;
	}

	private String row(String key, String value) {
		return "<tr><td style='border:1px solid #ddd;padding:8px;'><b>" + key + "</b></td>"
				+ "<td style='border:1px solid #ddd;padding:8px;'>" + value + "</td></tr>";
	}
}
