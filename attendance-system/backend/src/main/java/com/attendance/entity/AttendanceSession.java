package com.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "attendance_sessions")
@Data
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String courseName;
    private String facultyName;

    // Geofence: circular boundary (centre + radius)
    private double centerLat;
    private double centerLng;
    private double radiusMeters;

    private Instant startTime;
    private Instant endTime;

    /**
     * Per-session random secret used to sign/verify the rotating QR
     * tokens for THIS session only, so a token from one class can't be
     * replayed in another.
     */
    @Column(nullable = false)
    private String qrSecret;

    private boolean active = true;
}
