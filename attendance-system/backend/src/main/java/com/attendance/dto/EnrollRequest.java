package com.attendance.dto;

import lombok.Data;

import java.util.List;

@Data
public class EnrollRequest {
    private String rollNumber;
    private String name;
    private String email;
    private String password;
    private List<Double> faceEmbedding; // captured during a guided enrollment flow
}
