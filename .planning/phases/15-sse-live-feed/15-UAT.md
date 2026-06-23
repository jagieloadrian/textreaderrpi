---
status: testing
phase: 15-sse-live-feed
source: [15-VERIFICATION.md]
started: 2026-06-23T00:00:00Z
updated: 2026-06-23T00:00:00Z
---

## Current Test

number: 1
name: End-to-end SSE event delivery
expected: |
  POST to /api/v1/text while curl --no-buffer http://localhost:8080/api/v1/live
  is open; a named `event: display` SSE frame appears within ~1 second.
awaiting: user response

## Tests

### 1. End-to-end SSE event delivery

expected: |
  With the server running, open a terminal and run:
    curl --no-buffer http://localhost:8080/api/v1/live
  In another terminal POST to /api/v1/text (e.g. via the web UI or curl).
  Within ~1 second you should see:
    event: display
    data: {"id":"...","text":"...","effect":"...","zoneId":...,"displayedAt":"..."}
result: [pending]

### 2. 30-second SSE keep-alive heartbeat

expected: |
  With an idle SSE connection open (no POST traffic), wait 30+ seconds.
  You should see periodic comment frames:
    : keep-alive
  These confirm the heartbeat DSL is firing and proxies won't drop the connection.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
