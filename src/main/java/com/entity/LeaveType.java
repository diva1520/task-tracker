package com.entity;

public enum LeaveType {
    CASUAL_LEAVE("Casual Leave"),
    SICK_LEAVE("Sick Leave");

    private final String displayName;

    LeaveType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
