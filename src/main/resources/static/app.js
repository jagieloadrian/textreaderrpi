(() => {
  const maxLen = 128;

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
    requestAnimationFrame(() => {
      toast.style.opacity = "1";
    });
    setTimeout(() => {
      toast.style.opacity = "0";
      setTimeout(() => toast.remove(), 200);
    }, 2200);
  }

  async function submitForm() {
    const input = document.getElementById("textInput");
    if (!input) return;

    const text = input.value ?? "";
    try {
      const response = await fetch("/api/text", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text })
      });

      if (response.ok) {
        showToast("Text sent", "success");
      } else {
        showToast("Failed to send text", "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  function updateCounter() {
    const input = document.getElementById("textInput");
    const counter = document.getElementById("charCounter");
    if (!input || !counter) return;
    counter.textContent = `${input.value.length} / ${maxLen}`;
  }

  async function applyDriver() {
    const select = document.getElementById("driverSelect");
    if (!select) return;

    try {
      const response = await fetch("/api/display/select", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: select.value })
      });

      if (response.ok) {
        showToast("Driver switch queued", "success");
      } else {
        showToast("Cannot switch driver", "error");
      }
    } catch (_) {
      showToast("Network error", "error");
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    const input = document.getElementById("textInput");
    const submitBtn = document.getElementById("submitTextBtn");
    const applyBtn = document.getElementById("applyDriverBtn");

    if (input) {
      input.addEventListener("input", updateCounter);
      updateCounter();
    }
    if (submitBtn) {
      submitBtn.addEventListener("click", (e) => {
        e.preventDefault();
        submitForm();
      });
    }
    if (applyBtn) {
      applyBtn.addEventListener("click", (e) => {
        e.preventDefault();
        applyDriver();
      });
    }
  });
})();

