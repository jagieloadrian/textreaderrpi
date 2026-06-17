<!-- generated-by: gsd-doc-writer -->
# Configuration Overview

TextReaderRpi is configured through a single YAML file (`src/main/resources/application.yaml`) with every setting overridable at runtime via environment variables. The application uses Ktor's built-in config resolution: `${ENV_VAR:default}` means "read `ENV_VAR`; fall back to `default` if absent."

---

## Environment Variables

All variables are optional unless noted otherwise. Omitting any variable applies the listed default.

### Server

| Variable | Required | Default | Description |
|---|---|---|---|
| `PORT` | No | `8080` | HTTP server listen port |

### Display

| Variable | Required | Default | Description |
|---|---|---|---|
| `DISPLAY_TYPE` | No | `MAX7219` | Active display driver: `MAX7219`, `LCD`, or `OLED` |
| `MAX7219_NUM_DEVICES` | No | `2` | Number of chained MAX7219 modules |
| `GPIO_SPI_CE` | No | `24` | SPI chip-enable GPIO pin (application.yaml default; ConfigLoader code default is `8`) |
| `GPIO_SPI_MOSI` | No | `19` | SPI MOSI GPIO pin (application.yaml default; ConfigLoader code default is `10`) |
| `GPIO_SPI_MISO` | No | `9` | SPI MISO GPIO pin |
| `GPIO_SPI_SCK` | No | `23` | SPI clock GPIO pin (application.yaml default; ConfigLoader code default is `11`) |
| `I2C_BUS` | No | `1` | I2C bus number (used by LCD and OLED drivers) |

### Hardware Timeouts

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPI_TIMEOUT_MS` | No | `1000` | SPI operation timeout in milliseconds |
| `GPIO_TIMEOUT_MS` | No | `500` | GPIO operation timeout in milliseconds |

### Database

| Variable | Required | Default | Description |
|---|---|---|---|
| `DATABASE_URL` | No | `jdbc:h2:file:./data/schedules;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE` | JDBC connection URL |
| `DATABASE_DRIVER` | No | `org.h2.Driver` | JDBC driver class name |
| `DATABASE_USER` | No | `sa` | Database username |
| `DATABASE_PASSWORD` | No | _(empty)_ | Database password |
| `DATABASE_POOL_SIZE` | No | `5` | HikariCP connection pool size |

### API

| Variable | Required | Default | Description |
|---|---|---|---|
| `API_MAX_TEXT_LENGTH` | No | `128` | Maximum character count accepted per text display request |
| `API_RATE_LIMIT` | No | `60` | General API requests allowed per minute |
| `API_METRICS_RATE_LIMIT` | No | `120` | `/metrics` endpoint requests allowed per minute |

### Retry / Backoff

| Variable | Required | Default | Description |
|---|---|---|---|
| `RETRY_MAX_ATTEMPTS` | No | `5` | Maximum driver retry attempts before giving up |
| `RETRY_INITIAL_DELAY_MS` | No | `1000` | Initial retry delay in milliseconds |
| `RETRY_MAX_DELAY_MS` | No | `30000` | Maximum retry delay cap in milliseconds |
| `RETRY_FACTOR` | No | `2.0` | Exponential backoff multiplier |

### Metrics

| Variable | Required | Default | Description |
|---|---|---|---|
| `METRICS_ENABLED` | No | `true` | Enable the `/metrics` endpoint |
| `METRICS_PREFIX` | No | `textreaderrpi` | Prefix applied to all metric names |

### Resources

| Variable | Required | Default | Description |
|---|---|---|---|
| `SCREEN_DRIVER_MAX_SLOTS` | No | `10` | Maximum concurrent screen driver operation slots |

### Timing

| Variable | Required | Default | Description |
|---|---|---|---|
| `SCROLL_SPEED` | No | `16` | Scroll step delay in milliseconds |
| `REFRESH_RATE` | No | `60` | Display refresh rate in Hz |

### Logging

| Variable | Required | Default | Description |
|---|---|---|---|
| `LOG_LEVEL` | No | `INFO` | Log level: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `LOG_FORMAT` | No | `json` | Log output format: `json` or `text` |

### Webhooks

| Variable | Required | Default | Description |
|---|---|---|---|
| `WEBHOOK_DEFAULT_URL` | No | _(empty)_ | Default webhook URL fired after each scheduled display event; leave empty to disable |

### Zone Discovery

| Variable | Required | Default | Description |
|---|---|---|---|
| `discovery.enabled` | No | `true` | Enable mDNS / UDP network zone auto-discovery. Can also be set as a JVM system property (`-Ddiscovery.enabled=false`) |

---

## Config File Format

The canonical config file is `src/main/resources/application.yaml`. The structure groups settings by subsystem:

```yaml
ktor:
  deployment:
    port: ${PORT:8080}

display:
  type: "${DISPLAY_TYPE:MAX7219}"
  zones:
    0:
      id: "main"
      type: "${DISPLAY_TYPE:MAX7219}"
      numDevices: ${MAX7219_NUM_DEVICES:2}
      bus: 0
      chipSelect: 0
  max7219:
    numDevices: ${MAX7219_NUM_DEVICES:2}
    brightness: true
    gpioPins:
      spi_ce: ${GPIO_SPI_CE:24}
      spi_mosi: ${GPIO_SPI_MOSI:19}
      spi_miso: ${GPIO_SPI_MISO:9}
      spi_sck: ${GPIO_SPI_SCK:23}

database:
  url: "${DATABASE_URL:jdbc:h2:file:./data/schedules;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE}"
  driver: "${DATABASE_DRIVER:org.h2.Driver}"
  user: "${DATABASE_USER:sa}"
  password: "${DATABASE_PASSWORD:}"
  poolSize: ${DATABASE_POOL_SIZE:5}

retry:
  maxAttempts: ${RETRY_MAX_ATTEMPTS:5}
  initialDelayMs: ${RETRY_INITIAL_DELAY_MS:1000}
  maxDelayMs: ${RETRY_MAX_DELAY_MS:30000}
  factor: ${RETRY_FACTOR:2.0}
```

The test resource file (`src/test/resources/application.yaml`) overrides `retry` to use faster values (`maxAttempts: 1`, `initialDelayMs: 10`) and an in-memory H2 database (`jdbc:h2:mem:testdb`) so tests run without hardware or persistent state.

---

## Required vs Optional Settings

No setting causes a startup failure if absent — every key in `application.yaml` has a hardcoded default in `ConfigLoader` (`src/main/kotlin/com/anjo/config/loader/ConfigLoader.kt`). The config loader uses `propertyOrNull` throughout and falls back to Kotlin default values rather than throwing.

The one partial exception is `WEBHOOK_DEFAULT_URL`: if both the per-schedule `webhookUrl` and the default URL are blank, webhooks are silently skipped — there is no error.

---

## Defaults

Key defaults as resolved by `ConfigLoader`:

| Setting | Default value | Source |
|---|---|---|
| Display type | `MAX7219` | `ConfigLoader.parseDisplayType` |
| MAX7219 GPIO pins | CE=8, MOSI=10, MISO=9, SCK=11 | `ConfigLoader` fallback (differs from `application.yaml` which uses Raspberry Pi-specific pin numbers) |
| LCD I2C address | `0x27` | `LcdConfig` data class |
| OLED I2C address | `0x3C` | `OledConfig` data class |
| Database (test) | in-memory H2 | `ConfigLoader` fallback |
| Retry factor | `2.0` | `RetryConfig` data class |
| Discovery enabled | `true` | `ConfigLoader` |

---

## Per-Environment Overrides

There are no `.env.development` / `.env.production` / `.env.test` files in the repository. Override strategy by environment:

**Development (host)** — Copy `.env.example` to a local `.env` file (not committed) and source it before starting:

```bash
cp .env.example .env
# edit .env as needed
source .env && ./gradlew run
```

**Test** — The Gradle `test` task sets `-Ddiscovery.enabled=false` as a JVM system property and points at `src/test/resources/application.yaml`, which uses an in-memory H2 database and faster retry settings. No extra environment setup is needed.

**Production / container** — Pass variables directly to the Docker container or inject them through the deployment platform's secret manager. See `docs/deployment/production-guide.md` for the full deployment variable reference.

---

## Multi-Zone Configuration

Zones are configured under the `display.zones` key as a zero-indexed map. Each zone entry requires:

| Key | Type | Description |
|---|---|---|
| `id` | String | Unique zone identifier used in API calls |
| `type` | String | Driver type: `MAX7219`, `LCD`, or `OLED` |
| `numDevices` | Int | Number of chained devices (MAX7219 only) |
| `bus` | Int | SPI bus number |
| `chipSelect` | Int | SPI chip-select index |

Example with two zones:

```yaml
display:
  zones:
    0:
      id: "main"
      type: "MAX7219"
      numDevices: 2
      bus: 0
      chipSelect: 0
    1:
      id: "secondary"
      type: "MAX7219"
      numDevices: 1
      bus: 0
      chipSelect: 1
```

If no zones are defined, `ConfigLoader` synthesises a single `main` zone using `DISPLAY_TYPE` and `MAX7219_NUM_DEVICES`.

---

## Switching to PostgreSQL

Replace the three database variables (all other settings keep their defaults):

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/textreader
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_USER=textreader
DATABASE_PASSWORD=<your-password>
DATABASE_POOL_SIZE=10
```

The PostgreSQL JDBC driver (`runtimeOnly(ktorLibs.postgresql)`) is already included in the build; no additional dependencies are needed. Schema migrations are managed by Flyway and run automatically on startup.
