# TextReaderRpi - Requirements Document

**Version:** 1.0  
**Date:** 2025-01-25  
**Status:** Draft for Roadmap Planning

## 1. Project Scope

**Name:** TextReaderRpi  
**Vision:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface.

**Users:** Home lab enthusiasts, DIY electronics hobbyists

**Success Metrics:**
1. Text input on website successfully displays on LED screen (Functionality)
2. Support multiple screen types and configurations (Flexibility)
3. 99.5% uptime, no crashes (Reliability)

---

## 2. Feature Requirements

### MVP Features (Phase 1)

**2.1 Web Interface for Text Submission**
- HTTP API endpoint to receive text from web interface
- Simple text input form (HTML/CSS) or mobile-friendly UI
- Submit button that sends text to Raspberry Pi
- Confirmation response showing text accepted

**2.2 LED Display Rendering**
- Render submitted text on MAX7219 LED matrix
- Left-to-right scrolling animation
- Support for common ASCII characters (a-z, A-Z, 0-9, symbols)
- Clear display on new input

**2.3 Basic Server**
- Ktor HTTP server running on Raspberry Pi
- Port 8080 accessible on home network
- JSON API endpoints for text submission
- Graceful error handling

### Phase 2 Features (Enhanced Display Support)

**2.4 Multiple Display Type Support**
- Driver abstraction for different display types
- I2C 16x2 Character LCD support
- OLED display support (I2C)
- Configuration-driven display selection

**2.5 Display Configuration**
- HTTP endpoint to query connected display types
- Configuration file for display type selection
- Dynamic display switching without code changes

**2.6 Responsive HTML Pages (Primary Interaction Flow)**
- Replace plain text/business responses with full responsive HTML pages for browser flows
- Build pages using Ktor HTML DSL templates: https://ktor.io/docs/server-html-dsl.html#templates
- Required pages:
  - `GET /` for text submission form and operation feedback
  - `GET /status` for active display and service status
  - `GET /settings/display` for runtime display switching UI
- Browser-facing 400/500 should render HTML error pages
- Use a hybrid template strategy: shared base layout + route-specific page sections

### Phase 3 Features (Advanced)

**2.7 Status Monitoring**
- `/health` endpoint for system status
- Display uptime statistics
- Hardware error logging and reporting

**2.8 Text Management**
- Display text history/queue
- Scheduled text updates
- Multiple concurrent displays

---

## 3. Non-Functional Requirements

### Reliability
- **Target Uptime:** 99.5% (minimal downtime)
- **Error Recovery:** Automatic recovery from hardware errors
- **Crash Prevention:** Comprehensive error handling and logging
- **Graceful Degradation:** Continue operation if one display fails

### Performance
- **Text Submission Response:** <500ms
- **Display Update Latency:** <2s from submission to LED
- **Memory Usage:** <256MB JVM heap (RPi 4 constraint)
- **Max Concurrent Requests:** 32 (rate limiting)

### Maintainability
- **Code Quality:** Follow Kotlin conventions (already established)
- **Documentation:** Architecture docs, API docs, deployment guide
- **Testing:** Unit tests for services, integration tests for endpoints
- **Configuration:** Externalize hardcoded values (pins, ports, timeouts)

### Security (Home Network Context)
- **Input Validation:** Validate text length and character set
- **Rate Limiting:** Prevent API flooding (60 req/min per IP)
- **No Authentication:** Assumes isolated home network (documented in README)
- **Error Messages:** Log errors, don't expose internals to client

---

## 4. Technical Constraints

### Hardware Constraints
- **Raspberry Pi 4 (Target):** 4GB RAM, 4 CPU cores, limited GPIO
- **Multiple Display Buses:** SPI for MAX7219, I2C for LCD/OLED sharing same pins
- **Power:** LED matrices require external PSU if >3 modules
- **Operating System:** Raspberry Pi OS with GPIO/I2C/SPI enabled

### Software Constraints
- **Language:** Kotlin 2.1.0, JDK 21
- **Framework:** Ktor 2.3.12
- **Hardware Library:** Pi4J 2.6.0
- **Build System:** Gradle 8.11.1

### Integration Constraints
- **Existing Codebase:** Build on current Ktor/Kotlin foundation
- **No External Services:** Self-contained on Raspberry Pi
- **Local Network Only:** No cloud integration planned

---

## 5. Acceptance Criteria

### MVP Acceptance
- [ ] User can submit text via HTTP POST /api/text
- [ ] Text appears on MAX7219 LED matrix within 2 seconds
- [ ] Scrolling animation is smooth and readable
- [ ] Server recovers gracefully from network interruptions
- [ ] No memory leaks after 24 hours of operation
- [ ] Unit tests for critical services (>70% coverage)

### Enhanced Display Support Acceptance
- [ ] I2C 16x2 LCD renders text correctly
- [ ] OLED display shows text with correct formatting
- [ ] Configuration file controls display type selection
- [ ] Switching display types doesn't require code changes
- [ ] All display drivers share common interface
- [ ] `GET /`, `GET /status`, and `GET /settings/display` render responsive HTML pages
- [ ] HTML pages are generated with Ktor HTML DSL templates using shared layout + route-specific sections
- [ ] Browser-oriented 400/500 responses render HTML error pages

### Production Readiness Acceptance
- [ ] `/health` endpoint reports system status
- [ ] Error logging captures hardware failures
- [ ] Rate limiting prevents API abuse
- [ ] Deployment instructions documented
- [ ] README includes hardware setup guide

---

## 6. Out of Scope (Phase 4+)

- Cloud sync or remote access beyond home network
- User authentication/authorization
- Mobile app (web UI only)
- Real-time video streaming
- Display scripting/macros
- Custom font support
- Multilingual text rendering

---

## 7. Dependencies & Assumptions

### Dependencies
- Raspberry Pi 4 with GPIO/I2C/SPI enabled
- MAX7219 LED matrix (8x8 modules daisy-chained)
- Optional: I2C 16x2 LCD, OLED display
- Home network connectivity

### Assumptions
- Operator has physical access to Raspberry Pi for setup
- Home network is trusted (no authentication required)
- Raspberry Pi OS is pre-installed
- Java 21 runtime is available

### Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|-----------|
| Hardware driver failures | Display offline | Graceful error handling, fallback logging |
| Memory exhaustion | JVM crash | Heap size limits, resource throttling |
| Infinite loops in async | Server hang | Timeout protection, watchdog timer |
| GPIO pin conflicts | Hardware locked | Clear documentation, sanity checks |

---

## 8. Success Timeline

| Milestone | Target | Criteria |
|-----------|--------|----------|
| **MVP Complete** | 2-3 weeks | Text submission → LED display working |
| **Enhanced Displays** | 4-6 weeks | Multiple display types supported |
| **Production Ready** | 8-10 weeks | Health monitoring, error recovery, docs |

---

## 9. Glossary

- **MAX7219:** Serial LED matrix driver IC (supports 8×8 modules)
- **Pi4J:** Java library for GPIO/SPI/I2C control on Raspberry Pi
- **Ktor:** Asynchronous HTTP server framework for Kotlin
- **Dispatchers.IO:** Kotlin coroutine dispatcher for I/O-bound operations
- **DI (Dependency Injection):** Pattern for wiring services and dependencies
- **OLED:** Organic LED display (high contrast, low power)
- **I2C:** Inter-Integrated Circuit serial protocol (commonly used on RPi)

---

## 10. Next Steps

1. ✅ Requirements approved
2. → Create detailed roadmap with phases (ROADMAP.md)
3. → Begin Phase 1 planning with `/gsd-plan-phase 1`
4. → Execute phase 1 tasks
5. → Validate and iterate

---

*This document captures the project scope, features, constraints, and acceptance criteria for TextReaderRpi. Ready for roadmap creation.*

