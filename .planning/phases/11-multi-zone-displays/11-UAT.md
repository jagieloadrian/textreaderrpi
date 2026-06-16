---
status: complete
phase: 11-multi-zone-displays
source: [11-01-SUMMARY.md, 11-02-SUMMARY.md, 11-03-SUMMARY.md, 11-04-SUMMARY.md, 11-05-SUMMARY.md]
started: "2026-06-16T00:00:00Z"
updated: "2026-06-16T00:00:00Z"
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start + Flyway V5 Migration
expected: App starts clean, Flyway migrates V5 (network_zones), GET /health returns 200
result: pass

### 2. GET /api/v1/zones — lista stref
expected: |
  GET http://localhost:8080/api/v1/zones zwraca JSON array z co najmniej jedną strefą lokalną
  (np. [{"id":"main","type":"MAX7219","status":"ONLINE","ip":null}]).
result: pass

### 3. POST /api/v1/text?zone=main — routing do strefy
expected: |
  POST http://localhost:8080/api/v1/text?zone=main z body {"text":"Hej","effect":"SCROLL"}
  zwraca 202 Accepted (lub 503 jeśli brak fizycznego sprzętu).
result: pass
note: 503 returned — hardware offline, correct behavior per D-06

### 4. POST /api/v1/text?zone=nieistniejaca — 404
expected: |
  POST /api/v1/text?zone=xyz z body {"text":"test","effect":"SCROLL"}
  zwraca 404 Not Found (strefa nie istnieje).
result: pass

### 5. POST /api/v1/zones/{ip} — dodaj strefę sieciową (prawidłowe IP)
expected: |
  POST http://localhost:8080/api/v1/zones/192.168.1.50 zwraca 201 Created z JSON obiektu NetworkZone.
  Ponowne POST na ten sam IP zwraca 409 Conflict.
result: pass

### 6. POST /api/v1/zones/8.8.8.8 — odrzucenie publicznego IP
expected: |
  POST http://localhost:8080/api/v1/zones/8.8.8.8 zwraca 400 Bad Request
  (IP spoza zakresu RFC1918).
result: pass

### 7. DELETE /api/v1/zones/{id} — usuń strefę sieciową
expected: |
  Po dodaniu 192.168.1.50 (test 5), DELETE http://localhost:8080/api/v1/zones/192.168.1.50
  zwraca 204 No Content. GET /api/v1/zones nie zawiera już tej strefy.
result: pass
note: Powtórny DELETE zwraca 404 — poprawne zachowanie idempotentne

### 8. DELETE lokalnej strefy — ochrona
expected: |
  DELETE http://localhost:8080/api/v1/zones/main (strefa lokalna) zwraca 404 Not Found —
  lokalne strefy nie mogą być usunięte.
result: pass

### 9. /zones strona UI — widok i interakcja
expected: |
  GET http://localhost:8080/zones zwraca stronę HTML. Widać:
  - nagłówek "Zones"
  - link "Zones" w nawigacji (między History a Settings)
  - karty stref z kolorowymi znacznikami statusu
  - przycisk "Scan for Displays"
  - formularz "Add Display by IP"
  - przycisk "Remove" tylko przy strefach sieciowych (nie przy lokalnych)
result: pass

### 10. POST /api/v1/zones/discover — skanowanie UDP
expected: |
  POST http://localhost:8080/api/v1/zones/discover zwraca 200 z JSON array
  (pusta lista [] jeśli brak urządzeń w sieci — to jest poprawne).
result: pass

## Summary

total: 10
passed: 10
issues: 0
pending: 0
skipped: 0

## Gaps

[none yet]
