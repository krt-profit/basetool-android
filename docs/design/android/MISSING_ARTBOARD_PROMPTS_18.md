# Design gaps, round 18 — „Mein Lager", the „gestohlen" marker and the RSI handle

**Date:** 2026-09-26 · **Previous:** `MISSING_ARTBOARD_PROMPTS_17.md`.

The owner reversed an old scope decision for the app on 2026-09-26 (main repo, epic
`krt-profit/basetool#2078`, sub-issue #2097): the app gets **„Mein Lager"** — the member's own
stock, personal and shared — and **full parity** for a new Lager property, the **„gestohlen"
marker**. Neither has an artboard. Round 5 recorded „Mein Lager" only as *Part D — recorded, not yet
detailed*, and chapter 09 artboard 6 still names its own scope as `LOCATION` only.

**Owner decision: the drawings come first** — nothing in this round is built before it is drawn.
Every item names the web surface that already implements (or will implement) the same function, so
the drawing can follow real behaviour rather than a guess. The copy quoted below is the web's.

| § | Kind | What we need |
| --- | --- | --- |
| **N1** | New screen | „Mein Lager" |
| **N2** | New field on a drawn sheet | Book in as **persönlich** |
| **N3** | New sheet | Umbuchen **persönlich ↔ gemeinsam**, single and for a selection |
| **N4** | New sheet | **Einheit ändern** of a personal entry, single and for a selection |
| **N5** | New states on drawn surfaces | The **„gestohlen"** marker everywhere stock appears |
| **N6** | New row on a drawn screen | **RSI-Handle** in the profile / KONTO |
| **N7** | New overlay | The terms re-consent when any call answers `TERMS_ACCEPTANCE_REQUIRED` |

---

## N1 · „Mein Lager"

**Web:** `/inventory/my` („Mein Lager"). **API:** `GET /api/v1/inventory/my-inventory/grouped`,
`…/stack/entries`, `…/entry-ids` (added to the app's vhost with this release).

A second Lager destination next to the shared Lager of chapter 09, showing **only the member's own
rows** — personal and shared — in the same three levels: material → stack → entries (loaded lazily
per stack), server-side paging, the **personal filter** („Nur persönliche" / „Nur nicht-persönliche",
mutually exclusive), the location filter and the collapsible filter row the Lager already has.

Please draw:

- where „Mein Lager" sits in the navigation relative to chapter 09's Lager (a destination of its
  own, or a mode of the Lager screen);
- the stack row: a personal stack carries its **owning unit** as a chip, or „Keine Einheit";
- the **selection mode** with its bottom bar, which carries more actions than chapter 09's
  (ausbuchen, umbuchen, Einheit ändern, als gestohlen markieren — N3–N5).

## N2 · Book in as „persönlich"

**Web:** the Einbuchen form's „Persönlich" checkbox. Today `BookInDraft` documents `personal` as
always `false`. A personal entry cannot carry an order or mission earmark, so ticking it hides the
allocation rows — the web does the same.

## N3 · Umbuchen persönlich ↔ gemeinsam

**Web:** the Umbuchen dialog's „Persönlich" mode; „Markierte umbuchen" with the modes
„Als persönlich umbuchen" and „Ins gemeinsame Lager umbuchen". Chapter 09 artboard 2 draws one
target only (round 5, A1).

- Single entry: amount, and — only when moving **into** the shared Lager — the **org-unit pool
  picker** across all four kinds (Staffel, SK, Bereich, OL), preset to the row's unit, never empty.
- Selection: whole rows only, no amounts; the result says how many moved and how many were skipped
  („lagen bereits am Ziel").
- The SCU merge opt-in „Mit vorhandenem Bestand zusammenführen".

## N4 · Einheit ändern (personal entries only)

**Web:** row action and bulk-bar button „Markierte: Einheit ändern" on „Mein Lager" (REQ-INV-052).
Stock synced from other tools arrives **without a unit**, so the member assigns one here.

- A picker with **„Keine Einheit"** plus the member's own memberships of all four kinds, preset to the
  row's unit. Unlike N3's picker it **has** the „Keine Einheit" option.
- The visibility notice, verbatim on the web: *„Mitglieder mit Bearbeitungsrecht in dieser Einheit
  können den Eintrag dann sehen und ändern. Ohne Einheit siehst nur du ihn."*
- The SCU merge opt-in, as in N3.
- Selection: *„Einheit bei {0} Einträgen geändert, {1} hatten sie bereits."*; a selection that
  contains a shared entry is refused as a whole.

## N5 · The „gestohlen" marker

**Web:** follows with main-repo #2096; the app ships it in the same release, and the server switches
marking on only after this app version is the minimum. Stolen cargo cannot be sold at ordinary
terminals, so it is a property of the **stack**: stolen and legitimate stock of the same material,
place and quality are two stacks and never merge.

Please draw:

- the **badge** on stack and entry rows (both Lager destinations), in the allocation picker, on the
  order's and the mission's allocated stock, and in the Materialbörse list, search and detail;
- the **filter** „Nur gestohlene" / „Ohne gestohlene";
- the **book-in checkbox** „Gestohlen";
- **mark / unmark** an entry, including a **partial amount** (the marked part becomes its own
  entry), and for a selection from the bottom bar;
- the refusal when a partial mark would take an entry below what it offers on the Materialbörse.

## N6 · RSI-Handle

**Web:** profile page (main-repo #2106). An **optional** field, the member's Star Citizen account
handle, used only so a connected tool can ask *„does this handle belong to you?"* — the server
answers match / mismatch / unknown and never returns the handle. Please draw the row and one
sentence that says what it is for, in KONTO next to „Auszahlungspräferenz" and „Blueprints mit Org
teilen" (round 17, D1), with its conflict state (a handle can belong to one profile only).

## N7 · Terms re-consent from anywhere

**Today:** `TermsGate` checks `/api/v1/terms/status` only when it first appears; a later
`TERMS_ACCEPTANCE_REQUIRED` from any call shows as an error on whatever screen made it. The terms
change once more with the exchange's go-live. Please draw how the app interrupts the current screen,
shows the updated terms, and returns to it after acceptance — or ratify reusing the first-run terms
screen as a modal overlay.
