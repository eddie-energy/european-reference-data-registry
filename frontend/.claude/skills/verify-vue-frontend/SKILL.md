---
name: verify-vue-frontend
description: Use this skill to verify UI/CSS/layout changes in this Vue frontend actually work, rather than just reading the diff or asking the user to look. Applies whenever a change touches templates, styles, or layout (grid/flex, responsive behavior, overflow/scrolling, component rendering) in the `frontend/` app. Triggers on requests like "verify this works", "check the layout", "does this still overflow", "test at different screen sizes", or before reporting a UI fix as done.
---

# Verify Vue Frontend

Concrete, repeatable process for verifying a change in this repo's Vue app by
actually driving a browser against the running dev server — not just
type-checking or eyeballing the diff. No `chromium-cli` or Playwright is
installed in this project; drive Chrome directly via the DevTools Protocol
(CDP) using system `google-chrome` and Node's native `fetch`/`WebSocket`
(Node 22+, this repo runs Node 24).

## 1. Check whether the dev server is already running first

Never assume — check the port before starting a new server (avoids
`EADDRINUSE` and duplicate processes):

```bash
ss -ltnp 2>/dev/null | grep 5173   # default Vite port for this project
```

- **Running** → use it as-is, don't touch it.
- **Not running** → start it and poll until it responds:

```bash
npm run dev > /tmp/vite-dev.log 2>&1 &
disown
timeout 30 bash -c 'until curl -sf http://localhost:5173 >/dev/null; do sleep 1; done'
```

Only kill a dev server you started yourself.

## 2. Launch headless Chrome with a remote debugging port

```bash
google-chrome --headless=new --disable-gpu --no-sandbox \
  --remote-debugging-port=9333 --window-size=1400,900 about:blank \
  > /tmp/chrome-cdp.log 2>&1 &
disown
sleep 1
curl -s http://localhost:9333/json/version   # sanity check it's up
```

Pick a port not already in use; kill it when done:
`pkill -f 'remote-debugging-port=9333'`.

## 3. Drive it via CDP from a small Node script

Use `scripts/cdp-check.mjs` in this skill directory as the template. It:

- opens a new tab at the dev server URL,
- waits for `Page.loadEventFired` (+ a short extra pause for Vue/hydration
  and any ResizeObserver-driven libraries like `vue3-carousel` to settle),
- loops over a set of viewport widths via `Emulation.setDeviceMetricsOverride`
  (`mobile: false` — setting `mobile: true` skews `window.innerWidth`
  reporting and gives misleading results),
- runs `Runtime.evaluate` to pull back real layout facts, e.g.:
  `document.documentElement.scrollWidth` vs `window.innerWidth` to catch
  horizontal-overflow/unwanted-scrollbar bugs.

Copy/adapt it rather than starting from scratch — see the file for the full
working implementation (tab lifecycle, CDP `send`/`connect` helpers).

Run it: `node scripts/cdp-check.mjs`

For a visual check instead of/in addition to numeric checks, use
`Page.captureScreenshot` (base64 PNG) at specific widths and read the
resulting file with the Read tool — see `scripts/cdp-shots.mjs` pattern
(same tab/connect helpers, swap the `Runtime.evaluate` call for
`Page.captureScreenshot`).

## 4. Interpret results and adjust

- If overflow/breakage shows up, don't just patch the symptom at the
  component that visually broke — check ancestor layout first. In this
  codebase, CSS Grid/Flex containers (e.g. `App.vue`'s `.content-grid`)
  have implicit `min-width: auto` on their tracks/items, so a child with a
  large intrinsic content width (like a fixed-gap carousel) can force the
  *whole grid track* wider than the viewport even if the child itself has
  `overflow: hidden`. The fix is `min-width: 0` on the grid/flex item, not
  just clipping deeper in the tree.
- Re-run the width sweep after each change until the range you care about
  (state the range explicitly, e.g. "480px–1400px") is clean. Note known,
  out-of-scope limitations rather than silently expanding scope (e.g. this
  app's sidebar is a fixed `15rem` with no responsive collapse, so very
  narrow viewports will overflow regardless — that's a separate concern).

## 5. Clean up

- `pkill -f 'remote-debugging-port=<port>'` to stop the headless Chrome.
- Only stop the dev server if you started it in step 1.
