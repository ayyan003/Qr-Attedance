# Smart Attendance System

Rotating signed QR + on-device face match & liveness + geofencing with
mock-GPS detection + attendance analytics, built on Spring Boot.

## Architecture

```
frontend/                          (plain HTML/JS - open directly in a browser)
  teacher-display.html             faculty starts a session, shows a QR that
                                    re-signs itself every 12s
  student-scan.html                student's flow: scan QR -> face match +
                                    liveness (blink + head-turn) -> GPS -> submit
  enroll.html                      one-time face enrollment (captures embedding)
  faculty-dashboard.html           chronic-absenteeism / at-risk / weekly report view

backend/                           Spring Boot 3 (Java 17)
  entity/      Student, AttendanceSession, AttendanceRecord (JPA)
  service/
    QrRotationService              signs/validates short-lived per-session QR tokens (JWT/HMAC)
    GeofenceService                haversine distance + mock-GPS heuristics
    FaceVerificationService        cosine similarity match against enrolled embedding
    AttendanceService              orchestrates the full verify-and-mark pipeline
    AnalyticsService                chronic absenteeism / at-risk trend / weekly summaries
  controller/  REST endpoints (see below)
```

### Why face matching happens in the browser, not the server

The actual face-detection + embedding model (`face-api.js`, a
TensorFlow.js port of a small FaceNet-style network) runs **on the
student's device**. Only a 128-number embedding vector is sent to the
backend - never a photo. The backend just compares that vector to the
one captured at enrollment with cosine similarity. This matches the
"small on-device model" design in the spec, keeps images off the
network entirely, and is the same architecture ML Kit + a TFLite model
would give you on native Android/iOS - `enroll.html` and
`student-scan.html` are a working reference implementation; swapping
them for a native mobile app later only means re-implementing the same
two calls (`/api/students/enroll`, `/api/attendance/{id}/submit`) with
a native camera + TFLite pipeline instead of `face-api.js`.

### Why the device registry disappears

Liveness (blink detection via eye-aspect-ratio + a head-turn challenge
via yaw-ratio tracking, both computed from face landmarks) plus a
face match against the enrolled embedding is a stronger anti-proxy
signal than "is this a registered device," so that separate table is
gone, exactly as the spec asks.

### Rotating QR

`QrRotationService` signs a JWT-style token per session (HMAC, unique
secret per session) with a fixed **12-second** validity bucket. The
teacher's display polls `GET /api/qr/{sessionId}/current` every 12s
and redraws the QR. A photo of the screen is only valid for the
remainder of that 12s window - `AttendanceService` also rejects a
token whose `sid` claim doesn't match the session being submitted to,
so a token can't be replayed cross-session either.

### Geofencing + mock-GPS

`GeofenceService` does two independent checks:
1. Haversine distance from the session's registered center vs radius.
2. A rule-based mock-GPS detector: suspiciously perfect accuracy
   (≤1m, common with GPS-spoofing apps), physically-implausible
   position jumps between fixes (>40 m/s implied speed), and sudden
   speed changes paired with unrealistically tight accuracy. This
   isn't a substitute for OS-level attestation (Play Integrity /
   DeviceCheck) - it's the "simple AI/rule-based check" the spec asks
   for, and it's a clean place to slot in a trained classifier later
   without touching the rest of the pipeline.

### Analytics (additive, zero extra student effort)

`AnalyticsService` computes, straight from `AttendanceRecord` rows
already being stored:
- **Chronic absenteeism**: overall attendance rate < 75%.
- **At-risk prediction**: a simple, explainable trend check - recent
  (14-day) attendance rate vs overall historical rate; a ≥20-point
  drop flags the student even if their overall rate still looks okay.
  This is intentionally transparent instead of a black-box model; swap
  `computeInsights()` for a call to a trained classifier later if you
  want a fancier model - the endpoints don't change.
- **Weekly summary report**: auto-regenerates every Monday 6am via
  `@Scheduled`, also regenerable on demand for faculty.

## API reference

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/qr/session` | Faculty starts a session (course, geofence center + radius) |
| POST | `/api/qr/session/{id}/close` | Ends a session |
| GET  | `/api/qr/{sessionId}/current` | Current rotating token (poll every 12s) |
| POST | `/api/students/enroll` | One-time face enrollment |
| GET  | `/api/students` | List students |
| POST | `/api/attendance/{sessionId}/submit` | Student submits scan + face + liveness + GPS |
| GET  | `/api/attendance/session/{sessionId}/records` | Raw records for a session (audit) |
| GET  | `/api/analytics/insights` | Per-student chronic/at-risk flags |
| GET  | `/api/analytics/weekly-summary` | Latest weekly report |
| POST | `/api/analytics/weekly-summary/regenerate` | Force-regenerate the report |

## Running it

**Backend** (needs Java 17+ and internet access to pull Maven
dependencies the first time - this was written and organized in an
offline sandbox, so build it locally):

```bash
cd backend
mvn spring-boot:run
```

It boots on `http://localhost:8080` with an in-memory H2 database (see
`application.yml` for the commented Postgres block - uncomment and set
`DB_USER`/`DB_PASSWORD` env vars for production). H2 console is at
`/h2-console` if you want to inspect data live.

**Frontend**: just open the HTML files directly in a browser (or serve
`frontend/` with any static server - camera/geolocation APIs need
either `localhost` or `https://` to work, both of which are fine).

1. Open `enroll.html` on each student's device, capture their face once.
2. Open `teacher-display.html` on the classroom projector/screen,
   fill in the geofence center (use the room's actual lat/lng) and
   radius, start the session.
3. Students open `student-scan.html`, enter their student ID, and scan.
4. Faculty open `faculty-dashboard.html` any time to see chronic /
   at-risk flags and the weekly summary.

## Known simplifications (call these out if presenting this as final)

- Auth is wide open for the demo (`SecurityConfig` permits all
  requests) - real deployment needs login-gated endpoints and CORS
  locked to your real frontend origin; the JWT dependency is already
  in `pom.xml` for that.
- The mock-GPS detector is heuristic, not a trained model - documented
  above as an intentional, explainable placeholder.
- `AttendanceService`'s "last known GPS fix" cache is in-memory
  (`Map`), fine for one instance; move it to Redis/DB if you scale to
  multiple backend instances.
- Face-match threshold (0.90 cosine similarity) and liveness
  thresholds (EAR/yaw) are reasonable starting points - tune them
  against real enrollment photos and lighting conditions before
  relying on this for real grading-linked attendance.
