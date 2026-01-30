package com.controller;

import com.entity.LeaveRequest;
import com.service.LeaveService;
import com.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    @PostMapping("/request")
    public LeaveRequest createLeave(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody LeaveRequest leaveRequest) {
        return leaveService.createRequest(userDetails.getUserId(), leaveRequest);
    }

    @GetMapping("/my-history")
    public List<LeaveRequest> getMyLeaves(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return leaveService.getMyLeaves(userDetails.getUserId());
    }

    @GetMapping("/balance")
    public Map<String, Object> getBalance(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return leaveService.getLeaveBalance(userDetails.getUserId());
    }

    // Admin Endpoints

    @GetMapping("/admin/pending")
    public List<com.dto.LeaveRequestResponse> getPendingLeaves() {
        return leaveService.getAllPending();
    }

    public List<com.dto.LeaveRequestResponse> getLeaveHistory() {
        return leaveService.getAllHistory();
    }

    @PostMapping("/admin/search")
    public List<com.dto.LeaveRequestResponse> searchLeaves(@RequestBody com.dto.LeaveSearchRequest request) {
        return leaveService.searchLeaves(request);
    }

    @GetMapping("/admin/summaries")
    public java.util.List<com.dto.UserLeaveSummaryDto> getUserLeaveSummaries(
            @RequestParam(required = false) java.time.LocalDate fromDate,
            @RequestParam(required = false) java.time.LocalDate toDate) {
        return leaveService.getUserLeaveSummaries(fromDate, toDate);
    }

    @PutMapping("/admin/{id}/status")
    public LeaveRequest updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return leaveService.updateStatus(id, status);
    }
}
