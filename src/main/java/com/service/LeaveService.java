package com.service;

import com.entity.LeaveRequest;
import com.entity.User;
import com.repo.LeaveRepository;
import com.repo.UserRepository;
import com.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaveService {

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    public LeaveRequest createRequest(Long userId, LeaveRequest leaveRequest) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        // Check for overlaps
        boolean overlap = leaveRepository.existsByOverlap(userId, leaveRequest.getFromDate(), leaveRequest.getToDate());
        if (overlap) {
            throw new RuntimeException("Leave request overlaps with an existing request!");
        }

        leaveRequest.setUser(user);
        leaveRequest.setStatus("PENDING");
        LeaveRequest savedLeave = leaveRepository.save(leaveRequest);

        // Notify Admins
        List<User> admins = userRepository.findByRole(Role.ROLE_ADMIN);
        for (User admin : admins) {
            String subject = "New Leave Request from " + user.getUsername();
            String body = "<h3>New Leave Request</h3>" +
                    "<p><b>User:</b> " + user.getUsername() + "</p>" +
                    "<p><b>Reason:</b> " + leaveRequest.getReason() + "</p>" +
                    "<p><b>Days:</b> " + leaveRequest.getDays() + "</p>" +
                    "<p>Please log in to approve/reject.</p>";
            if (admin.getEmail() != null && !admin.getEmail().isEmpty()) {
                emailService.sendMail(admin.getEmail(), subject, body);
            }
        }

        return savedLeave;
    }

    public List<LeaveRequest> getMyLeaves(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return leaveRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<com.dto.LeaveRequestResponse> getAllPending() {
        return leaveRepository.findAllPending().stream().map(l -> {
            Map<String, Object> balance = getLeaveBalance(l.getUser().getId());
            return new com.dto.LeaveRequestResponse(l, balance);
        }).collect(java.util.stream.Collectors.toList());
    }

    public List<com.dto.LeaveRequestResponse> getAllHistory() {
        return leaveRepository.findAllHistory().stream().map(l -> {
            Map<String, Object> balance = getLeaveBalance(l.getUser().getId());
            return new com.dto.LeaveRequestResponse(l, balance);
        }).collect(java.util.stream.Collectors.toList());
    }

    public List<com.dto.LeaveRequestResponse> searchLeaves(com.dto.LeaveSearchRequest request) {
        List<Long> userIds = (request.getUserIds() == null || request.getUserIds().isEmpty()) ? null
                : request.getUserIds();

        return leaveRepository.searchLeaves(userIds, request.getFromDate(), request.getToDate())
                .stream().map(l -> {
                    Map<String, Object> balance = getLeaveBalance(l.getUser().getId());
                    return new com.dto.LeaveRequestResponse(l, balance);
                }).collect(java.util.stream.Collectors.toList());
    }

    public LeaveRequest updateStatus(Long leaveId, String status) {
        LeaveRequest leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found"));
        leave.setStatus(status);
        LeaveRequest savedLeave = leaveRepository.save(leave);

        // Notify User
        User user = savedLeave.getUser();
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            Map<String, Object> balanceInfo = getLeaveBalance(user.getId());

            String subject = "Leave Request " + status;
            String body = "<h3>Your Leave Request has been " + status + "</h3>" +
                    "<p><b>Reason:</b> " + savedLeave.getReason() + "</p>" +
                    "<p><b>Days:</b> " + savedLeave.getDays() + "</p>" +
                    "<br/>" +
                    "<h4>Leave Status:</h4>" +
                    "<ul>" +
                    "<li><b>Total Allowed:</b> " + balanceInfo.get("allowed") + "</li>" +
                    "<li><b>Taken (Approved):</b> " + balanceInfo.get("taken") + "</li>" +
                    "<li><b>Remaining Balance:</b> " + balanceInfo.get("balance") + "</li>" +
                    "<li><b>Loss of Pay (LOP) Days:</b> " + balanceInfo.get("lop") + "</li>" +
                    "</ul>";

            emailService.sendMail(user.getEmail(), subject, body);
        }

        return savedLeave;
    }

    public Map<String, Object> getLeaveBalance(Long userId) {
        List<LeaveRequest> approved = leaveRepository.findApprovedLeavesForCurrentYear(userId);

        double occupiedDays = approved.stream().mapToDouble(LeaveRequest::getDays).sum();
        double totalAllowed = 12.0; // Per year

        Map<String, Object> response = new HashMap<>();
        response.put("allowed", totalAllowed);
        response.put("taken", occupiedDays);
        response.put("balance", Math.max(0, totalAllowed - occupiedDays));
        response.put("lop", Math.max(0, occupiedDays - totalAllowed));

        return response;
    }

    public List<com.dto.UserLeaveSummaryDto> getUserLeaveSummaries(java.time.LocalDate from, java.time.LocalDate to) {
        List<User> users;
        if (from != null || to != null) {
            users = leaveRepository.findUsersWithLeavesInRange(from, to);
        } else {
            users = userRepository.findAll();
        }

        return users.stream().map(u -> {
            Map<String, Object> balance = getLeaveBalance(u.getId());
            return new com.dto.UserLeaveSummaryDto(
                    u.getId(),
                    u.getUsername(),
                    (Double) balance.get("taken"),
                    (Double) balance.get("lop"),
                    (Double) balance.get("balance"),
                    (Double) balance.get("allowed"));
        }).collect(java.util.stream.Collectors.toList());
    }
}
