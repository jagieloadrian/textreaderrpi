---
plan: 17-05
phase: 17-firmware-skeletons
status: complete
commit: 1fce807
---

## What Was Built

`.github/workflows/firmware-ci.yml` — separate "Firmware CI" workflow alongside the existing Gradle ci.yml.

**pico job** (matrix: pico_w, pico2_w):
- actions/checkout@v4 with submodules: recursive (pulls picowota)
- apt-get: gcc-arm-none-eabi, cmake, ninja-build
- Clones raspberrypi/pico-sdk at tag 2.1.1 recursively, exports PICO_SDK_PATH
- cmake configure: `cmake -S firmware/pico -B firmware/pico/build -DPICO_BOARD=<matrix> -DCMAKE_BUILD_TYPE=MinSizeRel`
- cmake build: `cmake --build firmware/pico/build --target textreader -j$(nproc)`
- Uploads `textreader.uf2` and `textreader_combined.uf2` as `pico-<board>-firmware` artifact, if-no-files-found: error

**esp32 job**:
- actions/checkout@v4
- actions/setup-python@v5 (Python 3.x)
- pip install platformio
- `pio run -e esp32dev` in firmware/esp32/
- Uploads `firmware.bin` and `firmware.elf` from `.pio/build/esp32dev/` as `esp32-firmware` artifact, if-no-files-found: error

Both jobs: timeout-minutes: 30, cancel-in-progress concurrency, permissions contents: read (matching ci.yml conventions).

## Key Decisions

- pico-sdk 2.1.1 pinned by git tag for determinism and pico2_w support
- Separate workflow file (not editing ci.yml) per D-18 / plan requirement
- if-no-files-found: error on artifact uploads ensures missing .uf2/.bin fails the build
- T-17-SC accepted: pip install platformio is a CI build dependency, not a runtime dependency

## Artifacts

- `.github/workflows/firmware-ci.yml` (91 lines)
