package com.attendance.repository;

import com.attendance.entity.AttendanceRecord;
import com.attendance.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByStudent(Student student);

    List<AttendanceRecord> findByStudentAndMarkedAtAfter(Student student, Instant after);

    List<AttendanceRecord> findBySession_Id(Long sessionId);

    boolean existsBySession_IdAndStudent_Id(Long sessionId, Long studentId);
}
