package com.config;

import com.entity.LeaveType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LeaveTypeConverter implements AttributeConverter<LeaveType, String> {

    @Override
    public String convertToDatabaseColumn(LeaveType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public LeaveType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        // Handle legacy data
        if (dbData.equalsIgnoreCase("Casual")) {
            return LeaveType.CASUAL_LEAVE;
        }
        if (dbData.equalsIgnoreCase("Sick")) {
            return LeaveType.SICK_LEAVE;
        }

        // Handle standard enum names
        try {
            return LeaveType.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown LeaveType: " + dbData);
            return null;
        }
    }
}
