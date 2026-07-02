<!-- generated-by: gsd-doc-writer -->
# API Reference

TextReaderRpi exposes a REST API under `/api/v1`, a Server-Sent Events stream, and a WebSocket endpoint for firmware-based display zones. All REST endpoints accept and return `application/json`.

A live Swagger UI is available at `/openapi` when the server is running.

---

## Authentication

No authentication is required. Rate limiting is applied instead (see [Rate Limits](#rate-limits)).

---

## Endpoints Overview

| Method | Path | Description | Rate Limited |
|--------|------|-------------|--------------|
| `POST` | `/api/v1/text` | Send text to one or all display zones | Yes |
| `GET` | `/api/v1/display/status` | Get current display driver status | Yes |
| `POST` | `/api/v1/display/select` | Switch display driver type (not implemented) | Yes |
| `GET` | `/api/v1/zones` | List all registered zones | Yes |
| `POST` | `/api/v1/zones` | Register a new zone manually | Yes |
| `DELETE` | `/api/v1/zones/{id}` | Remove a registered zone | Yes |
| `POST` | `/api/v1/zones/discover` | Trigger UDP zone discovery scan | Yes |
| `GET` | `/api/v1/schedule` | List all schedules | Yes |
| `POST` | `/api/v1/schedule` | Create a new schedule | Yes |
| `GET` | `/api/v1/schedule/{id}` | Get a single schedule by ID | Yes |
| `PATCH` | `/api/v1/schedule/{id}` | Update a schedule | Yes |
| `DELETE` | `/api/v1/schedule/{id}` | Delete a schedule | Yes |
| `POST` | `/api/v1/schedule/{id}/cancel` | Cancel a running schedule | Yes |
| `GET` | `/api/v1/history` | Paginated display history | Yes |
| `GET` | `/api/v1/history/export` | Export display history as CSV | Yes |
| `GET` | `/metrics` | Application metrics | Yes (separate limit) |
| `GET` | `/health/detail` | Hardware and zone health detail | Yes (separate limit) |
| `GET` | `/api/v1/live` | SSE stream of display events | No |
| `WS` | `/ws/zone/{id}` | WebSocket channel for firmware zones | No |

---

## Rate Limits

The `/api/v1` REST routes share a single rate-limit bucket. Metrics and health endpoints use a separate, higher bucket.

| Scope | Default limit | Config key |
|-------|--------------|------------|
| `/api/v1` routes | 60 requests / minute | `api.rateLimitPerMinute` |
| `/metrics` and `/health/detail` | 120 requests / minute | `api.metricsRateLimitPerMinute` |

Defaults can be overridden in `application.conf`. Requests that exceed the limit receive `429 Too Many Requests`.

---

## Text

### POST /api/v1/text

Send text to the display. Targets a single zone when the `zone` query parameter is provided; broadcasts to all zones when omitted.

**Query parameters**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `zone` | No | Zone ID to target. Must match `^[a-zA-Z0-9-]{1,64}$`. Omit to broadcast. |

**Request body**

```json
{
  "text": "Hello world",
  "effect": "SCROLL",
  "conflictPolicy": "INTERRUPT",
  "speed": null,
  "blinkPeriod": null,
  "fadeSteps": null
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `text` | string | Yes | — | Text to display. Must not be blank. Max length is `api.maxTextLength` (default 128). |
| `effect` | string | No | `SCROLL` | One of `SCROLL`, `BLINK`, `REVERSE`, `FADE`. |
| `conflictPolicy` | string | No | `INTERRUPT` | `INTERRUPT` replaces the current message; `SKIP_NEW` drops the request if the display is busy. |
| `speed` | integer | No | null | Effect speed in implementation-defined units. Must be positive. |
| `blinkPeriod` | integer | No | null | Blink interval in ms. Applies to `BLINK` effect. Must be positive. |
| `fadeSteps` | integer | No | null | Number of fade steps. Applies to `FADE` effect. Must be positive. |

**Responses**

`202 Accepted` — single zone targeted:
```json
{ "accepted": true, "message": "Text queued for rendering" }
```

`202 Accepted` — broadcast (no `zone` parameter):
```json
{
  "successful": ["main", "zone-b"],
  "failed": [{ "zoneId": "zone-c", "reason": "OFFLINE" }]
}
```

`404 Not Found` — zone ID not registered.

`503 Service Unavailable` — zone is registered but currently offline.

`400 Bad Request` — validation failure (blank text, length exceeded, invalid zone name, negative speed/period/steps).

---

## Display

### GET /api/v1/display/status

Returns the current state of the active display driver.

**Response** `200 OK`:
```json
{
  "displayType": "MAX7219",
  "isActive": true,
  "hardwareAvailable": true,
  "currentMessage": "Hello world",
  "error": null
}
```

### POST /api/v1/display/select

Reserved for future display driver switching. Always returns `501 Not Implemented`.

**Request body**

```json
{ "type": "MAX7219" }
```

Valid types: `MAX7219`, `LCD`, `OLED`, `FIRMWARE`.

---

## Zones

### GET /api/v1/zones

Returns all registered zones (local and network).

**Response** `200 OK` — array of zone status objects:
```json
[
  {
    "id": "main",
    "type": "MAX7219",
    "status": "ONLINE",
    "ip": null,
    "lastSeenAt": null,
    "error": null
  }
]
```

### POST /api/v1/zones

Register a zone manually.

**Request body**

```json
{
  "name": "my-zone",
  "type": "MAX7219",
  "ip": "192.168.1.42",
  "displaySubtype": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Unique zone identifier. |
| `type` | string | Yes | `MAX7219`, `LCD`, `OLED`, or `FIRMWARE`. |
| `ip` | string | Conditional | Required for non-firmware types. |
| `displaySubtype` | string | No | Optional hardware subtype hint. |

**Responses**

`201 Created` — returns the created `NetworkZone` object.

`400 Bad Request` — `ip` missing for a non-firmware zone type.

`409 Conflict` — a zone with the same name or IP is already registered.

### DELETE /api/v1/zones/{id}

Remove a network zone. Local (hardware-attached) zones cannot be deleted.

**Responses**

`204 No Content` — zone deleted.

`404 Not Found` — zone not found or is a local zone.

### POST /api/v1/zones/discover

Triggers a UDP broadcast scan for discoverable zones on the local network.

**Response** `200 OK` — array of discovered `NetworkZone` objects.

---

## Schedules

### GET /api/v1/schedule

Returns all schedules.

**Response** `200 OK` — array of `Schedule` objects.

### POST /api/v1/schedule

Create a new schedule.

**Request body**

```json
{
  "text": "Meeting in 5 minutes",
  "triggerType": "CRON",
  "triggerValue": "0 9 * * 1-5",
  "effect": "SCROLL",
  "priority": 0,
  "maxRuns": null,
  "expiresAt": null,
  "conflictPolicy": null,
  "webhookUrl": null,
  "zoneId": null
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `text` | string | Yes | Text to display. Max 512 characters. |
| `triggerType` | string | Yes | `ONESHOT`, `RECURRING`, or `CRON`. |
| `triggerValue` | string | Yes | Value interpreted by `triggerType` (see below). |
| `effect` | string | No | Default `SCROLL`. |
| `priority` | integer | No | 0–100. Default 0. Higher values take precedence. |
| `maxRuns` | integer | No | Stop after N firings. |
| `expiresAt` | string | No | ISO-8601 instant after which the schedule is inactive. |
| `conflictPolicy` | string | No | `INTERRUPT` or `SKIP_NEW`. Inherits display default when null. |
| `webhookUrl` | string | No | HTTP/HTTPS URL called after each firing. |
| `zoneId` | string | No | Target zone. Broadcasts when null. |

**triggerValue formats**

| triggerType | Format | Example |
|-------------|--------|---------|
| `ONESHOT` | ISO-8601 instant | `2026-12-31T09:00:00Z` |
| `RECURRING` | `<number><unit>` where unit is `s`, `m`, `h`, `d` | `5m`, `2h`, `30s` |
| `CRON` | Unix cron expression (5 fields) | `0 9 * * 1-5` |

**Response** `201 Created` — the created `Schedule` object including the generated `id`.

### GET /api/v1/schedule/{id}

**Response** `200 OK` — a single `Schedule` object. `404` if not found.

### PATCH /api/v1/schedule/{id}

Update a schedule. Supply the full `Schedule` body. If the schedule is `ACTIVE`, it is rescheduled immediately.

**Response** `200 OK` — the updated `Schedule`. `404` if not found.

### DELETE /api/v1/schedule/{id}

**Response** `204 No Content`. `404` if not found.

### POST /api/v1/schedule/{id}/cancel

Cancel a running schedule without deleting it.

**Response** `204 No Content`.

---

## History

### GET /api/v1/history

Paginated log of past display events.

**Query parameters**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `1` | Page number (1-based). |
| `size` | `20` | Items per page. Range 1–200. |
| `effect` | — | Filter by effect name (`SCROLL`, `BLINK`, etc.). Omit or `ALL` for no filter. |
| `source` | — | Filter by source. Omit or `ALL` for no filter. |
| `zone` | — | Filter by zone ID. Omit or `ALL` for no filter. |
| `search` | — | Full-text search term applied to the `text` field. |

**Response** `200 OK`:
```json
{
  "items": [
    {
      "id": "abc123",
      "text": "Hello world",
      "effect": "SCROLL",
      "source": "API",
      "scheduleId": null,
      "zoneId": "main",
      "displayedAt": "2026-07-02T10:00:00Z",
      "webhookStatus": null
    }
  ],
  "page": 1,
  "size": 20,
  "total": 42
}
```

### GET /api/v1/history/export

Export history as a CSV file. Accepts the same `effect`, `source`, `zone`, and `search` query parameters as `GET /api/v1/history`. Returns `Content-Disposition: attachment; filename="history.csv"`.

---

## Metrics

### GET /metrics

Returns grouped application metrics. Subject to the metrics rate limit.

**Response** `200 OK`:
```json
{
  "timestamp": "2026-07-02T10:00:00Z",
  "groups": [
    {
      "name": "display",
      "metrics": [
        {
          "key": "display.render.time",
          "type": "timer",
          "count": 1024,
          "meanRate": 1.5,
          "p95": 42.0
        }
      ]
    }
  ]
}
```

---

## Health

### GET /health/detail

Returns JVM and zone health information. Subject to the metrics rate limit.

**Response** `200 OK`:
```json
{
  "uptime": 86400000,
  "memoryUsed": 52428800,
  "memoryMax": 268435456,
  "displayStatus": "ONLINE",
  "totalFailures": 0,
  "zoneErrors": {
    "main": null,
    "zone-b": "connection timeout"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `uptime` | long | JVM uptime in milliseconds. |
| `memoryUsed` | long | Heap bytes in use. |
| `memoryMax` | long | Maximum heap size in bytes. |
| `displayStatus` | string | `ONLINE` if any zone is online, otherwise `OFFLINE`. |
| `totalFailures` | long | Cumulative display failure count since startup. |
| `zoneErrors` | object | Map of zone ID to last error message (null if no error). |

---

## Server-Sent Events

### GET /api/v1/live

Streams `DisplayEvent` objects as they are rendered. The connection is kept alive with a 30-second heartbeat comment.

**Event format**

```
event: display
data: {"id":"abc123","text":"Hello","effect":"SCROLL","zoneId":"main","displayedAt":"2026-07-02T10:00:00Z"}
```

Connect with the `EventSource` API or any SSE-capable HTTP client. No authentication or special headers are required.

---

## WebSocket — Firmware Zones

### WS /ws/zone/{id}

Bidirectional WebSocket channel used by firmware display nodes (e.g., Raspberry Pi Pico W running the custom firmware) to receive render commands.

| Parameter | Description |
|-----------|-------------|
| `id` | Zone ID registered as type `FIRMWARE`. |

The server closes the connection with code `1003 (CANNOT_ACCEPT)` if the zone ID is not found or not a firmware zone. The firmware client holds the connection open and the server pushes `FirmwareMessage` frames as text needs to be rendered.

---

## Error Responses

Validation errors from the request validation layer use the following shape:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Text exceeds maximum length of 128 characters (got 200)",
    "timestamp": "2026-07-02T10:00:00Z",
    "details": null
  }
}
```

Common HTTP status codes:

| Code | Meaning |
|------|---------|
| 400 | Invalid request body or query parameter |
| 404 | Resource not found |
| 409 | Conflict (duplicate zone name or IP) |
| 429 | Rate limit exceeded |
| 501 | Not implemented (display/select) |
| 503 | Target zone is offline |
