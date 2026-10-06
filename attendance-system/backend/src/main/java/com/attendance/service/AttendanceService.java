package com.attendance.service;

import com.attendance.dto.AttendanceResultResponse;
import com.attendance.dto.AttendanceSubmitRequest;
import com.attendance.entity.AttendanceRecord;
import com.attendance.entity.AttendanceSession;
import com.attendance.entity.Student;
import com.attendance.repository.AttendanceRecordRepository;
import com.attendance.repository.AttendanceSessionRepository;
import com.attendance.repository.StudentRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepo;
    private final StudentRepository studentRepo;
    private final AttendanceRecordRepository recordRepo;
    private final QrRotationService qrService;
    private final GeofenceService geofenceService;
    private final FaceVerificationService faceService;

    // last-known GPS fix per student, purely in-memory - used only to
    // compute the speed-delta / distance-jump mock-GPS heuristic.
    private final Map<Long, double[]> lastFix = new HashMap<>(); // studentId -> [lat, lon, speed, epochSeconds]

    public AttendanceService(AttendanceSessionRepository sessionRepo, StudentRepository studentRepo,
                              AttendanceRecordRepository recordRepo, QrRotationService qrService,
                              GeofenceService geofenceService, FaceVerificationService faceService) {
        this.sessionRepo = sessionRepo;
        this.studentRepo = studentRepo;
        this.recordRepo = recordRepo;
        this.qrService = qrService;
        this.geofenceService = geofenceService;
        this.faceService = faceService;
    }

    @Transactional
    public AttendanceResultResponse submit(Long sessionId, AttendanceSubmitRequest req) {
        AttendanceSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.isActive()) {
            return new AttendanceResultResponse(false, AttendanceRecord.AttendanceStatus.REJECTED_QR_EXPIRED,
                    "Session is closed", 0);
        }

        Student student = studentRepo.findById(req.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (recordRepo.existsBySession_IdAndStudent_Id(sessionId, student.getId())) {
            return new AttendanceResultResponse(false, null, "Attendance already marked for this session", 0);
        }

        // 1) Validate rotating QR token
        Claims claims;
        try {
            claims = qrService.validate(session, req.getQrToken());
        } catch (JwtException e) {
            return new AttendanceResultResponse(false, AttendanceRecord.AttendanceStatus.REJECTED_QR_EXPIRED,
                    "QR code expired or invalid - rescan the current code", 0);
        }
        Long sidInToken = ((Number) claims.get("sid")).longValue();
        if (!sidInToken.equals(sessionId)) {
            return new AttendanceResultResponse(false, AttendanceRecord.AttendanceStatus.REJECTED_QR_EXPIRED,
                    "QR code belongs to a different session", 0);
        }

        // 2) Face match
        double faceScore = faceService.score(student, req.getFaceEmbedding());
        if (faceScore < FaceVerificationService.MATCH_THRESHOLD) {
            return persistAndReturn(session, student, req, faceScore, false, null, true,
                    AttendanceRecord.AttendanceStatus.REJECTED_FACE, "Face did not match enrolled profile");
        }

        // 3) Liveness (blink + head-turn challenge, both required)
        boolean livenessPassed = req.isBlinkDetected() && req.isHeadTurnDetected();
        if (!livenessPassed) {
            return persistAndReturn(session, student, req, faceScore, false, null, true,
                    AttendanceRecord.AttendanceStatus.REJECTED_LIVENESS,
                    "Liveness check failed - possible photo/video spoof");
        }

        // 4) Mock-GPS heuristic
        double[] prev = lastFix.get(student.getId());
        Double speedDelta = null, distanceJump = null, timeDelta = null;
        if (prev != null) {
            speedDelta = Math.abs(req.getDeviceSpeedMps() - prev[2]);
            distanceJump = geofenceService.distanceMeters(prev[0], prev[1], req.getLatitude(), req.getLongitude());
            timeDelta = (req.getFixTimestampEpochMs() / 1000.0) - prev[3];
        }
        GeofenceService.MockGpsVerdict gpsVerdict = geofenceService.evaluateMockGps(
                req.getGpsAccuracyMeters(), speedDelta, distanceJump, timeDelta);
        lastFix.put(student.getId(), new double[]{req.getLatitude(), req.getLongitude(),
                req.getDeviceSpeedMps(), req.getFixTimestampEpochMs() / 1000.0});

        if (gpsVerdict.suspicious) {
            return persistAndReturn(session, student, req, faceScore, true, gpsVerdict, true,
                    AttendanceRecord.AttendanceStatus.REJECTED_GPS_SPOOF, "Suspicious GPS signal: " + gpsVerdict.reason);
        }

        // 5) Geofence
        double distance = geofenceService.distanceMeters(session.getCenterLat(), session.getCenterLng(),
                req.getLatitude(), req.getLongitude());
        if (distance > session.getRadiusMeters()) {
            return persistAndReturn(session, student, req, faceScore, true, gpsVerdict, false,
                    AttendanceRecord.AttendanceStatus.REJECTED_GEOFENCE,
                    "Outside the classroom geofence (" + String.format("%.0f", distance) + "m away)");
        }

        // All checks passed
        return persistAndReturn(session, student, req, faceScore, true, gpsVerdict, false,
                AttendanceRecord.AttendanceStatus.PRESENT, "Attendance marked successfully");
    }

    private AttendanceResultResponse persistAndReturn(AttendanceSession session, Student student,
            AttendanceSubmitRequest req, double faceScore, boolean livenessPassed,
            GeofenceService.MockGpsVerdict gpsVerdict, boolean skipDistanceCalc,
            AttendanceRecord.AttendanceStatus status, String message) {

        AttendanceRecord record = new AttendanceRecord();
        record.setSession(session);
        record.setStudent(student);
        record.setMarkedAt(Instant.now());
        record.setFaceMatchScore(faceScore);
        record.setLivenessPassed(livenessPassed);
        record.setGpsAccuracyMeters(req.getGpsAccuracyMeters());
        record.setGpsSuspicious(gpsVerdict != null && gpsVerdict.suspicious);
        record.setDistanceFromCenterM(skipDistanceCalc ? -1 :
                geofenceService.distanceMeters(session.getCenterLat(), session.getCenterLng(),
                        req.getLatitude(), req.getLongitude()));
        record.setStatus(status);
        recordRepo.save(record);

        boolean success = status == AttendanceRecord.AttendanceStatus.PRESENT;
        return new AttendanceResultResponse(success, status, message, faceScore);
    }
}
