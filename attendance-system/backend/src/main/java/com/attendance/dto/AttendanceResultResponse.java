package com.attendance.dto;

import com.attendance.entity.AttendanceRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AttendanceResultResponse {
    private boolean success;
    private AttendanceRecord.AttendanceStatus status;
    private String message;
    private double faceMatchScore;
}
