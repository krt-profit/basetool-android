# Rendering the design artboards

The chapters in [`docs/design/android/`](../../docs/design/android/README.md) are web pages, and the
only reliable way to check a screen against one is to look at both. Reading the chapter's prose is a
completeness check; it does not catch a tab row drawn as chips or a card missing its meter.

```bash
# 1. serve the spec — the .dc.html pages need their _ds/ and doc-page.js siblings
python -m http.server 8731 --directory docs/design/android

# 2. start headless Chrome with the debug port (Playwright's binary works; any Chrome does)
chrome --headless=new --remote-debugging-port=9222 --window-size=1600,1400        --user-data-dir=/tmp/spec-chrome about:blank &

# 3. render ONE artboard, clipped to its own frame
node tools/design/board.mjs "http://localhost:8731/06%20Missionen.dc.html" 2 ab06-2.png

# ...after clicking through to a tab, because the artboards are LIVE
node tools/design/board.mjs "http://localhost:8731/06%20Missionen.dc.html" 2 teilnehmer.png TEILNEHMER
```

The clip comes from the artboard's own bounding box, so a chapter that reflows does not silently
start cropping the wrong frame.

**Not every chapter captions its frames.** `board.mjs` anchors on the „N · Title" line above a
frame, and chapter 04 has none — its numbered lines are the headers of the *notes* cards under the
row of phones, so anchoring there clips the prose instead of the screen. For those chapters, take
the whole page and crop:

```bash
node tools/design/page.mjs "http://localhost:8731/04%20Auth.dc.html" auth-page.png
```

The other half of the spec is `_ds/…/krt-components.css`: the classes the artboards use
(`.facts-bar`, `.tab-nav`, `.attendance`, `card--flush`) carry the exact sizes and colours, and
several of them carry the reasoning too — `.attendance-meter` says in as many words why it is green
and not orange.
