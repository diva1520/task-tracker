package com.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_login_audit")
public class UserLoginAudit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private String username;

	private LocalDateTime loginTime;

	private LocalDateTime logoutTime;

	private Long sessionDurationMinutes;

	private String ipAddress;

	private String userAgent;

	private String status; // ACTIVE / LOGOUT
}
