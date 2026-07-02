<!-- generated-by: gsd-doc-writer -->
# Getting Started

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 25 (project uses `jvmToolchain(25)`) |
| Gradle | 9.5.1 (provided via `./gradlew` wrapper — no separate install needed) |
| Docker | Any recent version (optional — only for container-based first run) |

No other runtime is required. The embedded H2 database and the `OfflineDisplayDriver` let you run
and test the service on any machine without a Raspberry Pi or physical display hardware.

---

## Installation Steps

```bash
# 1. Clone the repository
git clone https://github.com/jagieloadrian/textreaderrpi.git
cd TextReaderRpi

# 2. Copy the environment template
cp .env.example .env.local
# .env.local is gitignored — edit it to override any defaults

# 3. Build (downloads dependencies, compiles, runs tests)
./gradlew build
```

The Gradle wrapper (`./gradlew`) downloads Gradle 9.5.1 automatically on the first run. No system
Gradle installation is needed.

---

## First Run

### On a development machine (no hardware)

```bash
./gradlew run
```

The service starts on `http://localhost:8080`. With no physical hardware present the
`OfflineDisplayDriver` is used — all API endpoints respond normally and the web UI is fully
functional.

Open the web UI at `http://localhost:8080` or send a test message:

```bash
curl -X POST http://localhost:8080/api/v1/text \
  -H 'Content-Type: application/json' \
  -d '{"text":"Hello","effect":"SCROLL"}'
```

A `202 Accepted` response confirms the service is running.

### On a Raspberry Pi with hardware

```bash
# Source your .env.local overrides, then run
source .env.local && ./gradlew run
```

Set `DISPLAY_TYPE`, `MAX7219_NUM_DEVICES`, and the GPIO pin variables in `.env.local` to match
your wiring. Defaults in `.env.example` use standard Raspberry Pi SPI pin numbers (CE=8, MOSI=10,
MISO=9, SCK=11).

### Via Docker (quickest isolated run)

```bash
# Build and load into local Docker daemon
./gradlew publishImageToLocalRegistry

# Run with defaults
docker run --rm -p 8080:8080 --env-file .env.local textreaderrpi:latest
```

---

## Common Setup Issues

**Wrong JDK version** — The build requires JDK 25. If `./gradlew build` fails with a toolchain
error, install JDK 25 and ensure it is on `PATH` or set `JAVA_HOME` accordingly. The Gradle
toolchain resolver will download a matching JDK automatically if your network allows it.

**Port 8080 already in use** — Set `PORT=<other-port>` in `.env.local` before starting, or pass
it inline:

```bash
PORT=9090 ./gradlew run
```

**SPI/I2C permission denied on Raspberry Pi** — The Pi4J GPIO plugin needs access to `/dev/spidev*`
and `/dev/i2c-*`. Add your user to the `spi` and `i2c` groups and re-login, or run the service
via Docker Compose which handles device mounts automatically (see
`docs/deployment/production-guide.md`).

**H2 lock error on restart** — If the JVM was killed without a clean shutdown the H2 file database
may hold a lock. Delete `./data/schedules.mv.db.lock` and restart. Alternatively set
`AUTO_SERVER=TRUE` in `DATABASE_URL` to allow concurrent access.

---

## Next Steps

- **Development workflow** — see `docs/development/` for build commands, code style, and PR process.
- **Configuration reference** — see [`docs/configuration/overview.md`](../configuration/overview.md)
  for the full environment variable table and multi-zone YAML examples.
- **Testing** — see `docs/testing/` for how to run the test suite and coverage thresholds.
- **Deployment** — see [`docs/deployment/production-guide.md`](../deployment/production-guide.md)
  for Docker Compose (RPi with SPI/I2C), Helm (K3s), and systemd host-install instructions.
- **Monitoring** — see [`docs/operations/monitoring-alerting.md`](../operations/monitoring-alerting.md).
