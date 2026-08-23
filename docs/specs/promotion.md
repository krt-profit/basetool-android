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

**Recorded deviation, standing:** the design handoff has **no Beförderung chapter**. The screen is
built from the DAS KARTELL design system's own components and from what the web page shows. That is
derivation, not a chapter being followed, and it is written into the screen's own Javadoc as well as
here so it cannot be discovered later as a surprise. If a chapter is authored, this screen is
re-checked against it.

---

### REQ-APP-PROMO-001 — Me-scoped by construction, not by discipline

Both paths end in `/my` (`GET /api/v1/promotion/evaluations/my`,
`GET /api/v1/promotion/eligibility/my`) and the server resolves the member from the token. There is
**no id to pass**, so the repository has no way to ask about somebody else — the scoping is a
property of the endpoints rather than a rule the client has to keep.

**Acceptance**

- [x] Neither call takes a user id (`PromotionRepository`).
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

- [x] A failing standings read leaves the assessments on screen, and vice versa
  (`PromotionViewModelTest`).
- [x] `loadOnce` reads once however often it is called; a refresh reads again
  (`PromotionViewModelTest`).

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

- [x] Category order follows the server (`PromotionViewModelTest`).
- [x] A step without rules survives the mapping and is marked as such (`PromotionViewModelTest`).
- [x] A row missing its topic or its level is dropped rather than rendered with a gap
  (`PromotionRepository`).

**Code:** `core/data/…/PromotionRepository.kt`, `app/…/promotion/`

## Known gaps

- **No device walk yet.** The slice is covered by JVM tests and the lint gate; it has not been
  walked on the emulator, which is where every previous slice found the defects the suite could not
  (#66).
- **No live-sync room**, deliberately: nobody else changes your evaluation while you are looking at
  it, so a room would be a subscription with no publisher.
- The rank numbers are shown as numbers. The server does not serve rank *names* on these endpoints,
  and inventing a mapping here is the same staleness trap as translating the levels.
