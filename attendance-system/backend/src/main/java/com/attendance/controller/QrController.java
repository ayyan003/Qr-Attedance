package com.attendance.controller;

import com.attendance.dto.SessionCreateRequest;
import com.attendance.entity.AttendanceSession;
import com.attendance.repository.AttendanceSessionRepository;
import com.attendance.service.QrRotationService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/qr")
public class QrController {

    private final AttendanceSessionRepository sessionRepo;
    private final QrRotationService qrService;

    public QrController(AttendanceSessionRepository sessionRepo, QrRotationService qrService) {
        this.sessionRepo = sessionRepo;
        this.qrService = qrService;
    }

    /** Faculty starts a class -> creates a session with a geofence centre + radius. */
    @PostMapping("/session")
    public AttendanceSession createSession(@RequestBody SessionCreateRequest req) {
        AttendanceSession session = new AttendanceSession();
        session.setCourseName(req.getCourseName());
        session.setFacultyName(req.getFacultyName());
        session.setCenterLat(req.getCenterLat());
        session.setCenterLng(req.getCenterLng());
        session.setRadiusMeters(req.getRadiusMeters());
        session.setStartTime(Instant.now());
        session.setQrSecret(QrRotationService.generateSecret());
        session.setActive(true);
        return sessionRepo.save(session);
    }

    @PostMapping("/session/{id}/close")
    public AttendanceSession closeSession(@PathVariable Long id) {
        AttendanceSession session = sessionRepo.findById(id).orElseThrow();
        session.setActive(false);
        session.setEndTime(Instant.now());
        return sessionRepo.save(session);
    }

    /** Polled every few seconds by the teacher's display to redraw the QR. */
    @GetMapping("/{sessionId}/current")
    public Map<String, Object> currentToken(@PathVariable Long sessionId) {
        AttendanceSession session = sessionRepo.findById(sessionId).orElseThrow();
        String token = qrService.currentToken(session);
        return Map.of(
                "token", token,
                "rotatesEverySeconds", QrRotationService.ROTATION_SECONDS,
                "sessionId", sessionId
        );
    }
}
