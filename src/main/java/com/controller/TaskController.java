package com.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dto.TaskRequest;
import com.dto.TaskResponse;
import com.service.TaskService;

@RestController
@RequestMapping("/user/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

	@Autowired
	TaskService taskService;

	@GetMapping
	public List<TaskResponse> viewTasks(Authentication authentication) {
		return taskService.viewTasks(authentication);
	}

	@PutMapping("/{taskId}")
	public ResponseEntity<?> editTask(@PathVariable Long taskId, @RequestHeader("Authorization") String authHeader,
			@RequestBody TaskRequest request) {
		return taskService.editTaskDetails(taskId, authHeader,request);
	}

	@PostMapping("/addtask")
	public ResponseEntity<?> addTask(@RequestHeader("Authorization") String authHeader,
			@RequestBody TaskRequest request) {
		return taskService.addTask(authHeader, request);
	}

}
