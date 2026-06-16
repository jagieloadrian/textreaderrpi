# Requirements: v1.1 Refactor + Fixes + UI + New Features

**Milestone:** v1.1  
**Created:** 2026-06-11  
**Status:** Active

---

## Hardware (HW)

- [x] **HW-01**: Scroll wyświetla tekst od lewego modułu do prawego na łańcuchu MAX7219 (2× 8×8)
- [x] **HW-02**: Warstwa driverów (Max7219Matrix, LCD, OLED, Offline) jest zrefaktoryzowana — usunięte workaroundy, uproszczona logika, brak manualnych poprawek

---

## Scheduler Fixes (SCHED)

- [x] **SCHED-01**: Użytkownik może wybrać `SKIP_NEW` jako `ConflictPolicy` — nowe żądanie wyświetlania jest pomijane gdy ekran jest zajęty
- [x] **SCHED-02**: ONESHOT harmonogram nie odpala się ponownie po restarcie Pi (kolumna `firedAt` ustawiana atomowo z `status=DONE`)
- [x] **SCHED-03**: Harmonogram z błędnym wyrażeniem CRON otrzymuje status `ERROR` i nie zapędza się w pętlę
- [x] **SCHED-04**: Harmonogram może mieć opcjonalny `webhookUrl` — URL do wywołania po wyświetleniu wiadomości

---

## Observability (OBS)

- [ ] **OBS-01**: `GET /health/detail` zwraca rozszerzony payload: uptime, memory, display status, error counts
- [ ] **OBS-02**: `GET /metrics` zawiera hardware group — display failures, recovery retries, resource utilization
- [ ] **OBS-03**: Strony HTML 404/500 są dostępne w przeglądarce dla nieznanych GET routes (fix Ktor SwaggerUI catch-all)

---

## Display History (HIST)

- [x] **HIST-01**: Każde wyświetlenie tekstu (immediate i scheduled) jest zapisywane w bazie — tekst, efekt, strefa, timestamp, źródło
- [x] **HIST-02**: `GET /api/v1/history` zwraca paginowaną listę zdarzeń wyświetlania
- [x] **HIST-03**: `GET /history` — strona HTML z historią wyświetlonych tekstów (karty/tabela, paginacja, filtr po strefie i efekcie)

---

## Webhooks (HOOK)

- [x] **HOOK-01**: Po wystrzeleniu harmonogramu, HTTP POST wysyłany jest do `webhookUrl` harmonogramu (fire-and-forget, timeout 5s)
- [x] **HOOK-02**: Webhook payload zawiera: tekst, efekt, schedule ID, zone ID, timestamp
- [x] **HOOK-03**: Globalny fallback `webhookUrl` konfigurowalny przez env var (`WEBHOOK_DEFAULT_URL`)

---

## Multi-Zone Displays (ZONE)

- [x] **ZONE-01**: Aplikacja obsługuje wiele wyświetlaczy podłączonych bezpośrednio do Pi przez SPI/I2C — named zones, konfiguracja przez env vars, backward-compatible default zone
- [ ] **ZONE-02**: Zewnętrzne wyświetlacze ogłaszają się w sieci WiFi przez UDP broadcast lub mDNS
- [ ] **ZONE-03**: Pi automatycznie wykrywa i rejestruje dostępne wyświetlacze sieciowe (autodiscovery)
- [ ] **ZONE-04**: Komunikacja z zewnętrznym wyświetlaczem przez WebSocket — przesyłanie tekstu i efektu
- [ ] **ZONE-05**: Heartbeat / health check — Pi śledzi które zewnętrzne urządzenia są online/offline
- [ ] **ZONE-06**: Użytkownik może ręcznie dodać zewnętrzny wyświetlacz przez IP (fallback gdy broadcast nie działa)
- [x] **ZONE-07**: `POST /api/v1/text?zone=X` — routing tekstu do dowolnej strefy (lokalnej lub sieciowej); harmonogramy mogą mieć przypisany `zoneId`
- [ ] **ZONE-08**: `GET /api/v1/zones` — lista wszystkich zarejestrowanych stref z ich statusem

---

## Refactor (REF)

- [x] **REF-01**: Analiza i usunięcie martwego kodu — nieużywane konfiguracje wyświetlaczy, unreferenced classes, stale constants, martwe ścieżki kodu
- [x] **REF-02**: Całościowe uproszczenie i oczyszczenie aplikacji — abstrakcje dozwolone tam gdzie mają sens, code smell usunięty
- [x] **REF-03**: DI smoke test weryfikuje że wszystkie providery w `configureDI()` są poprawnie podłączone (musi istnieć przed jakimkolwiek refactorem DI)
- [x] **REF-04**: Nowe ścieżki kodu pokryte testami (Kotest `should` convention, pokrycie ≥70%)

---

## UI/UX (UI)

- [ ] **UI-01**: Layout w stylu Material Design 3 — side navigation panel, karty (cards), gridy, responsywny (mobile-first), czysty HTML bez JS frameworka
- [ ] **UI-02**: Dark mode przez `prefers-color-scheme` + Material 3 color tokens
- [ ] **UI-03**: Side nav zawiera linki do: Wyślij tekst, Harmonogramy, Historia, Strefy, Status
- [ ] **UI-04**: Formularz wysyłania tekstu — zone selector (lokalne + sieciowe strefy), podgląd efektu
- [ ] **UI-05**: Strona harmonogramów (`/schedule`) — kolumna strefy, webhookUrl, expandable rows dla szczegółów
- [ ] **UI-06**: Strona historii (`/history`) — karty z paginacją, filtr po strefie i efekcie
- [ ] **UI-07**: Strona zarządzania strefami (`/zones`) — lista all zones (local + network), status online/offline, auto-discovered devices, przycisk "dodaj przez IP"
- [ ] **UI-08**: Strona status (`/status`) — dane z `GET /health/detail` + hardware metrics z `GET /metrics`

---

## Future Requirements (v1.2+)

- Pełnotekstowe przeszukiwanie historii wyświetleń
- Eksport historii do CSV
- WebSocket live feed — podgląd aktualnie wyświetlanego tekstu w przeglądarce w czasie rzeczywistym
- Dynamiczne tworzenie stref przez API (bez restartu)
- Zewnętrzny mikrokontroler firmware (ESP32/Arduino reference implementation)

---

## Out of Scope (v1.1)

- Uwierzytelnianie / autoryzacja (trusted home network)
- Cloud sync / dostęp spoza sieci domowej
- Aplikacja mobilna
- Własne fonty / multilingual rendering
- JS framework (React, Vue, HTMX) — Ktor HTML DSL wystarczy
- Baza danych inna niż H2/PostgreSQL

---

## Traceability

| REQ-ID | Phase | Status |
|--------|-------|--------|
| HW-01 | Phase 6 | Complete (06-01) |
| HW-02 | Phase 6 | Complete |
| SCHED-01 | Phase 7 | Complete |
| SCHED-02 | Phase 7 | Complete |
| SCHED-03 | Phase 7 | Complete |
| SCHED-04 | Phase 7 | Complete |
| REF-01 | Phase 8 | Complete |
| REF-02 | Phase 8 | Complete |
| REF-03 | Phase 8 | Complete |
| REF-04 | Phase 8 | Complete |
| HIST-01 | Phase 9 | Complete |
| HIST-02 | Phase 9 | Complete |
| HIST-03 | Phase 9 | Complete |
| HOOK-01 | Phase 10 | Complete |
| HOOK-02 | Phase 10 | Complete |
| HOOK-03 | Phase 10 | Complete |
| ZONE-01 | Phase 11 | Complete |
| ZONE-02 | Phase 11 | Pending |
| ZONE-03 | Phase 11 | Pending |
| ZONE-04 | Phase 11 | Pending |
| ZONE-05 | Phase 11 | Pending |
| ZONE-06 | Phase 11 | Pending |
| ZONE-07 | Phase 11 | Complete |
| ZONE-08 | Phase 11 | Pending |
| OBS-01 | Phase 12 | Pending |
| OBS-02 | Phase 12 | Pending |
| OBS-03 | Phase 12 | Pending |
| UI-01 | Phase 13 | Pending |
| UI-02 | Phase 13 | Pending |
| UI-03 | Phase 13 | Pending |
| UI-04 | Phase 13 | Pending |
| UI-05 | Phase 13 | Pending |
| UI-06 | Phase 13 | Pending |
| UI-07 | Phase 13 | Pending |
| UI-08 | Phase 13 | Pending |
