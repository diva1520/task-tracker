package com.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.entity.TaskDetail;

public interface TaskDetailRepository extends JpaRepository<TaskDetail, Long> {

	@Query("SELECT td FROM TaskDetail td"
			+ " WHERE td.task.id = :taskId AND td.status = com.entity.Status.IN_PROGRESS "
			+ " ORDER BY td.startedAt DESC")
	List<TaskDetail> findLatestInProgress(@Param("taskId") Long taskId);

	@Query("""
			SELECT td FROM TaskDetail td
			WHERE td.task.id = :taskId
			ORDER BY td.startedAt DESC
			""")
	List<TaskDetail> findAllByTaskId(Long taskId);

	List<TaskDetail> findByTask_Id(Long taskId);

	List<TaskDetail> findByTask_IdIn(List<Long> taskIds);

	boolean existsByTask_IdAndStatus(Long taskId, com.entity.Status status);

}
