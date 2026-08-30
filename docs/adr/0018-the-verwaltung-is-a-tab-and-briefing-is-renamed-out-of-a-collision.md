# ADR-0018 — the Verwaltung is an eighth tab, and the first tab is renamed out of a label collision

- **Status:** Accepted
- **Date:** 2026-08-29
- **Deciders:** @greluc
- **Related:** ADR-0011 (the app knows its permissions and refuses in place), design ch. 06,
  `docs/design/android/MISSING_ARTBOARD_PROMPTS_10.md` § 10a, REQ-APP-MIS-020, REQ-APP-MIS-022,
  REQ-APP-MIS-023

## Context

Round 10 asked the designer an open question (10a): the Einsatz's Verwaltung half is undrawn, and
the app needed to know whether it is **one sheet opened from the head** or **per-tab affordances**.
Pending an answer, the app shipped the first shape — a `KrtBottomSheet` opened by a full-width
ghost button wedged between the pinned head and the tab row.

Two things were wrong with that, and the owner named both on 2026-08-29 while looking at the screen.

**The ghost button was a fallback, not a design.** It sat where nothing else on the screen sits,
broke the head-then-tabs rhythm the chapter draws, and — being a bare outlined button with white
uppercase text — was the shape this app reaches for whenever an artboard is missing. The owner's
words: *"es kann nicht sein, dass sobald kein artboard da ist du immer und überall auf einfach
weiße Schrift auf schwarzem Grund zurückfällst"*. A missing drawing is not a licence to stop
designing.

**The screen carried two controls labelled „ÜBERSICHT" about 200 dp apart.** The shell's
Home destination (design ch. 03) and the Einsatz's own first tab (ch. 06) share the word. On the
web they never meet — the dashboard's entry is in a sidebar and the tab row is inside the page. On a
phone they are stacked on one screen, and the owner tapped the tab expecting the dashboard. The
navigation was working correctly; the labels were not.

## Decision

**One: the Verwaltung is the Einsatz's eighth tab, drawn only for a caller who may manage it.**

This answers 10a in favour of a place rather than a modal errand:

- Back keeps meaning „leave the Einsatz" instead of „close a sheet".
- A section can be saved and its answer re-read without the surface vanishing underneath it.
- Every manager affordance stays in one place instead of a pencil scattered across a pinned head,
  which is what 10d was asking us to design and which we no longer need to.
- It matches the web, which has had `mission.tab.admin=Verwaltung` all along — so the two products
  now name and place the same thing the same way.

~~The tab is **absent** for a caller who may not manage, not locked.~~ **Amended 2026-08-29 — see
below.** The tab is **locked and drawn**, like every other refused control in this app.

The gate is the server's own `canEdit`, relayed as `MissionDetail.canManage` — never a role read on
the device — and every write behind it is refused by the backend regardless. Drawing decides what is
offered, never what is allowed.

The form's lifecycle belongs to the tab: entering fills it from the Einsatz as last read, leaving
clears it. That is not tidiness. The form carries the three **independent** section version
counters it was filled with, and coming back to a stale set is exactly the 409 the per-section
locking exists to prevent.

**Two: the Einsatz's first tab is renamed „BRIEFING".**

A deliberate deviation from design ch. 06, which is why it is recorded here. The tab holds the KPI
band, „Einsatz auf einen Blick" and the description — it *is* the briefing, so the name is more
accurate as well as unambiguous. „Briefing" is identical in German and English, military-terse, and
already the word the domain uses.

Renaming the shell's destination instead was rejected: „Übersicht" is fixed by ch. 03, appears in
the top bar and the navigation on every form factor, and is the more established of the two words.
Changing the one that appears once beats changing the one that appears everywhere.

## Amendment — 2026-08-29: the tab is locked, not hidden

The paragraph above was wrong and shipped that way for a few hours. The design handoff's round-7
app audit (ch. 06 artboard 6, README correction 13a) names the exact line —
`MissionDetailScreen.kt:587`, `filter { canManage || it != ADMIN }` — and rejects the reasoning:

> Entschieden ist das Gegenteil (26.08.2026, Kap. 09 Artboards 11–14), und die App macht es in der
> Bank schon richtig.

The argument that a non-manager is „not one grant away" from running this Einsatz sounds careful and
is not: **this organisation grants roles by hand, and a function nobody sees is never requested.**
That is the whole reason `REQ-APP-AUTH-013` exists, and it does not carve out an exception for a
surface that happens to be large. The app's own Bank had it right; the Einsatz did not.

What the amended rule looks like:

- The eighth tab is **always drawn**. Without the right: label at 45 % alpha **plus** a lock glyph
  at full opacity — alpha alone is indistinguishable from a loading state.
- A tap does **not** open it. It raises the corner-bracket toast naming the **Missions-Manager**
  role, and the active tab stays where it was. Never `enabled = false`: what cannot be tapped
  cannot explain itself.
- The inner gates stay as a backstop, because `canManage` can change during a sitting.

The owner's original instruction — *„den nur berechtigte öffnen können"* — was compatible with this
all along: it says who may **open** it, not who may **see** it. The narrowing was mine.

## Consequences

- `MissionTab` gains `ADMIN`. ~~The tab row is built from a filtered list.~~ **Amended:** all eight
  are always drawn, so there is no index skew to guard against and the test that pinned it now
  pins the locked tab's refusal instead.
- `docs/design/android/MISSING_ARTBOARD_PROMPTS_10.md` § 10a and § 10d are **answered** and are
  marked so; the drawing is still wanted, and what it now corrects is a tab, not a sheet.
- The app and the web diverge on the first tab's label (`mission.tab.overview=Übersicht`). That is
  accepted: the collision only exists on a phone, and parity is the lowest of the three precedence
  levels in `CLAUDE.md`.
- The Verwaltung tab's composition remains **unratified** — it is built from drawn parts
  (`KrtTextField`, `KrtSectionTitle`, `KrtGhostButton`, `KrtCtaButton`, `KrtRadioRow`), not from an
  artboard.
