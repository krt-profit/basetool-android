# Materialbörse — offers and requests between members

> **Doc type:** Living spec · **Area:** `REQ-APP-MARKET-*` · **Design:** `docs/design/android/10 Auftraege.dc.html` §3–4
> **Server contract:** main repo `REQ-API-009`, `REQ-MARKET-001…020`, `docs/specs/materialboerse.md`
> **Related:** [`api-contract.md`](api-contract.md), [`sync.md`](sync.md), [`inventory.md`](inventory.md)

The org-wide board: who has material, who needs it, and who can help. Phase 4, slice 2
(krt-profit/basetool-android#64).

---

### REQ-APP-MARKET-001 — The board conveys interest and nothing else

Chapter 10 states it as copy on the screen: *„Übergabe & Ort bleiben off-tool und privat — die Börse
vermittelt nur Interesse."* That is a rule, not a caption, and the app honours it in three places:

- **No place reaches the board.** The only location name in the whole slice is on the „Angebot
  erstellen" sheet, where it distinguishes two stacks of the same material in the caller's *own*
  Lager. It is never sent and never rendered on a row.
- **No handover state exists.** There is no "delivered", no confirmation and no closing action —
  what happens after two members find each other is theirs.
- **The line itself is rendered**, so a member can read the rule off the screen instead of
  inferring it from an absence.

**Acceptance**

- [x] The privacy line is on the screen (`MaterialBoardScreenTest`).
- [x] No board row renders a location.

**Code:** `MaterialBoardScreen`

---

### REQ-APP-MARKET-002 — One model for two halves, and their writes stay apart

Offers and requests are different server families with different DTOs, and they agree on everything
a member sees. They map onto **one** `BoardEntry`, because keeping them apart would duplicate the
row, the toggle and the withdraw action for a difference nobody looks at.

The **paths** do not merge. `interestPath` and `deactivatePath` branch on the side, because one
shared builder is exactly where a request would end up deactivating an offer that happens to carry
the same id.

**Acceptance**

- [x] A request's withdraw addresses `/api/v1/material-requests/{id}/deactivate`
  (`MaterialBoardRepositoryTest`).
- [x] Both halves render through the same row composable.

**Code:** `MaterialBoardRepository`, `BoardEntry`

---

### REQ-APP-MARKET-003 — An item is not a material, and the unit travels with the row

An **item** row names itself in `itemName` / `itemQuantity`; a **material** row in `material.name` /
`amount`. Reading only the material fields renders every item row blank with no amount.

**The unit is never hardcoded.** An item counts pieces by definition, a material says which it uses
(`quantityType`), and „6 SCU" printed over six shields is a quantity a member would act on — in a
handover the tool never sees and cannot correct.

On a **request**, the quality field is a *minimum*, not an offered grade. The two are opposite
claims about the same number, so the row says „Min. Q 3" rather than „Q 3".

**Two display rules a device walk added.** The row printed the wire's ISO timestamp
(`2026-08-24T09:29:53.187358Z`) and the wire's trailing zero (`120.0 SCU`). Both now go through the
same helpers the rest of the app uses — a relative span in the member's zone, and `formatAmount` —
because a figure a member reads twice in two places has to read the same both times.

**Acceptance**

- [x] An item row reads its own fields and counts pieces (`MaterialBoardRepositoryTest`,
  `MaterialBoardScreenTest`).
- [x] A request row labels its quality as a minimum.
- [x] No row renders an ISO timestamp or a trailing `.0`, both asserted as absences.

**Code:** `MaterialBoardRepository`, `MaterialBoardScreen`

---

### REQ-APP-MARKET-004 — „Ich kann liefern" is a toggle, and never on your own row

Withdrawable at any time. The caller's own rows get **Zurückziehen** in the quiet-danger style
instead, per chapter 10 — the server refuses an interest signal on one's own entry, and offering the
control would be an invitation to a `400`.

A withdrawn row **leaves the list** rather than being replaced with a deactivated one: it is no
longer on the board, and leaving it there would invite the member to withdraw it again.

**Acceptance**

- [x] The caller's own row offers Zurückziehen and no toggle (`MaterialBoardScreenTest`).
- [x] A toggle on one's own row sends nothing (`MaterialBoardViewModelTest`).
- [x] A withdrawn row leaves the list.

**Code:** `MaterialBoardViewModel`, `MaterialBoardScreen`

---

### REQ-APP-MARKET-005 — Who pledged is the server's answer, never the app's inference

`interestedHandles` is populated **only for the owner** (server `REQ-MARKET-006`). The app renders
what it gets and derives nothing: a row that carries no list shows **no heading at all**, because an
empty „Zusagen" section would say nobody answered, which is a different claim from "you may not see
who did".

**Acceptance**

- [x] A row the caller does not own carries `null` handles (`MaterialBoardRepositoryTest`).
- [x] No supporter heading is rendered for such a row (`MaterialBoardScreenTest`).

**Code:** `MaterialBoardRepository`, `MaterialBoardScreen`

---

### REQ-APP-MARKET-006 — A write updates its row; only a create re-reads

Every toggle and withdraw answers with the **updated row**, so the screen replaces one entry in
place. Re-reading the page instead would scroll the member back to the top on every tap, on a board
whose entire interaction is tapping rows — and the count, the caller's flag and the version move
together in that one answer.

A **create** is the exception: the endpoints answer `202` with no body, so the app does not have the
row it just made. Inventing one locally would show a member an entry the server might have shaped
differently, so the board is re-read once, keeping what is on screen while it runs.

**Acceptance**

- [x] A toggle causes no second board read (`MaterialBoardViewModelTest`).
- [x] The other rows are untouched.

**Code:** `MaterialBoardViewModel`

---

### REQ-APP-MARKET-007 — A picked material is an id, and editing the field drops it

„Gesuch erstellen" addresses its material by **id**. A member who picks „Quantainium" and then edits
the text is no longer describing what they picked, so the id is cleared with the first keystroke and
the publish action goes back to disabled.

This is the failure the web app's comboboxes have hit before: a field that looks filled and submits
nothing. Here it would be worse than nothing — a stale id posts a request for a material the member
did not choose.

The **offer** sheet has no catalogue at all. It picks from the caller's own releasable stock, which
is what chapter 10's „Bestands-Vorschlag aus Mein Inventar" means, and the amount is pre-filled from
the chosen stack.

**Acceptance**

- [x] Editing the field after picking clears the id and disables publish
  (`MaterialBoardViewModelTest`).
- [x] Publish with no picked material sends nothing.
- [x] A failed create keeps the sheet open holding what was typed.

**Code:** `MaterialBoardViewModel`, `NewRequestSheet`, `NewOfferSheet`

---

### REQ-APP-MARKET-008 — Both sections are announced, and only the visible one is reloaded

One room, `materialboard`, with sections `board` and `requests`. A write announces **both**: a member
switching segments has to see a change made on the other half, and the frame carries no data, so
naming one section would be cheaper by nothing and wrong half the time.

Receiving is the opposite. Only the section matching the **visible** segment triggers a reload —
re-reading the half nobody is looking at would cost a request for a list that is not on screen.

**Acceptance**

- [x] A write publishes `board` and `requests` together (`MaterialBoardViewModelTest`).
- [x] A frame for the hidden half causes no read.

**Code:** `MaterialBoardViewModel`

---

### REQ-APP-MARKET-009 — Two things the app reads but does not create

**Item offers and item requests.** `POST /item-offers` and `/material-requests/item` address an item
by a `productKey` from the P4K catalogue, which the app has no picker for. Both halves render item
rows the web created; creating one is a phase-5 question together with the catalogue browse it
needs.

**Bearbeiten on an own row.** Chapter 10 pairs Zurückziehen with Bearbeiten. The edit endpoints
carry a `version` and change an amount or a remark — a small editor, but a third sheet with its own
optimistic-lock path, and the withdraw-and-repost it substitutes for costs a member two taps. It is
recorded here rather than left as a silent gap in the chapter.

**Acceptance**

- [x] No create path in this slice sends a `productKey`.
- [x] No Bearbeiten control is rendered, so nothing offers what is not built.

**Code:** `MaterialBoardRepository`, `MaterialBoardScreen`
