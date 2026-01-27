package com.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
@Data
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDate fromDate;
    private LocalDate toDate;

    // CASUAL, SICK
    @Convert(converter = com.config.LeaveTypeConverter.class)
    private LeaveType leaveType;

    private String reason;

    // PENDING, APPROVED, REJECTED
    private String status = "PENDING";

    private Boolean halfDay = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Helper to calculate days
    public double getDays() {
        if (fromDate == null || toDate == null)
            return 0;
        if (Boolean.TRUE.equals(halfDay))
            return 0.5;
        return java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    }
}
