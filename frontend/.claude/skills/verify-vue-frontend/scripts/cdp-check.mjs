// Verify layout at several viewport widths via CDP, without Playwright/chromium-cli.
// Usage: node cdp-check.mjs [url] [cdpPort]
const URL = process.argv[2] || 'http://localhost:5173/';
const CDP_BASE = `http://localhost:${process.argv[3] || 9333}`;

// Widths to sweep — adjust to the range you actually care about.
const WIDTHS = [1400, 1024, 768, 480, 375, 320];

async function newTab(url) {
  const res = await fetch(`${CDP_BASE}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' });
  return res.json();
}
async function closeTab(id) {
  await fetch(`${CDP_BASE}/json/close/${id}`);
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    ws.addEventListener('open', () => resolve(ws));
    ws.addEventListener('error', reject);
  });
}
function send(ws, method, params = {}) {
  return new Promise((resolve) => {
    const id = Math.floor(Math.random() * 1e9);
    const handler = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id === id) {
        ws.removeEventListener('message', handler);
        resolve(msg.result);
      }
    };
    ws.addEventListener('message', handler);
    ws.send(JSON.stringify({ id, method, params }));
  });
}
function waitForEvent(ws, method) {
  return new Promise((resolve) => {
    const handler = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.method === method) {
        ws.removeEventListener('message', handler);
        resolve(msg.params);
      }
    };
    ws.addEventListener('message', handler);
  });
}

async function main() {
  const tab = await newTab(URL);
  const ws = await connect(tab.webSocketDebuggerUrl);
  await send(ws, 'Page.enable');
  await send(ws, 'Runtime.enable');

  const loaded = waitForEvent(ws, 'Page.loadEventFired');
  await loaded;
  // Extra settle time for Vue mount + any ResizeObserver-driven libs (carousels, etc).
  await new Promise((r) => setTimeout(r, 1500));

  for (const width of WIDTHS) {
    await send(ws, 'Emulation.setDeviceMetricsOverride', {
      width,
      height: 900,
      deviceScaleFactor: 1,
      mobile: false, // true skews window.innerWidth reporting — keep false
    });
    await new Promise((r) => setTimeout(r, 400));

    const result = await send(ws, 'Runtime.evaluate', {
      expression: `
        JSON.stringify({
          innerWidth: window.innerWidth,
          scrollWidth: document.documentElement.scrollWidth,
          bodyScrollWidth: document.body.scrollWidth,
          hasHorizontalScrollbar: document.documentElement.scrollWidth > window.innerWidth,
        })
      `,
      returnByValue: true,
    });
    console.log(`width=${width}px ->`, JSON.parse(result.result.value));
  }

  await send(ws, 'Emulation.clearDeviceMetricsOverride');
  ws.close();
  await closeTab(tab.id);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
