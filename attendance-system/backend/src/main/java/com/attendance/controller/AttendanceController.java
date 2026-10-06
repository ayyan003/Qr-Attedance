package com.attendance.controller;

import com.attendance.dto.AttendanceResultResponse;
import com.attendance.dto.AttendanceSubmitRequest;
import com.attendance.entity.AttendanceRecord;
import com.attendance.repository.AttendanceRecordRepository;
import com.attendance.service.AttendanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceRecordRepository recordRepo;

    public AttendanceController(AttendanceService attendanceService, AttendanceRecordRepository recordRepo) {
        this.attendanceService = attendanceService;
        this.recordRepo = recordRepo;
    }

    @PostMapping("/{sessionId}/submit")
    public AttendanceResultResponse submit(@PathVariable Long sessionId, @RequestBody AttendanceSubmitRequest req) {
        return attendanceService.submit(sessionId, req);
    }

    @GetMapping("/session/{sessionId}/records")
    public List<AttendanceRecord> records(@PathVariable Long sessionId) {
        return recordRepo.findBySession_Id(sessionId);
    }
}
