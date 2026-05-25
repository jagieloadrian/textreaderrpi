# TextReaderRpi - Project Context

**Project Name:** TextReaderRpi  
**Created:** 2025-01-25  
**Status:** Initialization  

## Vision

Wyświetlać text na ekranie podpietym do rasbperry pi (za pomoca pi4j), który można aktualizować na stronie webowej.

**English:** Display text on a display connected to Raspberry Pi (using Pi4J) that can be updated via a web interface.

## Target Users & Stakeholders

- **Primary:** Home lab enthusiasts, DIY electronics hobbyists
- **Use Case:** Home automation projects, status displays, notification systems

## Key Success Metrics

1. **Functionality:** Text input on website successfully displays on LED screen
2. **Flexibility:** Support multiple screen types and configurations (not just MAX7219)
3. **Reliability:** 99.5% uptime, no crashes

## Timeline

- No hard deadline
- Iterative development with continuous improvement

## Existing Codebase Analysis

**Current State:** Ktor/Kotlin HTTP server with MAX7219 LED matrix driver
- Language: Kotlin 2.1.0, Java 21 runtime
- Framework: Ktor 2.3.12 (HTTP server)
- Hardware: MAX7219 LED matrix via SPI/GPIO (Pi4J 2.6.0)
- Current endpoint: POST /api/text for text submission

**Architecture:**
- Layered: HTTP Routing → Services → Hardware Driver
- Async coroutines with Dispatchers.IO for non-blocking I/O
- Text → Font → Bitmap → SPI → Display pipeline

**Quality Gaps:**
- Minimal test coverage (HTTP endpoints only)
- Hardcoded configuration (pins, SPI path, timing)
- No error recovery for hardware failures
- No support for multiple display types yet
- Missing health/status monitoring

## Next Steps

1. ✅ Codebase mapping complete (STACK, INTEGRATIONS, ARCHITECTURE, STRUCTURE, CONVENTIONS, TESTING, CONCERNS)
2. → Run domain research (display technologies, Pi4J patterns)
3. → Write requirements document with feature scoping
4. → Create development roadmap with phases
5. → Begin Phase 1 with `/gsd-plan-phase 1`

---

*Project initialized for GSD workflow*

