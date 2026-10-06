package com.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "students")
@Data
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String rollNumber;

    private String name;
    private String email;
    private String passwordHash; // bcrypt

    /**
     * 128-d (or 512-d, depending on model) face embedding captured at
     * enrollment time, stored as a comma-separated string. Kept simple
     * on purpose so this can run without a vector-DB extension.
     * In production, move this to pgvector / a proper vector column.
     */
    @Column(columnDefinition = "TEXT")
    private String faceEmbedding;

    private boolean enrolled = false;
}
