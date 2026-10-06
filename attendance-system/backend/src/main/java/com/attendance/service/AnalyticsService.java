package com.attendance.service;

import com.attendance.entity.AttendanceRecord;
import com.attendance.entity.Student;
import com.attendance.repository.AttendanceRecordRepository;
import com.attendance.repository.AttendanceSessionRepository;
import com.attendance.repository.StudentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight, dependency-free "AI insights" layer on top of the
 * attendance records already being stored - no separate ML service
 * required. It does three things:
 *
 *  1. Chronic absenteeism flag: attendance rate below a threshold
 *     over the whole term.
 *  2. At-risk prediction: simple trend detection - compares the
 *     student's attendance rate in the most recent window vs their
 *     historical average. A meaningfully worsening trend flags them
 *     as "at risk" even if their overall rate is still okay-ish.
 *     (This is a transparent, explainable stand-in for a trained
 *     classifier - swap runComputeAtRiskScore() for a call to a real
 *     model/service later without touching the rest of the pipeline.)
 *  3. Weekly summary report generation for faculty.
 */
@Service
public class AnalyticsService {

    private static final double CHRONIC_ABSENTEEISM_THRESHOLD = 0.75; // below 75% attendance
    private static final double AT_RISK_DROP_THRESHOLD = 0.20;        // 20pt drop vs historical avg

    private final StudentRepository studentRepo;
    private final AttendanceRecordRepository recordRepo;
    private final AttendanceSessionRepository sessionRepo;

    public AnalyticsService(StudentRepository studentRepo, AttendanceRecordRepository recordRepo,
                             AttendanceSessionRepository sessionRepo) {
        this.studentRepo = studentRepo;
        this.recordRepo = recordRepo;
        this.sessionRepo = sessionRepo;
    }

    public record StudentInsight(Long studentId, String name, String rollNumber,
                                  double overallAttendanceRate, double recentAttendanceRate,
                                  boolean chronicAbsentee, boolean atRisk, String note) {}

    public List<StudentInsight> computeInsights() {
        long totalSessions = sessionRepo.count();
        List<Student> students = studentRepo.findAll();
        List<StudentInsight> insights = new ArrayList<>();

        Instant recentCutoff = Instant.now().minus(14, ChronoUnit.DAYS);

        for (Student s : students) {
            List<AttendanceRecord> all = recordRepo.findByStudent(s);
            long presentCount = all.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT).count();
            double overallRate = totalSessions == 0 ? 1.0 : (double) presentCount / totalSessions;

            List<AttendanceRecord> recent = recordRepo.findByStudentAndMarkedAtAfter(s, recentCutoff);
            long recentPresent = recent.stream().filter(r -> r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT).count();
            double recentRate = recent.isEmpty() ? overallRate : (double) recentPresent / Math.max(recent.size(), 1);

            boolean chronic = overallRate < CHRONIC_ABSENTEEISM_THRESHOLD;
            boolean atRisk = (overallRate - recentRate) >= AT_RISK_DROP_THRESHOLD;

            String note;
            if (chronic && atRisk) note = "Chronically absent AND trending worse - prioritize outreach";
            else if (chronic) note = "Below 75% overall attendance";
            else if (atRisk) note = "Recent attendance dropping sharply vs historical average";
            else note = "On track";

            insights.add(new StudentInsight(s.getId(), s.getName(), s.getRollNumber(),
                    round(overallRate), round(recentRate), chronic, atRisk, note));
        }

        // Chronic/at-risk students surfaced first for faculty
        insights.sort(Comparator.comparing((StudentInsight i) -> !(i.chronicAbsentee() || i.atRisk())));
        return insights;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public record WeeklySummary(Instant generatedAt, long totalStudents, long chronicCount,
                                 long atRiskCount, double averageAttendanceRate,
                                 List<StudentInsight> flaggedStudents) {}

    public WeeklySummary generateWeeklySummary() {
        List<StudentInsight> insights = computeInsights();
        long chronic = insights.stream().filter(StudentInsight::chronicAbsentee).count();
        long atRisk = insights.stream().filter(StudentInsight::atRisk).count();
        double avg = insights.stream().mapToDouble(StudentInsight::overallAttendanceRate).average().orElse(0);
        List<StudentInsight> flagged = insights.stream()
                .filter(i -> i.chronicAbsentee() || i.atRisk())
                .collect(Collectors.toList());
        return new WeeklySummary(Instant.now(), insights.size(), chronic, atRisk, round(avg), flagged);
    }

    // In-memory store of the latest generated report so the endpoint
    // can just serve it without recomputing on every request.
    private volatile WeeklySummary lastReport;

    public WeeklySummary getLastReport() {
        return lastReport != null ? lastReport : generateWeeklySummary();
    }

    /** Runs every Monday at 06:00 server time; also callable on-demand via the controller. */
    @Scheduled(cron = "0 0 6 * * MON")
    public void scheduledWeeklyReport() {
        lastReport = generateWeeklySummary();
    }
}
