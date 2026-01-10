package com.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.entity.UserLoginAudit;

public interface UserLoginAuditRepository extends JpaRepository<UserLoginAudit, Long> {

	@Query("""
			   SELECT a FROM UserLoginAudit a
			   WHERE a.userId = :userId
			   AND a.status = 'ACTIVE'
			   ORDER BY a.loginTime DESC
			""")
	List<UserLoginAudit> findActiveSessions(Long userId);

	List<UserLoginAudit> findByStatus(String status);

	List<UserLoginAudit> findByUserId(Long userId);
}
