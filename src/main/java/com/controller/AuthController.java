package com.controller;

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
import com.security.CustomUserDetails;
import com.util.JwtUtil;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

	@Autowired
	private AuthenticationManager authManager;

	@Autowired
	private JwtUtil jwtUtil;

	@PostMapping("/login")
	public Map<String, String> login(@RequestBody LoginRequest req) {

		Authentication auth = authManager
				.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

		CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();

		String token = jwtUtil.generateToken(user);

		return Map.of("token", token);
	}

}
