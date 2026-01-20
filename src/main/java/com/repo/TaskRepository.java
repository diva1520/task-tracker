package com.repo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.entity.Task;

import jakarta.validation.constraints.NotNull;

public interface TaskRepository extends JpaRepository<Task, Long> {
	// List<Task> findByUser_Id(Long userId);

	@Query("select t from Task t where t.user.id = :userId")
	List<Task> fetchTasks(@Param("userId") Long userId);

	@Query("""
			SELECT t FROM Task t
			WHERE t.user.id IN :userIds
			AND t.createdAt BETWEEN :fromDate AND :toDate
			""")
	List<Task> findByUserIdInAndCreatedAtBetween(
			@Param("userIds") List<Long> userIds,
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate);

	@Query("""
			    SELECT t FROM Task t
			    WHERE t.createdAt BETWEEN :fromDate AND :toDate
			""")
	List<Task> findByCreatedAtBetween(
			@Param("fromDate") @NotNull LocalDate fromDate,
			@Param("toDate") @NotNull LocalDate toDate);

	List<Task> findByUserId(Long userId);

	boolean existsByUserId(Long userId);

	long countByUserIdAndStatus(Long userId, com.entity.Status status);

	long countByUserId(Long userId);
}
