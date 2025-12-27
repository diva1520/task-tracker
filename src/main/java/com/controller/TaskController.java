package com.controller;

import java.time.Duration;
import java.time.LocalDateTime;
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
import com.entity.Task;
import com.entity.TaskDetail;
import com.entity.User;
import com.repo.TaskDetailRepository;
import com.repo.TaskRepository;
import com.repo.UserRepository;
import com.security.CustomUserDetails;
import com.util.JwtUtil;
import com.entity.Status;

@RestController
@RequestMapping("/user/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TaskDetailRepository taskDetailRepo;

	@GetMapping
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
			tsk.add(new TaskResponse(task.getId(), task.getUser().getUsername(), task.getTitle(), task.getDescription(),
					task.getCreatedAt()));
		}

		return tsk;
	}

//	@PutMapping("/{taskId}")
//	public ResponseEntity<?> editTask(@PathVariable Long taskId, @RequestHeader("Authorization") String authHeader,
//			@RequestBody TaskRequest request) {
//		String token = authHeader.substring(7);
//		Long loggedInUserId = jwtUtil.extractUserId(token);
//
//		Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
//
//		if (!task.getUser().getId().equals(loggedInUserId)) {
//			return ResponseEntity.status(403).body("You are not allowed to edit this task");
//		}
//
//		if (request.getTitle() != null) {
//			task.setTitle(request.getTitle());
//		}
//
//		if (request.getDescription() != null) {
//			task.setDescription(request.getDescription());
//		}
//		if (request.getStatus() != null) {
//
//			switch (request.getStatus()) {
//			case TO_DO:
//				task.setStatus(Status.TO_DO);
//				break;
//			case IN_PROGRESS:
//				TaskDetail detail = new TaskDetail();
//				detail.setTask(task);
//				detail.setStatus(Status.IN_PROGRESS);
//				detail.setStartedAt(LocalDateTime.now());
//				taskDetailRepo.save(detail);
//
//				task.setStatus(Status.IN_PROGRESS);
//				break;
//			case COMPLETED:
//				TaskDetail last = taskDetailRepo.findLatestInProgress(task.getId());
//
//				last.setEndedAt(LocalDateTime.now());
//				taskDetailRepo.save(last);
//
// 				Duration worked = Duration.between(last.getStartedAt(), last.getEndedAt());
//
//				long minutes = worked.toMinutes();
//				task.setTotalWorkedMinutes(minutes);
//				task.setCompletedAt(LocalDateTime.now());
//
//				task.setStatus(Status.COMPLETED);
//				break;
//			default:
//				task.setStatus(Status.TO_DO);
//				break;
//			}
//
//			task.setStatus(request.getStatus());
//		}
//
//		taskRepository.save(task);
//
//		return ResponseEntity.ok("Task updated successfully");
//	}

	@PutMapping("/{taskId}")
	public ResponseEntity<?> editTask(@PathVariable Long taskId, @RequestHeader("Authorization") String authHeader,
			@RequestBody TaskRequest request) {

		String token = authHeader.substring(7);
		Long loggedInUserId = jwtUtil.extractUserId(token);

		Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));

		if (!task.getUser().getId().equals(loggedInUserId)) {
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

			// TO_DO → IN_PROGRESS
			if (oldStatus == Status.TO_DO && newStatus == Status.IN_PROGRESS) {

				TaskDetail detail = new TaskDetail();
				detail.setTask(task);
				detail.setStatus(Status.IN_PROGRESS);
				detail.setStartedAt(LocalDateTime.now());
				taskDetailRepo.save(detail);

				task.setStatus(Status.IN_PROGRESS);
			}

			// IN_PROGRESS → COMPLETED
			else if (oldStatus == Status.IN_PROGRESS && newStatus == Status.COMPLETED) {

				TaskDetail last = taskDetailRepo.findLatestInProgress(task.getId());

				if (last == null) {
					return ResponseEntity.badRequest().body("Task not in IN_PROGRESS state");
				}

				LocalDateTime completedTime = LocalDateTime.now();
				last.setEndedAt(completedTime);
				taskDetailRepo.save(last);

				// worked time
				Duration worked = Duration.between(last.getStartedAt(), last.getEndedAt());

				task.setTotalWorkedMinutes(worked.toMinutes());
				task.setCompletedAt(completedTime);

				// 🔥 DUE DATE CHECK
				if (task.getDueDate() != null) {

					if (!completedTime.isAfter(task.getDueDate())) {
						task.setCompletedOnTime(true);
						task.setDelayMinutes(0L);
					} else {
						task.setCompletedOnTime(false);

						Duration delay = Duration.between(task.getDueDate(), completedTime);
						task.setDelayMinutes(delay.toMinutes());
					}
				}

				task.setStatus(Status.COMPLETED);
			}

			// direct TO_DO
			else if (newStatus == Status.TO_DO) {
				task.setStatus(Status.TO_DO);
			}
		}

		taskRepository.save(task);
		return ResponseEntity.ok("Task updated successfully");
	}

//	@PostMapping("/addtask")
//	public ResponseEntity<?> addTask(@RequestHeader("Authorization") String authHeader,
//			@RequestBody TaskRequest request) {
//		String token = authHeader.substring(7);
//		Long userId = jwtUtil.extractUserId(token);
//
//		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
//
//		Task task = new Task();
//		task.setTitle(request.getTitle());
//		task.setDescription(request.getDescription());
//		task.setCreatedAt(LocalDateTime.now());
//		task.setUser(user);
//		taskRepository.save(task);
//
//		return ResponseEntity.ok("Task added successfully");
//	}
	
	@PostMapping("/addtask")
	public ResponseEntity<?> addTask(
	        @RequestHeader("Authorization") String authHeader,
	        @RequestBody TaskRequest request) {

	    String token = authHeader.substring(7);
	    Long userId = jwtUtil.extractUserId(token);

	    User user = userRepository.findById(userId)
	            .orElseThrow(() -> new RuntimeException("User not found"));

	    Task task = new Task();
	    task.setTitle(request.getTitle());
	    task.setDescription(request.getDescription());
	    task.setCreatedAt(LocalDateTime.now());
	    task.setUser(user);

	    // 🔥 IMPORTANT DEFAULTS
	    task.setStatus(Status.TO_DO);          // ← THIS FIXES NULL
	    task.setDueDate(request.getDueDate()); // if coming from UI

	    taskRepository.save(task);

	    return ResponseEntity.ok("Task added successfully");
	}


}
