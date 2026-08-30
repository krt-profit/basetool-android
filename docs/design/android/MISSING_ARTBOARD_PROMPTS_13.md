# Design gaps, round 13 — what building chapter 18 turned up

**Date:** 2026-08-30 · **Previous:** `MISSING_ARTBOARD_PROMPTS_12.md`, answered in full by
`18 Abweichungs-Register.dc.html`.

Chapter 18 is built. All of it: the three web surfaces that had no artboard (E1–E3), the two missing
components (F1, F2), the six ratified compositions (E4–E9), the §A/§B corrections in their own
chapters, and the app-wide B9. **No drawn item and no ratified composition is open.**

This list is what appeared *while* building it — nine items, each one a place where the
specification is internally inconsistent, names a value the system does not carry, or asks for a
control that does not exist. Every one is stated with where it is, what the app does instead, and
what would settle it.

Six of them are **contradictions inside the spec** rather than gaps in it. Those are the expensive
kind: each reads as authoritative on its own page, so whichever page is read second looks like the
app is wrong.

---

## § A — The specification contradicts itself (6)

### A1 · A text field is 48 dp in §1 and 46 dp in §11

**Where:** `02 Components.dc.html`, §1 („Fill #1C1C1C · hairline border · radius 0 · **height 48
dp**") against §11 („Beide **46 dp**, tabular, Radius 0 — **dieselben Maße wie ein Textfeld**").

The two sentences cannot both be true, and §11's own justification is the tell: it wants the pair to
match the field so a form does not jump. That is only achieved at one of the two numbers.

**What the app does:** the date/time pair is drawn at the field height (48 dp), because matching the
field is the stated intent and §1 is the field's own canon chapter.

**What would settle it:** one number in §11, or a note that the pair is deliberately 2 dp shorter
and why a form may jump by that much.

---

### A2 · The touch floor is 44 dp in ch. 01 and 48 dp in ch. 02

**Where:** `01 Foundations.dc.html` §5, reissued 2026-08-30 („44 dp, Navigation und App-Bar 48 als
Ausnahme"), against `02 Components.dc.html` §1's framing line („All interactive heights ≥ 48 dp (web
system uses 44 px — Android rounds up)").

Chapter 02's line is the *old* rule, still stated as a rule. It is not merely stale prose: it cost a
real defect. The app's field frame derived its **height** from the touch-target token, so lowering
the floor to 44 shrank every input, button, select and segmented control from 48 dp to 44.

**What the app does:** three tokens now — a 48 dp control height, a 48 dp nav-icon floor, and a
44 dp minimum tap area for rows, accordion heads and menu entries, which is what the chapters draw
those at.

**What would settle it:** strike or rewrite ch. 02 §1's framing line, so the floor is stated once.

---

### A3 · The spacing scale is `4 · 8 · 12 · 16 · 24 · 32` in §5 and `6 · 10 · 16 · 20 · 24` in §8

**Where:** `01 Foundations.dc.html`. §5's scale strip is data-driven and lists `4 · 8 · 12 · 16 · 24
· 32`. §8's Compose line names `KrtSpacing (xs 6 · sm 10 · md 16 · lg 20 · xl 24)`.

The artboards are drawn against §5's strip, and §8's own margin table needs `4, 8, 10, 12, 14, 16,
20, 24` — which neither list covers.

**What the app does:** keeps the drawn scale and **adds** the three steps the margin table needed
(10, 14, 20). Renaming the five existing steps to §8's prose would move ~830 call sites onto values
no artboard uses and still leave four table values without a token.

**What would settle it:** correct §8's Compose line to the strip, or say which of the two the
artboards were drawn against.

---

### A4 · E4's section order names two sections the Verwaltung tab does not have

**Where:** ch. 18 §3 (E4): „Reihenfolge fest: **Kern · Zeitplan · Ziele · Ablauf**." Against
`06 Missionen.dc.html` artboard 7, unchanged: **Kern · Zeitplan · Sichtbarkeit · Personen**.

Ziele and Ablauf are the *Ablauf tab's* content and carry their own version counters; Sichtbarkeit
and Personen are Verwaltung sections. The four named in E4 are not four sections of one tab.

**What the app does:** keeps artboard 7's four, and applies everything else E4 decided (cards, 10 dp
apart, the spinner in the saving section's own header).

**What would settle it:** one sentence saying which four the rhythm applies to.

---

### A5 · E5 counts five row actions and the Ablauf row has six

**Where:** ch. 18 §3 (E5): „Fünf Aktionen passen nicht in eine 411-dp-Zeile" → two move buttons plus
`⋮` for Bearbeiten · Duplizieren · Löschen.

That accounts for five. `06 Missionen.dc.html` artboard 13 also draws the **tick** — „abhaken" — and
it is the action a checklist row exists for.

**What the app does:** tick, ↑, ↓, `⋮`. The tick stays visible because burying the row's primary
action in an overflow would be the mistake the whole correction was about.

**What would settle it:** confirm the tick stays outside the overflow, or say it belongs in it.

---

### A6 · §11's own „Vergangenheit" line and §1's error tint

**Where:** `02 Components.dc.html` §11 asks for a **yellow** line at a past timestamp („gelbe Zeile
am Feld"). §1 documents only the error line, in the danger text tint.

Minor, but the system has no *warning* inline field message; this is the first surface to need one.

**What the app does:** added `KrtFieldWarning` — the error line's shape in the warning tint, and the
KDoc says why the difference matters (nothing is blocked).

**What would settle it:** ratify it as a component in ch. 02, or point at an existing one.

---

## § B — Drawn, but no wire field carries it (2)

### B1 · „Im Lager frei" on the Materialbedarf rows

**Where:** ch. 18 §1 (E1). Every row is drawn as „620 SCU offen / **im Lager frei: 180 SCU**".

`MaterialDemandRowDto` carries `required`, `booked`, `claimed`, `outstanding` and the orders that
ask — and **no stock field at all**. Joining `/inventory/aggregated` would need an unbounded
page-walk *and* would still report the **total** rather than the free amount, because that read
carries no claims either.

**What the app does:** the row without that line. A number labelled „frei" that is not free is worse
than no number.

**What would settle it:** a backend ask — `freeStock` on the demand row — or striking the line.

---

### B2 · The import preview's `SUGGESTED` rows have no bucket and no picker

**Where:** ch. 18 §2 (E2) draws three figures: **Neu · Vorhanden · Unbekannt**.

`GET /personal-blueprints/import/preview` answers **five** statuses. `MATCHED` and
`MATCHED_BY_ALIAS` are ready to write, `ALREADY_OWNED` is „Vorhanden", `UNMATCHED` is „Unbekannt" —
and **`SUGGESTED`** means the server found fuzzy candidates and resolved none. Those rows carry
`productKey = null` and need a human pick. The artboard has no fourth figure and draws no picker.

**What the app does:** counts them with the unknown ones and adds a line saying that rows needing a
pick can be resolved in the web portal. The alternative — auto-accepting the top suggestion — would
write a choice nobody made; the other alternative, dropping them silently, loses importable rows.

**What would settle it:** a fourth figure with a per-row picker, or a ruling that the app skips them
and says so (which is what it does now, unratified).

---

## § C — Asks for a control the system does not have (1)

### C1 · No „duplizieren" glyph in the icon set

**Where:** ch. 18 §3 (E5): the row's overflow carries **Bearbeiten · Duplizieren · Löschen**.

The in-house set has 60 glyphs and none of them means „duplicate". The design system's own rule
forbids the alternative: „no icon libraries — the in-house stroke icon set only", and drawing one
here would be inventing a glyph in application code.

**What the app does:** uses `ic_krt_plus`, which is the closest true statement — duplicating appends
a row.

**What would settle it:** one glyph, or a ruling that plus is right.

---

## Not on this list, deliberately

- **The six backend asks (§G of round 12)** are unchanged and still the owner's call, G3 first.
- **`#464646`** — §11 names it for a neighbouring month's day, and the foundations palette carries
  four greys. It lives at its one call site with the citation. If a fifth grey is real, it belongs
  in ch. 01; if not, say which of the four it is. *(Half an item — it is listed here rather than
  above because one value at one call site is not a gap in the drawing.)*

---

## What is built and needs no drawing

For completeness, so this list is not read as „the app is behind":

| Chapter 18 item | State |
| --- | --- |
| E1 Materialbedarf · E2 Blueprint-Import · E3 Auswahlmodus | built |
| E4 Verwaltungs-Rhythmus · E5/E8 Zeilenaktionen · E6 Wahl-Chip · E7 Umbenennen + Crew-Rollen · E9 Tablet | built |
| F1 Datum-/Zeit-Wähler (+ C7 Datumsbereich-Filter) · F2 Einsatz starten am Badge | built |
| §A, §B corrections in ch. 06/09/10/11/12/16/17 · B9 `displayId` | built |
| Ch. 01 reissue: capped glow · struck content cap · 44 dp floor · 2 dp modal stroke | built |
