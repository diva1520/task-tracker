package com.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

	public List<TaskResponse> getTasks(@NotNull LocalDate fromDate, @NotNull LocalDate toDate, List<Long> userIds) {

		return taskService.getTasks(fromDate, toDate, userIds);

	}

	public ResponseEntity<?> assignTask(AssignTaskDto request) {
		User user = userRepo.findById(request.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));

		Task task = new Task();
		task.setTitle(request.getTitle());
		task.setDescription(request.getDescription());
		task.setCreatedAt(LocalDateTime.now());
		task.setUser(user);
		task.setStatus(Status.TO_DO);
		taskRepo.save(task);
		return ResponseEntity.ok("Task added successfully");
	}

	public ResponseEntity<?> createUser(User user) {
		User res = userRepo.save(user);

		if (res != null) {
			return ResponseEntity.ok("User Created SucessFully UserID :: " + res.getId());
		} else {
			return ResponseEntity.ok("User Not Created");
		}
	}

	public List<User> getUsers() {
		return userRepo.findAll();
	}

}
