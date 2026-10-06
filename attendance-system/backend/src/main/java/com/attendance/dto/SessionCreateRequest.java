package com.attendance.dto;

import lombok.Data;

@Data
public class SessionCreateRequest {
    private String courseName;
    private String facultyName;
    private double centerLat;
    private double centerLng;
    private double radiusMeters;
}
