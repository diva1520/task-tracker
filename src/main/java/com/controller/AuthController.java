package com.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dto.LoginRequest;
import com.entity.UserLoginAudit;
import com.repo.UserLoginAuditRepository;
import com.security.CustomUserDetails;
import com.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

	@Autowired
	private AuthenticationManager authManager;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private UserLoginAuditRepository auditRepo;

	@PostMapping("/login")
	public Map<String, String> login(@RequestBody LoginRequest req) {

		Authentication auth = authManager
				.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

		CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();

		// Close any previous active sessions for this user
		List<UserLoginAudit> activeSessions = auditRepo.findActiveSessions(user.getUserId());
		for (UserLoginAudit active : activeSessions) {
			active.setLogoutTime(LocalDateTime.now());
			active.setStatus("LOGOUT");
			if (active.getLoginTime() != null) {
				active.setSessionDurationMinutes(
						Duration.between(active.getLoginTime(), active.getLogoutTime()).toMinutes());
			} else {
				active.setSessionDurationMinutes(0L);
			}
			auditRepo.save(active);
		}

		UserLoginAudit audit = new UserLoginAudit();
		audit.setUserId(user.getUserId());
		audit.setUsername(user.getUsername());
		audit.setLoginTime(LocalDateTime.now());
		audit.setStatus("ACTIVE");

		auditRepo.save(audit);

		String token = jwtUtil.generateToken(user);

		return Map.of("token", token);
	}

	@Transactional
	@PostMapping("/logout")
	public void logout(HttpServletRequest request) {

		System.out.println("logged out");

		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			return;
		}
		String token = header.substring(7);
		Long userId = jwtUtil.extractUserId(token);

		UserLoginAudit audit = auditRepo.findActiveSessions(userId)
				.stream()
				.findFirst()
				.orElse(null);

		if (audit != null) {
			audit.setLogoutTime(LocalDateTime.now());
			if (audit.getLoginTime() != null) {
				audit.setSessionDurationMinutes(
						Duration.between(audit.getLoginTime(), audit.getLogoutTime()).toMinutes());
			} else {
				audit.setSessionDurationMinutes(0L);
			}
			audit.setStatus("LOGOUT");
			auditRepo.save(audit);
		}

	}

}
