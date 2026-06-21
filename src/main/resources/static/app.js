(() => {
  const maxLen = 128;

  // ─── Toast ────────────────────────────────────────────────────────────────
  function showToast(message, type = "info") {
    const toast = document.createElement("div");
    toast.textContent = message;
    toast.className = `toast ${type}`;
    Object.assign(toast.style, {
      position: "fixed",
      right: "1rem",
      bottom: "1rem",
      padding: "0.75rem 1rem",
      borderRadius: "0.5rem",
      zIndex: "9999",
      color: "#fff",
      background: type === "error" ? "#b91c1c" : "#166534",
      opacity: "0",
      transition: "opacity .2s ease"
    });
    document.body.appendChild(toast);
    requestAnimationFrame(() => { toast.style.opacity = "1"; });
    setTimeout(() => {
      toast.style.opacity = "0";
      setTimeout(() => toast.remove(), 200);
    }, 2200);
  }

  // ─── Nav toggle ──────────────────────────────────────────────────────────
  function openNav() {
    document.body.classList.add("nav-open");
  }

  function closeNav() {
    document.body.classList.remove("nav-open");
  }

  function toggleNav() {
    document.body.classList.toggle("nav-open");
  }

  // ─── Home: submit text ────────────────────────────────────────────────────
  function updateCounter() {
    const input = document.getElementById("textInput");
    const counter = document.getElementById("charCounter");
    if (!input || !counter) return;
    counter.textContent = `${input.value.length} / ${maxLen}`;
  }

  function mirrorPreviewText() {
    const input = document.getElementById("textInput");
    const preview = document.getElementById("effectPreview");
    const span = document.getElementById("effectPreviewText");
    if (!input || !preview || !span) return;
    const text = input.value;
    span.textContent = text;
    preview.style.visibility = text ? "visible" : "hidden";
  }

  function applyEffectPreview() {
    const span = document.getElementById("effectPreviewText");
    const effectSelect = document.getElementById("effectSelect");
    if (!span || !effectSelect) return;
    span.classList.remove("effect-scroll", "effect-blink", "effect-reverse", "effect-fade");
    const effect = effectSelect.value.toLowerCase();
    if (effect === "scroll") span.classList.add("effect-scroll");
    else if (effect === "blink") span.classList.add("effect-blink");
    else if (effect === "reverse") span.classList.add("effect-reverse");
    else if (effect === "fade") span.classList.add("effect-fade");
  }

  async function submitForm() {
    const input = document.getElementById("textInput");
    const effectSelect = document.getElementById("effectSelect");
    if (!input) return;

    const text = input.value ?? "";
    const effect = effectSelect ? effectSelect.value : "SCROLL";
    const zoneId = document.getElementById("zoneSelect")?.value ?? "";
    const url = zoneId ? "/api/v1/text?zone=" + encodeURIComponent(zoneId) : "/api/v1/text";
    try {
      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, effect })
      });
      if (response.ok) {
        showToast("Text sent", "success");
      } else if (response.status === 503) {
        showToast("Zone offline. Text not sent.", "error");
      } else if (response.status === 404) {
        showToast("Zone not found.", "error");
      } else {
        const err = await response.text();
        showToast("Failed to send: " + (err || response.status), "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  // ─── Status: fetch and poll ───────────────────────────────────────────────
  let _uptimeBase = null;
  let _uptimeFetchedAt = null;

  function formatUptime(ms) {
    const totalSec = Math.floor(ms / 1000);
    const d = Math.floor(totalSec / 86400);
    const h = Math.floor((totalSec % 86400) / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    if (d > 0) return `${d}d ${h}h ${m}m ${s}s`;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  function tickUptime() {
    if (_uptimeBase === null || _uptimeFetchedAt === null) return;
    const el = document.getElementById("status-uptime");
    if (!el) return;
    el.textContent = formatUptime(_uptimeBase + (Date.now() - _uptimeFetchedAt));
  }

  function formatBytes(bytes) {
    if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + " GB";
    if (bytes >= 1048576) return Math.round(bytes / 1048576) + " MB";
    return Math.round(bytes / 1024) + " KB";
  }

  async function fetchStatusData() {
    try {
      const [detailRes, metricsRes] = await Promise.all([
        fetch("/health/detail"),
        fetch("/metrics")
      ]);
      if (detailRes.ok) {
        const detail = await detailRes.json();
        const setSpan = (id, val) => {
          const el = document.getElementById(id);
          if (el) el.textContent = val;
        };
        _uptimeBase = detail.uptime;
        _uptimeFetchedAt = Date.now();
        tickUptime();
        setSpan("status-memory-used", formatBytes(detail.memoryUsed));
        setSpan("status-memory-max", formatBytes(detail.memoryMax));
        setSpan("status-display", detail.displayStatus);
        setSpan("status-failures", detail.totalFailures);
      } else {
        ["status-uptime", "status-memory-used", "status-memory-max", "status-display", "status-failures"].forEach(id => {
          const el = document.getElementById(id);
          if (el) el.textContent = "Unavailable";
        });
      }
      if (metricsRes.ok) {
        const metrics = await metricsRes.json();
        const hwGroup = (metrics.groups || []).find(g => g.name === "hardware");
        const hwMetrics = hwGroup ? hwGroup.metrics || [] : [];
        const findCount = key => {
          const m = hwMetrics.find(m => m.key === key);
          return m !== undefined ? m.count : "Unavailable";
        };
        const setSpan = (id, val) => {
          const el = document.getElementById(id);
          if (el) el.textContent = val;
        };
        setSpan("status-hw-failures", findCount("display.failures"));
        setSpan("status-hw-retries", findCount("recovery.retries"));
      } else {
        ["status-hw-failures", "status-hw-retries"].forEach(id => {
          const el = document.getElementById(id);
          if (el) el.textContent = "Unavailable";
        });
      }
    } catch (_) {
      ["status-uptime", "status-memory-used", "status-memory-max", "status-display", "status-failures", "status-hw-failures", "status-hw-retries"].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = "Unavailable";
      });
    }
  }

  // ─── Schedule page ────────────────────────────────────────────────────────
  function escHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderScheduleList(schedules) {
    const container = document.getElementById("scheduleListContainer");
    if (!container) return;

    if (!schedules || schedules.length === 0) {
      container.innerHTML = "<div>No schedules yet.</div>";
      return;
    }

    const isStoppable = s =>
      s.status === "ACTIVE" && (s.triggerType === "RECURRING" || s.triggerType === "CRON");

    const rows = schedules.map(s => `
      <tr>
        <td>${escHtml(s.id.slice(0, 8))}</td>
        <td>${escHtml(s.text)}</td>
        <td>${escHtml(s.triggerType)}: ${escHtml(s.triggerValue)}</td>
        <td>${escHtml(s.effect)}</td>
        <td>${escHtml(s.status)}</td>
        <td>${escHtml(s.zoneId || "—")}</td>
        <td>${escHtml(s.webhookUrl || "—")}</td>
        <td>
          ${isStoppable(s) ? `<a href="#" data-stop-id="${escHtml(s.id)}">Stop</a> ` : ""}
          <a href="#" data-delete-id="${escHtml(s.id)}">Delete</a>
        </td>
      </tr>`).join("");

    container.innerHTML = `
      <table>
        <thead><tr>
          <th>ID</th><th>Text</th><th>Trigger</th><th>Effect</th><th>Status</th><th>Zone</th><th>Webhook</th><th>Actions</th>
        </tr></thead>
        <tbody>${rows}</tbody>
      </table>`;

    container.querySelectorAll("[data-stop-id]").forEach(link => {
      link.addEventListener("click", async e => {
        e.preventDefault();
        await stopSchedule(link.getAttribute("data-stop-id"));
      });
    });

    container.querySelectorAll("[data-delete-id]").forEach(link => {
      link.addEventListener("click", async e => {
        e.preventDefault();
        await deleteSchedule(link.getAttribute("data-delete-id"));
      });
    });
  }

  async function loadSchedules() {
    try {
      const response = await fetch("/api/v1/schedule");
      if (response.ok) renderScheduleList(await response.json());
    } catch (_) { /* silently ignore */ }
  }

  async function createSchedule() {
    const text = document.getElementById("text")?.value ?? "";
    const triggerType = document.getElementById("triggerType")?.value ?? "RECURRING";
    const triggerValue = document.getElementById("triggerValue")?.value ?? "";
    const effect = document.getElementById("effect")?.value ?? "SCROLL";
    const priority = parseInt(document.getElementById("priority")?.value ?? "0", 10);
    const zoneId = document.getElementById("scheduleZoneSelect")?.value ?? "";

    try {
      const response = await fetch("/api/v1/schedule", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, triggerType, triggerValue, effect, priority, zoneId: zoneId || null })
      });
      if (response.ok) {
        showToast("Schedule created", "success");
        document.getElementById("createScheduleForm")?.reset();
        await loadSchedules();
      } else {
        const err = await response.json().catch(() => ({}));
        showToast(err.message || "Failed to create schedule", "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  async function stopSchedule(id) {
    try {
      const response = await fetch(`/api/v1/schedule/${id}/cancel`, { method: "POST" });
      if (response.ok || response.status === 204) {
        showToast("Schedule stopped", "success");
        await loadSchedules();
      } else {
        showToast("Failed to stop schedule", "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  async function deleteSchedule(id) {
    try {
      const response = await fetch(`/api/v1/schedule/${id}`, { method: "DELETE" });
      if (response.ok || response.status === 204) {
        showToast("Schedule deleted", "success");
        await loadSchedules();
      } else {
        showToast("Failed to delete schedule", "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  // ─── Zones page ──────────────────────────────────────────────────────────
  async function scanForDisplays() {
    const btn = document.getElementById("scanBtn");
    const resultDiv = document.getElementById("scanResult");
    if (!btn || !resultDiv) return;
    btn.disabled = true;
    btn.textContent = "Scanning...";
    try {
      const response = await fetch("/api/v1/zones/discover", { method: "POST" });
      if (response.ok) {
        const data = await response.json().catch(() => []);
        const count = Array.isArray(data) ? data.length : 0;
        if (count > 0) {
          resultDiv.textContent = `Found ${count} new display(s). Page reloading...`;
          setTimeout(() => { window.location.href = "/zones"; }, 1000);
        } else {
          resultDiv.textContent = "No new displays found. Make sure devices are on the same network.";
        }
      } else {
        resultDiv.textContent = "Scan failed. Check server connection.";
      }
    } catch (_) {
      resultDiv.textContent = "Scan failed. Check server connection.";
    } finally {
      btn.disabled = false;
      btn.textContent = "Scan for Displays";
    }
  }

  async function addZoneByIp(e) {
    e.preventDefault();
    const ip = (document.getElementById("ipInput")?.value ?? "").trim();
    const resultDiv = document.getElementById("addZoneResult");
    if (!resultDiv) return;
    try {
      const response = await fetch("/api/v1/zones/" + encodeURIComponent(ip), { method: "POST" });
      if (response.status === 201 || response.status === 200) {
        resultDiv.textContent = "Zone added. Page reloading...";
        setTimeout(() => { window.location.href = "/zones"; }, 1000);
      } else if (response.status === 409) {
        resultDiv.textContent = "A zone with this IP is already registered.";
      } else if (response.status === 400) {
        resultDiv.textContent = "Enter a valid IP address (e.g. 192.168.1.50).";
      } else {
        resultDiv.textContent = `Could not connect to ${ip}. Verify the device is online.`;
      }
    } catch (_) {
      resultDiv.textContent = `Could not connect to ${ip}. Verify the device is online.`;
    }
  }

  async function deleteZone(id) {
    const resultDiv = document.getElementById("deleteResult-" + id);
    try {
      const response = await fetch("/api/v1/zones/" + encodeURIComponent(id), { method: "DELETE" });
      if (response.status === 204) {
        if (resultDiv) resultDiv.textContent = "Removed. Reloading...";
        setTimeout(() => { window.location.href = "/zones"; }, 800);
      } else if (response.status === 404) {
        if (resultDiv) resultDiv.textContent = "Zone not found.";
      } else {
        if (resultDiv) resultDiv.textContent = "Could not remove zone.";
      }
    } catch (_) {
      if (resultDiv) resultDiv.textContent = "Could not remove zone.";
    }
  }

  // ─── Boot ─────────────────────────────────────────────────────────────────
  document.addEventListener("DOMContentLoaded", () => {
    // Nav toggle (mobile hamburger)
    const navToggle = document.getElementById("navToggle");
    if (navToggle) navToggle.addEventListener("click", toggleNav);
    const navBackdrop = document.getElementById("navBackdrop");
    if (navBackdrop) navBackdrop.addEventListener("click", closeNav);
    document.querySelectorAll("aside nav a").forEach(link => {
      link.addEventListener("click", closeNav);
    });

    // Home page
    const textInput = document.getElementById("textInput");
    const submitBtn = document.getElementById("submitTextBtn");
    if (textInput) {
      textInput.addEventListener("input", updateCounter);
      textInput.addEventListener("input", mirrorPreviewText);
      updateCounter();
    }
    if (submitBtn) submitBtn.addEventListener("click", e => { e.preventDefault(); submitForm(); });

    // Effect preview
    const effectPreview = document.getElementById("effectPreview");
    if (effectPreview) {
      const effectSelect = document.getElementById("effectSelect");
      if (effectSelect) effectSelect.addEventListener("change", applyEffectPreview);
      mirrorPreviewText();
      applyEffectPreview();
    }

    // Schedule page
    const createBtn = document.getElementById("createScheduleBtn");
    if (createBtn) createBtn.addEventListener("click", e => { e.preventDefault(); createSchedule(); });
    if (document.getElementById("scheduleListContainer")) loadSchedules();

    // Status page
    if (document.getElementById("status-uptime")) {
      fetchStatusData();
      setInterval(fetchStatusData, 10000);
      setInterval(tickUptime, 1000);
    }

    // Zones page
    const scanBtn = document.getElementById("scanBtn");
    if (scanBtn) scanBtn.addEventListener("click", e => { e.preventDefault(); scanForDisplays(); });
    const addZoneForm = document.getElementById("addZoneForm");
    if (addZoneForm) addZoneForm.addEventListener("submit", addZoneByIp);
    document.querySelectorAll(".delete-zone-btn").forEach(btn => {
      btn.addEventListener("click", e => { e.preventDefault(); deleteZone(btn.dataset.zoneId); });
    });
  });
})();
