---
status: complete
phase: 12-observability-gap-closures
source: [12-01-SUMMARY.md, 12-02-SUMMARY.md, 12-03-SUMMARY.md]
started: 2026-06-17T11:10:00Z
updated: 2026-06-17T11:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. GET /metrics returns hardware group
expected: |
  GET /metrics returns JSON with groups array of size 3.
  Group names: ["runtime", "api", "hardware"].
  hardware group contains keys: display.failures, recovery.retries, display.inFlight, display.skipped.
  (Verified by: ./gradlew test — MetricsRoutesTest asserts shouldHaveSize 3 and groupNames)
result: pass

### 2. GET /health/detail returns correct JSON fields
expected: |
  GET /health/detail returns HTTP 200 with JSON body containing:
  uptime (ms), memoryUsed, memoryMax, displayStatus ("ONLINE"/"OFFLINE"),
  totalFailures, zoneErrors (map of zoneId → error or null).
  Rate-limited at 120 req/min matching /metrics.
  (Verified by: ./gradlew test — HealthRoutesTest asserts all JSON keys present)
result: pass

### 3. GET /health contains displayAvailable check
expected: |
  GET /health response body contains the string "displayAvailable".
  In production with an ONLINE zone → HTTP 200. In test env (no hardware) → HTTP 503 is expected.
  (Verified by: ./gradlew test — HealthRoutesTest asserts body contains "displayAvailable")
result: pass

### 4. Browser HTML 404 page
expected: |
  1. Run ./gradlew run, wait for "Application started"
  2. Navigate browser to http://localhost:8080/does-not-exist
  3. See: dark-theme HTML error page, heading "Error 404", text "Page not found", "Back to home" link
  4. No raw JSON body, no SwaggerUI page
  5. Optionally: /openapi still shows SwaggerUI
result: pass

### 5. API paths return JSON errors (not HTML)
expected: |
  GET /api/v1/does-not-exist returns 404 with JSON body — does NOT contain "Error 404" string.
  prefersHtml() correctly discriminates API paths from browser navigation paths.
  (Verified by: ./gradlew test — ApplicationTest "JSON error for API path unknown route")
result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]
