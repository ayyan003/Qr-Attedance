package com.attendance.service;

import com.attendance.entity.Student;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Identity verification step.
 *
 * Design choice: the actual face-detection + embedding model runs
 * ON-DEVICE (browser: face-api.js / TensorFlow.js, or mobile: ML Kit +
 * a small FaceNet/MobileFaceNet TFLite model). This keeps raw photos
 * off the wire entirely - only a small numeric embedding vector
 * (128 or 512 floats) is sent to the server, which is enough for
 * matching but not enough to reconstruct the face.
 *
 * The backend's job is just: compare the incoming embedding to the
 * one captured at enrollment using cosine similarity, and combine that
 * with the liveness flags the client reports (blink + head-turn
 * challenge, or equivalently an active liveness SDK's pass/fail).
 *
 * Swap point: replace matches()/similarity() with a call to a cloud
 * face API (Azure Face, AWS Rekognition, etc.) if you'd rather not
 * ship a model to the client - the rest of the pipeline is unchanged.
 */
@Service
public class FaceVerificationService {

    // Cosine similarity threshold - tune against your enrollment data.
    // 0.90+ is typical for well-lit, front-facing MobileFaceNet-style embeddings.
    public static final double MATCH_THRESHOLD = 0.90;

    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public List<Double> parseStoredEmbedding(Student student) {
        if (student.getFaceEmbedding() == null || student.getFaceEmbedding().isBlank()) {
            return List.of();
        }
        return Arrays.stream(student.getFaceEmbedding().split(","))
                .map(Double::parseDouble)
                .collect(Collectors.toList());
    }

    public String serializeEmbedding(List<Double> embedding) {
        return embedding.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean matches(Student student, List<Double> capturedEmbedding) {
        return score(student, capturedEmbedding) >= MATCH_THRESHOLD;
    }

    public double score(Student student, List<Double> capturedEmbedding) {
        return cosineSimilarity(parseStoredEmbedding(student), capturedEmbedding);
    }
}
