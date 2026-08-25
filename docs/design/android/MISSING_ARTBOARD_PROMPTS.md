# Prompts for the missing artboards

Three surfaces in the shipped app have no artboard in chapters 04–14, so there is nothing to build
them against and nothing to judge them by. Each prompt below is written to be pasted into Claude
Design **as is**; each produces one `.dc.html` chapter page in the same shape as the existing ones,
so the result drops into this folder and the handoff stays one bundle.

Found by the parity audit — [`docs/DESIGN_PARITY_AUDIT.md`](../../DESIGN_PARITY_AUDIT.md).

## Shared preamble

Every prompt below starts with this block. It is what keeps the new pages inside the same system as
the fourteen that exist, rather than a fifteenth style.

> You are extending an existing, delivered high-fidelity UI specification: the **Profit Basetool
> Android companion app** (squadron management for DAS KARTELL / Bereich Profit, Star Citizen).
> Kotlin + Jetpack Compose, Material 3, minSdk 30, **dark only** — there is no light theme and
> Material You dynamic colour is deliberately disabled.
>
> **Match the existing bundle exactly.** The chapters `00 Index` … `14 System States` in
> `docs/design/android/` are the reference for structure, markup idiom and fidelity: one page per
> screen area, a phone artboard at **412 × 915** plus a tablet artboard at **1280 × 800** where the
> surface has one, numbered section labels (`1 · …`, `2 · …`) in 12 px/700 Lato uppercase
> `#8A8A8A` with `.1em` letter-spacing, and handoff notes underneath. 1 CSS px = 1 dp.
>
> **The DAS KARTELL design system is binding** and mirrored in `_ds/`: dark surfaces, **radius 0**
> everywhere, Lato only (300/400/700/900), brand orange `#E77E23`, text `#FFFFFF` / `#D2D2D2`,
> muted `#8A8A8A` (never as body text on black — use it for labels), rules and disabled strokes
> `#282828`, success `#2EBC3D`, warning `#FFD23F`, danger `#F2564B`, info `#3B82F6`. HUD brackets on
> emphasis containers. **No emoji, no icon library** — only the in-house stroke set in
> `assets/krt-icons-mobile.js`, referenced as `<svg class="krt-icon"><use href="#krt-icon-NAME">`.
> **No native-styled dialogs**; overlays are the system's own modal and bottom-sheet components from
> `02 Components`.
>
> **Copy is German-first, military-terse, labels UPPERCASE, no emoji.** Fixed terms: „Einsätze"
> (never „Missionen"), „Bereich Profit", „Administration" (never „Führung"). Error states keep the
> English in-fiction canon (403 „Access Denied — …", 404 „Signal Lost — …", 500 „System
> Malfunction…", CTA „Zurück zur Basis").
>
> Draw every state the surface can be in — default, loading, empty, error, and any disabled or
> permission-restricted variant — as separate artboards, the way `02 Components` and `14 System
> States` do. Deliver one `.dc.html` page that loads `_ds/`, `assets/` and `support.js`
> relatively, exactly as the existing chapters do.

---

## 1 · Open-Source-Lizenzen

**Why it is missing:** chapter 13 draws the Einstellungen row that opens this screen, but never the
screen itself. It shipped built from the requirement text alone.

> …[shared preamble]…
>
> Add the **Open-Source-Lizenzen** screen as a new chapter page. It is a one-level push from
> Einstellungen → „Open-Source-Lizenzen" (chapter 13, section 2, group RECHTLICHES & DATEN), with a
> back arrow to Einstellungen.
>
> **What it shows.** The notice is *generated at build time* from the exact variant's dependency
> graph, so the list is long (well over a hundred artifacts), unfiltered and not searchable today —
> design for that reality rather than for a tidy dozen. Every artifact is listed with its **exact
> version**, because the terms apply to the code that shipped. Artifacts are **grouped by licence**;
> one offered under two licences appears under both. Each licence group has a heading with the
> licence's name (not its bare SPDX id) and a way to open the canonical licence text **in a browser**
> — the texts are deliberately not bundled, so this is an external link and must carry the
> external-link glyph rather than a chevron, the same distinction chapter 13 makes for the legal
> rows.
>
> **Artboards to draw:**
> 1. Phone 412 × 915 — the list: three or four licence groups with a realistic number of artifacts
>    under each, scrolled to show a group boundary. Include a long artifact coordinate
>    (`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`) so wrapping is decided here and not
>    by the implementer.
> 2. Phone — the top of the list, with whatever framing text the screen needs above the first group.
>    Decide whether it needs any; if it does not, say so in the handoff notes.
> 3. Tablet 1280 × 800 — the same list in the settings' 480 dp column convention, or a wider
>    treatment if you judge that better, with the reason in the notes.
>
> **Handoff notes must answer:** does the list get a sticky group header while scrolling; how a
> licence group with a single artifact looks; what happens on a device with no browser (the app can
> detect this); and whether the version is on the same line as the coordinate or beneath it.

---

## 2 · Auftrag — Notiz-Sheet und Status-Wechsel

**Why it is missing:** chapter 10 draws the queue, the four-tab detail, the Materialbörse and the
Gesuch sheet — but not the two write surfaces the detail screen actually opens.

> …[shared preamble]…
>
> Extend the **Aufträge** chapter with the two bottom sheets the order detail opens. Both are
> reached from chapter 10, artboard 2 (Auftrag — Detail), and both are ordinary member actions, not
> Logistiker ones.
>
> **A · Notiz zur Zuweisung.** A member who is on an order may leave, edit or clear a short note
> against **their own** assignment — never anyone else's. The sheet holds one multi-line text field,
> its label, a character allowance if you judge one is needed, and a save action. Clearing the note
> is the same act as saving an empty one; decide whether that needs its own control and say why in
> the notes. The note is optimistically locked: a concurrent edit answers HTTP 409 and the member
> must be told their copy is stale and re-shown the current one — draw that state.
>
> **B · Status wechseln.** The order's status moves between Offen, In Bearbeitung, Abgeschlossen and
> Abgelehnt. Not every transition is allowed and the allowed set depends on the caller's role, so the
> sheet must draw **both** a selectable and a disabled-with-reason option. Draw the confirmation for
> a terminal status (Abgeschlossen / Abgelehnt), which cannot be taken back from the app.
>
> **Artboards:**
> 1. Phone — note sheet, empty (first note).
> 2. Phone — note sheet, editing an existing note, with the save action enabled.
> 3. Phone — note sheet, optimistic-lock conflict.
> 4. Phone — status sheet with one option disabled and its reason visible.
> 5. Phone — terminal-status confirmation.
>
> **Handoff notes must answer:** whether the sheets dismiss on save or wait for the server; what the
> member sees while the write is in flight; and whether the status sheet shows the current status as
> selected or omits it from the list.

---

## 3 · Gate nicht erreichbar

**Why it is missing:** chapter 14 covers 403, 404, 500 and offline. This is none of those: the
approval gate — the check that decides whether a signed-in member may enter the app at all — could
not be read.

> …[shared preamble]…
>
> Add the **gate-unavailable** state to chapter 14 (System States). It sits between a successful
> login and the app: the member is authenticated, but the server that says whether their
> registration is approved did not answer.
>
> **What makes it its own state.** It is not 403 (the member may well be approved), not 404, not a
> plain offline banner (there is no cached content behind it — the app cannot show anything until
> the gate answers), and not the login screen (asking for a password would be wrong; they are signed
> in). The only honest actions are **retry** and **sign out**.
>
> It must not read as an accusation. The member has done nothing wrong and there is nothing they can
> fix; the copy says what happened and what will happen next. Keep the in-fiction register of the
> other error states without borrowing their status codes.
>
> **Artboards:**
> 1. Phone 412 × 915 — the state at rest, with both actions.
> 2. Phone — retry in flight.
> 3. Phone — after several failed retries, if you judge the state should change at all; if not, say
>    so in the notes and draw nothing.
> 4. Tablet 1280 × 800 — the same, in the centred column the other full-screen states use.
>
> **Handoff notes must answer:** whether the app retries on its own and on what rhythm (chapter 14's
> countdown pattern is available and may be reused); whether sign-out needs a confirmation here; and
> what the state does when the gate answers while the member is looking at it.
