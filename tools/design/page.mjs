// Whole-page screenshot of a chapter, for chapters whose frames carry no caption of their own.
const [, , url, out] = process.argv;
const endpoint = await (await fetch("http://127.0.0.1:9222/json/new?" + encodeURIComponent(url), { method: "PUT" })).json();
const ws = new WebSocket(endpoint.webSocketDebuggerUrl);
let id = 0; const pending = new Map();
ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result ?? m.error); pending.delete(m.id); } });
await new Promise((r) => ws.addEventListener("open", r));
const send = (method, params = {}) => new Promise((res) => { const n = ++id; pending.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
await send("Page.enable");
await new Promise((r) => setTimeout(r, 2600));
const { data } = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true });
const { writeFileSync } = await import("node:fs");
writeFileSync(out, Buffer.from(data, "base64"));
process.stdout.write("wrote " + out + "\n");
process.exit(0);
