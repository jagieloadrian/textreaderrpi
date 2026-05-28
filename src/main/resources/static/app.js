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

  // ─── Home: submit text ────────────────────────────────────────────────────
  function updateCounter() {
    const input = document.getElementById("textInput");
    const counter = document.getElementById("charCounter");
    if (!input || !counter) return;
    counter.textContent = `${input.value.length} / ${maxLen}`;
  }

  async function submitForm() {
    const input = document.getElementById("textInput");
    const effectSelect = document.getElementById("effectSelect");
    if (!input) return;

    const text = input.value ?? "";
    const effect = effectSelect ? effectSelect.value : "SCROLL";
    try {
      const response = await fetch("/api/v1/text", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, effect })
      });
      if (response.ok) {
        showToast("Text sent", "success");
      } else {
        const err = await response.text();
        showToast("Failed to send: " + (err || response.status), "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  // ─── Settings: apply driver ───────────────────────────────────────────────
  async function applyDriver() {
    const select = document.getElementById("driverSelect");
    if (!select) return;
    try {
      const response = await fetch("/api/v1/display/select", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: select.value })
      });
      if (response.ok) {
        showToast("Driver switch queued", "success");
      } else {
        const err = await response.json().catch(() => ({}));
        showToast(err.message || "Cannot switch driver", "error");
      }
    } catch (_) {
      showToast("Network error", "error");
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
        <td>
          ${isStoppable(s) ? `<a href="#" data-stop-id="${escHtml(s.id)}">Stop</a> ` : ""}
          <a href="#" data-delete-id="${escHtml(s.id)}">Delete</a>
        </td>
      </tr>`).join("");

    container.innerHTML = `
      <table>
        <thead><tr>
          <th>ID</th><th>Text</th><th>Trigger</th><th>Effect</th><th>Status</th><th>Actions</th>
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

    try {
      const response = await fetch("/api/v1/schedule", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, triggerType, triggerValue, effect, priority })
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

  // ─── Boot ─────────────────────────────────────────────────────────────────
  document.addEventListener("DOMContentLoaded", () => {
    // Home page
    const textInput = document.getElementById("textInput");
    const submitBtn = document.getElementById("submitTextBtn");
    if (textInput) {
      textInput.addEventListener("input", updateCounter);
      updateCounter();
    }
    if (submitBtn) submitBtn.addEventListener("click", e => { e.preventDefault(); submitForm(); });

    // Settings page
    const applyBtn = document.getElementById("applyDriverBtn");
    if (applyBtn) applyBtn.addEventListener("click", e => { e.preventDefault(); applyDriver(); });

    // Schedule page
    const createBtn = document.getElementById("createScheduleBtn");
    if (createBtn) createBtn.addEventListener("click", e => { e.preventDefault(); createSchedule(); });
    if (document.getElementById("scheduleListContainer")) loadSchedules();
  });
})();
