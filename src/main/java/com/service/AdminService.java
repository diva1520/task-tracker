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
	TaskService taskService;

	public List<TaskResponse> getTasks(@NotNull @NotNull LocalDate fromDate, @NotNull @NotNull LocalDate toDate, List<Long> userIds) {

		return taskService.getTasks(fromDate, toDate, userIds);

	}
	
	public List<TaskResponse> getTasks() {

		return taskService.getTasks();

	}
	
	

	public ResponseEntity<?> assignTask(AssignTaskDto request) {
		User user = userRepo.findById(request.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));

		Task task = new Task();
		task.setTitle(request.getTitle());
		task.setDescription(request.getDescription());
		task.setCreatedAt(LocalDate.now());
		task.setUser(user);
		task.setDueDate(request.getDueDate());
		task.setStatus(Status.TO_DO);
		taskRepo.save(task);
		return ResponseEntity.ok("Task added successfully");
	}

	public ResponseEntity<?> createUser(User user) {
		
		if (!validateUser(user)) {
			return ResponseEntity.badRequest().body("Invalid User Data");
		}
		else {
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
	
	
	private  boolean validateUser(User user) {
		
		if (user.getUsername() == null || user.getUsername().isEmpty()) {
			return false;
		}
		if (user.getPassword() == null || user.getPassword().isEmpty()) {
			return false;
		}
		if (user.getRole() == null || user.getRole().isEmpty()) {
			return false;
		}
		if(user.getEmail() == null || user.getEmail().isEmpty()) {
			return false;
		}if(!user.getEmail().contains("@")) {
			return false;
		}
		return true;
	}

	public List<User> getUsers() {
		return userRepo.findAll();
	}

}
