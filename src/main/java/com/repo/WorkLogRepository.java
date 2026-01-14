package com.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.entity.WorkLog;

@Repository
public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {
    List<WorkLog> findByUserId(Long userId);

    List<WorkLog> findByTaskId(Long taskId);
}
