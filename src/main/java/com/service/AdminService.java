package com.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.dto.AssignTaskDto;
import com.dto.TaskResponse;
import com.entity.Status;
import com.entity.Task;
import com.entity.User;
import com.repo.TaskRepository;
import com.repo.UserRepository;

import jakarta.validation.constraints.NotNull;

@Service
public class AdminService {

	@Autowired
	private TaskRepository taskRepo;

	@Autowired
	private UserRepository userRepo;

	@Autowired
	private com.repo.UserLoginAuditRepository auditRepo;

	@Autowired
	private com.repo.WorkLogRepository workLogRepo;

	@Autowired
	private TaskService taskService;

	@Autowired
	private EmailService mailService;

	@Autowired
	private com.repo.LeaveRepository leaveRepo;

	public List<TaskResponse> getTasks(@NotNull LocalDate fromDate, @NotNull LocalDate toDate,
			List<Long> userIds) {

		return taskService.getTasks(fromDate, toDate, userIds);

	}

	public List<TaskResponse> getTasks() {

		return taskService.getTasks();

	}

	public ResponseEntity<?> assignTask(AssignTaskDto request, Long adminId) {
		User user = userRepo.findById(request.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));

		if ("ROLE_ADMIN".equals(user.getRole())) {
			return ResponseEntity.badRequest().body("Tasks cannot be assigned to Admins");
		}

		User admin = userRepo.findById(adminId).orElseThrow(() -> new RuntimeException("User not found"));

		Task task = new Task();
		task.setTitle(request.getTitle());
		task.setDescription(request.getDescription());
		task.setCreatedAt(LocalDate.now());
		task.setUser(user);
		task.setDueDate(request.getDueDate());
		task.setStatus(Status.TO_DO);
		task.setAssignedBy(adminId);
		taskRepo.save(task);

		String emailSubject = "📝 New Task Assigned: " + task.getTitle();

		String emailBody = "<html>" + "<body style='font-family: Arial, sans-serif; line-height: 1.6;'>"
				+ "<h2 style='color:#2E86C1;'>New Task Assigned</h2>" + "<p>Hi <strong>" + user.getUsername()
				+ "</strong>,</p>" + "<p>You have been assigned a new task. Please find the details below:</p>"
				+ "<table style='border-collapse: collapse; width: 100%;'>"
				+ "<tr><td style='border: 1px solid #ddd; padding: 8px;'>Title</td>"
				+ "<td style='border: 1px solid #ddd; padding: 8px;'>" + task.getTitle() + "</td></tr>"
				+ "<tr><td style='border: 1px solid #ddd; padding: 8px;'>Description</td>"
				+ "<td style='border: 1px solid #ddd; padding: 8px;'>" + task.getDescription() + "</td></tr>"
				+ "<tr><td style='border: 1px solid #ddd; padding: 8px;'>Due Date</td>"
				+ "<td style='border: 1px solid #ddd; padding: 8px;'>" + task.getDueDate() + "</td></tr>"
				+ "<tr><td style='border: 1px solid #ddd; padding: 8px;'>Assigned By</td>"
				+ "<td style='border: 1px solid #ddd; padding: 8px;'>" + admin.getUsername() + "</td></tr>" + "</table>"
				+ "<p style='margin-top:20px;'>Please complete the task before the due date.</p>"
				+ "<p>Thanks,<br/>Task Management Team</p>" + "</body>" + "</html>";

		// Send email
		mailService.sendMail(user.getEmail(), emailSubject, emailBody);

		return ResponseEntity.ok("Task added successfully");
	}

	public ResponseEntity<?> createUser(User user) {

		if (!validateUser(user)) {
			return ResponseEntity.badRequest().body("Invalid User Data");
		} else if (userRepo.findByUsername(user.getUsername()).isPresent()) {
			return ResponseEntity.badRequest().body("Username already exists");
		} else {
			// Capture raw password for email before encoding
			String rawPassword = user.getPassword();

			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			user.setPassword(encoder.encode(user.getPassword()));
			User res = userRepo.save(user);

			if (res != null) {
				// Send Welcome Email
				String subject = "Welcome to Task Tracker - Your Account Details";
				String serverUrl = getServerUrl();
				String body = "<html>" +
						"<body style='font-family: Arial, sans-serif; line-height: 1.6;'>" +
						"<h2 style='color:#2E86C1;'>Welcome to Task Tracker!</h2>" +
						"<p>Hi <strong>" + user.getUsername() + "</strong>,</p>" +
						"<p>Your account has been created successfully. Here are your login credentials:</p>" +
						"<table style='border-collapse: collapse; width: 100%; max-width: 400px;'>" +
						"<tr><td style='border: 1px solid #ddd; padding: 8px;'><strong>Username</strong></td>" +
						"<td style='border: 1px solid #ddd; padding: 8px;'>" + user.getUsername() + "</td></tr>" +
						"<tr><td style='border: 1px solid #ddd; padding: 8px;'><strong>Password</strong></td>" +
						"<td style='border: 1px solid #ddd; padding: 8px;'>" + rawPassword + "</td></tr>" +
						"<tr><td style='border: 1px solid #ddd; padding: 8px;'><strong>Role</strong></td>" +
						"<td style='border: 1px solid #ddd; padding: 8px;'>" + user.getRole() + "</td></tr>" +
						"</table>" +
						"<p style='margin-top:20px;'>You can login here: <a href='" + serverUrl + "'>" + serverUrl
						+ "</a></p>" +
						// "<p>Please change your password after logging in.</p>" +
						"<p>Best Regards,<br/>Admin Team</p>" +
						"</body>" +
						"</html>";

				try {
					mailService.sendMail(user.getEmail(), subject, body);
				} catch (Exception e) {
					System.err.println("Failed to send welcome email: " + e.getMessage());
					// Don't fail the creation, just log it
				}

				return ResponseEntity.ok("User Created SucessFully UserID :: " + res.getId());
			} else {
				return ResponseEntity.ok("User Not Created");
			}
		}
	}

	private String getServerUrl() {
		try {
			return "http://" + java.net.InetAddress.getLocalHost().getHostAddress() + ":8080/index.html";
		} catch (Exception e) {
			return "http://localhost:8080/index.html";
		}
	}

	private boolean validateUser(User user) {
		if (user.getUsername() == null || user.getUsername().isBlank())
			return false;
		if (user.getPassword() == null || user.getPassword().isBlank())
			return false;
		if (user.getRole() == null || user.getRole().isBlank())
			return false;
		if (user.getEmail() == null || user.getEmail().isBlank())
			return false;

		String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
		return user.getEmail().matches(emailRegex);
	}

	public List<User> getUsers() {
		return userRepo.findAll();
	}

	public User getUserIdByUsername(String username) {
		return userRepo.findByUsername(username)
				.orElseThrow(() -> new RuntimeException("User not found with username: " + username));
	}

	public List<com.entity.WorkLog> getWorkLogs() {
		return workLogRepo.findAll();
	}

	public List<com.entity.UserLoginAudit> getAuditLogs() {
		return auditRepo.findAll();
	}

	public List<com.entity.UserLoginAudit> getUserAuditLogs(Long userId) {
		return auditRepo.findByUserId(userId);
	}

	@Autowired
	private com.repo.TaskDetailRepository taskDetailRepo;

	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public List<com.dto.WorkLogDto> getUserWorkLogs(Long userId) {
		List<com.entity.WorkLog> logs = workLogRepo.findByUserId(userId);

		if (logs.isEmpty()) {
			return java.util.Collections.emptyList();
		}

		// Batch fetch details
		List<Long> taskIds = logs.stream().map(l -> l.getTask().getId()).distinct().toList();
		List<com.entity.TaskDetail> allDetails = taskDetailRepo.findByTask_IdIn(taskIds);
		java.util.Map<Long, List<com.entity.TaskDetail>> detailsByTask = allDetails.stream()
				.collect(java.util.stream.Collectors.groupingBy(d -> d.getTask().getId()));

		// Sort details inside each list
		detailsByTask.values()
				.forEach(list -> list.sort(java.util.Comparator.comparing(com.entity.TaskDetail::getStartedAt)));

		return logs.stream().map(log -> {
			com.dto.WorkLogDto dto = new com.dto.WorkLogDto();
			dto.setId(log.getId());
			dto.setTaskId(log.getTask().getId());
			dto.setTaskTitle(log.getTask().getTitle());
			dto.setStartTime(log.getStartTime());
			dto.setEndTime(log.getEndTime());
			dto.setDurationMinutes(log.getDurationMinutes());

			// Infer Status
			String statusLabel = "To Do"; // Default
			List<com.entity.TaskDetail> details = detailsByTask.getOrDefault(log.getTask().getId(),
					java.util.Collections.emptyList());

			LocalDateTime logTime = log.getStartTime();
			com.entity.TaskDetail effectiveDetail = null;

			for (com.entity.TaskDetail d : details) {
				if (!d.getStartedAt().isAfter(logTime)) {
					effectiveDetail = d;
				} else {
					break;
				}
			}

			if (effectiveDetail != null) {
				if (effectiveDetail.getStatus() == com.entity.Status.IN_PROGRESS) {
					if (effectiveDetail.getEndedAt() == null || !logTime.isAfter(effectiveDetail.getEndedAt())) {
						statusLabel = "In Progress";
					} else {
						statusLabel = "Under Review";
					}
				} else if (effectiveDetail.getStatus() == com.entity.Status.REASSIGN) {
					statusLabel = "Reassigned";
				} else {
					statusLabel = effectiveDetail.getStatus().toString();
				}
			}

			dto.setComment(log.getComment() + " [" + statusLabel + "]");
			return dto;
		}).sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime())) // Sort DESC by time
				.toList();
	}

	public ResponseEntity<?> updateUserStatus(Long userId, boolean active) {
		User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
		user.setActive(active);
		userRepo.save(user);
		return ResponseEntity.ok("User status updated");
	}

	public com.dto.UserTaskStatsDto getUserTaskStats(Long userId) {
		User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
		List<Task> tasks = taskRepo.findByUserId(userId);

		com.dto.UserTaskStatsDto stats = new com.dto.UserTaskStatsDto();
		stats.setUserId(user.getId());
		stats.setUsername(user.getUsername());
		stats.setTotalTasks(tasks.size());

		int pending = 0;
		int completed = 0;
		java.util.Map<String, Integer> breakdown = new java.util.HashMap<>();

		for (Task t : tasks) {
			String s = t.getStatus().name();
			breakdown.put(s, breakdown.getOrDefault(s, 0) + 1);
			if (t.getStatus() == com.entity.Status.COMPLETED) {
				completed++;
			} else {
				pending++;
			}
		}

		stats.setPendingTasks(pending);
		stats.setCompletedTasks(completed);
		stats.setStatusBreakdown(breakdown);

		return stats;
	}

	public com.dto.AdminSummaryDto getAdminSummary() {
		List<User> allUsers = userRepo.findAll();
		java.util.List<Long> totalIds = allUsers.stream().map(User::getId).toList();

		java.util.List<Long> activeIds = auditRepo.findByStatus("ACTIVE").stream()
				.map(a -> a.getUserId())
				.distinct()
				.toList();

		java.util.List<Long> noTaskIds = allUsers.stream()
				.filter(u -> u.getRole().equals("ROLE_USER"))
				.filter(u -> !taskRepo.existsByUserId(u.getId()))
				.map(User::getId)
				.toList();

		List<Task> allTasks = taskRepo.findAll();
		long totalTasks = allTasks.size();
		long todoCount = allTasks.stream().filter(t -> t.getStatus() == com.entity.Status.TO_DO).count();
		long progressCount = allTasks.stream().filter(t -> t.getStatus() == com.entity.Status.IN_PROGRESS).count();
		long reviewCount = allTasks.stream().filter(t -> t.getStatus() == com.entity.Status.REVIEW).count();
		long completedCount = allTasks.stream().filter(t -> t.getStatus() == com.entity.Status.COMPLETED).count();

		java.util.List<Long> todoIds = allTasks.stream()
				.filter(t -> t.getStatus() == com.entity.Status.TO_DO)
				.map(t -> t.getUser() != null ? t.getUser().getId() : null)
				.filter(id -> id != null).distinct().toList();

		java.util.List<Long> progressIds = allTasks.stream()
				.filter(t -> t.getStatus() == com.entity.Status.IN_PROGRESS)
				.map(t -> t.getUser() != null ? t.getUser().getId() : null)
				.filter(id -> id != null).distinct().toList();

		java.util.List<Long> reviewIds = allTasks.stream()
				.filter(t -> t.getStatus() == com.entity.Status.REVIEW)
				.map(t -> t.getUser() != null ? t.getUser().getId() : null)
				.filter(id -> id != null).distinct().toList();

		java.util.List<Long> completedIds = allTasks.stream()
				.filter(t -> t.getStatus() == com.entity.Status.COMPLETED)
				.map(t -> t.getUser() != null ? t.getUser().getId() : null)
				.filter(id -> id != null).distinct().toList();

		java.util.List<com.entity.LeaveRequest> activeLeaves = leaveRepo.findActiveLeaves(LocalDate.now());
		long activeLeaveCount = activeLeaves.size();
		java.util.List<Long> activeLeaveUserIds = activeLeaves.stream()
				.map(l -> l.getUser().getId())
				.distinct()
				.toList();

		return new com.dto.AdminSummaryDto(
				totalIds.size(),
				activeIds.size(),
				noTaskIds.size(),
				totalIds,
				activeIds,
				noTaskIds,
				totalTasks,
				todoCount,
				progressCount,
				reviewCount,
				completedCount,
				todoIds,
				progressIds,
				reviewIds,
				completedIds,
				leaveRepo.findAllPending().size(),
				activeLeaveCount,
				activeLeaveUserIds);
	}
}
