# Phase 5: Scheduling + Effects — Research
**Researched:** 2026-05-27
**Status:** Complete
## Summary
Phase 5 has two tracks: Wave 1 (architectural cleanup) and Waves 2-3 (scheduling + effects).
## Key Technology Decisions
| Decision | Choice | Rationale |
|----------|--------|-----------|
| Retry library (D5-02) | In-house retryWithBackoff suspend function | Resilience4j adds 5+ JARs for a 25-line function; no YAML autoconfigure advantage |
| Cron parsing (D5-09) | com.cronutils:cron-utils | Standard Java cron library; no Quartz required; ~170KB JAR |
| Scheduler (D5-12) | kotlinx.coroutines delay loops + SupervisorJob | Coroutine-native; one Job per schedule in ConcurrentHashMap registry |
| Database (D5-13) | Jetbrains Exposed DSL + H2 (configurable to PostgreSQL) | Lightweight; Exposed handles coroutine-safe transactions |
| DB transactions | ALWAYS newSuspendedTransaction | Never use plain transaction {} inside coroutines — causes JDBC thread blocking |
| Schema migration | SchemaUtils.createMissingTablesAndColumns | Flyway overkill for embedded H2; single-version schema |
| Effects (D5-21) | EffectRenderer strategy interface + 4 impls | Clean separation; SwapIn/out without driver changes |
| Tests (D5-22) | kotlinx-coroutines-test runTest + advanceTimeBy | Virtual time; no real Thread.sleep; runs in ms |
## New Dependencies
| Library | Use |
|---------|-----|
| org.jetbrains.exposed:exposed-core | Exposed ORM DSL core |
| org.jetbrains.exposed:exposed-jdbc | JDBC backend |
| org.jetbrains.exposed:exposed-java-time | java.time column types |
| com.h2database:h2 | In-process SQL database |
| com.zaxxer:HikariCP | Connection pool |
| com.cronutils:cron-utils | Cron expression parsing |
> Verify current versions on Maven Central before pinning in build.gradle.kts
## H2 URL Patterns
- Production: jdbc:h2:file:./data/schedules;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE
- Tests: jdbc:h2:mem:test_schedules;DB_CLOSE_DELAY=-1  (NOT file-mode — avoids state leakage)
## Common Pitfalls
1. Exposed transaction {} inside coroutines → deadlock; always use newSuspendedTransaction
2. H2 AUTO_SERVER=TRUE in tests connects to production file; use mem: for tests
3. Scheduler Job leaks on restart; subscribe to ApplicationStopping to cancel all
4. cron-utils CronType mismatch (Unix 5-field vs Quartz 6-field); use CronType.UNIX
5. currentJob?.cancel() is cooperative; effects must use delay() for cancellation to work
6. Route package move breaks import references; grep for old package before and after
## Effect Architecture
- DisplayDriver interface needs: setBrightness(level: Int) and displayStatic(text: String)
- BlinkEffect: displayStatic → repeat(blinkCount) { delay; setBrightness(0); delay; setBrightness(15) }
- FadeEffect: displayStatic → setBrightness(0); for level in 0..15 { setBrightness(level); delay }; scrollText
- MAX7219 intensity register 0x0A; range 0-15
## Validation Architecture
| Requirement | Test Type | Command |
|-------------|-----------|---------|
| REQ-SCHED-01 one-shot fires at target time | Unit (virtual time) | ./gradlew test --tests "*SchedulerService*" |
| REQ-SCHED-01 recurring fires at interval | Unit (virtual time) | ./gradlew test --tests "*SchedulerService*" |
| REQ-CONFLICT-01 ad-hoc preempts scheduled | Behavioral (virtual time) | ./gradlew test --tests "*ConflictPolicy*" |
| REQ-EFFECT-01 BlinkEffect toggles brightness | Unit (MockK) | ./gradlew test --tests "*EffectRenderer*" |
| REQ-EFFECT-01 FadeEffect ramps 0-15 | Unit (MockK) | ./gradlew test --tests "*EffectRenderer*" |
| REQ-TEST-01 no Thread.sleep in tests | Enforcement | grep -r "Thread.sleep" src/test/ |
**Sampling rate:**
- Per task: ./gradlew test --tests "com.anjo.*"
- Per wave: ./gradlew clean test jacocoTestReport jacocoTestCoverageVerification
