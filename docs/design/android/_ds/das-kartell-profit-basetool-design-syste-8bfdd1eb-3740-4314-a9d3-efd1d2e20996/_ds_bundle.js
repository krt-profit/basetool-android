/* @ds-bundle: {"format":4,"namespace":"DASKARTELLProfitBasetoolDesignSystem_8bfdd1","components":[],"sourceHashes":{"proposals/doc-page.js":"5957844bb066","proposals/materialboerse-final.js":"fd7350a140af","proposals/tweaks-panel.jsx":"6591467622ed","slides/deck-stage.js":"0de1efd241e5","ui_kits/basetool/components.jsx":"7ef9bbaddfc4","ui_kits/basetool/data.jsx":"9ff5d7927402","ui_kits/basetool/icons.jsx":"970a9012d7a9","ui_kits/basetool/screen-mission-detail.jsx":"9d7cd4004fa7","ui_kits/basetool/screens.jsx":"43d0d99ff8ed"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.DASKARTELLProfitBasetoolDesignSystem_8bfdd1 = window.DASKARTELLProfitBasetoolDesignSystem_8bfdd1 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// proposals/doc-page.js
try { (() => {
// @ds-adherence-ignore -- omelette starter scaffold (raw elements/hex/px by design)
// Copied omelette starter. Re-running copy_starter_component with this kind overwrites this file with the latest version (page content is unaffected).
/* BEGIN USAGE */
/**
 * <doc-page> — paged-document shell for printable HTML.
 *
 * On screen the document renders as a single continuous sheet on a desk
 * background (Google Docs' pageless view): you scroll one tall page card.
 * There is no manual page-splitting — write the whole document as normal
 * flow inside <doc-page> and the browser's print engine paginates it at
 * export.
 *
 * At print the component injects `@page { size: …; margin: 0 }` (which
 * leaves Chrome no margin box to draw its date/URL/page-count header in)
 * and moves the visual margin onto the sheet's own padding, so the printed
 * page has the same inset you see on screen. Standard break-hygiene rules
 * (`break-inside: avoid` on figures, code blocks, images and table rows;
 * `orphans/widows: 3`) are applied so paragraphs and groups split cleanly.
 * On screen and at print, headings default to `text-wrap: balance` and
 * body text (p, li, blockquote, figcaption) to `text-wrap: pretty`, so
 * the document avoids widowed/orphaned words; the defaults have zero
 * specificity, so any text-wrap you declare on those elements wins.
 * The component also marks the document as owning its print CSS (a
 * `meta[name="omelette-owns-print"]` it injects at runtime), so the
 * PDF export never injects page-geometry CSS of its own on top.
 *
 * Usage:
 *   <style>doc-page:not(:defined){visibility:hidden}</style>
 *   <doc-page size="letter" margin="0.75in">
 *     <h1>Title</h1>
 *     <p>…body…</p>
 *   </doc-page>
 *   <script src="doc-page.js"></script>
 *
 * Attributes:
 *   size    — letter | a4 | legal (default letter)
 *   orientation — portrait (default) | landscape. For documents built to
 *           export, always set it explicitly. landscape swaps the named
 *           size's dimensions (letter landscape prints 11in × 8.5in).
 *   width / height — explicit CSS lengths, override `size` and
 *           `orientation`
 *   margin  — printable inset on every page (default 0.75in); margin="0"
 *           makes pages full-bleed (content then owns its own insets)
 *
 * Running header/footer (optional): give an element `slot="header"` or
 * `slot="footer"` and it repeats on every printed page via
 * `position: fixed`. To keep body text from sliding under it, the
 * component prints inside a single-cell table whose <thead>/<tfoot> are
 * spacers sized to the header/footer height — browsers repeat thead/tfoot
 * on every page, so each sheet's content starts below the header and ends
 * above the footer. On screen the header/footer render once at the
 * top/bottom of the sheet.
 *
 * Print best practices for the content you author:
 * - Multi-column text: use CSS columns (`column-count` +
 *   `column-gap`), never side-by-side flex/grid columns — only real
 *   CSS columns flow and break across pages. `column-span: all` lets
 *   a heading span the columns; `hyphens: auto` (needs `lang` on
 *   the html element) keeps narrow columns readable.
 * - Page breaks: `break-before: page` on an element that must start
 *   a new page (a chapter, an appendix). Add your own kept-together
 *   blocks (callouts, stat tiles, cards) to a `break-inside: avoid`
 *   rule, and keep each one shorter than a page.
 * - Extend `orphans: 3; widows: 3` to any custom text blocks you add
 *   (p and li are covered by default).
 * - Give long tables a <thead> — browsers repeat it on every printed
 *   page.
 * - No `position: fixed`/`sticky` and no viewport units in content:
 *   fixed elements stamp every printed page (running headers/footers go
 *   in the component's slots) and `100vh` mis-sizes at print.
 *
 * Author content as static HTML so the user can click-to-edit any text
 * directly. Do not set width/padding/background on the document body —
 * the component owns the sheet box.
 */
/* END USAGE */

(() => {
  const PAPER = {
    letter: ['8.5in', '11in'],
    a4: ['210mm', '297mm'],
    legal: ['8.5in', '14in']
  };
  const CSS_LENGTH = /^\d+(\.\d+)?(px|in|mm|cm|pt|pc)$/;
  // Unitless "0" is a valid CSS length and the natural way to write
  // margin="0"; normalise it to 0px so max()/calc() (which reject a bare
  // number) keep working.
  const safeLen = (v, fb) => {
    v = (v || '').trim();
    return v === '0' ? '0px' : CSS_LENGTH.test(v) ? v : fb;
  };
  const stylesheet = `
    :host {
      position: relative;
      display: block;
      /* When the viewport is narrower than the page, grow to wrap the
       * sheet (plus this padding) instead of staying viewport-width, so
       * the desk background and right margin reach the sheet's far edge
       * in the horizontal scroll. */
      min-width: max-content;
      min-height: 100vh;
      background: #ece8dd;
      padding: 48px 24px;
      box-sizing: border-box;
      font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Arial, sans-serif;
      --doc-page-w: 8.5in;
      --doc-page-h: 11in;
      --doc-page-margin: 0.75in;
      --doc-hdr-h: 0px;
      --doc-ftr-h: 0px;
      --doc-hdr-pad: 0px;
      --doc-ftr-pad: 0px;
    }
    .sheet {
      width: var(--doc-page-w);
      margin: 0 auto;
      background: #fff;
      box-shadow: 0 2px 14px rgba(20, 20, 19, 0.12);
      border-radius: 2px;
      box-sizing: border-box;
      padding: var(--doc-page-margin);
    }
    .frame { width: 100%; border-collapse: collapse; }
    .frame td, .frame th { padding: 0; text-align: left; font-weight: inherit; }
    .hdr-space { height: var(--doc-hdr-h); }
    .ftr-space { height: var(--doc-ftr-h); }
    ::slotted([slot="header"]),
    ::slotted([slot="footer"]) { display: block; box-sizing: border-box; }
    @media print {
      :host { background: none; padding: 0; min-width: 0; min-height: 0; }
      .sheet {
        width: auto; margin: 0; box-shadow: none; border-radius: 0;
        padding: 0 var(--doc-page-margin);
      }
      /* The thead/tfoot spacers repeat on every page, so they carry the
       * vertical page margin (which the sheet's own padding cannot, since
       * that padding is consumed once on the first/last page). The running
       * header/footer are fixed inside that band. */
      /* The 0.35in is breathing room between a running header/footer and
       * the body; without one the spacer is exactly the page margin, so a
       * margin="0" full-bleed document gets truly full-bleed pages. */
      .hdr-space { height: max(var(--doc-page-margin), calc(var(--doc-hdr-h) + var(--doc-hdr-pad))); }
      .ftr-space { height: max(var(--doc-page-margin), calc(var(--doc-ftr-h) + var(--doc-ftr-pad))); }
      ::slotted([slot="header"]) {
        position: fixed; top: 0; left: 0; right: 0; margin: 0;
        padding: calc(var(--doc-page-margin) * 0.45) var(--doc-page-margin) 0;
      }
      ::slotted([slot="footer"]) {
        position: fixed; bottom: 0; left: 0; right: 0; margin: 0;
        padding: 0 var(--doc-page-margin) calc(var(--doc-page-margin) * 0.45);
      }
    }
  `;
  class DocPage extends HTMLElement {
    static get observedAttributes() {
      return ['size', 'width', 'height', 'margin', 'orientation'];
    }
    constructor() {
      super();
      this._root = this.attachShadow({
        mode: 'open'
      });
      this._mo = typeof MutationObserver === 'function' ? new MutationObserver(() => this._scheduleMeasure()) : null;
    }

    /** The named paper's [w, h], swapped when orientation="landscape".
     *  Only the named size swaps — explicit width/height are exact values
     *  the author already oriented. */
    _paperSize() {
      const named = PAPER[(this.getAttribute('size') || '').toLowerCase()] || PAPER.letter;
      const landscape = (this.getAttribute('orientation') || '').trim().toLowerCase() === 'landscape';
      return landscape ? [named[1], named[0]] : named;
    }
    get pageWidth() {
      return safeLen(this.getAttribute('width'), this._paperSize()[0]);
    }
    get pageHeight() {
      return safeLen(this.getAttribute('height'), this._paperSize()[1]);
    }
    get pageMargin() {
      return safeLen(this.getAttribute('margin'), '0.75in');
    }
    connectedCallback() {
      if (!this._sheet) this._render();
      this._syncSize();
      this._syncPrintPageRule();
      this._ensureTextWrapDefaults();
      this._ensureOwnsPrintMeta();
      if (this._mo) this._mo.observe(this, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true
      });
      this._onResize = () => this._scheduleMeasure();
      window.addEventListener('resize', this._onResize);
      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(() => this._scheduleMeasure());
      }
      this._scheduleMeasure();
    }
    disconnectedCallback() {
      window.removeEventListener('resize', this._onResize);
      if (this._mo) this._mo.disconnect();
      if (this._raf) {
        cancelAnimationFrame(this._raf);
        this._raf = null;
      }
      // Drop the head rules when the last doc-page leaves, so a deleted
      // document's @page geometry and text-wrap defaults can't apply to
      // whatever replaces it.
      if (!document.querySelector('doc-page')) {
        ['doc-page-print', 'doc-page-text-wrap', 'doc-page-owns-print'].forEach(id => {
          const tag = document.getElementById(id);
          if (tag) tag.remove();
        });
      }
    }
    attributeChangedCallback() {
      if (!this._sheet) return;
      this._syncSize();
      this._syncPrintPageRule();
      this._scheduleMeasure();
    }
    _render() {
      this._root.innerHTML = `
        <style>${stylesheet}</style>
        <style id="vars"></style>
        <div class="sheet" data-screen-label="Document">
          <table class="frame" role="presentation">
            <thead><tr><th><div class="hdr-space"><slot name="header"></slot></div></th></tr></thead>
            <tbody><tr><td class="body"><slot></slot></td></tr></tbody>
            <tfoot><tr><td><div class="ftr-space"><slot name="footer"></slot></div></td></tr></tfoot>
          </table>
        </div>`;
      this._sheet = this._root.querySelector('.sheet');
      this._vars = this._root.getElementById('vars');
    }

    /** Runtime sizing lives in a shadow <style> :host rule, never on the
     *  light-DOM host element, so serialize-persist can't write it back. */
    _syncSize(hdrH, ftrH) {
      this._vars.textContent = ':host{' + '--doc-page-w:' + this.pageWidth + ';' + '--doc-page-h:' + this.pageHeight + ';' + '--doc-page-margin:' + this.pageMargin + ';' + '--doc-hdr-h:' + (hdrH || 0) + 'px;' + '--doc-ftr-h:' + (ftrH || 0) + 'px;' + '--doc-hdr-pad:' + (hdrH ? '0.35in' : '0px') + ';' + '--doc-ftr-pad:' + (ftrH ? '0.35in' : '0px') + '}';
    }

    /** @page is a no-op inside shadow DOM, so the rule lives in <head>.
     *  Re-appended on every sync so it stays last in source order — the
     *  @page cascade is source-order per descriptor, so this rule wins
     *  over any other @page rule in the document. */
    _syncPrintPageRule() {
      const id = 'doc-page-print';
      let tag = document.getElementById(id);
      if (!tag) {
        tag = document.createElement('style');
        tag.id = id;
      }
      document.head.appendChild(tag);
      tag.textContent = '@page { size: ' + this.pageWidth + ' ' + this.pageHeight + '; margin: 0; } ' + '@media print { html, body { margin: 0 !important; padding: 0 !important; background: none !important; height: auto !important; overflow: visible !important; } ' + 'h1,h2,h3,h4,h5,h6 { break-after: avoid; } ' + 'figure,pre,blockquote,img,svg,tr { break-inside: avoid; } ' + 'p,li { orphans: 3; widows: 3; } ' + '* { -webkit-print-color-adjust: exact; print-color-adjust: exact; } ' + '*, *::before, *::after { animation-delay: -99s !important; animation-duration: .001s !important; ' + 'animation-iteration-count: 1 !important; animation-fill-mode: both !important; ' + 'animation-play-state: running !important; transition-duration: 0s !important; } }';
    }

    /** Typographic defaults for document text: balance headings, avoid
     *  widowed/orphaned words in body copy (browsers without text-wrap
     *  support drop the declarations). Zero-specificity via :where() so
     *  any text-wrap authored on those elements wins; document-level so the
     *  rules reach the slotted (light DOM) content — shadow styles can't.
     *  data-omelette-injected marks the tag for the host editor to strip
     *  at serialize, so it is never written back as authored source. */
    _ensureTextWrapDefaults() {
      if (document.getElementById('doc-page-text-wrap')) return;
      const tag = document.createElement('style');
      tag.id = 'doc-page-text-wrap';
      tag.setAttribute('data-omelette-injected', '');
      tag.textContent = ':where(h1,h2,h3,h4,h5,h6){text-wrap:balance}' + ':where(p,li,blockquote,figcaption){text-wrap:pretty}';
      document.head.appendChild(tag);
    }

    /** Declares that this document owns its print CSS. The instant-PDF
     *  export checks for the meta by NAME PRESENCE alone (content is
     *  ignored) and skips its automatic print-CSS injections, so the
     *  component's @page geometry is never overridden by a heuristic.
     *  data-omelette-injected keeps it out of serialized source. */
    _ensureOwnsPrintMeta() {
      if (document.getElementById('doc-page-owns-print')) return;
      const tag = document.createElement('meta');
      tag.id = 'doc-page-owns-print';
      tag.name = 'omelette-owns-print';
      tag.content = 'true';
      tag.setAttribute('data-omelette-injected', '');
      document.head.appendChild(tag);
    }
    _scheduleMeasure() {
      if (this._raf) return;
      this._raf = requestAnimationFrame(() => {
        this._raf = null;
        this._measure();
      });
    }

    /** Slot heights feed the print spacers (--doc-hdr-h / --doc-ftr-h), so
     *  they re-measure on content mutation, resize, and font load. */
    _measure() {
      const hdr = this.querySelector(':scope > [slot="header"]');
      const ftr = this.querySelector(':scope > [slot="footer"]');
      this._syncSize(hdr ? hdr.offsetHeight : 0, ftr ? ftr.offsetHeight : 0);
    }
  }
  if (!customElements.get('doc-page')) {
    customElements.define('doc-page', DocPage);
  }
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "proposals/doc-page.js", error: String((e && e.message) || e) }); }

// proposals/materialboerse-final.js
try { (() => {
/* =============================================================================
   Materialbörse — interaktiver Prototyp (Flotte & Logistik)
   DAS KARTELL / Profit Basetool · fiktive Beispieldaten
   Eine Datenbasis + Logik, drei Darstellungen (Tabelle / Karten / Master-Detail).
   ============================================================================= */
(function () {
  "use strict";

  var ME = "Lenoro"; // aktueller Nutzer

  // -- Angebote (Materialposten auf der Börse) --------------------------------
  // qual: 0–1000 · menge: SCU · hours: vor wie vielen Stunden freigegeben
  // mine: eigenes Angebot (dann names = sichtbare Interessenten) ·
  // sonst: interess = Zahl (Namen bleiben verborgen)
  var OFFERS = [{
    id: "o1",
    mat: "Quantanium",
    kat: "High Value",
    owner: "Skorpi",
    sq: "IRI",
    foreign: false,
    qual: 920,
    menge: 512,
    hours: 2,
    interess: 4,
    remark: "Frisch raffiniert, **Qualität 920**. Tausche gegen **Bexalite** oder **aUEC** zum UEX-Kurs. Menge teilbar ab *128 SCU*."
  }, {
    id: "o2",
    mat: "Bexalite",
    kat: "High Value",
    owner: "Kessler",
    sq: "IRI",
    foreign: false,
    qual: 780,
    menge: 96,
    hours: 26,
    interess: 1,
    remark: "Nur **Komplettabnahme** (96 SCU). Suche im Gegenzug refined **Laranite** ab Qualität 600."
  }, {
    id: "o3",
    mat: "Laranite",
    kat: "Metalle",
    owner: "EndRageMusic",
    sq: "AGN",
    foreign: true,
    qual: 645,
    menge: 1240,
    hours: 70,
    interess: 0,
    remark: "Großposten. Biete gegen **Titanium** (≥ 700) oder **Agricium**. Kein aUEC."
  }, {
    id: "o4",
    mat: "Agricium",
    kat: "Metalle",
    owner: "Lenoro",
    sq: "IRI",
    foreign: false,
    qual: 796,
    menge: 340,
    hours: 5,
    mine: true,
    names: ["Mara", "Hex", "P6"],
    remark: "## Tauschangebot Agricium (Qualität 796)\n\nIch gebe **340 SCU Agricium** ab und suche vorrangig:\n\n- **Titanium** (Qualität ≥ 650)\n- **Laranite** oder **Hephaestanite**\n- Alternativ **aUEC** zum aktuellen UEX-Kurs\n\nTeilmengen ab *64 SCU* möglich. Übergabe im System **Stanton** oder **Pyro** — Ort besprechen wir direkt.\n\n> Kein Verkauf an Nicht-Kartell-Mitglieder ohne Rücksprache."
  }, {
    id: "o5",
    mat: "Titanium",
    kat: "Metalle",
    owner: "P6",
    sq: "IRI",
    foreign: false,
    qual: 540,
    menge: 880,
    hours: 150,
    interess: 2,
    remark: "Solides Mittelfeld-Titanium. Tausche gegen **Aluminum** oder **Gas** (Hydrogen/Chlorine)."
  }, {
    id: "o6",
    mat: "Aluminum",
    kat: "Metalle",
    owner: "Nova_7",
    sq: "IRI",
    foreign: false,
    qual: 910,
    menge: 2100,
    hours: 12,
    interess: 0,
    remark: "Großposten, **teilbar**. Suche **Quantanium** oder High-Value-Material — größere Mengen bevorzugt."
  }, {
    id: "o7",
    mat: "Aphorite",
    kat: "Edelgestein",
    owner: "GremlinTausch",
    sq: "SK-VÖL",
    foreign: true,
    qual: 615,
    menge: 60,
    hours: 92,
    interess: 1,
    remark: "Aphorite aus letztem Salvage. Tausch gegen **Beryl** oder **Dolivine** willkommen."
  }, {
    id: "o8",
    mat: "Hephaestanite",
    kat: "Metalle",
    owner: "Ferro",
    sq: "IRI",
    foreign: false,
    qual: 700,
    menge: 430,
    hours: 30,
    interess: 0,
    remark: "Genau **700er** Qualität. Suche **Agricium** oder **Titanium** im 1:1-Tausch nach Menge."
  }, {
    id: "o9",
    mat: "Quantanium",
    kat: "High Value",
    owner: "Lenoro",
    sq: "IRI",
    foreign: false,
    qual: 610,
    menge: 128,
    hours: 22,
    mine: true,
    names: ["Yuki"],
    remark: "Rest aus dem letzten Run (Qualität 610). Tausche gegen **refined Metalle** — Laranite/Agricium bevorzugt."
  }, {
    id: "o10",
    mat: "Beryl",
    kat: "Edelgestein",
    owner: "DocRebound",
    sq: "IRI",
    foreign: false,
    qual: 835,
    menge: 210,
    hours: 8,
    interess: 5,
    remark: "Hochwertiges **Beryl (835)**. Suche High-Value oder **Bexalite**. Schnelle Übergabe möglich."
  }, {
    id: "o11",
    mat: "Tungsten",
    kat: "Metalle",
    owner: "Yuki",
    sq: "IRI",
    foreign: false,
    qual: 480,
    menge: 1560,
    hours: 168,
    interess: 0,
    remark: "Günstig abzugeben, große Menge. Tausche gegen so ziemlich alles **Refined** — macht Angebote."
  }, {
    id: "o12",
    mat: "Corundum",
    kat: "Metalle",
    owner: "Mara",
    sq: "IRI",
    foreign: false,
    qual: 720,
    menge: 640,
    hours: 48,
    interess: 2,
    remark: "Tausche gegen **Aluminum** oder **Laranite**. Auch Teilmengen ab *128 SCU*."
  }];

  // Materialliste für das "Material anbieten"-Formular
  var MAT_LIST = ["Quantanium", "Bexalite", "Laranite", "Agricium", "Titanium", "Aluminum", "Hephaestanite", "Aphorite", "Beryl", "Tungsten", "Corundum", "Dolivine", "Quartz"];

  // -- globaler Zustand -------------------------------------------------------
  var myInterests = new Set(); // Angebote, in die ich mich als Interessent eingetragen habe
  var withdrawn = new Set(); // eigene Angebote, die ich von der Börse genommen habe
  var VARIANTS = ["md"]; // Master-Detail als festgelegtes Layout (V1 Tabelle / V2 Karten entfernt)
  var state = {};
  VARIANTS.forEach(function (v) {
    state[v] = {
      tab: "alle",
      q: "",
      minQual: 0,
      minMenge: 0,
      sort: "qual",
      onlyNoInt: false,
      open: new Set(),
      rmk: new Set(),
      sel: null
    };
  });

  // -- Helfer -----------------------------------------------------------------
  function fmt(n) {
    return n.toLocaleString("de-DE");
  }
  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;"
      }[c];
    });
  }
  function ago(h) {
    if (h < 24) return "vor " + h + " Std";
    var d = Math.round(h / 24);
    return "vor " + d + (d === 1 ? " Tag" : " Tagen");
  }
  function icon(id) {
    return '<svg class="krt-icon"><use href="#i-' + id + '"/></svg>';
  }
  function offer(id) {
    for (var i = 0; i < OFFERS.length; i++) if (OFFERS[i].id === id) return OFFERS[i];
    return null;
  }
  function intCount(o) {
    return o.mine ? o.names ? o.names.length : 0 : o.interess + (myInterests.has(o.id) ? 1 : 0);
  }

  // minimaler, sicherer Markdown-Renderer (##/### · **fett** · *kursiv* · - Liste · > Zitat · `code` · [link](url))
  function inline(t) {
    return t.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>').replace(/`([^`]+)`/g, "<code>$1</code>").replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>").replace(/\*([^*]+)\*/g, "<em>$1</em>");
  }
  function md(src) {
    var lines = esc(src).split(/\r?\n/),
      out = [],
      i = 0;
    while (i < lines.length) {
      var line = lines[i];
      if (/^\s*$/.test(line)) {
        i++;
        continue;
      }
      var h = line.match(/^\s*(#{2,3})\s+(.*)$/);
      if (h) {
        out.push("<h3>" + inline(h[2]) + "</h3>");
        i++;
        continue;
      }
      if (/^\s*>\s?/.test(line)) {
        var q = [];
        while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
          q.push(inline(lines[i].replace(/^\s*>\s?/, "")));
          i++;
        }
        out.push("<blockquote>" + q.join("<br>") + "</blockquote>");
        continue;
      }
      if (/^\s*[-*]\s+/.test(line)) {
        var items = [];
        while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
          items.push("<li>" + inline(lines[i].replace(/^\s*[-*]\s+/, "")) + "</li>");
          i++;
        }
        out.push("<ul>" + items.join("") + "</ul>");
        continue;
      }
      var para = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^\s*(#{2,3}\s|>|[-*]\s)/.test(lines[i])) {
        para.push(inline(lines[i]));
        i++;
      }
      out.push("<p>" + para.join(" ") + "</p>");
    }
    return out.join("");
  }
  function badge(o) {
    return '<span class="squadron-badge ' + (o.foreign ? "squadron-badge-foreign" : "") + '">' + esc(o.sq) + "</span>";
  }
  function gauge(qual, right, showMax) {
    return '<span class="qual ' + (right ? "right" : "") + '">' + '<span class="qv">' + qual + (showMax ? '<span class="qmax"> / 1000</span>' : "") + "</span>" + '<span class="gt"><i style="width:' + qual / 10 + '%"></i></span></span>';
  }
  function amt(menge) {
    return '<span class="amt">' + fmt(menge) + '<span class="u">SCU</span></span>';
  }
  function intChip(o) {
    var c = intCount(o),
      plural = c === 1 ? "" : "en";
    if (o.mine) return '<span class="chip ' + (c > 0 ? "chip--primary" : "chip--muted") + '">' + icon("users") + " " + c + " Interessent" + plural + "</span>";
    if (myInterests.has(o.id)) return '<span class="chip chip--primary">' + icon("check") + " " + c + " · du dabei</span>";
    return '<span class="chip chip--muted">' + icon("users") + " " + (c === 0 ? "Keine Interessenten" : c + " Interessent" + plural) + "</span>";
  }
  function miniInt(o) {
    var c = intCount(o),
      mine = myInterests.has(o.id) || o.mine;
    return '<span class="int ' + (mine && c > 0 ? "text-primary" : "text-muted") + '" title="' + c + ' Interessent(en)">' + icon("users") + " " + c + "</span>";
  }

  // eine einzelne Zeilen-Aktion (kompakt) — für Tabellenzeile & Karten-Fuß
  function primaryAction(o) {
    if (o.mine) return '<button class="btn btn-quiet-danger btn-xs" data-action="withdraw" data-id="' + o.id + '">Angebot deaktivieren</button>';
    if (myInterests.has(o.id)) return '<button class="btn btn-ghost btn-xs" data-action="interest" data-id="' + o.id + '" title="Interesse zurückziehen">' + icon("check") + " Zurückziehen</button>";
    return '<button class="btn btn-outline btn-xs" data-action="interest" data-id="' + o.id + '">Interesse anmelden</button>';
  }
  // volle Aktionen für die Detailansicht
  function actionsFull(o) {
    if (o.mine) return '<button class="btn btn-ghost btn-xs" data-action="edit" data-id="' + o.id + '">' + icon("edit") + ' Bemerkung bearbeiten</button>' + '<button class="btn btn-quiet-danger btn-xs" data-action="withdraw" data-id="' + o.id + '">Angebot deaktivieren</button>';
    if (myInterests.has(o.id)) return '<button class="btn btn-ghost btn-xs" data-action="interest" data-id="' + o.id + '">' + icon("check") + ' Interesse angemeldet — zurückziehen</button>';
    return '<button class="btn btn-outline btn-xs" data-action="interest" data-id="' + o.id + '">Interesse anmelden</button>';
  }
  function remarkBlock(o, vid, allowClamp) {
    var long = o.remark.length > 140,
      open = state[vid].rmk.has(o.id),
      clamp = allowClamp && long && !open;
    var html = '<div class="remark ' + (clamp ? "clamp" : "") + '"><div class="md">' + md(o.remark) + "</div></div>";
    if (allowClamp && long) html += '<button class="more-btn ' + (open ? "open" : "") + '" data-action="remark" data-id="' + o.id + '">' + icon("chevron-down") + " " + (open ? "Weniger" : "Mehr anzeigen") + "</button>";
    return html;
  }
  function anonNote() {
    return '<p class="anon-note">' + icon("shield") + " Standort &amp; Übergabeort werden bewusst nicht angezeigt — das verhandelt ihr direkt.</p>";
  }
  function interessentenBlock(o) {
    var c = intCount(o);
    if (o.mine) {
      var names = (o.names || []).map(function (n) {
        return '<span class="chip">' + icon("users") + " " + esc(n) + "</span>";
      }).join("");
      return '<div><p class="dt-label">Interessenten (' + c + ")</p>" + (c ? '<div class="int-names">' + names + "</div>" : '<span class="text-muted" style="font-size:12.5px">Noch keine Interessenten.</span>') + '<p class="owner-note"><b>Nur du</b> siehst diese Namen (dein Angebot) — nimm mit ihnen direkt die Verhandlung auf.</p></div>';
    }
    return '<div><p class="dt-label">Interessenten</p>' + intChip(o) + '<p class="owner-note">Die Namen sieht nur der Anbieter.' + (myInterests.has(o.id) ? " <b>Du bist eingetragen</b> — der Anbieter kann dich kontaktieren." : "") + "</p></div>";
  }
  function factsKv(o) {
    return '<div class="kvmini">' + '<span class="k">Qualität</span><span class="v">' + o.qual + '</span>' + '<span class="k">Menge</span><span class="v">' + fmt(o.menge) + ' SCU</span>' + '<span class="k">Freigegeben</span><span class="v" style="text-transform:none">' + ago(o.hours) + "</span></div>";
  }

  // -- Filter + Sortierung ----------------------------------------------------
  function filterSort(vid) {
    var s = state[vid];
    var list = OFFERS.filter(function (o) {
      return !withdrawn.has(o.id);
    });
    if (s.tab === "mein") list = list.filter(function (o) {
      return o.mine;
    });
    if (s.q) {
      var q = s.q.toLowerCase();
      list = list.filter(function (o) {
        return o.mat.toLowerCase().indexOf(q) >= 0 || o.owner.toLowerCase().indexOf(q) >= 0;
      });
    }
    if (s.minQual > 0) list = list.filter(function (o) {
      return o.qual >= s.minQual;
    });
    if (s.minMenge > 0) list = list.filter(function (o) {
      return o.menge >= s.minMenge;
    });
    list = list.slice();
    if (s.sort === "qual") list.sort(function (a, b) {
      return b.qual - a.qual;
    });else if (s.sort === "menge") list.sort(function (a, b) {
      return b.menge - a.menge;
    });else if (s.sort === "neu") list.sort(function (a, b) {
      return a.hours - b.hours;
    });else if (s.sort === "mat") list.sort(function (a, b) {
      return a.mat.localeCompare(b.mat, "de");
    });
    return list;
  }

  // -- Toolbar ----------------------------------------------------------------
  function opt(v, l, cur) {
    return '<option value="' + v + '"' + (v === cur ? " selected" : "") + ">" + l + "</option>";
  }
  function buildToolbar(vid) {
    var s = state[vid];
    var cAll = OFFERS.filter(function (o) {
      return !withdrawn.has(o.id);
    }).length;
    var cMine = OFFERS.filter(function (o) {
      return o.mine && !withdrawn.has(o.id);
    }).length;
    return '' + '<div class="mb-tabrow">' + '<div class="tab-nav" role="tablist">' + '<button class="tab ' + (s.tab === "alle" ? "active" : "") + '" data-action="tab" data-tab="alle" role="tab" aria-selected="' + (s.tab === "alle") + '">Alle Angebote <span class="tab-count">' + cAll + "</span></button>" + '<button class="tab ' + (s.tab === "mein" ? "active" : "") + '" data-action="tab" data-tab="mein" role="tab" aria-selected="' + (s.tab === "mein") + '">Meine Angebote <span class="tab-count">' + cMine + "</span></button>" + "</div>" + '<div class="mb-search">' + icon("search") + '<input type="search" data-role="search" value="' + esc(s.q) + '" placeholder="Material oder Spieler …" aria-label="Suche"></div>' + "</div>" + '<div class="mb-filters">' + '<div class="mb-filt"><span class="fl">Min. Qualität</span><input type="range" min="0" max="1000" step="10" value="' + s.minQual + '" data-role="minqual" aria-label="Mindestqualität"><span class="fv" data-fv>' + (s.minQual ? s.minQual + '<small> / 1000</small>' : "–") + "</span></div>" + '<div class="mb-filt"><span class="fl">Min. Menge</span><input type="number" min="0" step="10" value="' + (s.minMenge || "") + '" data-role="minmenge" placeholder="0" aria-label="Mindestmenge"><span class="fl" style="color:var(--color-gray-2)">SCU</span></div>' + '<div class="mb-filt"><span class="fl">Sortieren</span><select class="chip-select" data-role="sort" aria-label="Sortierung">' + opt("qual", "Qualität ↓", s.sort) + opt("menge", "Menge ↓", s.sort) + opt("mat", "Material A–Z", s.sort) + opt("neu", "Neueste zuerst", s.sort) + "</select></div>" + "</div>" + '<div class="mb-body"><div class="mb-list"></div></div>';
  }
  function emptyState(vid) {
    return '<div class="empty-state"><div class="empty-title">Keine Angebote</div>' + '<p class="empty-text">Für diese Filter gibt es gerade keine freigegebenen Materialien.</p>' + '<button class="btn btn-ghost btn-xs" data-action="reset">Filter zurücksetzen</button></div>';
  }

  // -- V1: Tabelle ------------------------------------------------------------
  function renderTable(list, vid) {
    var head = '<div class="mb-row mb-head">' + '<div class="mb-c">Material</div><div class="mb-c">Anbieter</div>' + '<div class="mb-c num">Qualität</div><div class="mb-c num">Menge</div>' + '<div class="mb-c">Interessenten</div><div class="mb-c" style="text-align:right">Aktion</div></div>';
    var rows = list.map(function (o) {
      var isOpen = state[vid].open.has(o.id);
      var row = '<div class="mb-drow ' + (o.mine ? "mine " : "") + (isOpen ? "open" : "") + '" data-id="' + o.id + '" role="button" tabindex="0" aria-expanded="' + isOpen + '">' + '<div class="mb-c"><span class="mb-mat ' + (isOpen ? "open" : "") + '"><span class="chev">▶</span><span><span class="mn">' + esc(o.mat) + '</span><span class="mk">' + esc(o.kat) + "</span></span></span></div>" + '<div class="mb-c"><span class="mb-off">' + badge(o) + '<span class="on">' + esc(o.owner) + "</span></span></div>" + '<div class="mb-c num">' + gauge(o.qual, true, false) + "</div>" + '<div class="mb-c num">' + amt(o.menge) + "</div>" + '<div class="mb-c">' + intChip(o) + "</div>" + '<div class="mb-c act">' + (o.mine ? '<span class="chip chip--primary">Deins</span>' : primaryAction(o)) + "</div>" + "</div>";
      if (isOpen) {
        row += '<div class="mb-detail"><div class="dt-grid">' + '<div><p class="dt-label">Bemerkung</p>' + remarkBlock(o, vid, true) + anonNote() + "</div>" + '<div class="dt-side">' + factsKv(o) + interessentenBlock(o) + '<div class="dt-actions">' + actionsFull(o) + "</div></div>" + "</div></div>";
      }
      return row;
    }).join("");
    return '<div class="mb-table">' + head + rows + "</div>";
  }

  // -- V2: Karten -------------------------------------------------------------
  function renderCard(o, i, list) {
    return renderCardV(o, "cards");
  }
  function renderCardV(o, vid) {
    return '<div class="mcard ' + (o.mine ? "mine" : "") + '">' + '<div class="mc-head"><div class="mc-title"><span class="mn">' + esc(o.mat) + '</span><span class="mk">' + esc(o.kat) + '</span></div>' + '<div style="text-align:right;flex:none">' + badge(o) + '<div class="on" style="font-size:12px;color:var(--color-gray-1);margin-top:4px">' + esc(o.owner) + "</div></div></div>" + '<div class="mc-body">' + '<div class="mc-stats">' + gauge(o.qual, false, true) + '<div class="amt" style="font-size:17px">' + fmt(o.menge) + '<span class="u">SCU</span></div></div>' + '<div><p class="dt-label">Bemerkung</p>' + remarkBlock(o, vid, true) + "</div>" + "</div>" + '<div class="mc-foot">' + intChip(o) + primaryAction(o) + "</div>" + "</div>";
  }

  // -- V3: Master-Detail ------------------------------------------------------
  function renderMD(list, vid) {
    var s = state[vid];
    if (!s.sel || !list.some(function (o) {
      return o.id === s.sel;
    })) s.sel = list.length ? list[0].id : null;
    var rows = list.map(function (o) {
      return '<button class="mrow ' + (o.mine ? "mine " : "") + (o.id === s.sel ? "active" : "") + '" data-action="select" data-id="' + o.id + '">' + '<span class="mr-main"><span class="mr-mat">' + esc(o.mat) + "</span>" + '<span class="mr-sub">' + badge(o) + '<span class="on">' + esc(o.owner) + "</span></span>" + '<span class="mr-amt">Q ' + o.qual + " · " + fmt(o.menge) + " SCU · " + ago(o.hours) + "</span></span>" + '<span class="mr-int">' + miniInt(o) + "</span></button>";
    }).join("");
    var sel = s.sel ? offer(s.sel) : null;
    var pane = sel ? mdDetail(sel, vid) : '<div class="dp-body"><div class="empty-state" style="border:none"><div class="empty-title">Kein Angebot gewählt</div><p class="empty-text">Wähle links einen Materialposten.</p></div></div>';
    return '<div class="mb-md ' + (sel ? "has-sel" : "") + '"><div class="mb-mlist">' + rows + '</div><div class="mb-detailpane">' + pane + "</div></div>";
  }
  function mdDetail(o, vid) {
    return '<div class="dp-head"><div class="dp-title"><h4>' + esc(o.mat) + '</h4><span class="mk">von ' + esc(o.owner) + " " + badge(o) + "</span>" + (o.mine ? '<div style="margin-top:7px"><span class="chip chip--primary">Dein Angebot</span></div>' : "") + "</div>" + '<button class="btn btn-ghost btn-xs dp-back" data-action="deselect">' + icon("chevron-left") + " Liste</button></div>" + '<div class="dp-facts">' + factsKv(o) + "</div>" + '<div class="dp-body"><div class="dt-grid">' + '<div><p class="dt-label">Bemerkung</p>' + remarkBlock(o, vid, false) + anonNote() + "</div>" + '<div class="dt-side">' + interessentenBlock(o) + '<div class="dt-actions">' + actionsFull(o) + "</div></div>" + "</div></div>";
  }

  // -- Render-Dispatch --------------------------------------------------------
  function renderList(vid) {
    var list = filterSort(vid);
    var cont = document.querySelector('[data-variant="' + vid + '"] .mb-list');
    if (!cont) return;
    if (!list.length) {
      cont.innerHTML = emptyState(vid);
      return;
    }
    if (vid === "table") cont.innerHTML = renderTable(list, vid);else if (vid === "cards") cont.innerHTML = '<div class="mb-cards">' + list.map(function (o) {
      return renderCardV(o, vid);
    }).join("") + "</div>";else cont.innerHTML = renderMD(list, vid);
  }
  function renderAll() {
    VARIANTS.forEach(renderList);
    syncTabCounts();
  }
  function syncTabCounts() {
    var cAll = OFFERS.filter(function (o) {
      return !withdrawn.has(o.id);
    }).length;
    var cMine = OFFERS.filter(function (o) {
      return o.mine && !withdrawn.has(o.id);
    }).length;
    VARIANTS.forEach(function (vid) {
      var tabs = document.querySelectorAll('[data-variant="' + vid + '"] .tab-count');
      if (tabs[0]) tabs[0].textContent = cAll;
      if (tabs[1]) tabs[1].textContent = cMine;
    });
  }

  // -- Toast ------------------------------------------------------------------
  function toast(title, msg) {
    var host = document.getElementById("toastHost");
    var el = document.createElement("div");
    el.className = "notification-toast";
    el.innerHTML = "<h4>" + esc(title) + "</h4><p>" + msg + "</p>";
    host.appendChild(el);
    setTimeout(function () {
      el.style.transition = "opacity .4s";
      el.style.opacity = "0";
      setTimeout(function () {
        el.remove();
      }, 400);
    }, 3400);
  }

  // -- Freigabe-/Bearbeiten-Modal --------------------------------------------
  var modalCtx = null;
  function openFreigabe(ctx) {
    modalCtx = ctx; // {mode:'new'|'lager'|'edit', mat?, qual?, menge?, offerId?, checkbox?}
    var o = ctx.offerId ? offer(ctx.offerId) : null;
    var fixedMat = ctx.mode === "lager" || ctx.mode === "edit";
    var mat = ctx.mat || o && o.mat || MAT_LIST[0];
    var qual = ctx.qual != null ? ctx.qual : o ? o.qual : 700;
    var menge = ctx.menge != null ? ctx.menge : o ? o.menge : 100;
    var remark = o ? o.remark : "";
    var title = ctx.mode === "edit" ? "Bemerkung bearbeiten" : "Material für die Börse freigeben";
    var cta = ctx.mode === "edit" ? "Speichern" : "Freigeben";
    var matField = fixedMat ? '<span class="data-value" style="display:inline-block">' + esc(mat) + "</span>" : '<select data-f="mat">' + MAT_LIST.map(function (m) {
      return '<option' + (m === mat ? " selected" : "") + ">" + m + "</option>";
    }).join("") + "</select>";
    var contextStrip = '' + '<div class="fg-context">' + '<div class="fg-fact"><span class="k">Material</span><span class="v">' + esc(mat) + '</span></div>' + '<div class="fg-fact"><span class="k">Qualität</span><span class="v">' + qual + '</span></div>' + '<div class="fg-fact"><span class="k">Menge</span><span class="v">' + fmt(menge) + ' SCU</span></div>' + '</div>';
    var fieldsGrid = '' + '<div class="fg-fields">' + '<div><label class="form-label-sm">Material</label>' + matField + '</div>' + '<div><label class="form-label-sm">Menge (SCU)</label><input type="number" min="1" step="1" data-f="menge" value="' + menge + '"></div>' + '<div><label class="form-label-sm">Qualität (0–1000)</label><input type="number" min="0" max="1000" step="1" data-f="qual" value="' + qual + '"></div>' + '</div>';
    var body = (fixedMat ? contextStrip : fieldsGrid) + '<label class="form-label-sm">Bemerkung <span style="color:var(--color-gray-2);font-weight:400;text-transform:none">— was suchst du im Gegenzug? (Markdown, max. 20.000 Zeichen)</span></label>' + '<textarea data-f="remark" rows="8" maxlength="20000" placeholder="z. B. Tausche gegen **Titanium** ≥ 700 oder aUEC …&#10;- teilbar ab 64 SCU&#10;- Übergabe verhandelbar">' + esc(remark) + "</textarea>" + '<div style="display:flex;justify-content:space-between;align-items:center;margin-top:6px;font-size:11px;color:var(--color-gray-2)">' + '<span>' + icon("shield") + ' Standort/Übergabeort bleiben privat.</span><span data-charcount>' + fmt(remark.length) + " / 20.000</span></div>";
    var overlay = document.createElement("div");
    overlay.className = "krt-modal-overlay";
    overlay.innerHTML = '<div class="krt-modal" role="dialog" aria-modal="true" aria-label="' + title + '" style="max-width:600px">' + '<div class="krt-modal-head"><h2>' + title + '</h2><button class="btn btn-ghost btn-icon" data-action="modal-close" aria-label="Schließen">' + icon("close") + "</button></div>" + '<div class="krt-modal-body">' + body + "</div>" + '<div class="krt-modal-foot"><button class="btn btn-ghost" data-action="modal-close">Abbrechen</button>' + '<button class="btn btn--cta" data-action="freigabe-submit">' + icon("check") + " " + cta + "</button></div></div>";
    document.getElementById("modalHost").innerHTML = "";
    document.getElementById("modalHost").appendChild(overlay);
    var ta = overlay.querySelector('[data-f="remark"]');
    if (ta) {
      ta.focus();
      ta.setSelectionRange(ta.value.length, ta.value.length);
    }
  }
  function closeModal(cancelled) {
    // Beim Abbrechen einer Lager-Freigabe die Checkbox zurücksetzen
    if (cancelled && modalCtx && modalCtx.mode === "lager" && modalCtx.checkbox) modalCtx.checkbox.checked = false;
    document.getElementById("modalHost").innerHTML = "";
    modalCtx = null;
  }
  function submitFreigabe() {
    var m = document.querySelector("#modalHost .krt-modal");
    if (!m || !modalCtx) return;
    var get = function (f, fb) {
      var el = m.querySelector('[data-f="' + f + '"]');
      return el ? el.value : fb != null ? fb : "";
    };
    var remark = get("remark");
    if (modalCtx.mode === "edit") {
      var oe = offer(modalCtx.offerId);
      if (oe) oe.remark = remark;
      closeModal(false);
      renderAll();
      toast("Gespeichert", "Die Bemerkung deines Angebots wurde aktualisiert.");
      return;
    }
    var mat = modalCtx.mat || get("mat");
    var qual = Math.max(0, Math.min(1000, parseInt(get("qual", modalCtx.qual), 10) || 0));
    var menge = Math.max(1, parseInt(get("menge", modalCtx.menge), 10) || 1);
    var id = modalCtx.linkedId || "n" + Date.now();
    withdrawn.delete(id);
    if (!offer(id)) {
      OFFERS.unshift({
        id: id,
        mat: mat,
        kat: catOf(mat),
        owner: ME,
        sq: "IRI",
        foreign: false,
        qual: qual,
        menge: menge,
        hours: 0,
        mine: true,
        names: [],
        remark: remark || "_Keine Bemerkung._"
      });
    } else {
      var ox = offer(id);
      ox.remark = remark || ox.remark;
      withdrawn.delete(id);
    }
    // Lager-Checkbox mit dem (evtl. neuen) Angebot verknüpfen, damit späteres Abwählen es wieder entfernt
    if (modalCtx.checkbox && !modalCtx.linkedId) modalCtx.checkbox.setAttribute("data-linked-id", id);
    if (modalCtx.statusCell) setLagerStatus(modalCtx.statusCell, true);
    closeModal(false);
    renderAll();
    toast("Auf die Börse gestellt", esc(mat) + " (" + fmt(menge) + " SCU, Q " + qual + ") ist jetzt für alle sichtbar.");
  }
  function catOf(mat) {
    var o = null;
    for (var i = 0; i < OFFERS.length; i++) if (OFFERS[i].mat === mat) {
      o = OFFERS[i];
      break;
    }
    if (o) return o.kat;
    if (["Quantanium", "Bexalite"].indexOf(mat) >= 0) return "High Value";
    if (["Aphorite", "Beryl", "Dolivine", "Quartz"].indexOf(mat) >= 0) return "Edelgestein";
    return "Metalle";
  }
  function setLagerStatus(cell, on) {
    cell.innerHTML = on ? '<span class="chip chip--primary">Auf Börse</span>' : '<span class="text-muted" style="font-size:11px">privat</span>';
  }

  // -- Events -----------------------------------------------------------------
  function variantOf(el) {
    var s = el.closest("[data-variant]");
    return s ? s.getAttribute("data-variant") : null;
  }
  document.addEventListener("click", function (e) {
    var a = e.target.closest("[data-action]");
    if (a) {
      var act = a.getAttribute("data-action");
      var vid = variantOf(a);
      var id = a.getAttribute("data-id");
      if (act === "tab") {
        state[vid].tab = a.getAttribute("data-tab");
        setActiveTab(vid, a);
        renderList(vid);
        return;
      }
      if (act === "remark") {
        toggleSet(state[vid].rmk, id);
        renderList(vid);
        return;
      }
      if (act === "select") {
        state[vid].sel = id;
        renderList(vid);
        return;
      }
      if (act === "deselect") {
        state[vid].sel = null;
        renderList(vid);
        return;
      }
      if (act === "reset") {
        resetFilters(vid);
        return;
      }
      if (act === "interest") {
        toggleInterest(id);
        return;
      }
      if (act === "withdraw") {
        doWithdraw(id);
        return;
      }
      if (act === "edit") {
        openFreigabe({
          mode: "edit",
          offerId: id
        });
        return;
      }
      if (act === "open-freigabe") {
        openFreigabe({
          mode: "new"
        });
        return;
      }
      if (act === "modal-close") {
        closeModal(true);
        return;
      }
      if (act === "freigabe-submit") {
        submitFreigabe();
        return;
      }
      return;
    }
    // Klick auf eine Tabellenzeile (nicht auf einen Button) → Detail auf/zu
    var row = e.target.closest(".mb-drow");
    if (row) {
      var v = variantOf(row);
      toggleSet(state[v].open, row.getAttribute("data-id"));
      renderList(v);
    }
  });

  // Tastatur: Enter/Space auf Tabellenzeile
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && document.querySelector("#modalHost .krt-modal")) {
      closeModal(true);
      return;
    }
    if ((e.key === "Enter" || e.key === " ") && e.target.classList && e.target.classList.contains("mb-drow")) {
      e.preventDefault();
      var v = variantOf(e.target);
      toggleSet(state[v].open, e.target.getAttribute("data-id"));
      renderList(v);
    }
  });
  document.addEventListener("input", function (e) {
    var el = e.target,
      role = el.getAttribute("data-role");
    if (role === "search") {
      var v = variantOf(el);
      state[v].q = el.value;
      renderList(v);
      return;
    }
    if (role === "minqual") {
      var v2 = variantOf(el);
      state[v2].minQual = parseInt(el.value, 10) || 0;
      var fv = el.parentNode.querySelector("[data-fv]");
      if (fv) fv.innerHTML = state[v2].minQual ? state[v2].minQual + "<small> / 1000</small>" : "–";
      renderList(v2);
      return;
    }
    if (role === "minmenge") {
      var v3 = variantOf(el);
      state[v3].minMenge = parseInt(el.value, 10) || 0;
      renderList(v3);
      return;
    }
    if (el.getAttribute("data-f") === "remark") {
      var cc = document.querySelector("#modalHost [data-charcount]");
      if (cc) cc.textContent = fmt(el.value.length) + " / 20.000";
      return;
    }
  });
  document.addEventListener("change", function (e) {
    var el = e.target,
      role = el.getAttribute("data-role");
    if (role === "sort") {
      var v = variantOf(el);
      state[v].sort = el.value;
      renderList(v);
      return;
    }
    if (el.getAttribute("data-action") === "freigabe-toggle") {
      var cell = document.querySelector('[data-status-for="' + el.getAttribute("data-mat") + '"]');
      if (el.checked) {
        openFreigabe({
          mode: "lager",
          mat: el.getAttribute("data-mat"),
          qual: parseInt(el.getAttribute("data-qual"), 10),
          menge: parseInt(el.getAttribute("data-menge"), 10),
          linkedId: el.getAttribute("data-linked-id") || null,
          statusCell: cell,
          checkbox: el
        });
      } else {
        var lid = el.getAttribute("data-linked-id");
        if (lid && offer(lid)) withdrawn.add(lid);
        if (cell) setLagerStatus(cell, false);
        renderAll();
        toast("Von der Börse genommen", el.getAttribute("data-mat") + " ist wieder privat.");
      }
    }
  });
  function setActiveTab(vid, btn) {
    var tabs = document.querySelectorAll('[data-variant="' + vid + '"] .tab');
    tabs.forEach(function (t) {
      t.classList.remove("active");
      t.setAttribute("aria-selected", "false");
    });
    btn.classList.add("active");
    btn.setAttribute("aria-selected", "true");
  }
  function toggleSet(set, id) {
    if (set.has(id)) set.delete(id);else set.add(id);
  }
  function resetFilters(vid) {
    var s = state[vid];
    s.q = "";
    s.minQual = 0;
    s.minMenge = 0;
    s.tab = "alle";
    // Toolbar-Controls dieser Variante neu bespielen
    var scope = document.querySelector('[data-variant="' + vid + '"]');
    scope.querySelector('[data-role="search"]').value = "";
    scope.querySelector('[data-role="minqual"]').value = 0;
    var fv = scope.querySelector("[data-fv]");
    if (fv) fv.innerHTML = "–";
    scope.querySelector('[data-role="minmenge"]').value = "";
    scope.querySelectorAll(".tab").forEach(function (t, i) {
      t.classList.toggle("active", i === 0);
      t.setAttribute("aria-selected", i === 0);
    });
    renderList(vid);
  }
  function toggleInterest(id) {
    var o = offer(id);
    if (!o || o.mine) return;
    var added = !myInterests.has(id);
    if (added) myInterests.add(id);else myInterests.delete(id);
    renderAll();
    if (added) toast("Interesse angemeldet", "Der Anbieter von <b style=\"color:var(--color-primary)\">" + esc(o.mat) + "</b> sieht dich jetzt als Interessent und kann die Verhandlung aufnehmen.");else toast("Interesse zurückgezogen", "Du wurdest aus den Interessenten für " + esc(o.mat) + " entfernt.");
  }
  function doWithdraw(id) {
    var o = offer(id);
    if (!o) return;
    withdrawn.add(id);
    // ggf. verknüpfte Lager-Checkbox zurücksetzen
    var cb = document.querySelector('[data-action="freigabe-toggle"][data-linked-id="' + id + '"]');
    if (cb) {
      cb.checked = false;
      var cell = document.querySelector('[data-status-for="' + cb.getAttribute("data-mat") + '"]');
      if (cell) setLagerStatus(cell, false);
    }
    renderAll();
    toast("Angebot deaktiviert", esc(o.mat) + " ist nicht mehr öffentlich sichtbar.");
  }

  // -- Init -------------------------------------------------------------------
  function init() {
    document.querySelectorAll(".screen[data-variant] .mb-app").forEach(function (app) {
      var vid = app.closest("[data-variant]").getAttribute("data-variant");
      app.innerHTML = buildToolbar(vid);
    });
    renderAll();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);else init();
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "proposals/materialboerse-final.js", error: String((e && e.message) || e) }); }

// proposals/tweaks-panel.jsx
try { (() => {
// @ds-adherence-ignore -- omelette starter scaffold (raw elements/hex/px by design)

/* BEGIN USAGE */
// tweaks-panel.jsx
// Reusable Tweaks shell + form-control helpers.
// Exports (to window): useTweaks, TweaksPanel, TweakSection, TweakRow, TweakSlider,
//   TweakToggle, TweakRadio, TweakSelect, TweakText, TweakNumber, TweakColor, TweakButton.
//
// Owns the host protocol (listens for __activate_edit_mode / __deactivate_edit_mode,
// posts __edit_mode_available / __edit_mode_set_keys / __edit_mode_dismissed) so
// individual prototypes don't re-roll it. Ships a consistent set of controls so you
// don't hand-draw <input type="range">, segmented radios, steppers, etc.
//
// Usage (in an HTML file that loads React + Babel):
//
//   const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
//     "primaryColor": "#D97757",
//     "palette": ["#D97757", "#29261b", "#f6f4ef"],
//     "fontSize": 16,
//     "density": "regular",
//     "dark": false
//   }/*EDITMODE-END*/;
//
//   function App() {
//     const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
//     return (
//       <div style={{ fontSize: t.fontSize, color: t.primaryColor }}>
//         Hello
//         <TweaksPanel>
//           <TweakSection label="Typography" />
//           <TweakSlider label="Font size" value={t.fontSize} min={10} max={32} unit="px"
//                        onChange={(v) => setTweak('fontSize', v)} />
//           <TweakRadio  label="Density" value={t.density}
//                        options={['compact', 'regular', 'comfy']}
//                        onChange={(v) => setTweak('density', v)} />
//           <TweakSection label="Theme" />
//           <TweakColor  label="Primary" value={t.primaryColor}
//                        options={['#D97757', '#2A6FDB', '#1F8A5B', '#7A5AE0']}
//                        onChange={(v) => setTweak('primaryColor', v)} />
//           <TweakColor  label="Palette" value={t.palette}
//                        options={[['#D97757', '#29261b', '#f6f4ef'],
//                                  ['#475569', '#0f172a', '#f1f5f9']]}
//                        onChange={(v) => setTweak('palette', v)} />
//           <TweakToggle label="Dark mode" value={t.dark}
//                        onChange={(v) => setTweak('dark', v)} />
//         </TweaksPanel>
//       </div>
//     );
//   }
//
// TweakRadio is the segmented control for 2–3 short options (auto-falls-back to
// TweakSelect past ~16/~10 chars per label); reach for TweakSelect directly when
// options are many or long. For color tweaks always curate 3-4 options rather than
// a free picker; an option can also be a whole 2–5 color palette (the stored value
// is the array). The Tweak* controls are a floor, not a ceiling — build custom
// controls inside the panel if a tweak calls for UI they don't cover.
/* END USAGE */
// ─────────────────────────────────────────────────────────────────────────────

const __TWEAKS_STYLE = `
  .twk-panel{position:fixed;right:16px;bottom:16px;z-index:2147483646;width:280px;
    max-height:calc(100vh - 32px);display:flex;flex-direction:column;
    transform:scale(var(--dc-inv-zoom,1));transform-origin:bottom right;
    background:rgba(250,249,247,.78);color:#29261b;
    -webkit-backdrop-filter:blur(24px) saturate(160%);backdrop-filter:blur(24px) saturate(160%);
    border:.5px solid rgba(255,255,255,.6);border-radius:14px;
    box-shadow:0 1px 0 rgba(255,255,255,.5) inset,0 12px 40px rgba(0,0,0,.18);
    font:11.5px/1.4 ui-sans-serif,system-ui,-apple-system,sans-serif;overflow:hidden}
  .twk-hd{display:flex;align-items:center;justify-content:space-between;
    padding:10px 8px 10px 14px;cursor:move;user-select:none}
  .twk-hd b{font-size:12px;font-weight:600;letter-spacing:.01em}
  .twk-x{appearance:none;border:0;background:transparent;color:rgba(41,38,27,.55);
    width:22px;height:22px;border-radius:6px;cursor:default;font-size:13px;line-height:1}
  .twk-x:hover{background:rgba(0,0,0,.06);color:#29261b}
  .twk-body{padding:2px 14px 14px;display:flex;flex-direction:column;gap:10px;
    overflow-y:auto;overflow-x:hidden;min-height:0;
    scrollbar-width:thin;scrollbar-color:rgba(0,0,0,.15) transparent}
  .twk-body::-webkit-scrollbar{width:8px}
  .twk-body::-webkit-scrollbar-track{background:transparent;margin:2px}
  .twk-body::-webkit-scrollbar-thumb{background:rgba(0,0,0,.15);border-radius:4px;
    border:2px solid transparent;background-clip:content-box}
  .twk-body::-webkit-scrollbar-thumb:hover{background:rgba(0,0,0,.25);
    border:2px solid transparent;background-clip:content-box}
  .twk-row{display:flex;flex-direction:column;gap:5px}
  .twk-row-h{flex-direction:row;align-items:center;justify-content:space-between;gap:10px}
  .twk-lbl{display:flex;justify-content:space-between;align-items:baseline;
    color:rgba(41,38,27,.72)}
  .twk-lbl>span:first-child{font-weight:500}
  .twk-val{color:rgba(41,38,27,.5);font-variant-numeric:tabular-nums}

  .twk-sect{font-size:10px;font-weight:600;letter-spacing:.06em;text-transform:uppercase;
    color:rgba(41,38,27,.45);padding:10px 0 0}
  .twk-sect:first-child{padding-top:0}

  .twk-field{appearance:none;box-sizing:border-box;width:100%;min-width:0;height:26px;padding:0 8px;
    border:.5px solid rgba(0,0,0,.1);border-radius:7px;
    background:rgba(255,255,255,.6);color:inherit;font:inherit;outline:none}
  .twk-field:focus{border-color:rgba(0,0,0,.25);background:rgba(255,255,255,.85)}
  select.twk-field{padding-right:22px;
    background-image:url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'><path fill='rgba(0,0,0,.5)' d='M0 0h10L5 6z'/></svg>");
    background-repeat:no-repeat;background-position:right 8px center}

  .twk-slider{appearance:none;-webkit-appearance:none;width:100%;height:4px;margin:6px 0;
    border-radius:999px;background:rgba(0,0,0,.12);outline:none}
  .twk-slider::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;
    width:14px;height:14px;border-radius:50%;background:#fff;
    border:.5px solid rgba(0,0,0,.12);box-shadow:0 1px 3px rgba(0,0,0,.2);cursor:default}
  .twk-slider::-moz-range-thumb{width:14px;height:14px;border-radius:50%;
    background:#fff;border:.5px solid rgba(0,0,0,.12);box-shadow:0 1px 3px rgba(0,0,0,.2);cursor:default}

  .twk-seg{position:relative;display:flex;padding:2px;border-radius:8px;
    background:rgba(0,0,0,.06);user-select:none}
  .twk-seg-thumb{position:absolute;top:2px;bottom:2px;border-radius:6px;
    background:rgba(255,255,255,.9);box-shadow:0 1px 2px rgba(0,0,0,.12);
    transition:left .15s cubic-bezier(.3,.7,.4,1),width .15s}
  .twk-seg.dragging .twk-seg-thumb{transition:none}
  .twk-seg button{appearance:none;position:relative;z-index:1;flex:1;border:0;
    background:transparent;color:inherit;font:inherit;font-weight:500;min-height:22px;
    border-radius:6px;cursor:default;padding:4px 6px;line-height:1.2;
    overflow-wrap:anywhere}

  .twk-toggle{position:relative;width:32px;height:18px;border:0;border-radius:999px;
    background:rgba(0,0,0,.15);transition:background .15s;cursor:default;padding:0}
  .twk-toggle[data-on="1"]{background:#34c759}
  .twk-toggle i{position:absolute;top:2px;left:2px;width:14px;height:14px;border-radius:50%;
    background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.25);transition:transform .15s}
  .twk-toggle[data-on="1"] i{transform:translateX(14px)}

  .twk-num{display:flex;align-items:center;box-sizing:border-box;min-width:0;height:26px;padding:0 0 0 8px;
    border:.5px solid rgba(0,0,0,.1);border-radius:7px;background:rgba(255,255,255,.6)}
  .twk-num-lbl{font-weight:500;color:rgba(41,38,27,.6);cursor:ew-resize;
    user-select:none;padding-right:8px}
  .twk-num input{flex:1;min-width:0;height:100%;border:0;background:transparent;
    font:inherit;font-variant-numeric:tabular-nums;text-align:right;padding:0 8px 0 0;
    outline:none;color:inherit;-moz-appearance:textfield}
  .twk-num input::-webkit-inner-spin-button,.twk-num input::-webkit-outer-spin-button{
    -webkit-appearance:none;margin:0}
  .twk-num-unit{padding-right:8px;color:rgba(41,38,27,.45)}

  .twk-btn{appearance:none;height:26px;padding:0 12px;border:0;border-radius:7px;
    background:rgba(0,0,0,.78);color:#fff;font:inherit;font-weight:500;cursor:default}
  .twk-btn:hover{background:rgba(0,0,0,.88)}
  .twk-btn.secondary{background:rgba(0,0,0,.06);color:inherit}
  .twk-btn.secondary:hover{background:rgba(0,0,0,.1)}

  .twk-swatch{appearance:none;-webkit-appearance:none;width:56px;height:22px;
    border:.5px solid rgba(0,0,0,.1);border-radius:6px;padding:0;cursor:default;
    background:transparent;flex-shrink:0}
  .twk-swatch::-webkit-color-swatch-wrapper{padding:0}
  .twk-swatch::-webkit-color-swatch{border:0;border-radius:5.5px}
  .twk-swatch::-moz-color-swatch{border:0;border-radius:5.5px}

  .twk-chips{display:flex;gap:6px}
  .twk-chip{position:relative;appearance:none;flex:1;min-width:0;height:46px;
    padding:0;border:0;border-radius:6px;overflow:hidden;cursor:default;
    box-shadow:0 0 0 .5px rgba(0,0,0,.12),0 1px 2px rgba(0,0,0,.06);
    transition:transform .12s cubic-bezier(.3,.7,.4,1),box-shadow .12s}
  .twk-chip:hover{transform:translateY(-1px);
    box-shadow:0 0 0 .5px rgba(0,0,0,.18),0 4px 10px rgba(0,0,0,.12)}
  .twk-chip[data-on="1"]{box-shadow:0 0 0 1.5px rgba(0,0,0,.85),
    0 2px 6px rgba(0,0,0,.15)}
  .twk-chip>span{position:absolute;top:0;bottom:0;right:0;width:34%;
    display:flex;flex-direction:column;box-shadow:-1px 0 0 rgba(0,0,0,.1)}
  .twk-chip>span>i{flex:1;box-shadow:0 -1px 0 rgba(0,0,0,.1)}
  .twk-chip>span>i:first-child{box-shadow:none}
  .twk-chip svg{position:absolute;top:6px;left:6px;width:13px;height:13px;
    filter:drop-shadow(0 1px 1px rgba(0,0,0,.3))}
`;

// ── useTweaks ───────────────────────────────────────────────────────────────
// Single source of truth for tweak values. setTweak persists via the host
// (__edit_mode_set_keys → host rewrites the EDITMODE block on disk).
function useTweaks(defaults) {
  const [values, setValues] = React.useState(defaults);
  // Accepts either setTweak('key', value) or setTweak({ key: value, ... }) so a
  // useState-style call doesn't write a "[object Object]" key into the persisted
  // JSON block.
  const setTweak = React.useCallback((keyOrEdits, val) => {
    const edits = typeof keyOrEdits === 'object' && keyOrEdits !== null ? keyOrEdits : {
      [keyOrEdits]: val
    };
    setValues(prev => ({
      ...prev,
      ...edits
    }));
    window.parent.postMessage({
      type: '__edit_mode_set_keys',
      edits
    }, '*');
    // Same-window signal so in-page listeners (deck-stage rail thumbnails)
    // can react — the parent message only reaches the host, not peers.
    window.dispatchEvent(new CustomEvent('tweakchange', {
      detail: edits
    }));
  }, []);
  return [values, setTweak];
}

// ── TweaksPanel ─────────────────────────────────────────────────────────────
// Floating shell. Registers the protocol listener BEFORE announcing
// availability — if the announce ran first, the host's activate could land
// before our handler exists and the toolbar toggle would silently no-op.
// The close button posts __edit_mode_dismissed so the host's toolbar toggle
// flips off in lockstep; the host echoes __deactivate_edit_mode back which
// is what actually hides the panel.
function TweaksPanel({
  title = 'Tweaks',
  children
}) {
  const [open, setOpen] = React.useState(false);
  const dragRef = React.useRef(null);
  const offsetRef = React.useRef({
    x: 16,
    y: 16
  });
  const PAD = 16;
  const clampToViewport = React.useCallback(() => {
    const panel = dragRef.current;
    if (!panel) return;
    const w = panel.offsetWidth,
      h = panel.offsetHeight;
    const maxRight = Math.max(PAD, window.innerWidth - w - PAD);
    const maxBottom = Math.max(PAD, window.innerHeight - h - PAD);
    offsetRef.current = {
      x: Math.min(maxRight, Math.max(PAD, offsetRef.current.x)),
      y: Math.min(maxBottom, Math.max(PAD, offsetRef.current.y))
    };
    panel.style.right = offsetRef.current.x + 'px';
    panel.style.bottom = offsetRef.current.y + 'px';
  }, []);
  React.useEffect(() => {
    if (!open) return;
    clampToViewport();
    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', clampToViewport);
      return () => window.removeEventListener('resize', clampToViewport);
    }
    const ro = new ResizeObserver(clampToViewport);
    ro.observe(document.documentElement);
    return () => ro.disconnect();
  }, [open, clampToViewport]);
  React.useEffect(() => {
    const onMsg = e => {
      const t = e?.data?.type;
      if (t === '__activate_edit_mode') setOpen(true);else if (t === '__deactivate_edit_mode') setOpen(false);
    };
    window.addEventListener('message', onMsg);
    window.parent.postMessage({
      type: '__edit_mode_available'
    }, '*');
    return () => window.removeEventListener('message', onMsg);
  }, []);
  const dismiss = () => {
    setOpen(false);
    window.parent.postMessage({
      type: '__edit_mode_dismissed'
    }, '*');
  };
  const onDragStart = e => {
    const panel = dragRef.current;
    if (!panel) return;
    const r = panel.getBoundingClientRect();
    const sx = e.clientX,
      sy = e.clientY;
    const startRight = window.innerWidth - r.right;
    const startBottom = window.innerHeight - r.bottom;
    const move = ev => {
      offsetRef.current = {
        x: startRight - (ev.clientX - sx),
        y: startBottom - (ev.clientY - sy)
      };
      clampToViewport();
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };
  if (!open) return null;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("style", null, __TWEAKS_STYLE), /*#__PURE__*/React.createElement("div", {
    ref: dragRef,
    className: "twk-panel",
    "data-omelette-chrome": "",
    style: {
      right: offsetRef.current.x,
      bottom: offsetRef.current.y
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-hd",
    onMouseDown: onDragStart
  }, /*#__PURE__*/React.createElement("b", null, title), /*#__PURE__*/React.createElement("button", {
    className: "twk-x",
    "aria-label": "Close tweaks",
    onMouseDown: e => e.stopPropagation(),
    onClick: dismiss
  }, "\u2715")), /*#__PURE__*/React.createElement("div", {
    className: "twk-body"
  }, children)));
}

// ── Layout helpers ──────────────────────────────────────────────────────────

function TweakSection({
  label,
  children
}) {
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "twk-sect"
  }, label), children);
}
function TweakRow({
  label,
  value,
  children,
  inline = false
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: inline ? 'twk-row twk-row-h' : 'twk-row'
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-lbl"
  }, /*#__PURE__*/React.createElement("span", null, label), value != null && /*#__PURE__*/React.createElement("span", {
    className: "twk-val"
  }, value)), children);
}

// ── Controls ────────────────────────────────────────────────────────────────

function TweakSlider({
  label,
  value,
  min = 0,
  max = 100,
  step = 1,
  unit = '',
  onChange
}) {
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label,
    value: `${value}${unit}`
  }, /*#__PURE__*/React.createElement("input", {
    type: "range",
    className: "twk-slider",
    min: min,
    max: max,
    step: step,
    value: value,
    onChange: e => onChange(Number(e.target.value))
  }));
}
function TweakToggle({
  label,
  value,
  onChange
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "twk-row twk-row-h"
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-lbl"
  }, /*#__PURE__*/React.createElement("span", null, label)), /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "twk-toggle",
    "data-on": value ? '1' : '0',
    role: "switch",
    "aria-checked": !!value,
    onClick: () => onChange(!value)
  }, /*#__PURE__*/React.createElement("i", null)));
}
function TweakRadio({
  label,
  value,
  options,
  onChange
}) {
  const trackRef = React.useRef(null);
  const [dragging, setDragging] = React.useState(false);
  // The active value is read by pointer-move handlers attached for the lifetime
  // of a drag — ref it so a stale closure doesn't fire onChange for every move.
  const valueRef = React.useRef(value);
  valueRef.current = value;

  // Segments wrap mid-word once per-segment width runs out. The track is
  // ~248px (280 panel − 28 body pad − 4 seg pad), each button loses 12px
  // to its own padding, and 11.5px system-ui averages ~6.3px/char — so 2
  // options fit ~16 chars each, 3 fit ~10. Past that (or >3 options), fall
  // back to a dropdown rather than wrap.
  const labelLen = o => String(typeof o === 'object' ? o.label : o).length;
  const maxLen = options.reduce((m, o) => Math.max(m, labelLen(o)), 0);
  const fitsAsSegments = maxLen <= ({
    2: 16,
    3: 10
  }[options.length] ?? 0);
  if (!fitsAsSegments) {
    // <select> emits strings — map back to the original option value so the
    // fallback stays type-preserving (numbers, booleans) like the segment path.
    const resolve = s => {
      const m = options.find(o => String(typeof o === 'object' ? o.value : o) === s);
      return m === undefined ? s : typeof m === 'object' ? m.value : m;
    };
    return /*#__PURE__*/React.createElement(TweakSelect, {
      label: label,
      value: value,
      options: options,
      onChange: s => onChange(resolve(s))
    });
  }
  const opts = options.map(o => typeof o === 'object' ? o : {
    value: o,
    label: o
  });
  const idx = Math.max(0, opts.findIndex(o => o.value === value));
  const n = opts.length;
  const segAt = clientX => {
    const r = trackRef.current.getBoundingClientRect();
    const inner = r.width - 4;
    const i = Math.floor((clientX - r.left - 2) / inner * n);
    return opts[Math.max(0, Math.min(n - 1, i))].value;
  };
  const onPointerDown = e => {
    setDragging(true);
    const v0 = segAt(e.clientX);
    if (v0 !== valueRef.current) onChange(v0);
    const move = ev => {
      if (!trackRef.current) return;
      const v = segAt(ev.clientX);
      if (v !== valueRef.current) onChange(v);
    };
    const up = () => {
      setDragging(false);
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("div", {
    ref: trackRef,
    role: "radiogroup",
    onPointerDown: onPointerDown,
    className: dragging ? 'twk-seg dragging' : 'twk-seg'
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-seg-thumb",
    style: {
      left: `calc(2px + ${idx} * (100% - 4px) / ${n})`,
      width: `calc((100% - 4px) / ${n})`
    }
  }), opts.map(o => /*#__PURE__*/React.createElement("button", {
    key: o.value,
    type: "button",
    role: "radio",
    "aria-checked": o.value === value
  }, o.label))));
}
function TweakSelect({
  label,
  value,
  options,
  onChange
}) {
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("select", {
    className: "twk-field",
    value: value,
    onChange: e => onChange(e.target.value)
  }, options.map(o => {
    const v = typeof o === 'object' ? o.value : o;
    const l = typeof o === 'object' ? o.label : o;
    return /*#__PURE__*/React.createElement("option", {
      key: v,
      value: v
    }, l);
  })));
}
function TweakText({
  label,
  value,
  placeholder,
  onChange
}) {
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("input", {
    className: "twk-field",
    type: "text",
    value: value,
    placeholder: placeholder,
    onChange: e => onChange(e.target.value)
  }));
}
function TweakNumber({
  label,
  value,
  min,
  max,
  step = 1,
  unit = '',
  onChange
}) {
  const clamp = n => {
    if (min != null && n < min) return min;
    if (max != null && n > max) return max;
    return n;
  };
  const startRef = React.useRef({
    x: 0,
    val: 0
  });
  const onScrubStart = e => {
    e.preventDefault();
    startRef.current = {
      x: e.clientX,
      val: value
    };
    const decimals = (String(step).split('.')[1] || '').length;
    const move = ev => {
      const dx = ev.clientX - startRef.current.x;
      const raw = startRef.current.val + dx * step;
      const snapped = Math.round(raw / step) * step;
      onChange(clamp(Number(snapped.toFixed(decimals))));
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "twk-num"
  }, /*#__PURE__*/React.createElement("span", {
    className: "twk-num-lbl",
    onPointerDown: onScrubStart
  }, label), /*#__PURE__*/React.createElement("input", {
    type: "number",
    value: value,
    min: min,
    max: max,
    step: step,
    onChange: e => onChange(clamp(Number(e.target.value)))
  }), unit && /*#__PURE__*/React.createElement("span", {
    className: "twk-num-unit"
  }, unit));
}

// Relative-luminance contrast pick — checkmarks drawn over a swatch need to
// read on both #111 and #fafafa without per-option configuration. Hex input
// only (#rgb / #rrggbb); named or rgb()/hsl() colors fall through to "light".
function __twkIsLight(hex) {
  const h = String(hex).replace('#', '');
  const x = h.length === 3 ? h.replace(/./g, c => c + c) : h.padEnd(6, '0');
  const n = parseInt(x.slice(0, 6), 16);
  if (Number.isNaN(n)) return true;
  const r = n >> 16 & 255,
    g = n >> 8 & 255,
    b = n & 255;
  return r * 299 + g * 587 + b * 114 > 148000;
}
const __TwkCheck = ({
  light
}) => /*#__PURE__*/React.createElement("svg", {
  viewBox: "0 0 14 14",
  "aria-hidden": "true"
}, /*#__PURE__*/React.createElement("path", {
  d: "M3 7.2 5.8 10 11 4.2",
  fill: "none",
  strokeWidth: "2.2",
  strokeLinecap: "round",
  strokeLinejoin: "round",
  stroke: light ? 'rgba(0,0,0,.78)' : '#fff'
}));

// TweakColor — curated color/palette picker. Each option is either a single
// hex string or an array of 1-5 hex strings; the card adapts — a lone color
// renders solid, a palette renders colors[0] as the hero (left ~2/3) with the
// rest stacked in a sharp column on the right. onChange emits the
// option in the shape it was passed (string stays string, array stays array).
// Without options it falls back to the native color input for back-compat.
function TweakColor({
  label,
  value,
  options,
  onChange
}) {
  if (!options || !options.length) {
    return /*#__PURE__*/React.createElement("div", {
      className: "twk-row twk-row-h"
    }, /*#__PURE__*/React.createElement("div", {
      className: "twk-lbl"
    }, /*#__PURE__*/React.createElement("span", null, label)), /*#__PURE__*/React.createElement("input", {
      type: "color",
      className: "twk-swatch",
      value: value,
      onChange: e => onChange(e.target.value)
    }));
  }
  // Native <input type=color> emits lowercase hex per the HTML spec, so
  // compare case-insensitively. String() guards JSON.stringify(undefined),
  // which returns the primitive undefined (no .toLowerCase).
  const key = o => String(JSON.stringify(o)).toLowerCase();
  const cur = key(value);
  return /*#__PURE__*/React.createElement(TweakRow, {
    label: label
  }, /*#__PURE__*/React.createElement("div", {
    className: "twk-chips",
    role: "radiogroup"
  }, options.map((o, i) => {
    const colors = Array.isArray(o) ? o : [o];
    const [hero, ...rest] = colors;
    const sup = rest.slice(0, 4);
    const on = key(o) === cur;
    return /*#__PURE__*/React.createElement("button", {
      key: i,
      type: "button",
      className: "twk-chip",
      role: "radio",
      "aria-checked": on,
      "data-on": on ? '1' : '0',
      "aria-label": colors.join(', '),
      title: colors.join(' · '),
      style: {
        background: hero
      },
      onClick: () => onChange(o)
    }, sup.length > 0 && /*#__PURE__*/React.createElement("span", null, sup.map((c, j) => /*#__PURE__*/React.createElement("i", {
      key: j,
      style: {
        background: c
      }
    }))), on && /*#__PURE__*/React.createElement(__TwkCheck, {
      light: __twkIsLight(hero)
    }));
  })));
}
function TweakButton({
  label,
  onClick,
  secondary = false
}) {
  return /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: secondary ? 'twk-btn secondary' : 'twk-btn',
    onClick: onClick
  }, label);
}
Object.assign(window, {
  useTweaks,
  TweaksPanel,
  TweakSection,
  TweakRow,
  TweakSlider,
  TweakToggle,
  TweakRadio,
  TweakSelect,
  TweakText,
  TweakNumber,
  TweakColor,
  TweakButton
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "proposals/tweaks-panel.jsx", error: String((e && e.message) || e) }); }

// slides/deck-stage.js
try { (() => {
/* BEGIN USAGE */
/**
 * <deck-stage> — reusable web component for HTML decks.
 *
 * Handles:
 *  (a) speaker notes — reads <script type="application/json" id="speaker-notes">
 *      and posts {slideIndexChanged: N} to the parent window on nav.
 *  (b) keyboard navigation — ←/→, PgUp/PgDn, Space, Home/End, number keys.
 *      On touch devices, tapping the left/right half of the stage goes
 *      prev/next — taps on links, buttons and other interactive slide
 *      content are left alone.
 *  (c) press R to reset to slide 0 (with a tasteful keyboard hint).
 *  (d) bottom-center overlay showing slide count + hints, fades out on idle.
 *  (e) auto-scaling — inner canvas is a fixed design size (default 1920×1080)
 *      scaled with `transform: scale()` to fit the viewport, letterboxed.
 *      Set the `noscale` attribute to render at authored size (1:1) — the
 *      PPTX exporter sets this so its DOM capture sees unscaled geometry.
 *  (f) print — `@media print` lays every slide out as its own page at the
 *      design size, so the browser's Print → Save as PDF produces a clean
 *      one-page-per-slide PDF with no extra setup.
 *  (g) thumbnail rail — resizable left-hand column of per-slide thumbnails
 *      (static clones). Click to navigate; ↑/↓ with a thumbnail focused to
 *      step between slides; drag to reorder; right-click for
 *      Skip / Move up / Move down / Delete (opens a Cancel/Delete confirm
 *      dialog). Drag the rail's right edge to resize; width persists to
 *      localStorage. Skipped slides carry `data-deck-skip`, are dimmed in
 *      the rail, omitted from prev/next navigation, and hidden at print.
 *      The rail is suppressed in presenting mode, in the host's Preview
 *      mode (ViewerMode='none'), on `noscale`, on narrow viewports
 *      (≤640px), and via the `no-rail` attribute. Rail mutations dispatch
 *      a `deckchange`
 *      CustomEvent on the element: detail = {action, from, to, slide}.
 *
 * Slides are HIDDEN, not unmounted. Non-active slides stay in the DOM with
 * `visibility: hidden` + `opacity: 0`, so their state (videos, iframes,
 * form inputs, React trees) is preserved across navigation.
 *
 * Lifecycle event — the component dispatches a `slidechange` CustomEvent on
 * itself whenever the active slide changes (including the initial mount).
 * The event bubbles and composes out of shadow DOM, so you can listen on
 * the <deck-stage> element or on document:
 *
 *   document.querySelector('deck-stage').addEventListener('slidechange', (e) => {
 *     e.detail.index         // new 0-based index
 *     e.detail.previousIndex // previous index, or -1 on init
 *     e.detail.total         // total slide count
 *     e.detail.slide         // the new active slide element
 *     e.detail.previousSlide // the prior slide element, or null on init
 *     e.detail.reason        // 'init' | 'keyboard' | 'click' | 'tap' | 'api'
 *   });
 *
 * Persistence: none at the deck level. The host app keeps the current slide
 * in its own URL (?slide=) and re-delivers it via location.hash on load, so a
 * bare load with no hash always starts at slide 1.
 *
 * Usage:
 *   <style>deck-stage:not(:defined){visibility:hidden}</style>
 *   <deck-stage width="1920" height="1080">
 *     <section data-label="Title">...</section>
 *     <section data-label="Agenda">...</section>
 *   </deck-stage>
 *   <script src="deck-stage.js"></script>
 *
 * The :not(:defined) rule prevents a flash of the first slide at its
 * authored styles before this script runs and attaches the shadow root.
 *
 * Slides are the direct element children of <deck-stage>. Each slide is
 * automatically tagged with:
 *   - data-screen-label="NN Label"   (1-indexed, for comment flow)
 *   - data-om-validate="no_overflowing_text,no_overlapping_text,slide_sized_text"
 *
 * Speaker notes stay in sync because the component posts {slideIndexChanged: N}
 * to the parent — just include the #speaker-notes script tag if asked for notes.
 *
 * Authoring guidance:
 *   - Write slide bodies as static HTML inside <deck-stage>, with sizing via
 *     CSS custom properties in a <style> block rather than JS constants.
 *     Static slide markup is what lets the user click a heading in edit mode
 *     and retype it directly; a slide rendered through <script type="text/babel">,
 *     React, or a loop over a JS array has to round-trip every tweak through a
 *     chat message instead. Reach for script-generated slides only when the
 *     content genuinely needs interactive behaviour static HTML can't express.
 *   - Do NOT set position/inset/width/height on the slide <section> elements —
 *     the component absolutely positions every slotted child for you.
 */
/* END USAGE */

(() => {
  const DESIGN_W_DEFAULT = 1920;
  const DESIGN_H_DEFAULT = 1080;
  const OVERLAY_HIDE_MS = 1800;
  const VALIDATE_ATTR = 'no_overflowing_text,no_overlapping_text,slide_sized_text';
  const FINE_POINTER_MQ = matchMedia('(hover: hover) and (pointer: fine)');
  const NARROW_MQ = matchMedia('(max-width: 640px)');
  // Slide-authored controls that should keep a tap instead of it navigating.
  const INTERACTIVE_SEL = 'a[href], button, input, select, textarea, summary, label, video[controls], audio[controls], [role="button"], [onclick], [tabindex]:not([tabindex^="-"]), [contenteditable]:not([contenteditable="false" i])';
  const pad2 = n => String(n).padStart(2, '0');

  // Label precedence: data-label → data-screen-label (number stripped) → first heading → "Slide".
  const getSlideLabel = el => {
    const explicit = el.getAttribute('data-label');
    if (explicit) return explicit;
    const existing = el.getAttribute('data-screen-label');
    if (existing) return existing.replace(/^\s*\d+\s*/, '').trim() || existing;
    const h = el.querySelector('h1, h2, h3, [data-title]');
    const t = h && (h.textContent || '').trim().slice(0, 40);
    if (t) return t;
    return 'Slide';
  };
  const stylesheet = `
    :host {
      position: fixed;
      inset: 0;
      display: block;
      background: #000;
      color: #fff;
      font-family: -apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif;
      overflow: hidden;
      -webkit-tap-highlight-color: transparent;
    }
    /* connectedCallback holds this until document.fonts.ready (capped 2s) so
     * the first visible paint has the deck's real typography + final rail
     * layout. opacity (not visibility) so the active slide can't un-hide
     * itself via the ::slotted([data-deck-active]) visibility:visible rule.
     * Only the stage/rail hide — the black :host background stays, so the
     * iframe doesn't flash the page's default white. */
    :host([data-fonts-pending]) .stage,
    :host([data-fonts-pending]) .rail { opacity: 0; pointer-events: none; }

    .stage {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .canvas {
      position: relative;
      transform-origin: center center;
      flex-shrink: 0;
      background: #fff;
      will-change: transform;
    }

    /* Slides live in light DOM (via <slot>) so authored CSS still applies.
       We absolutely position each slotted child to stack them. */
    ::slotted(*) {
      position: absolute !important;
      inset: 0 !important;
      width: 100% !important;
      height: 100% !important;
      box-sizing: border-box !important;
      overflow: hidden;
      opacity: 0;
      pointer-events: none;
      visibility: hidden;
    }
    ::slotted([data-deck-active]) {
      opacity: 1;
      pointer-events: auto;
      visibility: visible;
    }

    .overlay {
      position: fixed;
      left: 50%;
      bottom: 22px;
      transform: translate(-50%, 6px) scale(0.92);
      filter: blur(6px);
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 4px;
      background: #000;
      color: #fff;
      border-radius: 999px;
      font-size: 12px;
      font-feature-settings: "tnum" 1;
      letter-spacing: 0.01em;
      opacity: 0;
      pointer-events: none;
      transition: opacity 260ms ease, transform 260ms cubic-bezier(.2,.8,.2,1), filter 260ms ease;
      transform-origin: center bottom;
      z-index: 2147483000;
      user-select: none;
    }
    .overlay[data-visible] {
      opacity: 1;
      pointer-events: auto;
      transform: translate(-50%, 0) scale(1);
      filter: blur(0);
    }

    .btn {
      appearance: none;
      -webkit-appearance: none;
      background: transparent;
      border: 0;
      margin: 0;
      padding: 0;
      color: inherit;
      font: inherit;
      cursor: default;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 28px;
      min-width: 28px;
      border-radius: 999px;
      color: rgba(255,255,255,0.72);
      transition: background 140ms ease, color 140ms ease;
      -webkit-tap-highlight-color: transparent;
    }
    .btn:hover { background: rgba(255,255,255,0.12); color: #fff; }
    .btn:active { background: rgba(255,255,255,0.18); }
    .btn:focus { outline: none; }
    .btn:focus-visible { outline: none; }
    .btn::-moz-focus-inner { border: 0; }
    .btn svg { width: 14px; height: 14px; display: block; }
    .btn.reset {
      font-size: 11px;
      font-weight: 500;
      letter-spacing: 0.02em;
      padding: 0 10px 0 12px;
      gap: 6px;
      color: rgba(255,255,255,0.72);
    }
    .btn.reset .kbd {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
      font-size: 10px;
      line-height: 1;
      color: rgba(255,255,255,0.88);
      background: rgba(255,255,255,0.12);
      border-radius: 4px;
    }

    .count {
      font-variant-numeric: tabular-nums;
      color: #fff;
      font-weight: 500;
      padding: 0 8px;
      min-width: 42px;
      text-align: center;
      font-size: 12px;
    }
    .count .sep { color: rgba(255,255,255,0.45); margin: 0 3px; font-weight: 400; }
    .count .total { color: rgba(255,255,255,0.55); }

    .divider {
      width: 1px;
      height: 14px;
      background: rgba(255,255,255,0.18);
      margin: 0 2px;
    }

    /* ── Thumbnail rail ──────────────────────────────────────────────────
       Fixed column on the left; each thumbnail is a static deep-clone of
       the light-DOM slide scaled into a 16:9 (or design-aspect) frame. The
       stage re-fits around it (see _fit); hidden during present / noscale
       / print so capture geometry and fullscreen output are unchanged. */
    .rail {
      position: fixed;
      left: 0;
      top: 0;
      bottom: 0;
      width: var(--deck-rail-w, 188px);
      background: #141414;
      border-right: 1px solid rgba(255,255,255,0.08);
      overflow-y: auto;
      overflow-x: hidden;
      padding: 12px 10px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: 12px;
      z-index: 2147482500;
      scrollbar-width: thin;
      scrollbar-color: rgba(255,255,255,0.18) transparent;
    }
    .rail::-webkit-scrollbar { width: 8px; }
    .rail::-webkit-scrollbar-track { background: transparent; margin: 2px; }
    .rail::-webkit-scrollbar-thumb {
      background: rgba(255,255,255,0.18);
      border-radius: 4px;
      border: 2px solid transparent;
      background-clip: content-box;
    }
    .rail::-webkit-scrollbar-thumb:hover {
      background: rgba(255,255,255,0.28);
      border: 2px solid transparent;
      background-clip: content-box;
    }
    :host([no-rail]) .rail,
    :host([noscale]) .rail { display: none; }
    .rail[data-presenting] { display: none; }
    @media (max-width: 640px) {
      .rail, .rail-resize { display: none; }
    }
    /* User-driven show/hide (the TweaksPanel toggle) slides instead of
       popping. Transitions are gated on :host([data-rail-anim]) — set only
       for the 200ms around the toggle — so window-resize and rail-width
       drag (which also call _fit) don't lag behind the cursor. */
    .rail[data-user-hidden] { transform: translateX(-100%); }
    :host([data-rail-anim]) .rail { transition: transform 200ms cubic-bezier(.3,.7,.4,1); }
    :host([data-rail-anim]) .stage { transition: left 200ms cubic-bezier(.3,.7,.4,1); }
    :host([data-rail-anim]) .canvas { transition: transform 200ms cubic-bezier(.3,.7,.4,1); }
    /* transition shorthand replaces rather than merges — repeat the base
       .overlay opacity/transform/filter transitions so visibility changes
       during the 200ms toggle window still fade instead of popping. */
    :host([data-rail-anim]) .overlay {
      transition: margin-left 200ms cubic-bezier(.3,.7,.4,1),
                  opacity 260ms ease,
                  transform 260ms cubic-bezier(.2,.8,.2,1),
                  filter 260ms ease;
    }

    .thumb {
      position: relative;
      display: flex;
      align-items: flex-start;
      gap: 8px;
      cursor: pointer;
      user-select: none;
    }
    .thumb .num {
      width: 16px;
      flex-shrink: 0;
      font-size: 11px;
      font-weight: 500;
      text-align: right;
      color: rgba(255,255,255,0.55);
      padding-top: 2px;
      font-variant-numeric: tabular-nums;
    }
    .thumb .frame {
      position: relative;
      flex: 1;
      min-width: 0;
      aspect-ratio: var(--deck-aspect);
      background: #fff;
      border-radius: 4px;
      outline: 2px solid transparent;
      outline-offset: 0;
      overflow: hidden;
      transition: outline-color 120ms ease;
    }
    .thumb:hover .frame { outline-color: rgba(255,255,255,0.25); }
    .thumb { outline: none; }
    .thumb:focus-visible .frame { outline-color: rgba(255,255,255,0.5); }
    .thumb[data-current] .num { color: #fff; }
    .thumb[data-current] .frame { outline-color: #D97757; }
    .thumb[data-dragging] { opacity: 0.35; }
    .thumb::before {
      content: '';
      position: absolute;
      left: 24px;
      right: 0;
      height: 3px;
      border-radius: 2px;
      background: #D97757;
      opacity: 0;
      pointer-events: none;
    }
    .thumb[data-drop="before"]::before { top: -8px; opacity: 1; }
    .thumb[data-drop="after"]::before { bottom: -8px; opacity: 1; }
    .thumb[data-skip] .frame { opacity: 0.35; }
    .thumb[data-skip] .frame::after {
      content: 'Skipped';
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0,0,0,0.45);
      color: #fff;
      font-size: 10px;
      font-weight: 500;
      letter-spacing: 0.04em;
    }

    .ctxmenu {
      position: fixed;
      min-width: 150px;
      padding: 4px;
      background: #242424;
      border: 1px solid rgba(255,255,255,0.12);
      border-radius: 7px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.45);
      z-index: 2147483100;
      display: none;
      font-size: 12px;
    }
    .ctxmenu[data-open] { display: block; }
    .ctxmenu button {
      display: block;
      width: 100%;
      appearance: none;
      border: 0;
      background: transparent;
      color: #e8e8e8;
      font: inherit;
      text-align: left;
      padding: 6px 10px;
      border-radius: 4px;
      cursor: pointer;
    }
    .ctxmenu button:hover:not(:disabled) { background: rgba(255,255,255,0.08); }
    .ctxmenu button:disabled { opacity: 0.35; cursor: default; }
    .ctxmenu hr {
      border: 0;
      border-top: 1px solid rgba(255,255,255,0.1);
      margin: 4px 2px;
    }

    .rail-resize {
      position: fixed;
      left: calc(var(--deck-rail-w, 188px) - 3px);
      top: 0;
      bottom: 0;
      width: 6px;
      cursor: col-resize;
      z-index: 2147482600;
      touch-action: none;
    }
    .rail-resize:hover,
    .rail-resize[data-dragging] { background: rgba(255,255,255,0.12); }
    :host([no-rail]) .rail-resize,
    :host([noscale]) .rail-resize,
    .rail[data-presenting] + .rail-resize,
    .rail[data-user-hidden] + .rail-resize { display: none; }

    /* Delete-confirm popup — matches the SPA's ConfirmDialog layout
       (title + message body, depressed footer with Cancel / Delete). */
    .confirm-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.45);
      z-index: 2147483200;
      display: none;
      align-items: center;
      justify-content: center;
    }
    .confirm-backdrop[data-open] { display: flex; }
    .confirm {
      width: 320px;
      max-width: calc(100vw - 32px);
      background: #2a2a2a;
      color: #e8e8e8;
      border: 1px solid rgba(255,255,255,0.12);
      border-radius: 12px;
      box-shadow: 0 12px 32px rgba(0,0,0,0.5);
      overflow: hidden;
      font-family: inherit;
      animation: deck-confirm-in 0.18s ease;
    }
    @keyframes deck-confirm-in {
      from { opacity: 0; transform: scale(0.96); }
      to { opacity: 1; transform: scale(1); }
    }
    .confirm .body { padding: 20px 20px 16px; }
    .confirm .title { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
    .confirm .msg { font-size: 13px; line-height: 1.5; color: rgba(255,255,255,0.65); }
    .confirm .footer {
      padding: 14px 20px;
      background: #1f1f1f;
      border-top: 1px solid rgba(255,255,255,0.08);
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
    .confirm button {
      appearance: none;
      font: inherit;
      font-size: 13px;
      font-weight: 500;
      padding: 8px 16px;
      border-radius: 8px;
      cursor: pointer;
    }
    .confirm .cancel {
      background: transparent;
      border: 0;
      color: rgba(255,255,255,0.8);
    }
    .confirm .cancel:hover { background: rgba(255,255,255,0.08); }
    .confirm .danger {
      background: #c96442;
      border: 1px solid rgba(0,0,0,0.15);
      color: #fff;
      box-shadow: 0 1px 3px rgba(166,50,68,0.3), 0 2px 6px rgba(166,50,68,0.18);
    }
    .confirm .danger:hover { background: #b5563a; }

    /* ── Print: one page per slide, no chrome ────────────────────────────
       The screen layout stacks every slide at inset:0 inside a scaled
       canvas; for print we want them in document flow at the authored
       design size so the browser paginates one slide per sheet. The
       @page size is set from the width/height attributes via the inline
       <style id="deck-stage-print-page"> that connectedCallback injects
       into <head> (the @page at-rule has no effect inside shadow DOM). */
    @media print {
      :host {
        position: static;
        inset: auto;
        background: none;
        overflow: visible;
        color: inherit;
      }
      .stage { position: static; display: block; }
      .canvas {
        transform: none !important;
        width: auto !important;
        height: auto !important;
        background: none;
        will-change: auto;
      }
      ::slotted(*) {
        position: relative !important;
        inset: auto !important;
        width: var(--deck-design-w) !important;
        height: var(--deck-design-h) !important;
        box-sizing: border-box !important;
        opacity: 1 !important;
        visibility: visible !important;
        pointer-events: auto;
        break-after: page;
        page-break-after: always;
        break-inside: avoid;
        overflow: hidden;
      }
      /* :last-child alone isn't enough once data-deck-skip hides the
         trailing slide(s) — the last *visible* slide still carries
         break-after:page and prints a blank sheet. _markLastVisible()
         maintains data-deck-last-visible on the last non-skipped slide. */
      ::slotted(*:last-child),
      ::slotted([data-deck-last-visible]) {
        break-after: auto;
        page-break-after: auto;
      }
      ::slotted([data-deck-skip]) { display: none !important; }
      .overlay, .rail, .rail-resize, .ctxmenu, .confirm-backdrop { display: none !important; }
    }
  `;
  class DeckStage extends HTMLElement {
    static get observedAttributes() {
      return ['width', 'height', 'noscale', 'no-rail'];
    }
    constructor() {
      super();
      this._root = this.attachShadow({
        mode: 'open'
      });
      this._index = 0;
      this._slides = [];
      this._notes = [];
      this._hideTimer = null;
      this._mouseIdleTimer = null;
      this._menuIndex = -1;
      this._onKey = this._onKey.bind(this);
      this._onResize = this._onResize.bind(this);
      this._onSlotChange = this._onSlotChange.bind(this);
      this._onMouseMove = this._onMouseMove.bind(this);
      this._onTap = this._onTap.bind(this);
      this._onMessage = this._onMessage.bind(this);
      // Capture-phase close so a click anywhere dismisses the menu, but
      // ignore clicks that land inside the menu itself — otherwise the
      // capture handler runs before the menu's own (bubble) handler and
      // clears _menuIndex out from under it.
      this._onDocClick = e => {
        if (this._menu && e.composedPath && e.composedPath().includes(this._menu)) return;
        this._closeMenu();
      };
    }
    get designWidth() {
      return parseInt(this.getAttribute('width'), 10) || DESIGN_W_DEFAULT;
    }
    get designHeight() {
      return parseInt(this.getAttribute('height'), 10) || DESIGN_H_DEFAULT;
    }
    connectedCallback() {
      // Presenter-view popup loads deckUrl?_snthumb=...#N for its prev/cur/
      // next thumbnails — the rail has no business rendering inside those
      // (wrong scale, and it offsets the stage so the thumb shows a gutter).
      if (/[?&]_snthumb=/.test(location.search)) this.setAttribute('no-rail', '');
      this._render();
      this._loadNotes();
      this._syncPrintPageRule();
      window.addEventListener('keydown', this._onKey);
      window.addEventListener('resize', this._onResize);
      window.addEventListener('mousemove', this._onMouseMove, {
        passive: true
      });
      window.addEventListener('message', this._onMessage);
      window.addEventListener('click', this._onDocClick, true);
      this.addEventListener('click', this._onTap);
      // Initial collection + layout happens via slotchange, which fires on mount.
      this._enableRail();
      // Hold the stage hidden until webfonts are ready so the first visible
      // paint has the deck's real typography — the :not(:defined) guard in
      // the page HTML only covers custom-element upgrade, not font load.
      // Capped so a 404'd font URL can't blank the deck indefinitely.
      this.setAttribute('data-fonts-pending', '');
      const reveal = () => this.removeAttribute('data-fonts-pending');
      // rAF first: fonts.ready is a pre-resolved promise until layout has
      // resolved the slotted text's font-family and pushed a FontFace into
      // 'loading'. Reading it here in connectedCallback (parse-time) would
      // settle the race in a microtask before any font fetch starts.
      requestAnimationFrame(() => {
        Promise.race([document.fonts ? document.fonts.ready : Promise.resolve(), new Promise(r => setTimeout(r, 2000))]).then(reveal, reveal);
      });
    }
    _enableRail() {
      // Idempotent — older host builds still post __omelette_rail_enabled.
      // no-rail guard keeps the observers/stylesheet walk off the cheap path
      // for presenter-popup thumbnail iframes (up to 9 per view).
      if (this._railEnabled || this.hasAttribute('no-rail')) return;
      this._railEnabled = true;
      // Per-viewer preference — restored alongside rail width. Default on;
      // only a stored '0' (from the TweaksPanel toggle) hides it.
      this._railVisible = true;
      try {
        if (localStorage.getItem('deck-stage.railVisible') === '0') this._railVisible = false;
      } catch (e) {}
      // Live thumbnail updates: watch the light-DOM slides for content
      // edits and re-clone just the affected thumb(s), debounced. Ignore
      // the data-deck-* / data-screen-label / data-om-validate attributes
      // this component itself writes so nav and skip don't trigger
      // spurious refreshes.
      const OWN_ATTRS = /^data-(deck-|screen-label$|om-validate$)/;
      this._liveDirty = new Set();
      this._liveObserver = new MutationObserver(records => {
        for (const r of records) {
          if (r.type === 'attributes' && OWN_ATTRS.test(r.attributeName || '')) continue;
          let n = r.target;
          while (n && n.parentElement !== this) n = n.parentElement;
          if (n && this._slideSet && this._slideSet.has(n)) this._liveDirty.add(n);
        }
        if (this._liveDirty.size && !this._liveTimer) {
          this._liveTimer = setTimeout(() => {
            this._liveTimer = null;
            this._liveDirty.forEach(s => this._refreshThumb(s));
            this._liveDirty.clear();
          }, 200);
        }
      });
      this._liveObserver.observe(this, {
        subtree: true,
        childList: true,
        characterData: true,
        attributes: true
      });
      // Lazy thumbnail materialization — clone the slide only when its
      // frame scrolls into (or near) the rail viewport. rootMargin gives
      // ~4 thumbs of pre-load so fast scrolling doesn't flash blanks.
      this._railObserver = new IntersectionObserver(entries => {
        entries.forEach(e => {
          if (e.isIntersecting && e.target.__deckThumb) {
            this._materialize(e.target.__deckThumb);
          }
        });
      }, {
        root: this._rail,
        rootMargin: '400px 0px'
      });
      // Tweaks typically change CSS vars / attrs OUTSIDE <deck-stage>
      // (on <html>, <body>, a wrapper div, or a <style> tag), which
      // _liveObserver can't see. Re-snapshot author CSS (constructable
      // sheet is shared by reference, so one replaceSync updates every
      // thumb shadow root) and re-sync each thumb host's attrs + custom
      // properties. In-slide DOM mutations are _liveObserver's job.
      // Debounced so slider drags don't thrash.
      this._onTweakChange = () => {
        clearTimeout(this._tweakTimer);
        this._tweakTimer = setTimeout(() => {
          this._snapshotAuthorCss();
          // One getComputedStyle for the whole batch — each
          // getPropertyValue read below reuses the same computed style
          // as long as nothing invalidates layout between thumbs.
          const cs = getComputedStyle(this);
          (this._thumbs || []).forEach(t => {
            if (t.host) this._syncThumbHostAttrs(t.host, cs);
          });
        }, 120);
      };
      window.addEventListener('tweakchange', this._onTweakChange);
      this._snapshotAuthorCss();
      // Build the rail now that it's enabled — slotchange already fired,
      // so _renderRail's early-return skipped the initial build.
      this._syncRailHidden();
      this._renderRail();
      this._fit();
    }

    /** Snapshot document stylesheets into a constructable sheet that each
     *  thumbnail's nested shadow root adopts — so author CSS styles the
     *  cloned slide content without touching this component's chrome.
     *  Cross-origin sheets throw on .cssRules — skip them. Re-callable:
     *  the existing constructable sheet is reused via replaceSync so every
     *  already-adopted shadow root picks up the fresh CSS without re-adopt. */
    _snapshotAuthorCss() {
      // :root in an adopted sheet inside a shadow root matches nothing
      // (only the document root qualifies), so author rules like
      // `:root[data-voice="modern"] .serif` never reach the clones.
      // Rewrite :root → :host and mirror <html>'s data-*/class/lang onto
      // each thumb host (see _syncThumbHostAttrs) so the same selectors
      // match inside the thumbnail's shadow tree.
      const authorCss = Array.from(document.styleSheets).map(sh => {
        try {
          return Array.from(sh.cssRules).map(r => r.cssText).join('\n');
        } catch (e) {
          return '';
        }
      }).join('\n')
      // The shadow host is featureless outside the functional :host(...)
      // form, so any compound on :root — [attr], .class, #id, :pseudo —
      // must become :host(<compound>) not :host<compound>. Same for the
      // html type selector (Tailwind class-strategy dark mode emits
      // html.dark; Pico uses html[data-theme]), which has nothing to
      // match inside the thumb's shadow tree.
      .replace(/:root((?:\[[^\]]*\]|[.#][-\w]+|:[-\w]+(?:\([^)]*\))?)+)/g, ':host($1)').replace(/:root\b/g, ':host').replace(/(^|[\s,>~+(}])html((?:\[[^\]]*\]|[.#][-\w]+|:[-\w]+(?:\([^)]*\))?)+)(?![-\w])/g, '$1:host($2)').replace(/(^|[\s,>~+(}])html(?![-\w])/g, '$1:host');
      // Every custom property the author references. _syncThumbHostAttrs
      // mirrors each one's *computed* value at <deck-stage> onto the
      // thumb host so the live value wins over the :host default above
      // regardless of which ancestor the tweak wrote to (<html>, <body>,
      // a wrapper div, or the deck-stage element itself all inherit
      // down to getComputedStyle(this)).
      this._authorVars = new Set(authorCss.match(/--[\w-]+/g) || []);
      try {
        if (!this._adoptedSheet) this._adoptedSheet = new CSSStyleSheet();
        this._adoptedSheet.replaceSync(authorCss);
      } catch (e) {
        this._adoptedSheet = null;
        this._authorCss = authorCss;
      }
    }
    _syncThumbHostAttrs(host, cs) {
      const de = document.documentElement;
      // setAttribute overwrites but can't delete — an attr removed from
      // <html> (toggleAttribute off, classList emptied) would linger on
      // the host and :host([data-*]) / :host(.foo) rules would keep
      // matching. Remove stale mirrored attrs first; iterate backward
      // because removeAttribute mutates the live NamedNodeMap.
      for (let i = host.attributes.length - 1; i >= 0; i--) {
        const n = host.attributes[i].name;
        if ((n.startsWith('data-') || n === 'class' || n === 'lang') && !de.hasAttribute(n)) {
          host.removeAttribute(n);
        }
      }
      for (const a of de.attributes) {
        if (a.name.startsWith('data-') || a.name === 'class' || a.name === 'lang') {
          host.setAttribute(a.name, a.value);
        }
      }
      // The :root→:host rewrite in _snapshotAuthorCss pins each custom
      // property to its stylesheet default on the thumb host, shadowing
      // the live value that would otherwise inherit. Tweaks can write the
      // live value on any ancestor — <html>, <body>, a wrapper div, the
      // deck-stage element — so read it as the *computed* value at
      // <deck-stage> (which sees the whole inheritance chain) rather than
      // trying to guess which element the author wrote to. Inline on the
      // host beats the :host{} rule. remove-stale covers vars dropped
      // from the stylesheet between snapshots.
      const vars = this._authorVars || new Set();
      for (let i = host.style.length - 1; i >= 0; i--) {
        const p = host.style[i];
        if (p.startsWith('--') && !vars.has(p)) host.style.removeProperty(p);
      }
      const live = cs || getComputedStyle(this);
      vars.forEach(p => {
        const v = live.getPropertyValue(p);
        if (v) host.style.setProperty(p, v.trim());else host.style.removeProperty(p);
      });
    }
    disconnectedCallback() {
      window.removeEventListener('keydown', this._onKey);
      window.removeEventListener('resize', this._onResize);
      window.removeEventListener('mousemove', this._onMouseMove);
      window.removeEventListener('message', this._onMessage);
      window.removeEventListener('click', this._onDocClick, true);
      this.removeEventListener('click', this._onTap);
      if (this._hideTimer) clearTimeout(this._hideTimer);
      if (this._mouseIdleTimer) clearTimeout(this._mouseIdleTimer);
      if (this._liveTimer) clearTimeout(this._liveTimer);
      if (this._tweakTimer) clearTimeout(this._tweakTimer);
      if (this._railAnimTimer) clearTimeout(this._railAnimTimer);
      if (this._scaleRaf) cancelAnimationFrame(this._scaleRaf);
      if (this._liveObserver) this._liveObserver.disconnect();
      if (this._railObserver) this._railObserver.disconnect();
      if (this._onTweakChange) window.removeEventListener('tweakchange', this._onTweakChange);
    }
    attributeChangedCallback() {
      if (this._canvas) {
        this._canvas.style.width = this.designWidth + 'px';
        this._canvas.style.height = this.designHeight + 'px';
        this._canvas.style.setProperty('--deck-design-w', this.designWidth + 'px');
        this._canvas.style.setProperty('--deck-design-h', this.designHeight + 'px');
        if (this._rail) {
          this._rail.style.setProperty('--deck-aspect', this.designWidth + '/' + this.designHeight);
        }
        this._fit();
        this._scaleThumbs();
        this._syncPrintPageRule();
      }
    }
    _render() {
      const style = document.createElement('style');
      style.textContent = stylesheet;
      const stage = document.createElement('div');
      stage.className = 'stage';
      const canvas = document.createElement('div');
      canvas.className = 'canvas';
      canvas.style.width = this.designWidth + 'px';
      canvas.style.height = this.designHeight + 'px';
      canvas.style.setProperty('--deck-design-w', this.designWidth + 'px');
      canvas.style.setProperty('--deck-design-h', this.designHeight + 'px');
      const slot = document.createElement('slot');
      slot.addEventListener('slotchange', this._onSlotChange);
      canvas.appendChild(slot);
      stage.appendChild(canvas);

      // Overlay: compact, solid black, with clickable controls.
      const overlay = document.createElement('div');
      overlay.className = 'overlay export-hidden';
      overlay.setAttribute('role', 'toolbar');
      overlay.setAttribute('aria-label', 'Deck controls');
      overlay.setAttribute('data-omelette-chrome', '');
      overlay.innerHTML = `
        <button class="btn prev" type="button" aria-label="Previous slide" title="Previous (←)">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 3L5 8l5 5"/></svg>
        </button>
        <span class="count" aria-live="polite"><span class="current">1</span><span class="sep">/</span><span class="total">1</span></span>
        <button class="btn next" type="button" aria-label="Next slide" title="Next (→)">
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 3l5 5-5 5"/></svg>
        </button>
        <span class="divider"></span>
        <button class="btn reset" type="button" aria-label="Reset to first slide" title="Reset (R)">Reset<span class="kbd">R</span></button>
      `;
      overlay.querySelector('.prev').addEventListener('click', () => this._advance(-1, 'click'));
      overlay.querySelector('.next').addEventListener('click', () => this._advance(1, 'click'));
      overlay.querySelector('.reset').addEventListener('click', () => this._go(0, 'click'));

      // Thumbnail rail + context menu. Thumbnails are populated in
      // _renderRail() after _collectSlides().
      const rail = document.createElement('div');
      rail.className = 'rail export-hidden';
      rail.setAttribute('data-omelette-chrome', '');
      rail.style.setProperty('--deck-aspect', this.designWidth + '/' + this.designHeight);
      // Edge auto-scroll while dragging a thumb near the rail's top/bottom
      // so off-screen drop targets are reachable. Native dragover fires
      // continuously while the pointer is stationary, so a per-event nudge
      // (ramped by edge proximity) is enough — no rAF loop needed.
      rail.addEventListener('dragover', e => {
        if (this._dragFrom == null) return;
        const r = rail.getBoundingClientRect();
        const EDGE = 40;
        const dt = e.clientY - r.top;
        const db = r.bottom - e.clientY;
        if (dt < EDGE) rail.scrollTop -= Math.ceil((EDGE - dt) / 3);else if (db < EDGE) rail.scrollTop += Math.ceil((EDGE - db) / 3);
      });
      const menu = document.createElement('div');
      menu.className = 'ctxmenu export-hidden';
      menu.setAttribute('data-omelette-chrome', '');
      menu.innerHTML = `
        <button type="button" data-act="skip">Skip slide</button>
        <button type="button" data-act="up">Move up</button>
        <button type="button" data-act="down">Move down</button>
        <hr>
        <button type="button" data-act="delete">Delete slide</button>
      `;
      menu.addEventListener('click', e => {
        const act = e.target && e.target.getAttribute && e.target.getAttribute('data-act');
        if (!act) return;
        const i = this._menuIndex;
        this._closeMenu();
        if (act === 'skip') this._toggleSkip(i);else if (act === 'up') this._moveSlide(i, i - 1);else if (act === 'down') this._moveSlide(i, i + 1);else if (act === 'delete') this._openConfirm(i);
      });
      menu.addEventListener('contextmenu', e => e.preventDefault());

      // Rail resize handle — drag to set --deck-rail-w, persisted to
      // localStorage so the width survives reloads.
      const resize = document.createElement('div');
      resize.className = 'rail-resize export-hidden';
      resize.setAttribute('data-omelette-chrome', '');
      resize.addEventListener('pointerdown', e => {
        e.preventDefault();
        resize.setPointerCapture(e.pointerId);
        resize.setAttribute('data-dragging', '');
        const move = ev => this._setRailWidth(ev.clientX);
        const up = () => {
          resize.removeEventListener('pointermove', move);
          resize.removeEventListener('pointerup', up);
          resize.removeEventListener('pointercancel', up);
          resize.removeAttribute('data-dragging');
          try {
            localStorage.setItem('deck-stage.railWidth', String(this._railPx));
          } catch (err) {}
        };
        resize.addEventListener('pointermove', move);
        resize.addEventListener('pointerup', up);
        resize.addEventListener('pointercancel', up);
      });

      // Delete-confirm dialog — mirrors the SPA's ConfirmDialog layout.
      const confirm = document.createElement('div');
      confirm.className = 'confirm-backdrop export-hidden';
      confirm.setAttribute('data-omelette-chrome', '');
      confirm.innerHTML = `
        <div class="confirm" role="dialog" aria-modal="true">
          <div class="body">
            <div class="title">Delete slide?</div>
            <div class="msg">This slide will be removed from the deck.</div>
          </div>
          <div class="footer">
            <button type="button" class="cancel">Cancel</button>
            <button type="button" class="danger">Delete</button>
          </div>
        </div>
      `;
      confirm.addEventListener('click', e => {
        if (e.target === confirm) this._closeConfirm();
      });
      confirm.querySelector('.cancel').addEventListener('click', () => this._closeConfirm());
      confirm.querySelector('.danger').addEventListener('click', () => {
        const i = this._confirmIndex;
        this._closeConfirm();
        this._deleteSlide(i);
      });
      this._root.append(style, rail, resize, stage, overlay, menu, confirm);
      this._canvas = canvas;
      this._stage = stage;
      this._slot = slot;
      this._overlay = overlay;
      this._rail = rail;
      this._resize = resize;
      this._menu = menu;
      this._confirm = confirm;
      this._countEl = overlay.querySelector('.current');
      this._totalEl = overlay.querySelector('.total');

      // Restore persisted rail width.
      let rw = 188;
      try {
        const s = localStorage.getItem('deck-stage.railWidth');
        if (s) rw = parseInt(s, 10) || rw;
      } catch (err) {}
      this._setRailWidth(rw);
      this._syncRailHidden();
    }
    _setRailWidth(px) {
      const w = Math.max(120, Math.min(360, Math.round(px)));
      this._railPx = w;
      this.style.setProperty('--deck-rail-w', w + 'px');
      this._fit();
      // _scaleThumbs forces a sync layout (frame.offsetWidth) then writes
      // N transforms. During a resize drag this runs per-pointermove;
      // coalesce to one per frame.
      if (!this._scaleRaf) {
        this._scaleRaf = requestAnimationFrame(() => {
          this._scaleRaf = null;
          this._scaleThumbs();
        });
      }
    }

    /** @page must live in the document stylesheet — it's a no-op inside
     *  shadow DOM. Inject/update a single <head> style tag so the print
     *  sheet matches the design size and Save-as-PDF yields one slide per
     *  page with no margins. */
    _syncPrintPageRule() {
      const id = 'deck-stage-print-page';
      let tag = document.getElementById(id);
      if (!tag) {
        tag = document.createElement('style');
        tag.id = id;
        document.head.appendChild(tag);
      }
      tag.textContent = '@page { size: ' + this.designWidth + 'px ' + this.designHeight + 'px; margin: 0; } ' + '@media print { html, body { margin: 0 !important; padding: 0 !important; background: none !important; overflow: visible !important; height: auto !important; } ' + '* { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }';
    }
    _onSlotChange() {
      // Rail mutations (delete/move) already reconcile synchronously and
      // emit slidechange with reason 'api'; skip the async slotchange that
      // would otherwise re-broadcast with reason 'init'.
      if (this._squelchSlotChange) {
        this._squelchSlotChange = false;
        return;
      }
      this._collectSlides();
      this._restoreIndex();
      this._applyIndex({
        showOverlay: false,
        broadcast: true,
        reason: 'init'
      });
      this._fit();
    }
    _collectSlides() {
      const assigned = this._slot.assignedElements({
        flatten: true
      });
      this._slides = assigned.filter(el => {
        // Skip template/style/script nodes even if someone slots them.
        const tag = el.tagName;
        return tag !== 'TEMPLATE' && tag !== 'SCRIPT' && tag !== 'STYLE';
      });
      this._slideSet = new Set(this._slides);
      this._slides.forEach((slide, i) => {
        const n = i + 1;
        slide.setAttribute('data-screen-label', `${pad2(n)} ${getSlideLabel(slide)}`);

        // Validation attribute for comment flow / auto-checks.
        if (!slide.hasAttribute('data-om-validate')) {
          slide.setAttribute('data-om-validate', VALIDATE_ATTR);
        }
        slide.setAttribute('data-deck-slide', String(i));
      });
      if (this._totalEl) this._totalEl.textContent = String(this._slides.length || 1);
      if (this._index >= this._slides.length) this._index = Math.max(0, this._slides.length - 1);
      this._markLastVisible();
      this._renderRail();
    }

    /** Tag the last non-skipped slide so print CSS can drop its
     *  break-after (see the @media print comment above — :last-child
     *  alone matches a hidden skipped slide). */
    _markLastVisible() {
      let last = null;
      this._slides.forEach(s => {
        s.removeAttribute('data-deck-last-visible');
        if (!s.hasAttribute('data-deck-skip')) last = s;
      });
      if (last) last.setAttribute('data-deck-last-visible', '');
    }
    _loadNotes() {
      const tag = document.getElementById('speaker-notes');
      if (!tag) {
        this._notes = [];
        return;
      }
      try {
        const parsed = JSON.parse(tag.textContent || '[]');
        if (Array.isArray(parsed)) this._notes = parsed;
      } catch (e) {
        console.warn('[deck-stage] Failed to parse #speaker-notes JSON:', e);
        this._notes = [];
      }
    }
    _restoreIndex() {
      // The host's ?slide= param is delivered as a #<int> hash (1-indexed) on
      // the iframe src. No hash → slide 1; the deck itself keeps no position
      // state across loads.
      const h = (location.hash || '').match(/^#(\d+)$/);
      if (h) {
        const n = parseInt(h[1], 10) - 1;
        if (n >= 0 && n < this._slides.length) this._index = n;
      }
    }
    _applyIndex({
      showOverlay = true,
      broadcast = true,
      reason = 'init'
    } = {}) {
      if (!this._slides.length) return;
      const prev = this._prevIndex == null ? -1 : this._prevIndex;
      const curr = this._index;
      // Keep the iframe's own hash in sync so an in-iframe location.reload()
      // (reload banner path in viewer-handle.ts) lands on the current slide,
      // not the stale deep-link hash from initial load.
      try {
        history.replaceState(null, '', '#' + (curr + 1));
      } catch (e) {}
      this._slides.forEach((s, i) => {
        if (i === curr) s.setAttribute('data-deck-active', '');else s.removeAttribute('data-deck-active');
      });
      if (this._countEl) this._countEl.textContent = String(curr + 1);
      // Follow-scroll on every navigation (init deep-link, keyboard, click,
      // tap, external goTo) — the only time we *don't* want the rail to
      // track current is after a rail-internal mutation, where _renderRail
      // has already restored the user's scroll position and yanking back to
      // current would undo it.
      this._syncRail(reason !== 'mutation');
      if (broadcast) {
        // (1) Legacy: host-window postMessage for speaker-notes renderers.
        try {
          window.postMessage({
            slideIndexChanged: curr,
            deckTotal: this._slides.length,
            deckSkipped: this._skippedIndices()
          }, '*');
        } catch (e) {}

        // (2) In-page CustomEvent on the <deck-stage> element itself.
        //     Bubbles and composes out of shadow DOM so slide code can listen:
        //       document.querySelector('deck-stage').addEventListener('slidechange', e => {
        //         e.detail.index, e.detail.previousIndex, e.detail.total, e.detail.slide, e.detail.reason
        //       });
        const detail = {
          index: curr,
          previousIndex: prev,
          total: this._slides.length,
          slide: this._slides[curr] || null,
          previousSlide: prev >= 0 ? this._slides[prev] || null : null,
          reason: reason // 'init' | 'keyboard' | 'click' | 'tap' | 'api'
        };
        this.dispatchEvent(new CustomEvent('slidechange', {
          detail,
          bubbles: true,
          composed: true
        }));
      }
      this._prevIndex = curr;
      if (showOverlay) this._flashOverlay();
    }
    _flashOverlay() {
      // Host posts __omelette_presenting while in fullscreen/tab presentation
      // mode — suppress the nav footer entirely (both hover and slide-change
      // flash) so the audience sees clean slides.
      if (!this._overlay || this._presenting) return;
      this._overlay.setAttribute('data-visible', '');
      if (this._hideTimer) clearTimeout(this._hideTimer);
      this._hideTimer = setTimeout(() => {
        this._overlay.removeAttribute('data-visible');
      }, OVERLAY_HIDE_MS);
    }
    _railWidth() {
      // State-based, no offsetWidth: the first _fit() can run before the
      // rail has had layout on some load paths, and a 0 there paints the
      // slide full-width for one frame before the post-slotchange _fit()
      // corrects it.
      if (!this._railEnabled || !this._railVisible || this.hasAttribute('no-rail') || this.hasAttribute('noscale') || this._presenting || this._previewMode || NARROW_MQ.matches) return 0;
      return this._railPx || 0;
    }
    _fit() {
      if (!this._canvas) return;
      const stage = this._canvas.parentElement;
      // PPTX export sets noscale so the DOM capture sees authored-size
      // geometry — the scaled canvas is in shadow DOM, so the exporter's
      // resetTransformSelector can't reach .canvas.style.transform directly.
      if (this.hasAttribute('noscale')) {
        this._canvas.style.transform = 'none';
        if (stage) stage.style.left = '0';
        if (this._overlay) this._overlay.style.marginLeft = '0';
        return;
      }
      const rw = this._railWidth();
      if (stage) stage.style.left = rw + 'px';
      // Overlay is centred on the viewport via left:50% + translate(-50%);
      // marginLeft shifts the centre by rw/2 so it lands in the middle of
      // the [rw, innerWidth] stage region.
      if (this._overlay) this._overlay.style.marginLeft = rw / 2 + 'px';
      const vw = window.innerWidth - rw;
      const vh = window.innerHeight;
      const s = Math.min(vw / this.designWidth, vh / this.designHeight);
      this._canvas.style.transform = `scale(${s})`;
    }
    _onResize() {
      this._fit();
      // Crossing the narrow-viewport breakpoint reveals the rail — rerun the
      // thumbnail scale the same way _setRailWidth does.
      if (!this._scaleRaf) {
        this._scaleRaf = requestAnimationFrame(() => {
          this._scaleRaf = null;
          this._scaleThumbs();
        });
      }
    }
    _onMouseMove() {
      // Keep overlay visible while mouse moves; hide after idle.
      this._flashOverlay();
    }
    _onMessage(e) {
      const d = e.data;
      if (d && typeof d.__omelette_presenting === 'boolean') {
        this._presenting = d.__omelette_presenting;
        if (this._presenting && this._overlay) {
          this._overlay.removeAttribute('data-visible');
          if (this._hideTimer) clearTimeout(this._hideTimer);
        }
        this._syncRailHidden();
        this._closeMenu();
        this._closeConfirm();
        this._fit();
        this._scaleThumbs();
      }
      // Host's Preview segment (ViewerMode='none'): the rail's drag-reorder /
      // right-click skip-delete affordances are editing chrome, so hide it
      // while the user is just looking at the deck. Same hard-hide path as
      // presenting; independent of the user's _railVisible preference so
      // returning to Edit restores whatever they had.
      if (d && typeof d.__omelette_preview_mode === 'boolean') {
        if (d.__omelette_preview_mode === this._previewMode) return;
        this._previewMode = d.__omelette_preview_mode;
        this._syncRailHidden();
        this._closeMenu();
        this._closeConfirm();
        this._fit();
        this._scaleThumbs();
      }
      // Per-viewer show/hide, driven by the TweaksPanel's auto-injected
      // "Thumbnail rail" toggle (or any author script). Independent of
      // whether the Tweaks panel itself is open — closing the panel
      // doesn't change rail visibility. Persists alongside rail width.
      if (d && d.type === '__deck_rail_visible' && typeof d.on === 'boolean') {
        if (d.on === this._railVisible) return;
        this._railVisible = d.on;
        try {
          localStorage.setItem('deck-stage.railVisible', d.on ? '1' : '0');
        } catch (e) {}
        // Arm the transition, commit it, then flip state — otherwise the
        // browser coalesces both writes and nothing animates on show.
        this.setAttribute('data-rail-anim', '');
        void (this._rail && this._rail.offsetHeight);
        this._syncRailHidden();
        this._fit();
        this._scaleThumbs();
        clearTimeout(this._railAnimTimer);
        this._railAnimTimer = setTimeout(() => this.removeAttribute('data-rail-anim'), 220);
      }
      if (d && d.type === '__omelette_rail_enabled') this._enableRail();
    }
    _syncRailHidden() {
      if (!this._rail) return;
      // data-presenting is the hard hide (display:none) for flag-off,
      // presentation mode, and the host's Preview segment — instant, no
      // transition. data-user-hidden is the soft hide (translateX(-100%))
      // for the viewer's rail toggle, so show/hide slides under
      // :host([data-rail-anim]).
      const hard = !this._railEnabled || this._presenting || this._previewMode;
      if (hard) this._rail.setAttribute('data-presenting', '');else this._rail.removeAttribute('data-presenting');
      if (!this._railVisible) this._rail.setAttribute('data-user-hidden', '');else this._rail.removeAttribute('data-user-hidden');
      // translateX hide leaves thumbs (tabIndex=0) in the tab order —
      // inert keeps them unfocusable while the rail is off-screen.
      this._rail.inert = hard || !this._railVisible;
    }
    _onTap(e) {
      // Touch-only — keyboard + the overlay toolbar cover nav on desktop.
      if (FINE_POINTER_MQ.matches) return;
      // Only taps that land on the stage (slide content or letterbox); the
      // overlay / rail / menus are siblings with their own click handlers.
      const path = e.composedPath();
      if (!this._stage || !path.includes(this._stage)) return;
      // Let interactive slide content keep the tap. composedPath (not
      // e.target.closest) so we see through open shadow roots — a <button>
      // inside a slide-authored custom element retargets e.target to the
      // host but still appears in the composed path.
      if (e.defaultPrevented) return;
      for (const n of path) {
        if (n === this._stage) break;
        if (n.matches && n.matches(INTERACTIVE_SEL)) return;
      }
      e.preventDefault();
      const rw = this._railWidth();
      const mid = rw + (window.innerWidth - rw) / 2;
      this._advance(e.clientX < mid ? -1 : 1, 'tap');
    }
    _onKey(e) {
      // Ignore when the user is typing.
      const t = e.target;
      if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return;
      // Confirm dialog swallows nav keys while open; Escape cancels. Enter
      // is left to the focused button's native activation so Tab→Cancel
      // →Enter activates Cancel, not the window-level confirm path.
      if (this._confirm && this._confirm.hasAttribute('data-open')) {
        if (e.key === 'Escape') {
          this._closeConfirm();
          e.preventDefault();
        }
        return;
      }
      if (e.key === 'Escape' && this._menu && this._menu.hasAttribute('data-open')) {
        this._closeMenu();
        e.preventDefault();
        return;
      }
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const key = e.key;
      let handled = true;
      if (key === 'ArrowRight' || key === 'PageDown' || key === ' ' || key === 'Spacebar') {
        this._advance(1, 'keyboard');
      } else if (key === 'ArrowLeft' || key === 'PageUp') {
        this._advance(-1, 'keyboard');
      } else if (key === 'Home') {
        this._go(0, 'keyboard');
      } else if (key === 'End') {
        this._go(this._slides.length - 1, 'keyboard');
      } else if (key === 'r' || key === 'R') {
        this._go(0, 'keyboard');
      } else if (/^[0-9]$/.test(key)) {
        // 1..9 jump to that slide; 0 jumps to 10.
        const n = key === '0' ? 9 : parseInt(key, 10) - 1;
        if (n < this._slides.length) this._go(n, 'keyboard');
      } else {
        handled = false;
      }
      if (handled) {
        e.preventDefault();
        this._flashOverlay();
      }
    }
    _go(i, reason = 'api') {
      if (!this._slides.length) return;
      const clamped = Math.max(0, Math.min(this._slides.length - 1, i));
      if (clamped === this._index) {
        this._flashOverlay();
        return;
      }
      this._index = clamped;
      this._applyIndex({
        showOverlay: true,
        broadcast: true,
        reason
      });
    }

    /** Step forward/back skipping any slide marked data-deck-skip. Falls
     *  back to _go's clamp-at-ends behaviour (flash overlay) when there's
     *  nothing further in that direction. */
    _advance(dir, reason) {
      if (!this._slides.length) return;
      let i = this._index + dir;
      while (i >= 0 && i < this._slides.length && this._slides[i].hasAttribute('data-deck-skip')) {
        i += dir;
      }
      if (i < 0 || i >= this._slides.length) {
        this._flashOverlay();
        return;
      }
      this._go(i, reason);
    }

    // ── Thumbnail rail ────────────────────────────────────────────────────
    //
    // Thumbs are keyed by slide element and reused across _renderRail()
    // calls, so a reorder/delete is an O(changed) DOM shuffle instead of an
    // O(N) teardown-and-re-clone. Each thumb starts as a lightweight shell
    // (num + empty frame); the clone is materialized lazily by an
    // IntersectionObserver when the frame scrolls into (or near) view, so
    // only visible-ish slides pay the clone + image-decode cost.

    _renderRail() {
      if (!this._rail || !this._railEnabled) {
        this._thumbs = [];
        return;
      }
      // FLIP: record each *materialized* thumb's top before the reconcile.
      // Off-screen (non-materialized) thumbs don't need the animation and
      // skipping their getBoundingClientRect saves a forced layout per
      // off-screen thumb on large decks.
      const prevTops = new Map();
      (this._thumbs || []).forEach(({
        thumb,
        slide,
        host
      }) => {
        if (host) prevTops.set(slide, thumb.getBoundingClientRect().top);
      });
      const st = this._rail.scrollTop;

      // Reconcile: reuse thumbs that already exist for a slide, create
      // shells for new slides, drop thumbs for removed slides.
      const bySlide = new Map();
      (this._thumbs || []).forEach(t => bySlide.set(t.slide, t));
      const next = [];
      this._slides.forEach(slide => {
        let t = bySlide.get(slide);
        if (t) bySlide.delete(slide);else t = this._makeThumb(slide);
        next.push(t);
      });
      // Orphans — slides removed since last render.
      bySlide.forEach(t => {
        if (this._railObserver) this._railObserver.unobserve(t.frame);
        t.thumb.remove();
      });
      // Put thumbs into document order to match _slides. insertBefore on
      // an already-correctly-placed node is a no-op, so this is cheap
      // when nothing moved.
      next.forEach((t, i) => {
        const want = t.thumb;
        const at = this._rail.children[i];
        if (at !== want) this._rail.insertBefore(want, at || null);
        t.i = i;
        t.num.textContent = String(i + 1);
        if (t.slide.hasAttribute('data-deck-skip')) t.thumb.setAttribute('data-skip', '');else t.thumb.removeAttribute('data-skip');
      });
      this._thumbs = next;
      this._rail.scrollTop = st;
      if (prevTops.size) {
        const moved = [];
        this._thumbs.forEach(({
          thumb,
          slide
        }) => {
          const old = prevTops.get(slide);
          if (old == null) return;
          const dy = old - thumb.getBoundingClientRect().top;
          if (Math.abs(dy) < 1) return;
          thumb.style.transition = 'none';
          thumb.style.transform = `translateY(${dy}px)`;
          moved.push(thumb);
        });
        if (moved.length) {
          // Commit the inverted positions before flipping the transition
          // on — otherwise the browser coalesces both style writes and
          // nothing animates.
          void this._rail.offsetHeight;
          moved.forEach(t => {
            t.style.transition = 'transform 180ms cubic-bezier(.2,.7,.3,1)';
            t.style.transform = '';
          });
          setTimeout(() => moved.forEach(t => {
            t.style.transition = '';
          }), 220);
        }
      }
      requestAnimationFrame(() => this._scaleThumbs());
      this._syncRail(false);
    }

    /** Create a lightweight thumb shell for one slide. The clone is
     *  materialized later by the IntersectionObserver. Event handlers
     *  look up the thumb's *current* index (via _thumbs.indexOf) so the
     *  same element can be reused across reorders. */
    _makeThumb(slide) {
      const thumb = document.createElement('div');
      thumb.className = 'thumb';
      thumb.tabIndex = 0;
      const num = document.createElement('div');
      num.className = 'num';
      const frame = document.createElement('div');
      frame.className = 'frame';
      thumb.append(num, frame);
      const entry = {
        thumb,
        num,
        frame,
        slide,
        clone: null,
        host: null,
        i: -1
      };
      // entry.i is refreshed on every _renderRail reconcile pass, so
      // handlers read the thumb's current position without an O(N) scan.
      const idx = () => entry.i;
      thumb.addEventListener('click', () => this._go(idx(), 'click'));
      // ↑/↓ step through the rail when a thumb has focus. _go clamps at the
      // ends and _applyIndex→_syncRail scrolls the new current thumb into
      // view; we move focus to it (preventScroll — _syncRail already
      // scrolled) so a held key walks the whole list. stopPropagation keeps
      // this out of the window-level _onKey nav handler.
      thumb.addEventListener('keydown', e => {
        if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
        if (e.metaKey || e.ctrlKey || e.altKey) return;
        e.preventDefault();
        e.stopPropagation();
        this._go(idx() + (e.key === 'ArrowDown' ? 1 : -1), 'keyboard');
        const cur = this._thumbs && this._thumbs[this._index];
        if (cur) cur.thumb.focus({
          preventScroll: true
        });
      });
      thumb.addEventListener('contextmenu', e => {
        e.preventDefault();
        this._openMenu(idx(), e.clientX, e.clientY);
      });
      thumb.draggable = true;
      thumb.addEventListener('dragstart', e => {
        this._dragFrom = idx();
        thumb.setAttribute('data-dragging', '');
        e.dataTransfer.effectAllowed = 'move';
        try {
          e.dataTransfer.setData('text/plain', String(this._dragFrom));
        } catch (err) {}
      });
      thumb.addEventListener('dragend', () => {
        thumb.removeAttribute('data-dragging');
        this._clearDrop();
        this._dragFrom = null;
      });
      thumb.addEventListener('dragover', e => {
        if (this._dragFrom == null) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        const r = thumb.getBoundingClientRect();
        this._setDrop(idx(), e.clientY < r.top + r.height / 2 ? 'before' : 'after');
      });
      thumb.addEventListener('drop', e => {
        if (this._dragFrom == null) return;
        e.preventDefault();
        const i = idx();
        const r = thumb.getBoundingClientRect();
        let to = e.clientY >= r.top + r.height / 2 ? i + 1 : i;
        if (this._dragFrom < to) to--;
        const from = this._dragFrom;
        this._clearDrop();
        this._dragFrom = null;
        if (to !== from) this._moveSlide(from, to);
      });
      if (this._railObserver) this._railObserver.observe(frame);
      frame.__deckThumb = entry;
      return entry;
    }

    /** Lazily build the clone for a thumb that has scrolled into view. */
    _materialize(entry) {
      if (entry.host) return;
      const dw = this.designWidth,
        dh = this.designHeight;
      let clone = entry.slide.cloneNode(true);
      clone.removeAttribute('id');
      clone.removeAttribute('data-deck-active');
      clone.querySelectorAll('[id]').forEach(el => el.removeAttribute('id'));
      // Neuter heavy media; replace <video> with its poster so the box
      // keeps a visual. <iframe>/<audio> become empty placeholders.
      clone.querySelectorAll('iframe, audio, object, embed').forEach(el => {
        el.removeAttribute('src');
        el.removeAttribute('srcdoc');
        el.removeAttribute('data');
        el.innerHTML = '';
      });
      clone.querySelectorAll('video').forEach(el => {
        if (!el.poster) {
          el.removeAttribute('src');
          el.innerHTML = '';
          return;
        }
        const img = document.createElement('img');
        img.src = el.poster;
        img.alt = '';
        img.style.cssText = el.style.cssText + ';object-fit:cover;width:100%;height:100%;';
        img.className = el.className;
        el.replaceWith(img);
      });
      // Images: defer decode and let the browser pick the smallest
      // srcset candidate for the ~140px thumb. Same-URL clones reuse the
      // slide's decoded bitmap (URL-keyed cache), so the remaining cost
      // is paint/composite — lazy+async keeps that off the main thread.
      clone.querySelectorAll('img').forEach(el => {
        el.loading = 'lazy';
        el.decoding = 'async';
        if (el.srcset) el.sizes = (this._railPx || 188) + 'px';
      });
      // Custom elements inside the slide would have their
      // connectedCallback fire when the clone is appended. Replace them
      // with inert boxes so a component-heavy deck doesn't run N copies
      // of each component's mount logic in the rail. Children are
      // preserved so layout-wrapper elements (<my-column><h2>…</h2>)
      // still show their authored content; the querySelectorAll NodeList
      // is static, so nested custom elements in the moved subtree are
      // still visited on later iterations.
      const neuter = el => {
        const box = document.createElement('div');
        box.style.cssText = (el.getAttribute('style') || '') + ';background:rgba(0,0,0,0.06);border:1px dashed rgba(0,0,0,0.15);';
        box.className = el.className;
        // Preserve theming/i18n hooks so [data-*] / :lang() / [dir]
        // descendant selectors still match the neutered root.
        for (const a of el.attributes) {
          const n = a.name;
          if (n.startsWith('data-') || n.startsWith('aria-') || n === 'lang' || n === 'dir' || n === 'role' || n === 'title') {
            box.setAttribute(n, a.value);
          }
        }
        while (el.firstChild) box.appendChild(el.firstChild);
        return box;
      };
      // querySelectorAll('*') returns descendants only — a custom-element
      // slide root (<my-slide>…</my-slide>) would slip through and upgrade
      // on append. Swap the root first.
      if (clone.tagName.includes('-')) clone = neuter(clone);
      clone.querySelectorAll('*').forEach(el => {
        if (el.tagName.includes('-')) el.replaceWith(neuter(el));
      });
      clone.style.cssText += ';position:absolute;top:0;left:0;transform-origin:0 0;' + 'pointer-events:none;width:' + dw + 'px;height:' + dh + 'px;' + 'box-sizing:border-box;overflow:hidden;visibility:visible;opacity:1;';
      const host = document.createElement('div');
      host.style.cssText = 'position:absolute;inset:0;';
      this._syncThumbHostAttrs(host);
      const sr = host.attachShadow({
        mode: 'open'
      });
      if (this._adoptedSheet) sr.adoptedStyleSheets = [this._adoptedSheet];else {
        const st = document.createElement('style');
        st.textContent = this._authorCss || '';
        sr.appendChild(st);
      }
      sr.appendChild(clone);
      entry.frame.appendChild(host);
      entry.host = host;
      entry.clone = clone;
      if (this._thumbScale) clone.style.transform = 'scale(' + this._thumbScale + ')';
      // Once materialized the IO callback is a no-op early-return —
      // unobserve so scroll doesn't keep firing it.
      if (this._railObserver) this._railObserver.unobserve(entry.frame);
    }

    /** Re-clone a single thumb (live-update path). No-op if the thumb
     *  hasn't been materialized yet — it'll pick up current content when
     *  it scrolls into view. */
    _refreshThumb(slide) {
      const entry = (this._thumbs || []).find(t => t.slide === slide);
      if (!entry || !entry.host) return;
      entry.host.remove();
      entry.host = entry.clone = null;
      this._materialize(entry);
    }
    _scaleThumbs() {
      if (!this._thumbs || !this._thumbs.length) return;
      // Every frame is the same width; if it reads 0 the rail is
      // display:none (noscale / no-rail / presenting / print) — leave the
      // clones as-is and re-run when the rail is revealed.
      const fw = this._thumbs[0].frame.offsetWidth;
      if (!fw) return;
      this._thumbScale = fw / this.designWidth;
      this._thumbs.forEach(({
        clone
      }) => {
        if (clone) clone.style.transform = 'scale(' + this._thumbScale + ')';
      });
    }
    _setDrop(i, where) {
      // dragover fires at pointer-event rate; touch only the previous
      // and new target rather than sweeping all N thumbs.
      const t = this._thumbs && this._thumbs[i];
      if (this._dropOn && this._dropOn !== t) {
        this._dropOn.thumb.removeAttribute('data-drop');
      }
      if (t) t.thumb.setAttribute('data-drop', where);
      this._dropOn = t || null;
    }
    _clearDrop() {
      if (this._dropOn) this._dropOn.thumb.removeAttribute('data-drop');
      this._dropOn = null;
    }
    _syncRail(follow) {
      if (!this._thumbs) return;
      this._thumbs.forEach(({
        thumb
      }, i) => {
        if (i === this._index) {
          thumb.setAttribute('data-current', '');
          if (follow && typeof thumb.scrollIntoView === 'function') {
            thumb.scrollIntoView({
              block: 'nearest'
            });
          }
        } else {
          thumb.removeAttribute('data-current');
        }
      });
    }
    _openMenu(i, x, y) {
      if (!this._menu) return;
      this._menuIndex = i;
      const slide = this._slides[i];
      const skip = slide && slide.hasAttribute('data-deck-skip');
      this._menu.querySelector('[data-act="skip"]').textContent = skip ? 'Unskip slide' : 'Skip slide';
      this._menu.querySelector('[data-act="up"]').disabled = i <= 0;
      this._menu.querySelector('[data-act="down"]').disabled = i >= this._slides.length - 1;
      this._menu.querySelector('[data-act="delete"]').disabled = this._slides.length <= 1;
      // Place, then clamp to viewport after it's measurable.
      this._menu.style.left = x + 'px';
      this._menu.style.top = y + 'px';
      this._menu.setAttribute('data-open', '');
      const r = this._menu.getBoundingClientRect();
      const nx = Math.min(x, window.innerWidth - r.width - 4);
      const ny = Math.min(y, window.innerHeight - r.height - 4);
      this._menu.style.left = Math.max(4, nx) + 'px';
      this._menu.style.top = Math.max(4, ny) + 'px';
    }
    _closeMenu() {
      if (this._menu) this._menu.removeAttribute('data-open');
      this._menuIndex = -1;
    }
    _openConfirm(i) {
      if (!this._confirm) return;
      this._confirmIndex = i;
      this._confirm.querySelector('.title').textContent = 'Delete slide ' + (i + 1) + '?';
      this._confirm.setAttribute('data-open', '');
      const btn = this._confirm.querySelector('.danger');
      if (btn && btn.focus) btn.focus();
    }
    _closeConfirm() {
      if (this._confirm) this._confirm.removeAttribute('data-open');
      this._confirmIndex = -1;
    }
    _emitDeckChange(detail) {
      this.dispatchEvent(new CustomEvent('deckchange', {
        detail,
        bubbles: true,
        composed: true
      }));
    }
    _deleteSlide(i) {
      const slide = this._slides[i];
      if (!slide || this._slides.length <= 1) return;
      const wasCurrent = i === this._index;
      if (i < this._index || wasCurrent && i === this._slides.length - 1) this._index--;
      this._squelchSlotChange = true;
      slide.remove();
      this._emitDeckChange({
        action: 'delete',
        from: i,
        slide
      });
      this._collectSlides();
      this._applyIndex({
        showOverlay: true,
        broadcast: true,
        reason: 'mutation'
      });
    }
    _toggleSkip(i) {
      const slide = this._slides[i];
      if (!slide) return;
      const on = !slide.hasAttribute('data-deck-skip');
      if (on) slide.setAttribute('data-deck-skip', '');else slide.removeAttribute('data-deck-skip');
      if (this._thumbs && this._thumbs[i]) {
        if (on) this._thumbs[i].thumb.setAttribute('data-skip', '');else this._thumbs[i].thumb.removeAttribute('data-skip');
      }
      this._markLastVisible();
      this._emitDeckChange({
        action: on ? 'skip' : 'unskip',
        from: i,
        slide
      });
      // Re-broadcast so the presenter popup's prev/next thumbnails re-pick
      // the nearest non-skipped slide without waiting for a nav event.
      try {
        window.postMessage({
          slideIndexChanged: this._index,
          deckTotal: this._slides.length,
          deckSkipped: this._skippedIndices()
        }, '*');
      } catch (e) {}
    }
    _skippedIndices() {
      const out = [];
      for (let i = 0; i < this._slides.length; i++) {
        if (this._slides[i].hasAttribute('data-deck-skip')) out.push(i);
      }
      return out;
    }
    _moveSlide(i, j) {
      if (j < 0 || j >= this._slides.length || j === i) return;
      const slide = this._slides[i];
      const ref = j < i ? this._slides[j] : this._slides[j].nextSibling;
      // Track the active slide across the reorder so the same content
      // stays on screen.
      const cur = this._index;
      if (cur === i) this._index = j;else if (i < cur && j >= cur) this._index = cur - 1;else if (i > cur && j <= cur) this._index = cur + 1;
      this._squelchSlotChange = true;
      this.insertBefore(slide, ref);
      this._emitDeckChange({
        action: 'move',
        from: i,
        to: j,
        slide
      });
      this._collectSlides();
      this._applyIndex({
        showOverlay: false,
        broadcast: true,
        reason: 'mutation'
      });
    }

    // Public API ------------------------------------------------------------

    /** Current slide index (0-based). */
    get index() {
      return this._index;
    }
    /** Total slide count. */
    get length() {
      return this._slides.length;
    }
    /** Programmatically navigate. */
    goTo(i) {
      this._go(i, 'api');
    }
    next() {
      this._advance(1, 'api');
    }
    prev() {
      this._advance(-1, 'api');
    }
    reset() {
      this._go(0, 'api');
    }
  }
  if (!customElements.get('deck-stage')) {
    customElements.define('deck-stage', DeckStage);
  }
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "slides/deck-stage.js", error: String((e && e.message) || e) }); }

// ui_kits/basetool/components.jsx
try { (() => {
/* Profit Basetool — shared chrome components. Depends on Icon (icons.jsx). */
const {
  useState,
  useEffect,
  useCallback
} = React;
const LOGO_MARK = "../../assets/krt.webp";
function HudBox({
  children,
  className,
  style
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "hud-box" + (className ? " " + className : ""),
    style: style
  }, children);
}
function Btn({
  variant,
  children,
  onClick,
  type,
  disabled,
  icon,
  style
}) {
  const cls = "btn" + (variant ? " btn-" + variant : "");
  return /*#__PURE__*/React.createElement("button", {
    className: cls,
    onClick: onClick,
    type: type || "button",
    disabled: disabled,
    style: style
  }, icon ? /*#__PURE__*/React.createElement(Icon, {
    name: icon
  }) : null, children);
}
function Badge({
  variant,
  children
}) {
  const cls = "squadron-badge" + (variant ? " squadron-badge-" + variant : "");
  return /*#__PURE__*/React.createElement("span", {
    className: cls
  }, children);
}
function StatusPill({
  status
}) {
  const map = {
    PLANNED: "status-planned",
    ACTIVE: "status-active",
    COMPLETED: "status-completed",
    CANCELLED: "status-cancelled",
    CANCELED: "status-cancelled"
  };
  return /*#__PURE__*/React.createElement("span", {
    className: "status-pill " + (map[status] || "status-completed")
  }, status);
}
function Header({
  onHamburger,
  admin,
  activeSquadron
}) {
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("header", {
    className: "app-header" + (admin ? " admin" : "")
  }, /*#__PURE__*/React.createElement("button", {
    className: "hamburger",
    onClick: onHamburger,
    "aria-label": "Menu"
  }, /*#__PURE__*/React.createElement("span", null), /*#__PURE__*/React.createElement("span", null), /*#__PURE__*/React.createElement("span", null)), /*#__PURE__*/React.createElement("a", {
    className: "brand",
    href: "#",
    onClick: e => e.preventDefault()
  }, /*#__PURE__*/React.createElement("img", {
    src: LOGO_MARK,
    alt: "DAS KARTELL"
  }), /*#__PURE__*/React.createElement("span", {
    className: "logo-text"
  }, "Profit Basetool")), admin ? /*#__PURE__*/React.createElement("span", {
    className: "admin-chip"
  }, "Admin") : null), /*#__PURE__*/React.createElement("div", {
    className: "ctx-chip"
  }, /*#__PURE__*/React.createElement("span", {
    className: "lbl"
  }, "Staffel"), /*#__PURE__*/React.createElement("span", {
    className: "val"
  }, activeSquadron || "IRI")));
}
const NAV_MAIN = [{
  id: "home",
  label: "Home"
}, {
  id: "missions",
  label: "Missions"
}, {
  id: "hangar",
  label: "Hangar"
}, {
  id: "materials",
  label: "Price Overview"
}];
const NAV_ADMIN = [{
  id: "members",
  label: "Member Management"
}, {
  id: "uex",
  label: "UEX Data"
}, {
  id: "settings",
  label: "System Settings"
}];
function Sidebar({
  open,
  onClose,
  current,
  onNavigate,
  onLogout
}) {
  const go = id => {
    onNavigate(id);
    onClose();
  };
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "sidebar" + (open ? " open" : "")
  }, /*#__PURE__*/React.createElement("div", {
    className: "sidebar-content"
  }, /*#__PURE__*/React.createElement("button", {
    className: "close-sidebar",
    onClick: onClose,
    "aria-label": "Close"
  }, "\xD7"), /*#__PURE__*/React.createElement("div", {
    className: "sidebar-links"
  }, NAV_MAIN.map(n => /*#__PURE__*/React.createElement("button", {
    key: n.id,
    className: "navlink" + (current === n.id ? " active" : ""),
    onClick: () => go(n.id)
  }, n.label)), /*#__PURE__*/React.createElement("div", {
    className: "sidebar-group"
  }, /*#__PURE__*/React.createElement("div", {
    className: "grp-title"
  }, "Administration"), /*#__PURE__*/React.createElement("div", {
    className: "sidebar-sublinks"
  }, NAV_ADMIN.map(n => /*#__PURE__*/React.createElement("button", {
    key: n.id,
    className: "navlink" + (current === n.id ? " active" : ""),
    onClick: () => go(n.id)
  }, n.label)))), /*#__PURE__*/React.createElement("div", {
    className: "sidebar-group"
  }, /*#__PURE__*/React.createElement("button", {
    className: "navlink",
    onClick: onLogout
  }, "Logout"))))), /*#__PURE__*/React.createElement("div", {
    className: "sidebar-overlay" + (open ? " visible" : ""),
    onClick: onClose
  }));
}
function Footer() {
  return /*#__PURE__*/React.createElement("footer", {
    className: "app-footer"
  }, /*#__PURE__*/React.createElement("div", {
    className: "links"
  }, /*#__PURE__*/React.createElement("a", {
    href: "#",
    onClick: e => e.preventDefault()
  }, "Impressum"), /*#__PURE__*/React.createElement("a", {
    href: "#",
    onClick: e => e.preventDefault()
  }, "Datenschutz"), /*#__PURE__*/React.createElement("a", {
    href: "#",
    onClick: e => e.preventDefault()
  }, "Nutzungsbedingungen")), /*#__PURE__*/React.createElement("span", {
    className: "ver"
  }, "DAS KARTELL \xB7 Profit Basetool \xB7 v1.4.3"));
}

/* Toast system ------------------------------------------------------------- */
function Toast({
  t
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "notification-toast toast-enter" + (t.error ? " error-toast" : "")
  }, /*#__PURE__*/React.createElement("h4", null, t.title), /*#__PURE__*/React.createElement("p", null, t.body));
}
function ToastViewport({
  toasts
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "toast-vp"
  }, toasts.map(t => /*#__PURE__*/React.createElement(Toast, {
    key: t.id,
    t: t
  })));
}
function useToasts() {
  const [toasts, setToasts] = useState([]);
  const push = useCallback((title, body, error) => {
    const id = Math.random().toString(36).slice(2);
    setToasts(p => [...p, {
      id,
      title,
      body,
      error
    }]);
    setTimeout(() => setToasts(p => p.filter(x => x.id !== id)), 3200);
  }, []);
  return {
    toasts,
    push
  };
}
Object.assign(window, {
  HudBox,
  Btn,
  Badge,
  StatusPill,
  Header,
  Sidebar,
  Footer,
  Toast,
  ToastViewport,
  useToasts,
  NAV_MAIN,
  NAV_ADMIN
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/basetool/components.jsx", error: String((e && e.message) || e) }); }

// ui_kits/basetool/data.jsx
try { (() => {
/* Profit Basetool — sample data for the kit (fictional, on-theme). */

const NEXT_MISSION = {
  id: 1,
  name: "Operation Tiefschlag",
  status: "PLANNED",
  description: "Coordinated quantanium run through Pyro — combat escort + refinery handover.",
  meetingTime: "29.05.2026, 18:45",
  startTime: "29.05.2026, 19:00",
  participants: "12/18"
};
const MISSIONS = [{
  id: 1,
  name: "Operation Tiefschlag",
  status: "PLANNED",
  start: "29.05.2026, 19:00",
  owner: "Valk",
  dept: "raumueberlegenheit",
  deptLabel: "Raumüberlegenheit",
  participants: "12/18"
}, {
  id: 2,
  name: "Aaron Halo Sweep",
  status: "ACTIVE",
  start: "28.05.2026, 20:30",
  owner: "Mara",
  dept: "profit",
  deptLabel: "Profit",
  participants: "6/8"
}, {
  id: 3,
  name: "Pyro Recon — Ghost Hollow",
  status: "PLANNED",
  start: "31.05.2026, 21:00",
  owner: "Hex",
  dept: "sub-radar",
  deptLabel: "Sub-Radar",
  participants: "3/6"
}, {
  id: 4,
  name: "Daymar Salvage Pull",
  status: "COMPLETED",
  start: "24.05.2026, 19:00",
  owner: "Dane",
  dept: "profit",
  deptLabel: "Profit",
  participants: "9/9"
}, {
  id: 5,
  name: "Refinery Convoy — ARC-L1",
  status: "COMPLETED",
  start: "22.05.2026, 18:00",
  owner: "Mara",
  dept: "search-rescue",
  deptLabel: "Search & Rescue",
  participants: "7/7"
}, {
  id: 6,
  name: "Checkmate Drill",
  status: "CANCELLED",
  start: "20.05.2026, 20:00",
  owner: "Valk",
  dept: "marinekorps",
  deptLabel: "Marinekorps",
  participants: "0/10"
}];
const SHIPS = [{
  id: 1,
  name: "Schwarze Witwe",
  type: "Constellation Andromeda",
  maker: "RSI",
  owner: "Valk",
  insurance: "LTI",
  location: "Area18 — ArcCorp",
  fitted: true
}, {
  id: 2,
  name: "Erntemaschine",
  type: "MOLE",
  maker: "Argo",
  owner: "Mara",
  insurance: "6 Months",
  location: "Lorville — Hurston",
  fitted: true
}, {
  id: 3,
  name: "Nadelöhr",
  type: "Vulture",
  maker: "Drake",
  owner: "Dane",
  insurance: "LTI",
  location: "GrimHEX — Yela",
  fitted: false
}, {
  id: 4,
  name: "Stiller Bote",
  type: "Hull C",
  maker: "MISC",
  owner: "Hex",
  insurance: "12 Months",
  location: "Everus Harbor",
  fitted: false
}, {
  id: 5,
  name: "Eisenfaust",
  type: "Hammerhead",
  maker: "Aegis",
  owner: "Valk",
  insurance: "LTI",
  location: "Seraphim Station",
  fitted: true
}, {
  id: 6,
  name: "Spürhund",
  type: "Terrapin",
  maker: "Anvil",
  owner: "Hex",
  insurance: "24 Months",
  location: "Area18 — ArcCorp",
  fitted: true
}];
const TERMINALS = [{
  name: "TDD Area18",
  planet: "ArcCorp"
}, {
  name: "Baijini Point",
  planet: "ArcCorp"
}, {
  name: "CRU-L1",
  planet: "Crusader"
}, {
  name: "Lorville TDD",
  planet: "Hurston"
}];
const MATERIALS = [{
  kind: "Metals",
  rows: [{
    name: "Laranite",
    prices: [3090, 2980, 3010, 2760]
  }, {
    name: "Agricium",
    prices: [2810, 2700, 2560, 2640]
  }, {
    name: "Titanium",
    prices: [null, 980, 920, 940]
  }]
}, {
  kind: "Gasses",
  rows: [{
    name: "Hydrogen",
    prices: [120, 110, null, 118]
  }, {
    name: "Chlorine",
    prices: [1580, 1490, 1520, null]
  }]
}, {
  kind: "High Value",
  rows: [{
    name: "Quantanium",
    prices: [29400, null, 28800, 27200],
    volatile: true
  }, {
    name: "Bexalite",
    prices: [42100, 41800, null, 40900]
  }]
}];
Object.assign(window, {
  NEXT_MISSION,
  MISSIONS,
  SHIPS,
  TERMINALS,
  MATERIALS
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/basetool/data.jsx", error: String((e && e.message) || e) }); }

// ui_kits/basetool/icons.jsx
try { (() => {
/* KRT icon sprite + <Icon> — in-house 24px line set, currentColor. */
const KRT_SPRITE = /*#__PURE__*/React.createElement("svg", {
  className: "krt-icon-sprite",
  "aria-hidden": "true",
  style: {
    position: 'absolute',
    width: 0,
    height: 0
  }
}, /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-close",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M6 6l12 12M18 6L6 18",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-chevron-down",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M6 9l6 6 6-6",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-chevron-right",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M9 6l6 6-6 6",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-warning",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M12 3l10 18H2L12 3z",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinejoin: "round",
  fill: "none"
}), /*#__PURE__*/React.createElement("path", {
  d: "M12 10v5M12 18v0.01",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-success",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M5 12l5 5 9-11",
  stroke: "currentColor",
  strokeWidth: "2.5",
  strokeLinecap: "round",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-info",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("circle", {
  cx: "12",
  cy: "12",
  r: "9",
  stroke: "currentColor",
  strokeWidth: "2",
  fill: "none"
}), /*#__PURE__*/React.createElement("path", {
  d: "M12 11v6M12 7v0.01",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-plus",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M12 5v14M5 12h14",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-search",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("circle", {
  cx: "11",
  cy: "11",
  r: "6",
  stroke: "currentColor",
  strokeWidth: "2",
  fill: "none"
}), /*#__PURE__*/React.createElement("path", {
  d: "M20 20l-4-4",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-filter",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M4 5h16l-6 8v6l-4-2v-4z",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-edit",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M14 4l6 6-11 11H3v-6z",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-trash",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M5 7h14M9 7V4h6v3M7 7l1 14h8l1-14",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-ship",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M3 12l18-6-7 6 7 6-18-6z",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinejoin: "round",
  fill: "none"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-mission",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("circle", {
  cx: "12",
  cy: "12",
  r: "9",
  stroke: "currentColor",
  strokeWidth: "2",
  fill: "none"
}), /*#__PURE__*/React.createElement("path", {
  d: "M12 3v4M12 17v4M3 12h4M17 12h4",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinecap: "round"
})), /*#__PURE__*/React.createElement("symbol", {
  id: "krt-icon-box",
  viewBox: "0 0 24 24"
}, /*#__PURE__*/React.createElement("path", {
  d: "M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10",
  stroke: "currentColor",
  strokeWidth: "2",
  strokeLinejoin: "round",
  fill: "none"
})));
function Icon({
  name,
  size,
  className
}) {
  const cls = "krt-icon" + (size === "lg" ? " krt-icon-lg" : size === "xl" ? " krt-icon-xl" : "") + (className ? " " + className : "");
  return /*#__PURE__*/React.createElement("svg", {
    className: cls,
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("use", {
    href: "#krt-icon-" + name
  }));
}
Object.assign(window, {
  KRT_SPRITE,
  Icon
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/basetool/icons.jsx", error: String((e && e.message) || e) }); }

// ui_kits/basetool/screen-mission-detail.jsx
try { (() => {
/* Profit Basetool — Mission detail screen. Demonstrates the action hierarchy.
   Depends on components.jsx, data.jsx, icons.jsx. */
const {
  useState: useMD
} = React;
function Panel({
  id,
  title,
  count,
  defaultOpen,
  children
}) {
  const [open, setOpen] = useMD(defaultOpen !== false);
  return /*#__PURE__*/React.createElement("div", {
    className: "mcol" + (open ? "" : " collapsed")
  }, /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "panel-header",
    "aria-expanded": open,
    onClick: () => setOpen(o => !o)
  }, /*#__PURE__*/React.createElement("h2", null, title, count != null ? /*#__PURE__*/React.createElement("span", {
    className: "panel-count"
  }, count) : null), /*#__PURE__*/React.createElement("span", {
    className: "toggle-icon",
    "aria-hidden": "true"
  })), /*#__PURE__*/React.createElement("div", {
    className: "col-body"
  }, children));
}
function MissionDetailScreen({
  push,
  onBack
}) {
  const [parts, setParts] = useMD([{
    id: 1,
    user: "cmdr.valk",
    org: "IRI",
    job: "Pilot",
    state: "in"
  }, {
    id: 2,
    user: "mara.k",
    org: "IRI",
    job: "Gunner",
    state: "out"
  }, {
    id: 3,
    user: "hex_07",
    org: null,
    job: "Medic",
    state: "pre"
  }]);
  const checkIn = id => {
    setParts(p => p.map(x => x.id === id ? {
      ...x,
      state: "in"
    } : x));
    push("Check-In", "Participant checked in.");
  };
  const del = id => {
    setParts(p => p.filter(x => x.id !== id));
    push("Action successful", "Participant removed.");
  };
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "greeting hud-box",
    style: {
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      gap: "1rem",
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "0.75rem",
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement("h1", {
    style: {
      margin: 0,
      fontSize: "1.6rem"
    }
  }, "Einsatz: Operation Tiefschlag"), /*#__PURE__*/React.createElement("span", {
    className: "squadron-badge"
  }, "IRI"), /*#__PURE__*/React.createElement("span", {
    className: "status-pill status-planned",
    style: {
      marginLeft: "0.25rem"
    }
  }, "PLANNED")), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost",
    onClick: onBack
  }, "Zur\xFCck")), /*#__PURE__*/React.createElement("div", {
    className: "mission-cols"
  }, /*#__PURE__*/React.createElement(Panel, {
    title: "Details"
  }, /*#__PURE__*/React.createElement("div", {
    className: "hud-box"
  }, /*#__PURE__*/React.createElement("div", {
    className: "form-group"
  }, /*#__PURE__*/React.createElement("label", {
    className: "form-label-sm"
  }, "Name"), /*#__PURE__*/React.createElement("input", {
    type: "text",
    defaultValue: "Operation Tiefschlag"
  })), /*#__PURE__*/React.createElement("div", {
    className: "form-group"
  }, /*#__PURE__*/React.createElement("label", {
    className: "form-label-sm"
  }, "Beschreibung"), /*#__PURE__*/React.createElement("textarea", {
    rows: "2",
    defaultValue: "Quantanium-Run durch Pyro \u2014 Kampfeskorte + Raffinerie-\xDCbergabe."
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: "1rem",
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "form-group",
    style: {
      flex: 1,
      minWidth: 140
    }
  }, /*#__PURE__*/React.createElement("label", {
    className: "form-label-sm"
  }, "Status"), /*#__PURE__*/React.createElement("select", {
    defaultValue: "PLANNED"
  }, /*#__PURE__*/React.createElement("option", null, "PLANNED"), /*#__PURE__*/React.createElement("option", null, "ACTIVE"), /*#__PURE__*/React.createElement("option", null, "COMPLETED"))), /*#__PURE__*/React.createElement("div", {
    className: "form-group",
    style: {
      flex: 1,
      minWidth: 140
    }
  }, /*#__PURE__*/React.createElement("label", {
    className: "form-label-sm"
  }, "Geplanter Start"), /*#__PURE__*/React.createElement("input", {
    type: "text",
    defaultValue: "29.05.2026  19:00"
  })))), /*#__PURE__*/React.createElement("div", {
    className: "hud-box detail-actions",
    style: {
      justifyContent: "flex-end"
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "btn btn-quiet-danger",
    style: {
      marginRight: "auto"
    },
    onClick: () => push("Bestätigung", "Wirklich löschen?", true)
  }, "Delete"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost",
    onClick: onBack
  }, "Zur\xFCck"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn--cta",
    onClick: () => push("Gespeichert", "Einsatz erfolgreich gespeichert.")
  }, "Speichern"))), /*#__PURE__*/React.createElement(Panel, {
    title: "Organisation"
  }, /*#__PURE__*/React.createElement("div", {
    className: "hud-box"
  }, /*#__PURE__*/React.createElement("div", {
    className: "kv-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "kv-key"
  }, "Einsatzleiter"), /*#__PURE__*/React.createElement("span", {
    className: "data-value"
  }, "cmdr.valk")), /*#__PURE__*/React.createElement("div", {
    className: "kv-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "kv-key"
  }, "Flottenfunk"), /*#__PURE__*/React.createElement("span", {
    className: "kv-right"
  }, /*#__PURE__*/React.createElement("span", {
    className: "data-value data-value--mono"
  }, "123.450"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost btn-sm2",
    onClick: () => push("Frequenz", "Bearbeiten…")
  }, "Edit"))), /*#__PURE__*/React.createElement("div", {
    className: "kv-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "kv-key"
  }, "Bodenfunk"), /*#__PURE__*/React.createElement("span", {
    className: "kv-right"
  }, /*#__PURE__*/React.createElement("span", {
    className: "data-value data-value--mono"
  }, "88.200"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost btn-sm2",
    onClick: () => push("Frequenz", "Bearbeiten…")
  }, "Edit"))))), /*#__PURE__*/React.createElement(Panel, {
    title: "Teilnehmer",
    count: parts.filter(p => p.state === "in").length + "/" + parts.length
  }, /*#__PURE__*/React.createElement("div", {
    className: "hud-box"
  }, /*#__PURE__*/React.createElement("div", {
    className: "panel-toolbar"
  }, /*#__PURE__*/React.createElement("button", {
    className: "btn btn--cta",
    onClick: () => push("Anmeldung", "Teilnehmer-Formular geöffnet.")
  }, "\uFF0B Anmelden")), /*#__PURE__*/React.createElement("table", {
    className: "mission-table",
    style: {
      marginTop: 0
    }
  }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", null, "Benutzer"), /*#__PURE__*/React.createElement("th", null, "Org"), /*#__PURE__*/React.createElement("th", null, "Aufgabe"), /*#__PURE__*/React.createElement("th", {
    style: {
      textAlign: "right"
    }
  }, "Aktion"))), /*#__PURE__*/React.createElement("tbody", null, parts.map(p => /*#__PURE__*/React.createElement("tr", {
    key: p.id
  }, /*#__PURE__*/React.createElement("td", {
    style: {
      fontWeight: 700
    }
  }, p.user), /*#__PURE__*/React.createElement("td", null, p.org ? /*#__PURE__*/React.createElement(Badge, null, p.org) : /*#__PURE__*/React.createElement(Badge, {
    variant: "muted"
  }, "\u2014")), /*#__PURE__*/React.createElement("td", null, p.job), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("div", {
    className: "act"
  }, p.state === "pre" ? /*#__PURE__*/React.createElement("button", {
    className: "btn btn-success btn-sm2",
    onClick: () => checkIn(p.id)
  }, "Check-In") : null, p.state === "in" ? /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost btn-sm2",
    onClick: () => push("Check-Out", "Ausgecheckt.")
  }, "Check-Out") : null, /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost btn-sm2",
    onClick: () => push("Bearbeiten", "Teilnehmer bearbeiten…")
  }, "Edit"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-quiet-danger btn-sm2",
    onClick: () => del(p.id)
  }, "Delete"))))))))), /*#__PURE__*/React.createElement(Panel, {
    title: "Einheiten"
  }, /*#__PURE__*/React.createElement("div", {
    className: "hud-box"
  }, /*#__PURE__*/React.createElement("div", {
    className: "panel-toolbar"
  }, /*#__PURE__*/React.createElement("button", {
    className: "btn btn--cta",
    onClick: () => push("Einheit", "Einheit hinzufügen…")
  }, "\uFF0B Hinzuf\xFCgen")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: "0.75rem"
    }
  }, [{
    n: "Schwarze Witwe",
    t: "Constellation Andromeda"
  }, {
    n: "Eisenfaust",
    t: "Hammerhead"
  }].map(u => /*#__PURE__*/React.createElement("div", {
    className: "unit-box",
    key: u.n
  }, /*#__PURE__*/React.createElement("div", {
    className: "unit-head"
  }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
    className: "unit-name"
  }, u.n), /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--color-gray-1)"
    }
  }, " \u2014 ", u.t)), /*#__PURE__*/React.createElement("div", {
    className: "detail-actions"
  }, /*#__PURE__*/React.createElement("button", {
    className: "btn btn-outline btn-sm2",
    onClick: () => push("Crew", "Crew zuweisen…")
  }, "Crew zuweisen"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-ghost btn-sm2",
    onClick: () => push("Bearbeiten", "Einheit bearbeiten…")
  }, "Edit"), /*#__PURE__*/React.createElement("button", {
    className: "btn btn-quiet-danger btn-sm2",
    onClick: () => push("Einheit", "Einheit gelöscht.")
  }, "Delete"))))))))));
}
Object.assign(window, {
  MissionDetailScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/basetool/screen-mission-detail.jsx", error: String((e && e.message) || e) }); }

// ui_kits/basetool/screens.jsx
try { (() => {
/* Profit Basetool — screens. Depends on components.jsx, data.jsx, icons.jsx. */
const {
  useState: useS
} = React;
const fmt = n => n == null ? null : n.toLocaleString("de-DE");

/* ---------------------------------------------------------------- LOGIN --- */
function LoginScreen({
  onLogin
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "login-stage"
  }, /*#__PURE__*/React.createElement(HudBox, {
    className: "login-card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "login-logo"
  }, /*#__PURE__*/React.createElement("img", {
    src: "../../assets/krt.webp",
    alt: "DAS KARTELL"
  }), /*#__PURE__*/React.createElement("div", {
    className: "name"
  }, "Profit Basetool"), /*#__PURE__*/React.createElement("div", {
    className: "sub"
  }, "IRIDIUM \xB7 Squadron Access")), /*#__PURE__*/React.createElement("form", {
    onSubmit: e => {
      e.preventDefault();
      onLogin();
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "field"
  }, /*#__PURE__*/React.createElement("label", null, "Username"), /*#__PURE__*/React.createElement("input", {
    type: "text",
    defaultValue: "cmdr.valk",
    autoComplete: "username"
  })), /*#__PURE__*/React.createElement("div", {
    className: "field"
  }, /*#__PURE__*/React.createElement("label", null, "Password"), /*#__PURE__*/React.createElement("input", {
    type: "password",
    defaultValue: "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
    autoComplete: "current-password"
  })), /*#__PURE__*/React.createElement(Btn, {
    variant: null,
    type: "submit"
  }, "Sign in via Keycloak")), /*#__PURE__*/React.createElement("div", {
    className: "login-foot"
  }, "Access is reserved for members & approved guests.", /*#__PURE__*/React.createElement("br", null), /*#__PURE__*/React.createElement("a", {
    href: "#",
    onClick: e => {
      e.preventDefault();
      onLogin();
    }
  }, "Create an order as guest \u2192"))));
}

/* ------------------------------------------------------------ DASHBOARD --- */
function Dashboard({
  onOpenMission
}) {
  const m = NEXT_MISSION;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "greeting"
  }, /*#__PURE__*/React.createElement("h1", null, "Welcome, Commander Valk"), /*#__PURE__*/React.createElement("p", null, "Central platform for mission planning and fleet management.")), /*#__PURE__*/React.createElement("div", {
    className: "dash-grid"
  }, /*#__PURE__*/React.createElement(HudBox, null, /*#__PURE__*/React.createElement("h2", {
    style: {
      marginTop: 0
    }
  }, "Next Mission"), /*#__PURE__*/React.createElement("div", {
    className: "info-grid"
  }, /*#__PURE__*/React.createElement("strong", null, "Name:"), /*#__PURE__*/React.createElement("span", null, m.name), /*#__PURE__*/React.createElement("strong", null, "Status:"), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(StatusPill, {
    status: m.status
  })), /*#__PURE__*/React.createElement("strong", null, "Description:"), /*#__PURE__*/React.createElement("span", null, m.description), /*#__PURE__*/React.createElement("strong", null, "Meeting (TS):"), /*#__PURE__*/React.createElement("span", null, m.meetingTime), /*#__PURE__*/React.createElement("strong", null, "Server Join:"), /*#__PURE__*/React.createElement("span", null, m.startTime), /*#__PURE__*/React.createElement("strong", null, "Participants:"), /*#__PURE__*/React.createElement("span", null, m.participants)), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "1.25rem"
    }
  }, /*#__PURE__*/React.createElement(Btn, {
    onClick: () => onOpenMission()
  }, "Open Mission"))), /*#__PURE__*/React.createElement(HudBox, null, /*#__PURE__*/React.createElement("h2", {
    style: {
      marginTop: 0
    }
  }, "Squadron Status"), /*#__PURE__*/React.createElement("div", {
    className: "stat-row"
  }, /*#__PURE__*/React.createElement("div", {
    className: "stat hud-box",
    style: {
      padding: "1rem"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "n"
  }, "18"), /*#__PURE__*/React.createElement("div", {
    className: "k"
  }, "Active Pilots")), /*#__PURE__*/React.createElement("div", {
    className: "stat hud-box",
    style: {
      padding: "1rem"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "n"
  }, "42"), /*#__PURE__*/React.createElement("div", {
    className: "k"
  }, "Ships in Hangar"))), /*#__PURE__*/React.createElement("div", {
    className: "stat-row"
  }, /*#__PURE__*/React.createElement("div", {
    className: "stat hud-box",
    style: {
      padding: "1rem"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "n"
  }, "7"), /*#__PURE__*/React.createElement("div", {
    className: "k"
  }, "Open Job Orders")), /*#__PURE__*/React.createElement("div", {
    className: "stat hud-box",
    style: {
      padding: "1rem"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "n",
    style: {
      color: "var(--color-success)"
    }
  }, "2.4M"), /*#__PURE__*/React.createElement("div", {
    className: "k"
  }, "Profit (30d, aUEC)"))))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "1rem"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "alert alert-warning",
    style: {
      marginBottom: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "warning"
  }), " \xA0UEX price feed last synced 3h ago \u2014 refinery margins may be stale.")));
}

/* ------------------------------------------------------------- MISSIONS --- */
function MissionsScreen({
  push,
  onOpen
}) {
  const [q, setQ] = useS("");
  const [showPast, setShowPast] = useS(true);
  let rows = MISSIONS.filter(m => m.name.toLowerCase().includes(q.toLowerCase()));
  if (!showPast) rows = rows.filter(m => m.status === "PLANNED" || m.status === "ACTIVE");
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "page-head"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "section-title",
    style: {
      border: "none",
      marginBottom: 0
    }
  }, "Mission Management"), /*#__PURE__*/React.createElement(Btn, {
    icon: "plus",
    onClick: () => push("Action", "New mission form opened.")
  }, "New Mission")), /*#__PURE__*/React.createElement("div", {
    className: "toolbar",
    style: {
      marginTop: "1rem"
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "search"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "search"
  }), /*#__PURE__*/React.createElement("input", {
    type: "search",
    placeholder: "Enter mission name\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  })), /*#__PURE__*/React.createElement("label", {
    style: {
      display: "flex",
      gap: "0.5rem",
      alignItems: "center",
      color: "var(--color-gray-1)",
      fontSize: "0.9rem"
    }
  }, /*#__PURE__*/React.createElement("input", {
    type: "checkbox",
    checked: showPast,
    onChange: e => setShowPast(e.target.checked)
  }), " Show past missions")), /*#__PURE__*/React.createElement("table", null, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", null, "Mission"), /*#__PURE__*/React.createElement("th", null, "Department"), /*#__PURE__*/React.createElement("th", null, "Status"), /*#__PURE__*/React.createElement("th", null, "Server Join"), /*#__PURE__*/React.createElement("th", null, "Owner"), /*#__PURE__*/React.createElement("th", null, "Part."))), /*#__PURE__*/React.createElement("tbody", null, rows.map(m => /*#__PURE__*/React.createElement("tr", {
    key: m.id,
    style: {
      cursor: onOpen ? "pointer" : "default"
    },
    onClick: () => onOpen && onOpen(m.id)
  }, /*#__PURE__*/React.createElement("td", null, m.name), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("span", {
    className: "dept-tag",
    style: {
      color: "var(--color-dept-" + m.dept + ")"
    }
  }, m.deptLabel)), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement(StatusPill, {
    status: m.status
  })), /*#__PURE__*/React.createElement("td", null, m.start), /*#__PURE__*/React.createElement("td", null, m.owner), /*#__PURE__*/React.createElement("td", {
    className: "num-cell"
  }, m.participants))), rows.length === 0 ? /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("td", {
    colSpan: "6",
    style: {
      textAlign: "center",
      fontStyle: "italic",
      color: "var(--color-gray-2)"
    }
  }, "No missions found.")) : null)));
}

/* --------------------------------------------------------------- HANGAR --- */
function HangarScreen({
  push
}) {
  const [ships, setShips] = useS(SHIPS);
  const toggle = id => setShips(p => p.map(s => s.id === id ? {
    ...s,
    fitted: !s.fitted
  } : s));
  const del = id => {
    setShips(p => p.filter(s => s.id !== id));
    push("Action successful", "Ship successfully deleted.");
  };
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "page-head"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "section-title",
    style: {
      border: "none",
      marginBottom: 0
    }
  }, "Hangar"), /*#__PURE__*/React.createElement(Btn, {
    icon: "plus",
    onClick: () => push("Action", "Add-ship form opened.")
  }, "Add Ship")), /*#__PURE__*/React.createElement("table", {
    style: {
      marginTop: "1rem"
    }
  }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", null, "Name"), /*#__PURE__*/React.createElement("th", null, "Ship Type"), /*#__PURE__*/React.createElement("th", null, "Maker"), /*#__PURE__*/React.createElement("th", null, "Owner"), /*#__PURE__*/React.createElement("th", null, "Insurance"), /*#__PURE__*/React.createElement("th", null, "Location"), /*#__PURE__*/React.createElement("th", null, "Fitted"), /*#__PURE__*/React.createElement("th", null, "Action"))), /*#__PURE__*/React.createElement("tbody", null, ships.map(s => /*#__PURE__*/React.createElement("tr", {
    key: s.id
  }, /*#__PURE__*/React.createElement("td", {
    style: {
      fontWeight: 700
    }
  }, s.name), /*#__PURE__*/React.createElement("td", null, s.type), /*#__PURE__*/React.createElement("td", null, s.maker), /*#__PURE__*/React.createElement("td", null, s.owner), /*#__PURE__*/React.createElement("td", null, s.insurance === "LTI" ? /*#__PURE__*/React.createElement(Badge, null, "LTI") : s.insurance), /*#__PURE__*/React.createElement("td", {
    style: {
      color: "var(--color-gray-1)"
    }
  }, s.location), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("span", {
    className: "dot",
    onClick: () => toggle(s.id),
    role: "button",
    title: "Toggle fitted",
    style: {
      cursor: "pointer",
      background: s.fitted ? "var(--color-success)" : "var(--color-gray-2)"
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: "0.8rem",
      color: s.fitted ? "var(--color-success)" : "var(--color-gray-2)"
    }
  }, s.fitted ? "Ready" : "Unfitted")), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("span", {
    className: "row-action"
  }, /*#__PURE__*/React.createElement("button", {
    className: "icon-btn",
    title: "Edit",
    onClick: () => push("Action", "Editing " + s.name + ".")
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "edit"
  })), /*#__PURE__*/React.createElement("button", {
    className: "icon-btn danger",
    title: "Delete",
    onClick: () => del(s.id)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "trash"
  })))))))));
}

/* ------------------------------------------------------------ MATERIALS --- */
function MaterialsScreen() {
  const [collapsed, setCollapsed] = useS({});
  const toggle = k => setCollapsed(p => ({
    ...p,
    [k]: !p[k]
  }));
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("h1", {
    className: "section-title"
  }, "Price Overview"), /*#__PURE__*/React.createElement(HudBox, null, /*#__PURE__*/React.createElement("p", {
    style: {
      color: "var(--color-gray-2)",
      fontSize: "0.85rem",
      marginTop: 0
    }
  }, "Sell prices in ", /*#__PURE__*/React.createElement("span", {
    className: "price-sell"
  }, "green (+)"), ", buy prices in ", /*#__PURE__*/React.createElement("span", {
    className: "price-buy"
  }, "red (\u2212)"), ", per terminal. Click a category to collapse."), /*#__PURE__*/React.createElement("div", {
    className: "hud-scroll scroll-x",
    style: {
      marginTop: "0.5rem"
    }
  }, /*#__PURE__*/React.createElement("table", {
    className: "matrix-table",
    style: {
      marginTop: 0
    }
  }, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", null, "Commodity"), TERMINALS.map(t => /*#__PURE__*/React.createElement("th", {
    key: t.name,
    className: "num-cell",
    title: t.planet
  }, t.name)))), /*#__PURE__*/React.createElement("tbody", null, MATERIALS.map(grp => /*#__PURE__*/React.createElement(React.Fragment, {
    key: grp.kind
  }, /*#__PURE__*/React.createElement("tr", {
    className: "kind-row",
    onClick: () => toggle(grp.kind)
  }, /*#__PURE__*/React.createElement("td", {
    colSpan: TERMINALS.length + 1
  }, collapsed[grp.kind] ? "+" : "−", " \xA0", grp.kind)), !collapsed[grp.kind] && grp.rows.map(r => /*#__PURE__*/React.createElement("tr", {
    key: r.name
  }, /*#__PURE__*/React.createElement("td", null, r.volatile ? /*#__PURE__*/React.createElement("span", {
    className: "text-warning",
    title: "Volatile",
    style: {
      marginRight: 6
    }
  }, "\u26A0") : null, r.name), r.prices.map((p, i) => /*#__PURE__*/React.createElement("td", {
    key: i,
    className: "num-cell"
  }, p == null ? /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--color-gray-3)"
    }
  }, "\u2013") : /*#__PURE__*/React.createElement("span", {
    className: "price-sell"
  }, "+", fmt(p)))))))))))));
}

/* --------------------------------------------------- ADMIN (light) -------- */
function MembersScreen() {
  const members = [{
    name: "Valk",
    roles: "Admin · Officer",
    sk: "—",
    status: "In Keycloak"
  }, {
    name: "Mara",
    roles: "Logistician",
    sk: "Vipers",
    status: "In Keycloak"
  }, {
    name: "Hex",
    roles: "Mission Manager",
    sk: "Vipers",
    status: "In Keycloak"
  }, {
    name: "Dane",
    roles: "Squadron Member",
    sk: "—",
    status: "In Keycloak"
  }];
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("h1", {
    className: "section-title"
  }, "Member Management"), /*#__PURE__*/React.createElement("p", {
    style: {
      color: "var(--color-gray-2)",
      fontSize: "0.9rem"
    }
  }, "Manage the members of your squadron."), /*#__PURE__*/React.createElement("table", null, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", null, "Name"), /*#__PURE__*/React.createElement("th", null, "Staffel Roles"), /*#__PURE__*/React.createElement("th", null, "SK"), /*#__PURE__*/React.createElement("th", null, "Status"), /*#__PURE__*/React.createElement("th", null, "Action"))), /*#__PURE__*/React.createElement("tbody", null, members.map(m => /*#__PURE__*/React.createElement("tr", {
    key: m.name
  }, /*#__PURE__*/React.createElement("td", {
    style: {
      fontWeight: 700
    }
  }, m.name), /*#__PURE__*/React.createElement("td", null, m.roles), /*#__PURE__*/React.createElement("td", null, m.sk === "—" ? /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--color-gray-3)"
    }
  }, "\u2014") : /*#__PURE__*/React.createElement(Badge, {
    variant: "sk"
  }, m.sk)), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--color-success)",
      fontSize: "0.85rem"
    }
  }, m.status)), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("button", {
    className: "icon-btn",
    title: "Edit"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "edit"
  }))))))));
}
function PlaceholderScreen({
  title,
  note
}) {
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("h1", {
    className: "section-title"
  }, title), /*#__PURE__*/React.createElement(HudBox, null, /*#__PURE__*/React.createElement("p", {
    style: {
      color: "var(--color-gray-2)",
      margin: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "info"
  }), " \xA0", note)));
}
Object.assign(window, {
  LoginScreen,
  Dashboard,
  MissionsScreen,
  HangarScreen,
  MaterialsScreen,
  MembersScreen,
  PlaceholderScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/basetool/screens.jsx", error: String((e && e.message) || e) }); }

})();
