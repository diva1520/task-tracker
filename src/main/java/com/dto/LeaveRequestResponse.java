package com.dto;

import com.entity.LeaveRequest;
import java.util.Map;

public class LeaveRequestResponse {
    private LeaveRequest leaveRequest;
    private double balance;
    private double lopDays;
    private double allowed;
    private boolean isLop;

    public LeaveRequestResponse(LeaveRequest leaveRequest, Map<String, Object> balanceInfo) {
        this.leaveRequest = leaveRequest;
        if (balanceInfo != null) {
            this.balance = (double) balanceInfo.get("balance");
            this.allowed = (double) balanceInfo.get("allowed");
            double taken = (double) balanceInfo.get("taken");

            // Calculate impact of THIS request
            double currentReqDays = leaveRequest.getDays();
            double totalAfterRequest;
            double totalBeforeRequest;

            if ("APPROVED".equalsIgnoreCase(leaveRequest.getStatus())) {
                // If already approved, 'taken' includes this request
                totalAfterRequest = taken;
                totalBeforeRequest = taken - currentReqDays;
            } else {
                // If pending/rejected, 'taken' does not include this request
                totalAfterRequest = taken + currentReqDays;
                totalBeforeRequest = taken;
            }

            this.isLop = totalAfterRequest > this.allowed;

            double afterLop = Math.max(0, totalAfterRequest - this.allowed);
            double beforeLop = Math.max(0, totalBeforeRequest - this.allowed);

            // Set lopDays to the IMPACT of this request, not the total
            this.lopDays = afterLop - beforeLop;
        } else {
            this.isLop = false;
        }
    }

    // Getters
    public LeaveRequest getLeaveRequest() {
        return leaveRequest;
    }

    public double getBalance() {
        return balance;
    }

    public double getLopDays() {
        return lopDays;
    }

    public double getAllowed() {
        return allowed;
    }

    public boolean getIsLop() {
        return isLop;
    }
}
