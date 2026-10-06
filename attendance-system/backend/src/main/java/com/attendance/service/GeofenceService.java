package com.attendance.service;

import com.attendance.entity.AttendanceSession;
import org.springframework.stereotype.Service;

/**
 * Two responsibilities:
 *  1. Classic geometric geofence check (haversine distance vs radius).
 *  2. Lightweight rule-based mock/fake-GPS detector:
 *       - GPS accuracy that's suspiciously "too perfect" (mock providers
 *         often report 0-1m accuracy, real phones rarely do indoors).
 *       - Implausible speed delta between consecutive fixes (teleporting
 *         into the geofence).
 *       - Accuracy far worse than typical, which combined with a suspicious
 *         speed jump also gets flagged.
 *  This isn't a replacement for a real device-integrity attestation
 *  (SafetyNet/Play Integrity), but catches the common "fake GPS app" case
 *  and is a reasonable place to plug in a stronger check later.
 */
@Service
public class GeofenceService {

    private static final double EARTH_RADIUS_M = 6_371_000;

    public double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    public boolean withinGeofence(AttendanceSession session, double lat, double lon) {
        return distanceMeters(session.getCenterLat(), session.getCenterLng(), lat, lon)
                <= session.getRadiusMeters();
    }

    public static class MockGpsVerdict {
        public boolean suspicious;
        public String reason;
        public MockGpsVerdict(boolean suspicious, String reason) {
            this.suspicious = suspicious;
            this.reason = reason;
        }
    }

    /**
     * @param accuracyMeters   accuracy reported by the device's location API
     * @param speedDeltaMps    |current speed - previous speed| between last two fixes
     * @param distanceJumpM    distance moved since previous fix
     * @param timeDeltaSeconds time between the two fixes
     */
    public MockGpsVerdict evaluateMockGps(double accuracyMeters, Double speedDeltaMps,
                                           Double distanceJumpM, Double timeDeltaSeconds) {
        // Rule 1: suspiciously perfect accuracy (common with mock-location apps)
        if (accuracyMeters <= 1.0) {
            return new MockGpsVerdict(true, "Suspiciously perfect GPS accuracy (<=1m)");
        }

        // Rule 2: implied speed between fixes is physically implausible for a
        // student walking/standing (e.g. > 40 m/s ~ 144 km/h "teleport")
        if (distanceJumpM != null && timeDeltaSeconds != null && timeDeltaSeconds > 0) {
            double impliedSpeed = distanceJumpM / timeDeltaSeconds;
            if (impliedSpeed > 40.0) {
                return new MockGpsVerdict(true, "Implausible position jump (" +
                        String.format("%.1f", impliedSpeed) + " m/s)");
            }
        }

        // Rule 3: large sudden speed delta while accuracy is unrealistically tight
        if (speedDeltaMps != null && speedDeltaMps > 15 && accuracyMeters < 5) {
            return new MockGpsVerdict(true, "Sudden speed change with tight accuracy - typical of spoofers");
        }

        return new MockGpsVerdict(false, "OK");
    }
}
