(() => {
  const src = new EventSource('/api/v1/live');

  src.addEventListener('display', e => {
    const d = JSON.parse(e.data);
    const textEl = document.getElementById('live-text');
    const metaEl = document.getElementById('live-meta');
    if (textEl) textEl.textContent = d.text;
    if (metaEl) metaEl.textContent = (d.zoneId ?? '—') + ' · ' + d.effect;
  });

  src.onerror = () => {};
})();
