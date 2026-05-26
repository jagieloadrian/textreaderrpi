---
phase: 2
slug: enhanced-display-support
status: approved
css_framework: pico-css-v2
template_engine: ktor-html-dsl
created: 2026-05-26
approved: 2026-05-26
---

# Phase 2 — UI Design Contract

> **Consumers:** gsd-planner, gsd-executor, gsd-ui-checker, gsd-ui-auditor
> **Sources:** 02-CONTEXT.md (D-11–D-16), 02-REQUIREMENTS.md, Pico CSS v2 docs
> **Design system:** Pico CSS v2 (classless, CDN-loaded, dark theme). No shadcn. No React. No build step.

---

## Design System

| Property | Value | Source |
|----------|-------|--------|
| CSS Framework | Pico CSS v2 | D-15 |
| CDN URL | `https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css` | Pico v2 docs |
| Theme | Dark — `data-theme="dark"` on `<html>` element | Design direction |
| Template engine | Ktor HTML DSL (`kotlinx.html`) + `Template<HTML>` interface | D-14 |
| JavaScript | Vanilla JS — single `app.js` static file, loaded with `defer` | NFR (no JS frameworks) |
| Icons | Unicode symbols only: `✓` `⚠` `✗` `→` | Zero-dependency |
| JS frameworks | None | Context constraint |

### Pico CSS Accent Override

Place in `BaseLayout` `<head>` as inline `<style>` block to override Pico defaults:

```css
:root {
  --pico-primary: #00aaff;
  --pico-primary-hover: #0090dd;
  --pico-primary-focus: rgba(0, 170, 255, 0.25);
}
```

All Pico-styled buttons, links, and focus rings inherit this override automatically. No other CSS file is needed.

---

## Spacing Scale

8-point base scale. Pico CSS ships its own `--pico-spacing` rhythm (1rem = 16px) — do not fight it; only declare project-specific overrides when needed.

| Token | Value | Primary Use |
|-------|-------|-------------|
| `--sp-1` | 4px | Icon gap, tight inline spacing (e.g. status badge gap) |
| `--sp-2` | 8px | Label-to-input gap, small padding |
| `--sp-3` | 16px | Card internal padding, paragraph spacing |
| `--sp-4` | 24px | Section vertical gap |
| `--sp-5` | 32px | Between major page sections |
| `--sp-6` | 48px | Page-level top/bottom padding |

**Exceptions:**
- Character counter `<small>` below `<textarea>`: 4px top margin (tight coupling to field)
- Nav links: use Pico `<nav>` default padding — no manual padding
- Touch targets: minimum 44px height for all interactive elements on mobile (Pico button defaults meet this)

---

## Typography

**Font family:** System font stack — no web fonts loaded (keeps page under 50 KB target).

```
system-ui, -apple-system, "Segoe UI", Roboto, sans-serif
```

### Type Scale — 4 sizes, 2 weights

| Role | Size | Weight | Line Height | Mapped Pico Element |
|------|------|--------|-------------|---------------------|
| Page heading | 28px (1.75rem) | 700 (bold) | 1.2 | `<h1>` |
| Section heading | 20px (1.25rem) | 700 (bold) | 1.3 | `<h2>` |
| Body / labels | 16px (1rem) | 400 (regular) | 1.5 | `<p>`, `<label>`, `<td>` |
| Small / muted | 14px (0.875rem) | 400 (regular) | 1.4 | `<small>`, counter, timestamp |

**Weight constraint:** Exactly 2 weights in use (400 and 700). Do not load a semibold (600) weight.

---

## Color

All colors from Pico CSS v2 dark theme. Only `--pico-primary` is overridden (to `#00aaff`).

### Palette — 60 / 30 / 10 split

| Role | % | Variable | Hex (Dark) | Used On |
|------|----|----------|------------|---------|
| Dominant surface | 60% | `--pico-background-color` | `#11191f` | Page background, `<body>` |
| Secondary surface | 30% | `--pico-card-background-color` | `#18232c` | `<article>` cards, form containers, status sections |
| Accent | 10% | `--pico-primary` (overridden) | `#00aaff` | Submit button, nav active link, focus ring, `✓` success icon |

### Semantic Colors

| Role | Hex | Reserved For |
|------|-----|--------------|
| Body text | `#c9d1d9` | All paragraph and label text (Pico `--pico-color`) |
| Muted text | `#6b7785` | Timestamps, character counter, footer (Pico `--pico-muted-color`) |
| Input border | `#374956` | `<input>`, `<textarea>`, `<select>` borders (Pico `--pico-form-element-border-color`) |
| Success / connected | `#3fb950` | Inline success feedback, `✓ Connected` badge, form submit success |
| Error / disconnected | `#e05c5c` | Inline error feedback, `✗` badge, error page code display, hardware error text |
| Warning | `#d29922` | `⚠` badge, switch failure warning |

### Accent Reserved-For List

Accent (`#00aaff`) is ONLY used on:
1. Primary submit button (`<button type="submit">`) — via Pico `[type=submit]` selector
2. Navigation links in current/active state
3. Focus rings on all interactive elements (via `--pico-primary-focus`)
4. `✓` success icon color inline

Do NOT apply accent to: body text, headings, decorative dividers, table rows, status badges.

---

## Page Inventory

### Page 1: `GET /` — Text Submission (Primary Action Page)

**`<title>`:** `TextReader — Send Text`
**Purpose:** Primary interaction — user submits text to be displayed on the connected display.

#### Layout Structure

```
<html data-theme="dark">
  BaseLayout:
    <header><nav> brand left / links right </nav></header>
    <main class="container">
      <h1>Send Text to Display</h1>
      <article>                              ← Pico card container
        <form id="text-form">
          <label for="text-input">Text to display</label>
          <textarea id="text-input" name="text" maxlength="128" rows="3"
                    required aria-describedby="char-counter feedback"
                    placeholder="Enter text (max 128 characters)"></textarea>
          <small id="char-counter" aria-live="polite">0 / 128</small>
          <button type="submit">Send to Display</button>
        </form>
        <div id="feedback" role="alert" aria-live="assertive"></div>
      </article>
      <p><small>Sending to: <strong>{displayType}</strong></small></p>
    </main>
    <footer><small>TextReader v{version}</small></footer>
</html>
```

#### Interactions

| Trigger | Mechanism | Behavior |
|---------|-----------|----------|
| Typing in textarea | `input` event (JS) | Updates `#char-counter` to `{n} / 128`; color turns `#e05c5c` at 128 |
| Form submit (JS on) | `submit` event → `fetch()` POST `/api/text` | Prevents default; sets button to `Sending…` + disabled; on resolve updates `#feedback`; re-enables button |
| Form submit (no JS) | Native HTML `POST /` | Server returns page with flash message in URL query param (`?msg=sent` / `?err=...`) |
| Success | JS updates `#feedback` | `<span style="color:#3fb950">✓ Text sent successfully.</span>` |
| API error | JS updates `#feedback` | `<span style="color:#e05c5c">✗ {message from error JSON field}</span>` |
| Network error | JS catch | `<span style="color:#e05c5c">✗ Could not reach the server. Check your connection.</span>` |

#### Empty / Zero State
- Textarea: blank
- Counter: `0 / 128` (muted color)
- Feedback div: empty (`min-height: 1.5rem` to prevent layout shift on appear)
- Display indicator: shows live `displayType` from server

---

### Page 2: `GET /status` — Display Status

**`<title>`:** `TextReader — Display Status`
**Purpose:** At-a-glance operational view. Scannable in under 5 seconds.

#### Layout Structure

```
<main class="container">
  <h1>Display Status</h1>
  <div class="grid">                         ← Pico grid: side-by-side on lg+, stacked mobile
    <article>
      <h2>Active Display</h2>
      <p><strong>{MAX7219 | LCD | OLED}</strong></p>
      <p>{connection badge}</p>              ← see Badge Spec below
    </article>
    <article>
      <h2>Last Message</h2>
      <p><code>{lastText}</code></p>         ← em-dash if never sent
      <p><small>Last rendered: {timestamp}</small></p>   ← em-dash if never
    </article>
  </div>
  <!-- Rendered ONLY when hardware is in error state: -->
  <article>
    <h2>⚠ Hardware Error</h2>
    <p style="color:#e05c5c">{errorMessage}</p>
  </article>
</main>
```

#### Connection State Badges (inline style — no CSS class needed)

```html
<!-- Connected -->
<span style="color:#3fb950">✓ Connected</span>

<!-- Disconnected -->
<span style="color:#6b7785">✗ Disconnected</span>

<!-- Error -->
<span style="color:#e05c5c">⚠ Error</span>
```

#### Empty States

| Element | Empty Value |
|---------|-------------|
| Last submitted text | `—` (em-dash, `&mdash;`) |
| Last rendered timestamp | `—` (em-dash) |
| Error card | Not rendered (condition: hardware error == null) |

---

### Page 3: `GET /settings/display` — Display Settings

**`<title>`:** `TextReader — Display Settings`
**Purpose:** Switch active display type. Single configurable setting.

#### Layout Structure

```
<main class="container">
  <h1>Display Settings</h1>
  <article>
    <p>Active display: <strong>{currentType}</strong></p>
    <form method="post" action="/settings/display">
      <label for="display-type">Switch to</label>
      <select id="display-type" name="displayType">
        <option value="MAX7219" [selected]>MAX7219 (LED Matrix)</option>
        <option value="LCD"     [selected]>LCD (I2C 16×2)</option>
        <option value="OLED"    [selected]>OLED (SSD1306)</option>
      </select>
      <button type="submit">Apply</button>
    </form>
    <!-- Rendered only when ?result query param is present: -->
    <p id="switch-feedback">{feedback message}</p>
  </article>
</main>
```

**Confirmation:** None required — display switch is non-destructive and instantly reversible. No `confirm()` dialog, no intermediate page. Confirmation: none (home-lab context, single user).

#### POST → Redirect Pattern (PRG)

```
POST /settings/display
  → DisplayService.switchDisplay(type)
  → On success: HTTP 303 → GET /settings/display?result=success&type={newType}
  → On failure: HTTP 303 → GET /settings/display?result=error&reason={urlEncoded}
```

Server renders `#switch-feedback` from query params. No JS required.

#### Feedback Copy

| State | Copy |
|-------|------|
| Success | `✓ Display switched to {newType}.` (color: `#3fb950`) |
| Error | `⚠ Could not switch display: {reason}. Current display unchanged.` (color: `#d29922`) |
| Initial load (no param) | Not rendered |

#### Empty / Initial State
- `<select>` pre-selected to current `displayType`
- Feedback paragraph not rendered (no `?result` param)

---

### Page 4: Error Pages (400 / 404 / 500)

**`<title>`:** `TextReader — Error {code}`
**Purpose:** Human-readable browser error pages using `BaseLayout`.

#### Layout Structure

```html
<main class="container">
  <hgroup>
    <h1>{400 | 404 | 500}</h1>
    <p>{human description}</p>
  </hgroup>
  <p><a href="/">→ Return to home</a></p>
</main>
```

#### Copy Per Status Code

| Code | `<h1>` | `<p>` Description |
|------|--------|-------------------|
| 400 | `400` | `Bad request. Check your input and try again.` |
| 404 | `404` | `Page not found. This route doesn't exist.` |
| 500 | `500` | `Something went wrong on the server. The display may still be running.` |

No stack traces, no exception messages, no debug info in HTML output. Errors logged server-side only.

---

## Template Architecture

### BaseLayout

**Kotlin file:** `src/main/kotlin/com/anjo/routing/templates/BaseLayout.kt`
**Implements:** `Template<HTML>` (Ktor HTML DSL)

```kotlin
class BaseLayout(
    private val pageTitle: String,
    private val appVersion: String = "0.0.1"
) : Template<HTML> {

    val content = Placeholder<FlowContent>()

    override fun HTML.apply() {
        lang = "en"
        attributes["data-theme"] = "dark"

        head {
            meta(charset = "UTF-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title { +pageTitle }
            link(
                rel = "stylesheet",
                href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css"
            )
            style {
                unsafe {
                    +"""
                    :root {
                        --pico-primary: #00aaff;
                        --pico-primary-hover: #0090dd;
                        --pico-primary-focus: rgba(0, 170, 255, 0.25);
                    }
                    #feedback { min-height: 1.5rem; }
                    """.trimIndent()
                }
            }
            script(src = "/static/app.js") { attributes["defer"] = "true" }
        }

        body {
            header {
                nav {
                    ul { li { strong { +"TextReader" } } }
                    ul {
                        li { a(href = "/") { +"Home" } }
                        li { a(href = "/status") { +"Status" } }
                        li { a(href = "/settings/display") { +"Settings" } }
                    }
                }
            }
            main(classes = "container") {
                insert(content)
            }
            footer {
                small { +"TextReader v$appVersion" }
            }
        }
    }
}
```

**Nav structure note:** Pico CSS renders `<header><nav>` with two `<ul>` children as a split bar — left `<ul>` is brand, right `<ul>` is links. No custom CSS required.

### Per-Page Template Classes

| Page | Class | File |
|------|-------|------|
| Text submission | `IndexPage(displayType: String)` | `templates/IndexPage.kt` |
| Display status | `StatusPage(status: DisplayStatus)` | `templates/StatusPage.kt` |
| Display settings | `SettingsPage(currentType: DisplayType, result: SwitchResult?)` | `templates/SettingsPage.kt` |
| Error page | `ErrorPage(code: Int, message: String)` | `templates/ErrorPage.kt` |

Each page class takes typed data as constructor params (not raw `Map<String, Any>`).

### Route Usage Pattern

```kotlin
// Example: GET /
get("/") {
    val displayType = call.application.displayService.currentType()
    call.respondHtmlTemplate(
        BaseLayout(pageTitle = "TextReader — Send Text", appVersion = version)
    ) {
        content { insert(IndexPage(displayType = displayType.name)) }
    }
}
```

### Static Files

`GET /static/app.js` → served from `src/main/resources/static/app.js`
Configure Ktor static plugin in `Routing.kt`:
```kotlin
static("/static") { resources("static") }
```

---

## Responsive Breakpoints

Pico CSS v2 `class="container"` on `<main>` handles all responsive centering and max-width. No custom media queries needed for Phase 2.

| Breakpoint | Min Width | Container Max Width |
|------------|-----------|---------------------|
| Mobile (xs) | 0px | 100% − 2rem padding |
| Small (sm) | 576px | 510px |
| Medium (md) | 768px | 700px |
| Large (lg) | 1024px | 950px |
| XL (xl) | 1280px | 1200px |

**Minimum supported width:** 360px (common Android phone viewport). Pico v2 tested to 320px — no custom breakpoints required.

**Grid usage:** `class="grid"` on Status page only — places status cards side-by-side on `lg+`, collapses to single column on mobile automatically.

---

## Copywriting Contract

### Global Chrome

| Element | Copy |
|---------|------|
| App brand (nav left) | `TextReader` |
| Nav: home | `Home` |
| Nav: status | `Status` |
| Nav: settings | `Settings` |
| Footer | `TextReader v{appVersion}` |

### GET / — Text Submission

| Element | Copy |
|---------|------|
| `<title>` | `TextReader — Send Text` |
| Page heading | `Send Text to Display` |
| Textarea label | `Text to display` |
| Textarea placeholder | `Enter text (max 128 characters)` |
| Counter (initial) | `0 / 128` |
| Counter (at limit) | `128 / 128` — rendered in `color:#e05c5c` |
| Submit button (idle) | `Send to Display` |
| Submit button (pending) | `Sending…` |
| Display indicator | `Sending to: {MAX7219 \| LCD \| OLED}` |
| Success feedback | `✓ Text sent successfully.` |
| Error feedback — API error | `✗ {message field from JSON error response}` |
| Error feedback — network | `✗ Could not reach the server. Check your connection.` |
| Error feedback — validation | `✗ Text must be between 1 and 128 characters.` |

### GET /status — Display Status

| Element | Copy |
|---------|------|
| `<title>` | `TextReader — Display Status` |
| Page heading | `Display Status` |
| Active display card heading | `Active Display` |
| Connection: connected | `✓ Connected` |
| Connection: disconnected | `✗ Disconnected` |
| Connection: error | `⚠ Error` |
| Last message card heading | `Last Message` |
| Last text — empty | `—` |
| Last rendered label | `Last rendered:` |
| Last rendered — empty | `—` |
| Hardware error card heading | `⚠ Hardware Error` |

### GET /settings/display — Display Settings

| Element | Copy |
|---------|------|
| `<title>` | `TextReader — Display Settings` |
| Page heading | `Display Settings` |
| Current setting label | `Active display:` |
| Select label | `Switch to` |
| Option: MAX7219 | `MAX7219 (LED Matrix)` |
| Option: LCD | `LCD (I2C 16×2)` |
| Option: OLED | `OLED (SSD1306)` |
| Submit button | `Apply` |
| Success feedback | `✓ Display switched to {newType}.` |
| Error feedback | `⚠ Could not switch display: {reason}. Current display unchanged.` |

### Error Pages

| Code | `<h1>` | `<p>` | Link text |
|------|--------|-------|-----------|
| 400 | `400` | `Bad request. Check your input and try again.` | `→ Return to home` |
| 404 | `404` | `Page not found. This route doesn't exist.` | `→ Return to home` |
| 500 | `500` | `Something went wrong on the server. The display may still be running.` | `→ Return to home` |

---

## JavaScript Contract (`src/main/resources/static/app.js`)

All JS is progressive enhancement. Every form must be functional without JS via standard HTML POST.

### Character Counter

```js
document.addEventListener('DOMContentLoaded', () => {
    const textarea = document.getElementById('text-input');
    const counter  = document.getElementById('char-counter');
    if (!textarea || !counter) return;

    textarea.addEventListener('input', () => {
        const len = textarea.value.length;
        counter.textContent = `${len} / 128`;
        counter.style.color = len >= 128 ? '#e05c5c' : '';
    });
});
```

### Async Form Submit

```js
document.addEventListener('DOMContentLoaded', () => {
    const form     = document.getElementById('text-form');
    const textarea = document.getElementById('text-input');
    const btn      = form?.querySelector('button[type="submit"]');
    const fb       = document.getElementById('feedback');
    if (!form || !textarea || !btn || !fb) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        btn.disabled    = true;
        btn.textContent = 'Sending\u2026';
        fb.innerHTML    = '';

        try {
            const res  = await fetch('/api/text', {
                method:  'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body:    JSON.stringify({ text: textarea.value })
            });
            const data = await res.json().catch(() => ({}));
            if (res.ok) {
                fb.innerHTML = '<span style="color:#3fb950">\u2713 Text sent successfully.</span>';
                textarea.value = '';
                document.getElementById('char-counter').textContent = '0 / 128';
                document.getElementById('char-counter').style.color = '';
            } else {
                const msg = data.error?.message || data.message || 'Unknown error';
                fb.innerHTML = `<span style="color:#e05c5c">\u2717 ${msg}</span>`;
            }
        } catch {
            fb.innerHTML = '<span style="color:#e05c5c">\u2717 Could not reach the server. Check your connection.</span>';
        } finally {
            btn.disabled    = false;
            btn.textContent = 'Send to Display';
        }
    });
});
```

No other JS functionality in Phase 2. Total JS: < 80 lines.

---

## Accessibility Contract

| Requirement | Implementation | Who Handles |
|-------------|----------------|-------------|
| Semantic landmarks | `<header>`, `<nav>`, `<main>`, `<footer>` | BaseLayout template |
| All inputs labeled | `<label for="...">` paired with every `<input>`, `<textarea>`, `<select>` | Per-page template |
| Error association | `aria-describedby="char-counter feedback"` on `<textarea>` | IndexPage template |
| Live feedback region | `role="alert" aria-live="assertive"` on `#feedback` div | IndexPage template |
| Character counter live | `aria-live="polite"` on `#char-counter` | IndexPage template |
| Touch targets ≥ 44px | Pico button default `--pico-form-element-spacing-vertical: 0.75rem` | ✓ Pico default |
| Color contrast WCAG AA | All text on dark surfaces passes (Pico dark theme designed for this) | ✓ Pico default |
| Color not sole indicator | All badges use text + symbol (`✓ Connected`, not just green dot) | Per-page template |

---

## Checker Sign-Off

| Dimension | Verdict | Notes |
|-----------|---------|-------|
| 1 Copywriting | ✓ PASS | All states covered: idle, pending, success, error, empty, network failure, per page |
| 2 Visuals | ✓ PASS | All 4 page layouts with concrete HTML structure; focal points declared; template classes named |
| 3 Color | ✓ PASS | 60/30/10 declared; accent scoped to 4 explicit elements; semantic map for success/error/warning/muted |
| 4 Typography | ✓ PASS | 4 sizes (28/20/16/14px), 2 weights (400/700), line heights declared for all roles |
| 5 Spacing | ✓ PASS | 6-token 8pt scale (4/8/16/24/32/48px); all multiples of 4; exceptions documented |
| 6 Registry Safety | ✓ PASS | Pico CSS v2 CDN only; no component registry; no shadcn; no npm build; aligns with D-15 |

**Decisions cross-check:**

| Decision | Status |
|----------|--------|
| D-11 — HTML as primary browser flow | ✓ All page types defined |
| D-12 — GET /, /status, /settings/display | ✓ All three fully specced |
| D-13 — HTML error pages (400/404/500) | ✓ Specced with concrete copy |
| D-14 — Hybrid BaseLayout + per-page templates | ✓ Architecture with Kotlin code sketch |
| D-15 — Lightweight CSS framework (Pico CSS v2) | ✓ CDN URL, dark theme, accent override |
| D-16 — JSON API preserved | ✓ HTML routes are additive; `/api/*` unchanged |

**Approval:** approved 2026-05-26

