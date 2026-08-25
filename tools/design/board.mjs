// Screenshots ONE artboard of a design-spec chapter, exactly clipped, after optional clicks.
//
// Drives headless Chrome over the DevTools Protocol with Node's built-in WebSocket: the repo has
// Playwright's browser binaries but no bindings, and these artboards are interactive — the mission
// detail's seven tabs, the segment switches and the payout radios only exist after a click.
//
// The clip comes from the artboard's own bounding box rather than from guessed pixels, so a
// chapter that reflows does not silently start cropping the wrong frame.
//
// usage: node board.mjs <url> <artboardNumber> <out.png> [clickText ...]
const [, , url, board, out, ...clicks] = process.argv;

const endpoint = await (
  await fetch("http://127.0.0.1:9222/json/new?" + encodeURIComponent(url), { method: "PUT" })
).json();
const ws = new WebSocket(endpoint.webSocketDebuggerUrl);
let id = 0;
const pending = new Map();
ws.addEventListener("message", (e) => {
  const msg = JSON.parse(e.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg.result ?? msg.error);
    pending.delete(msg.id);
  }
});
await new Promise((r) => ws.addEventListener("open", r));
const send = (method, params = {}) =>
  new Promise((resolve) => {
    const n = ++id;
    pending.set(n, resolve);
    ws.send(JSON.stringify({ id: n, method, params }));
  });
const evaluate = async (expression) =>
  (await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true }))?.result?.value;

await send("Page.enable");
await new Promise((r) => setTimeout(r, 2600));

// The caption "N · Title" sits in a sibling above the frame; its parent column is the artboard.
const rect = await evaluate(`(() => {
  const caption = [...document.querySelectorAll('div')]
    .find(e => e.children.length === 0 && new RegExp('^\\\\s*${board}\\\\s*[·]').test(e.textContent || ''));
  if (!caption) return null;
  const col = caption.parentElement;
  const r = col.getBoundingClientRect();
  return { x: r.x + window.scrollX, y: r.y + window.scrollY, width: r.width, height: r.height,
           label: caption.textContent.trim().slice(0, 70) };
})()`);
if (!rect) {
  process.stdout.write(`artboard ${board} not found\n`);
  process.exit(2);
}
process.stdout.write(`${rect.label}\n`);

for (const label of clicks) {
  const hit = await evaluate(`(() => {
    const wanted = ${JSON.stringify(label)}.toLowerCase();
    const scope = document.body;
    const el = [...scope.querySelectorAll('button,.tab,label,[onclick],[role=tab],select')]
      .find(e => (e.textContent || '').trim().toLowerCase().startsWith(wanted));
    if (!el) return 'MISS';
    el.click();
    return 'HIT';
  })()`);
  process.stdout.write(`  click ${label}: ${hit}\n`);
  await new Promise((r) => setTimeout(r, 800));
}

const { data } = await send("Page.captureScreenshot", {
  format: "png",
  captureBeyondViewport: true,
  clip: { x: rect.x, y: rect.y, width: rect.width, height: rect.height, scale: 1 },
});
const { writeFileSync } = await import("node:fs");
writeFileSync(out, Buffer.from(data, "base64"));
process.stdout.write(`  wrote ${out}\n`);
await send("Page.close");
ws.close();
process.exit(0);
