# Phase 5: Scheduling + Effects — Discussion Log
Session date: 2026-05-27
## Areas Covered
### 1. Schedule model & trigger style
- Both one-shot and recurring supported
- Recurring: interval strings (5m, 2h) AND cron expressions
- Rich fields: text, trigger, effect, priority, maxRuns, expiresAt
- Full CRUD API: POST/GET/DELETE/PATCH /api/schedule
- Scheduler engine: agent decides (lightweight, no Quartz)
- Extra: schedule UI page at GET /schedule using existing templates
### 2. Effects API & scope
- Effect as field on text/schedule requests (agent decides exact API)
- Effects in scope: scroll (default), blink, reverse, fade
- Architecture: agent decides (integrates with DisplayDriver)
### 3. Conflict & queue policy
- Live submissions preempt scheduled items
- Unbounded queue
- Tie-break: priority descending then creation order
### 4. Persistence & lifecycle
- SQL: H2 default, configurable to PostgreSQL via application.yaml
- Exposed ORM (JetBrains, Kotlin-first)
- Recurring stops on optional expiresAt (or explicit DELETE)
- Config: standard yaml url+driver+credentials
### 5. Refactoring items (user-initiated)
- Health: remove /health/detail, merge into /health (D5-01)
- RecoveryPolicy: kotlin-native library + withRetry() wrapper + YAML config (D5-02, D5-03)
- ResourceTracker: removed, replaced with Mutex (D5-04)
- Metrics: config-driven from application.yaml (D5-05)
- Comments: remove all (D5-06)
- Routes: consolidate web.routes into routing package (D5-07)
- Tests: unify under com.anjo.* packages (D5-08)
### Sequencing decision
User delegated to agent: Wave 1=refactoring, Wave 2=scheduling, Wave 3=effects
