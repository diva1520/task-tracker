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
	private TaskService taskService;

	@Autowired
	private EmailService mailService;

	public List<TaskResponse> getTasks(@NotNull @NotNull LocalDate fromDate, @NotNull @NotNull LocalDate toDate,
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
		} else {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			user.setPassword(encoder.encode(user.getPassword()));
			User res = userRepo.save(user);

			if (res != null) {
				return ResponseEntity.ok("User Created SucessFully UserID :: " + res.getId());
			} else {
				return ResponseEntity.ok("User Not Created");
			}
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

}
