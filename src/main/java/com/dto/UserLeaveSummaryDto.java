package com.dto;

import lombok.Data;

@Data
public class UserLeaveSummaryDto {
    private Long userId;
    private String username;
    private Double taken;
    private Double lop;
    private Double balance;
    private Double allowed;

    public UserLeaveSummaryDto(Long userId, String username, Double taken, Double lop, Double balance, Double allowed) {
        this.userId = userId;
        this.username = username;
        this.taken = taken;
        this.lop = lop;
        this.balance = balance;
        this.allowed = allowed;
    }
}
