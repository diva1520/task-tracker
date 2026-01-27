package com.repo;

import com.entity.LeaveRequest;
import com.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByUserOrderByCreatedAtDesc(User user);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT l FROM LeaveRequest l WHERE l.status = 'PENDING' ORDER BY l.createdAt DESC")
    List<LeaveRequest> findAllPending();

    @Query("SELECT l FROM LeaveRequest l WHERE l.user.id = :userId AND l.status = 'APPROVED' AND YEAR(l.fromDate) = YEAR(CURRENT_DATE)")
    List<LeaveRequest> findApprovedLeavesForCurrentYear(@Param("userId") Long userId);
}
