---
plan: 17-06
phase: 17-firmware-skeletons
status: deferred
---

## Status

Physical hardware verification deferred by user decision. Will be completed when hardware is available.

## Six Physical Success Criteria (pending)

| # | Criterion | Board | Status |
|---|-----------|-------|--------|
| SC-1 | Flash textreader_combined.uf2, POST "Hello", verify scroll on MAX7219 | Pico W | PENDING |
| SC-1 | Same on RP2350 | Pico 2W | PENDING |
| SC-2 | pio run -e esp32dev -t upload, POST text+effect, verify render | ESP32 | PENDING |
| SC-4 | Drop server/WiFi, confirm "Connecting..." + auto-reconnect | Pico W or ESP32 | PENDING |
| SC-5 | Send {"command":"ota"}, confirm OTA push accepted | Pico W or ESP32 | PENDING |
| SC-6 | Erase creds, power on, confirm TextReader-Setup AP + portal provisioning | Pico W or ESP32 | PENDING |

## To Resume

Run `/gsd-execute-phase 17` and navigate to plan 17-06, or manually verify the six
criteria from the `how-to-verify` section in `17-06-PLAN.md` and record results here.
