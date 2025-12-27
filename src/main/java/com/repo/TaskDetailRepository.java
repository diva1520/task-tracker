package com.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.entity.TaskDetail;

public interface TaskDetailRepository extends JpaRepository<TaskDetail, Long> {

	@Query("""
			   SELECT td FROM TaskDetail td
			   WHERE td.task.id = :taskId
			     AND td.status = com.entity.Status.IN_PROGRESS
			   ORDER BY td.startedAt DESC
			""")
			TaskDetail findLatestInProgress(@Param("taskId") Long taskId);


}
