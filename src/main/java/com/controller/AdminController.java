package com.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dto.AssignTaskDto;
import com.entity.User;
import com.service.AdminService;
 

import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*")
public class AdminController {
	@Autowired
	AdminService service;

	@GetMapping("/users")
	public List<User> getAllUsers() {
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

	@PostMapping("/assign-task")
	public ResponseEntity<?> addTask(@RequestBody AssignTaskDto request) {
		return service.assignTask(request);
	}

	@PostMapping("/create-user")
	public ResponseEntity<?> postMethodName(@RequestBody User user) {

		return service.createUser(user);
	}

}
