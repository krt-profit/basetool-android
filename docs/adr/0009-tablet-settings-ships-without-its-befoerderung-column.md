# ADR-0009 — The tablet's Einstellungen ships without its Beförderung column

> **Status:** Accepted · **Date:** 2026-08-24 · **Deciders:** @greluc
> **Design:** `docs/design/android/13 Einstellungen.dc.html` ("Tablet 1280×800 — Einstellungen
> 2-spaltig + Beförderung rechts")
> **Related:** `krt-profit/basetool-android#66`, [`0006-minsdk-30-and-no-second-path-for-the-app-lock.md`](0006-minsdk-30-and-no-second-path-for-the-app-lock.md)

## Context

The binding design handoff gives every chapter a tablet layout, and chapter 13 gives Einstellungen
a two-column one: the settings groups on the left, Beförderung on the right. During the 2026-08-24
reconciliation of the app against the handoff, this was one of twelve deviations found, and the
only one that could not simply be built.

Beförderung is not missing. Its repository, view model and screen exist and are covered by tests,
and the paths it needs are on the frozen external contract. What is missing is a **design chapter**
for it: the handoff has none, and a layout derived from the other chapters would be a layout nobody
approved. The owner therefore withheld the screen (#66), and its destination renders a placeholder.

That turns chapter 13's second column into a placeholder pane. On a phone the placeholder is one
destination a member reaches only by choosing it; in the two-column layout it would sit beside the
settings on every tablet, permanently, as half the screen.

## Decision

**Build the left column only.** Einstellungen keeps its single 480 dp capped, centred column on
every window size, and the tablet does not get chapter 13's split until #66 lands.

This is recorded as a deviation rather than left implicit, because the project's rule is that a
departure from the design system needs an ADR — and because the previous in-code justification for
the same gap had gone stale without anyone noticing. It claimed Beförderung "reads evaluations from
the backend and lands with the live-parity phase", which stopped being true when the screen shipped
behind #66. A comment that explains a gap with a reason that has expired is worse than no comment:
it answers the question a reader would otherwise ask.

## Consequences

- A tablet shows the settings in a 480 dp column with empty space beside it. That is the cost, and
  it is smaller than the alternative: the cap exists because settings rows stretched to 1280 dp put
  a 44 dp toggle a hand's width from its own label.
- Restoring the pairing is a one-place change. The column keeps its width and gains a sibling; no
  settings row moves.
- **This ADR expires with #66.** When Beförderung gets a design chapter and its screen is
  un-gated, chapter 13's split must be built in the same unit of work and this ADR superseded —
  otherwise the gap outlives its reason a second time.
- Every other tablet layout in the handoff *was* built in the same pass (Übersicht's two-column
  grid, the Terms split, the inbox's 720 dp column, the Hangar table, list-detail). This is the
  single exception, which is what makes it worth naming.
