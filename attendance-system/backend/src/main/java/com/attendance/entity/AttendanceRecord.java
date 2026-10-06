package com.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "attendance_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "student_id"}))
@Data
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    private AttendanceSession session;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    private Instant markedAt;

    // --- verification evidence, kept for audit / anti-fraud review ---
    private double faceMatchScore;      // cosine similarity 0..1
    private boolean livenessPassed;     // blink + head-turn challenge result
    private double distanceFromCenterM; // geofence check
    private boolean gpsSuspicious;      // mock-GPS heuristic flag
    private double gpsAccuracyMeters;
    private Double deviceSpeedDelta;    // m/s change vs previous known fix

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    public enum AttendanceStatus { PRESENT, REJECTED_FACE, REJECTED_LIVENESS, REJECTED_GEOFENCE, REJECTED_GPS_SPOOF, REJECTED_QR_EXPIRED }
}
