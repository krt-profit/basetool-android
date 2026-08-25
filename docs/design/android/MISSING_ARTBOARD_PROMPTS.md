# Prompts for the missing artboards — all three fulfilled

**Status: closed on 2026-08-25.** The design bundle delivered that day contains all three surfaces
these prompts asked for. The prompts are kept as the record of what was requested, so the delivered
artboards can be read against the brief that produced them.

| requested | delivered |
| :-- | :-- |
| Open-Source-Lizenzen | **chapter 15**, five artboards (list start, sticky group change, no-browser fallback, loading, error) + tablet |
| Auftrag — Notiz- und Status-Sheet | **chapter 10, artboards 5–9** (empty note, editing with counter, 409 conflict, status with a disabled option and its reason, terminal confirmation) |
| Gate nicht erreichbar | **chapter 14**, „Gate-Ausfall nach Login" with a live countdown |

Every question the prompts asked the designer to answer in the handoff notes was answered. Two are
worth carrying into the implementation because they are decisions, not drawings:

- The licences list gets a **sticky group header** and an end-of-report line naming the generator
  and its version; the summary line states artefact count, licence count, version and flavour.
- The no-browser case **copies the URL** and says so in a toast, rather than disabling the row. That
  closes the open acceptance item on `REQ-APP-SET-005`.

What the app still has to build against them is tracked in
[`docs/DESIGN_PARITY_AUDIT.md`](../../DESIGN_PARITY_AUDIT.md).

---

*The original prompts follow, unchanged.*

## Shared preamble

> You are extending an existing, delivered high-fidelity UI specification: the **Profit Basetool
> Android companion app**. Kotlin + Jetpack Compose, Material 3, minSdk 30, **dark only**.
> Match the existing bundle exactly: one page per screen area, a phone artboard at **412 × 915**
> plus a tablet artboard at **1280 × 800**, numbered section labels, handoff notes underneath.
> 1 CSS px = 1 dp. The DAS KARTELL design system is binding and mirrored in `_ds/`: radius 0, Lato
> only, brand orange `#E77E23`, no emoji, no icon library beyond the in-house stroke set, no
> native-styled dialogs. Copy is German-first, military-terse, labels UPPERCASE. Draw every state
> the surface can be in — default, loading, empty, error, disabled — as separate artboards.

## 1 · Open-Source-Lizenzen

> Add the **Open-Source-Lizenzen** screen: a one-level push from Einstellungen. The notice is
> generated at build time from the exact variant's dependency graph, so the list is long, unfiltered
> and not searchable; artifacts are grouped by licence, each listed with its exact version, and each
> licence group offers its canonical text **in a browser** (external-link glyph, not a chevron).
> Draw the list start, a group boundary mid-scroll, and the tablet column. Answer in the notes:
> sticky group header or not; a group with a single artifact; a device with no browser; version on
> the coordinate's line or beneath it.

## 2 · Auftrag — Notiz-Sheet und Status-Wechsel

> Extend **Aufträge** with the two bottom sheets the order detail opens. **A · Notiz zur Zuweisung:**
> a member edits or clears a note against their own assignment only; clearing equals saving empty;
> the note is optimistically locked, so a concurrent edit answers 409 and the member must be told
> their copy is stale and shown the current one. **B · Status wechseln:** not every transition is
> allowed and the allowed set depends on role, so draw both a selectable and a
> disabled-with-reason option, plus the confirmation for a terminal status.

## 3 · Gate nicht erreichbar

> Add the **gate-unavailable** state to chapter 14: authenticated, but the server that says whether
> the registration is approved did not answer. Not 403, not 404, not a plain offline banner, not the
> login screen. The only honest actions are **retry** and **sign out**, and the copy must not read as
> an accusation. Answer in the notes: does the app retry on its own and on what rhythm; does sign-out
> need a confirmation here; what happens when the gate answers while the member is looking at it.
