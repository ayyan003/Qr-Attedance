package com.attendance.dto;

import lombok.Data;

import java.util.List;

/**
 * What the student's browser/app sends after scanning the QR and
 * completing the face + liveness capture.
 */
@Data
public class AttendanceSubmitRequest {

    private Long studentId;
    private String qrToken;              // the signed rotating token read from the QR

    private List<Double> faceEmbedding;  // embedding computed on-device (face-api.js / mobile model)
    private boolean blinkDetected;       // liveness signal 1
    private boolean headTurnDetected;    // liveness signal 2

    private double latitude;
    private double longitude;
    private double gpsAccuracyMeters;    // from device Geolocation API
    private double deviceSpeedMps;       // instantaneous speed reported by device, if any
    private long fixTimestampEpochMs;
}
