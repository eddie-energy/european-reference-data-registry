// Screenshot the page at several viewport widths via CDP.
// Usage: node cdp-shots.mjs [url] [cdpPort] [outDir]
// Writes outDir/w<width>.png for each width — read them with the Read tool.
import fs from 'fs';

const URL = process.argv[2] || 'http://localhost:5173/';
const CDP_BASE = `http://localhost:${process.argv[3] || 9333}`;
const OUT_DIR = process.argv[4] || '.';
const WIDTHS = [1024, 768, 480];

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
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const tab = await newTab(URL);
  const ws = await connect(tab.webSocketDebuggerUrl);
  await send(ws, 'Page.enable');

  const loaded = waitForEvent(ws, 'Page.loadEventFired');
  await loaded;
  await new Promise((r) => setTimeout(r, 1500));

  for (const width of WIDTHS) {
    await send(ws, 'Emulation.setDeviceMetricsOverride', {
      width,
      height: 700,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await new Promise((r) => setTimeout(r, 400));
    const { data } = await send(ws, 'Page.captureScreenshot', { format: 'png' });
    const path = `${OUT_DIR}/w${width}.png`;
    fs.writeFileSync(path, Buffer.from(data, 'base64'));
    console.log('wrote', path);
  }

  ws.close();
  await closeTab(tab.id);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
