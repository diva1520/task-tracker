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

        @Query("SELECT l FROM LeaveRequest l WHERE l.status <> 'PENDING' ORDER BY l.createdAt DESC")
        List<LeaveRequest> findAllHistory();

        @Query("SELECT l FROM LeaveRequest l WHERE l.user.id = :userId AND l.status = 'APPROVED' AND YEAR(l.fromDate) = YEAR(CURRENT_DATE)")
        List<LeaveRequest> findApprovedLeavesForCurrentYear(@Param("userId") Long userId);

        @Query("SELECT l FROM LeaveRequest l WHERE l.status = 'APPROVED' AND :date BETWEEN l.fromDate AND l.toDate")
        List<LeaveRequest> findActiveLeaves(@Param("date") java.time.LocalDate date);

        @Query("SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.user.id = :userId " +
                        "AND l.status IN ('PENDING', 'APPROVED') " +
                        "AND (:toDate >= l.fromDate AND :fromDate <= l.toDate)")
        boolean existsByOverlap(@Param("userId") Long userId,
                        @Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);

        @Query("SELECT l FROM LeaveRequest l WHERE " +
                        "(:userIds IS NULL OR l.user.id IN :userIds) AND " +
                        "(CAST(:fromDate AS date) IS NULL OR CAST(:toDate AS date) IS NULL OR (l.fromDate <= :toDate AND l.toDate >= :fromDate)) "
                        +
                        "ORDER BY l.createdAt DESC")
        List<LeaveRequest> searchLeaves(@Param("userIds") List<Long> userIds,
                        @Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);

        @Query("SELECT DISTINCT l.user FROM LeaveRequest l WHERE " +
                        "(:fromDate IS NULL OR :toDate IS NULL OR (l.fromDate <= :toDate AND l.toDate >= :fromDate))")
        List<User> findUsersWithLeavesInRange(@Param("fromDate") java.time.LocalDate fromDate,
                        @Param("toDate") java.time.LocalDate toDate);
}
