(() => {
  const src = new EventSource('/api/v1/live');

  src.addEventListener('display', e => {
    let d;
    try { d = JSON.parse(e.data); } catch { return; }
    const textEl = document.getElementById('live-text');
    const metaEl = document.getElementById('live-meta');
    if (textEl) textEl.textContent = d.text;
    if (metaEl) metaEl.textContent = (d.zoneId ?? '—') + ' · ' + d.effect;
  });

  src.onerror = () => {
    if (src.readyState === EventSource.CLOSED) window.location.reload();
  };
})();
