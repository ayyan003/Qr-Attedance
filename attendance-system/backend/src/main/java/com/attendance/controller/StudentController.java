package com.attendance.controller;

import com.attendance.dto.EnrollRequest;
import com.attendance.entity.Student;
import com.attendance.repository.StudentRepository;
import com.attendance.service.FaceVerificationService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentRepository studentRepo;
    private final FaceVerificationService faceService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public StudentController(StudentRepository studentRepo, FaceVerificationService faceService) {
        this.studentRepo = studentRepo;
        this.faceService = faceService;
    }

    /** One-time enrollment: capture face embedding + basic profile. */
    @PostMapping("/enroll")
    public Student enroll(@RequestBody EnrollRequest req) {
        Student student = studentRepo.findByRollNumber(req.getRollNumber()).orElseGet(Student::new);
        student.setRollNumber(req.getRollNumber());
        student.setName(req.getName());
        student.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            student.setPasswordHash(encoder.encode(req.getPassword()));
        }
        student.setFaceEmbedding(faceService.serializeEmbedding(req.getFaceEmbedding()));
        student.setEnrolled(true);
        return studentRepo.save(student);
    }

    @GetMapping
    public List<Student> all() {
        return studentRepo.findAll();
    }

    @GetMapping("/{id}")
    public Student get(@PathVariable Long id) {
        return studentRepo.findById(id).orElseThrow();
    }
}
