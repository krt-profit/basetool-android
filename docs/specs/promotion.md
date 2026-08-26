# Beförderung — the member's own record

> **Area:** `REQ-APP-PROMO-*` · **Server:** main repo `docs/specs/promotion-evaluation-matrix.md`
> (that spec covers the officers' matrix; this one covers only what a member sees of themselves) ·
> **Phase:** 4 · **Issue:** #66

## Context & goal

A member wants two things from Beförderung: what they have been assessed on, and how far they are
from the next rank. Both are me-scoped reads. The officers' matrix (`/promotion/manage`,
`/evaluations/all`, `/evaluations/members`) stays web-only with the rest of the admin area
(plan Q7), so this screen is read-only by nature — nobody assesses themselves, and there is no
write, no version echo and nothing to disable when the device is offline.

> **Status: removed from the app** (owner decision, 2026-08-25 — `24fa14d`). The screen, its view
> model, its test, its destination and its strings are **gone**, not hidden. What remains is
> `core:data`'s `PromotionRepository`, deliberately: it is the data layer's contract with the
> backend, it costs nothing to keep correct, and re-adding the screen should not start from an empty
> file. The deleted screen is one command away —
> `git show 096cbdb^:app/src/main/kotlin/de/greluc/krt/profit/basetool/android/promotion/PromotionScreen.kt`.

**Why it was removed:** it had been built (`41fc01c`, 2026-08-23) but its route was never wired, so
the „Mehr" entry fell through to the placeholder and read „Dieser Bereich wird gerade gebaut." A menu
entry whose only content is an apology is worse than no entry, and unreachable code is worse than
absent code: nothing exercises it and nothing keeps it honest against the API.

**The design chapter it was once said to lack does exist.** Chapter 13, artboard 1
(„Beförderung — Meine Bewertungen") has been in the handoff since the first import on 2026-08-17 and
carries an owner correction dated 2026-08-25 reducing the matrix to *Thema · Bewertung · Ziel*. The
removed screen had already been rebuilt against it. `#66` and
[ADR-0009](../adr/0009-tablet-settings-ships-without-its-befoerderung-column.md) both still say the
handoff has no chapter for this area; that premise is stale, and the *current* decision does not
rest on it.

**What the requirements below describe** is therefore the screen as it was built and as chapter 13
draws it — a specification waiting for its slice, not a description of running code. Acceptance
items that were ticked against `PromotionViewModelTest` are marked open again: that file went with
the screen.

---

### REQ-APP-PROMO-001 — Me-scoped by construction, not by discipline

Both paths end in `/my` (`GET /api/v1/promotion/evaluations/my`,
`GET /api/v1/promotion/eligibility/my`) and the server resolves the member from the token. There is
**no id to pass**, so the repository has no way to ask about somebody else — the scoping is a
property of the endpoints rather than a rule the client has to keep.

**Acceptance**

- [x] Neither call takes a user id — the paths are constants ending in `/my`
  (`PromotionRepository`). Read from the code: there is no `PromotionRepositoryTest`.
- [x] No `/promotion/manage`, `/evaluations/all` or `/evaluations/members` path appears in the app.

---

### REQ-APP-PROMO-002 — Two reads, two failures, one screen

The assessments and the rank standings are separate endpoints behind separate service logic, and
**one going down must not blank the other** — the rule the Übersicht and the Hangar already follow.
A member whose standings fail can still read what they have been assessed on, which is the half they
came for; each half carries its own message.

They run **concurrently**. They are unrelated, and making the member wait for the sum of two round
trips would be a cost with nothing bought.

**Acceptance**

- [ ] A failing standings read leaves the assessments on screen, and vice versa
  **Open** — covered by the removed `PromotionViewModelTest`.
- [ ] `loadOnce` reads once however often it is called; a refresh reads again
  **Open** — covered by the removed `PromotionViewModelTest`.

---

### REQ-APP-PROMO-003 — The screen states what the organisation decided, and nothing more

Three judgements, each of which the easy implementation would get wrong:

**Categories keep the order the officers configured**, and the topics inside them keep server order.
Sorting alphabetically would present their matrix in an arrangement nobody chose. A row whose
category the server left blank lands in one unnamed group rather than being dropped — the assessment
is real either way.

**Levels are shown as the server spells them.** They are configured per organisation, so an app-side
translation table would go stale the moment a level is renamed, and it would do so silently.

**A rank step with no configured rules is kept and says so.** Dropping it leaves the member with no
row and no explanation; rendering an empty requirement list reads as *"not met"* — a verdict the
organisation never made. `hasConfiguredRules` is the field that tells the two apart and the screen
has its own sentence for it.

**Acceptance**

- [ ] Category order follows the server. **Open** — covered by `PromotionViewModelTest`, removed with the screen.
- [ ] A step without rules survives the mapping and is marked as such. **Open** — same test.
- [x] A row missing its topic or its level is dropped rather than rendered with a gap —
  `mapNotNull` in `PromotionRepository`. **Implemented, not covered:** this module has no
  `PromotionRepositoryTest`, so the tick is read off the code rather than pinned by a test.

**Code:** `core/data/…/PromotionRepository.kt`. The `app/…/promotion/` package no longer
exists — see the status note above for the command that recovers it.

## Known gaps

- **The screen is not reachable**, by the decision above. Nothing about the data layer waits on it:
  the repository is wired into the object graph and its requirements hold as written.
- **No device walk yet.** The slice is covered by JVM tests and the lint gate; it has not been
  walked on the emulator, which is where every previous slice found the defects the suite could not
  (#66).
- **No live-sync room**, deliberately: nobody else changes your evaluation while you are looking at
  it, so a room would be a subscription with no publisher.
- The rank numbers are shown as numbers. The server does not serve rank *names* on these endpoints,
  and inventing a mapping here is the same staleness trap as translating the levels.
