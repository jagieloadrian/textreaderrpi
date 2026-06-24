---
status: testing
phase: 17-firmware-skeletons
source: [17-VERIFICATION.md]
started: 2026-06-24T10:05:00Z
updated: 2026-06-24T10:05:00Z
---

## Current Test

number: 1
name: Flash Pico W and verify scroll on MAX7219
expected: |
  Zone shows ONLINE on server, serial shows WS connect + received JSON, 'Hello' scrolls on MAX7219
awaiting: user response

## Tests

### 1. Flash Pico W — scroll "Hello" on MAX7219
expected: Flash textreader_combined.uf2 to Pico W, POST {"text":"Hello"} to the Pico zone; zone shows ONLINE, serial confirms WS connect + JSON received, "Hello" scrolls on MAX7219
result: [pending]

### 2. Flash Pico 2W (RP2350) — same scroll test
expected: Same as test 1 on RP2350 hardware
result: [pending]

### 3. Flash ESP32 — render text+effect on MAX7219
expected: Flash ESP32 via cmake/idf, POST {"text":"Hi","effect":"SCROLL","speed":40}; ESP32 connects, receives JSON, renders on MAX7219 with speed affecting scroll timing
result: [pending]

### 4. WiFi/server drop + auto-reconnect
expected: Pull WiFi or drop server while device is displaying; display shows "Connecting..." within RECONNECT_INTERVAL_MS; device reconnects without manual reset
result: [pending]

### 5. Pico W OTA trigger
expected: Send {"command":"ota"} WS message to Pico W; picowota_reboot(true) is called; device enters OTA bootloader and accepts new textreader.uf2 over the network
result: [pending]

### 6. First-boot captive portal provisioning
expected: Erase stored credentials, power on; "TextReader-Setup" AP is visible; captive portal accepts SSID/password and device reboots into station mode connecting to home network
result: [pending]

### 7. ESP32 OTA stub decision
expected: Send {"command":"ota"} WS message to ESP32; ota_trigger() logs "not yet implemented". Human reviewer decides: accept as skeleton-phase stub, or open a gap-closure plan for real esp_ota_begin implementation
result: [pending]

## Summary

total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps
