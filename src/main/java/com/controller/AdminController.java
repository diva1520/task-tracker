package com.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.dto.AssignTaskDto;
import com.dto.GetTaskRequest;
import com.entity.User;
import com.service.AdminService;
import com.service.ReportService;

import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*")
public class AdminController {

	@Autowired
	private AdminService service;

	@Autowired
	ReportService reportService;

	@GetMapping("/users")
	public List<User> getAllUsers() {
		System.out.println("DEBUG: AdminController.getAllUsers called");
		return service.getUsers();
	}

	@GetMapping("/tasks")
	public ResponseEntity<?> getTasks(@RequestParam @NotNull LocalDate fromDate,
			@RequestParam @NotNull LocalDate toDate, @RequestParam(required = false) List<Long> userIds) {

		if (fromDate.isAfter(toDate)) {
			return ResponseEntity.badRequest().body("fromDate must be before toDate");
		}
		return ResponseEntity.ok(service.getTasks(fromDate, toDate, userIds));
	}

	@PostMapping("/task")
	public ResponseEntity<?> getAllTasks(@RequestBody(required = false) GetTaskRequest request) {
		if (request == null || (request.getFromDate() == null && request.getToDate() == null)) {
			return ResponseEntity.ok(service.getTasks());
		}
		return ResponseEntity.ok(service.getTasks(request.getFromDate(), request.getToDate(), request.getUserIds()));
	}

	@PostMapping("/assign-task")
	public ResponseEntity<?> assignTask(@RequestBody AssignTaskDto request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();
		Long adminId = service.getUserIdByUsername(username).getId();
		return service.assignTask(request, adminId);
	}

	@PostMapping("/create-user")
	public ResponseEntity<?> createUser(@RequestBody User user) {
		return service.createUser(user);
	}

	@GetMapping("/audit")
	public ResponseEntity<?> getAuditLogs() {
		return ResponseEntity.ok(service.getAuditLogs());
	}

	@GetMapping("/work-logs")
	public ResponseEntity<?> getWorkLogs() {
		return ResponseEntity.ok(service.getWorkLogs());
	}

	@GetMapping("/audit/{userId}")
	public ResponseEntity<?> getUserAuditLogs(@org.springframework.web.bind.annotation.PathVariable Long userId) {
		return ResponseEntity.ok(service.getUserAuditLogs(userId));
	}

	@GetMapping("/work-logs/{userId}")
	public ResponseEntity<?> getUserWorkLogs(@org.springframework.web.bind.annotation.PathVariable Long userId) {
		return ResponseEntity.ok(service.getUserWorkLogs(userId));
	}

	@PostMapping("/users/{id}/status")
	public ResponseEntity<?> updateUserStatus(@org.springframework.web.bind.annotation.PathVariable Long id,
			@RequestParam boolean active) {
		// Need to implement this in service
		return service.updateUserStatus(id, active);
	}

	@GetMapping("/users/{id}/stats")
	public ResponseEntity<?> getUserStats(@org.springframework.web.bind.annotation.PathVariable Long id) {
		return ResponseEntity.ok(service.getUserTaskStats(id));
	}

	@GetMapping("/summary")
	public ResponseEntity<?> getAdminSummary() {
		return ResponseEntity.ok(service.getAdminSummary());
	}

	@GetMapping("/users/{userId}/report")
	public ResponseEntity<InputStreamResource> downloadUserReport(@PathVariable Long userId)
			throws java.io.IOException {

		java.io.ByteArrayInputStream in = reportService.generateUserActivityReport(userId);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Disposition", "attachment; filename=user_report_" + userId + ".xlsx");

		return ResponseEntity.ok()
				.headers(headers)
				.contentType(
						MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(new InputStreamResource(in));
	}
}
