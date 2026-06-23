---
status: complete
phase: 16-zone-management
source: 16-01-SUMMARY.md, 16-02-SUMMARY.md, 16-03-SUMMARY.md
started: 2026-06-23T19:30:00Z
updated: 2026-06-23T19:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Zones page loads with Add Zone form
expected: Navigate to the Zones page (e.g. http://localhost:8080/zones). You should see an "Add Zone" form at the top with a "Zone Name" input, a "Zone Type" dropdown (Network / Firmware options), an "IP Address" field, and an "Add Zone" button. Below it, "Registered Zones" section.
result: pass

### 2. Zone Type toggle hides/shows IP field
expected: On the Zones page, change the Zone Type dropdown from "Network" to "Firmware (Pico / ESP32)". The IP Address field should disappear and a "Display Type" text input should appear instead. Switching back to "Network" should show the IP field again and hide Display Type.
result: pass

### 3. Add a NETWORK zone via the form
expected: Fill in Zone Name = "test-net", Zone Type = "Network", IP = "192.168.1.200". Click "Add Zone". A success toast/message appears and the new zone "test-net" shows up in the Registered Zones list with a "Network" label and an OFFLINE status badge (since nothing is listening at that IP).
result: pass

### 4. Add a FIRMWARE zone via the form
expected: Fill in Zone Name = "pico-test", Zone Type = "Firmware (Pico / ESP32)". (No IP needed — the IP field should be hidden.) Optionally fill Display Type = "MAX7219". Click "Add Zone". A success message appears and "pico-test" shows in the Registered Zones list with a "Firmware" label (not "Firmware (FIRMWARE)") and OFFLINE status.
result: pass

### 5. Validation — blank name rejected
expected: Leave Zone Name empty, fill in a valid IP, click "Add Zone". The form should respond with a validation error saying the name cannot be blank. No zone is added.
result: issue
reported: "validation works but after adding a new zone the form fields are not cleared — form should reset to blank after successful submission"
severity: minor

### 6. Validation — public IP rejected for NETWORK zone
expected: Enter Zone Name = "bad-zone", Zone Type = "Network", IP = "8.8.8.8" (public IP). Click "Add Zone". The form should respond with a 422 validation error referencing RFC1918 or private address. No zone is added.
result: issue
reported: "validation works and blocks the zone, but error displays as raw JSON instead of the same styled floating message used for success — error messages should be human-readable and match the success toast style"
severity: minor

### 7. Validation — local hardware type rejected
expected: Try submitting a POST to /api/v1/zones with type "MAX7219" (or "LCD" / "OLED") — either via curl or browser dev tools. The response should be 422 Unprocessable Entity with a message about local hardware types needing startup configuration. (This validates via the API, not the UI form — the form only shows Network and Firmware options.)
result: pass

### 8. Duplicate zone name returns 409
expected: Add a zone named "dup-test" successfully. Then try to add another zone with the same name "dup-test". The second attempt should return a 409 Conflict response (either shown in the UI as an error, or verify via API).
result: issue
reported: "409 conflict is correctly detected but displayed as raw response instead of a styled floating message — same error display issue as test 6"
severity: minor

### 9. Delete a zone
expected: From the Registered Zones list, click the "Remove" button on a non-local zone (e.g. the "test-net" zone added in test 3). The zone card disappears from the list. Refreshing the page confirms it is gone.
result: pass

### 10. FIRMWARE zone shows "Firmware" label (not "Firmware (FIRMWARE)")
expected: The "pico-test" firmware zone added in test 4 shows the type label as "Firmware" — not "Firmware (FIRMWARE)". (This verifies the IN-02 display fix.)
result: pass

### 11. GET /ws/zone/{id} WebSocket endpoint reachable
expected: Using a WebSocket client (e.g. browser dev tools, wscat, or websocat), connect to ws://localhost:8080/ws/zone/pico-test. The connection should be accepted (101 Switching Protocols). The pico-test zone status in the Registered Zones list should now show ONLINE (after a page refresh). Closing the WebSocket connection should return it to OFFLINE.
result: pass

### 12. UDP discover scan returns a result (or empty list)
expected: Click the "Scan for Displays" button on the Zones page. A UDP broadcast goes out on port 54321. Within about 3 seconds the scan result area should show either a JSON array of discovered devices or an empty array []. The page should NOT hang or error.
result: pass

## Summary

total: 12
passed: 9
issues: 3
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Add Zone form fields clear to blank after a successful zone submission"
  status: failed
  reason: "User reported: after adding new zone those fields are not empty, as ux good practice this should be blank after adding new item"
  severity: minor
  test: 5
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "All error responses (422 validation, 409 conflict) display as styled human-readable floating messages matching the success toast"
  status: failed
  reason: "User reported (test 6): error shows as simple json instead of a floating message. Confirmed again (test 8): 409 conflict also shows raw instead of styled message"
  severity: minor
  test: 6
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
