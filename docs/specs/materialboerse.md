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

### REQ-APP-MARKET-009 — ~~Two things the app reads but does not create~~ — both landed 2026-08-30

> **Superseded.** This requirement recorded the item half and the own-row editor as deliberate
> gaps: the item writes address a product by a `productKey` the app had no picker for, and the
> editor was a third sheet with its own optimistic-lock path. `GET /blueprints/products/search`
> supplies the picker, so both were built as design ch. 17 artboards 1–3 draw them. See
> `REQ-APP-MARKET-012` and `REQ-APP-MARKET-013`. Kept rather than deleted, dated, so the earlier
> decision and its reversal both stay readable.

---

### REQ-APP-MARKET-012 — Material and item are one sheet with a switch

Design ch. 17's first decision, and the Auftrag form's precedent since round 5: **one form with a
switch at the very top**, not two entries in the menu. The switch changes only the middle fields —
material: a catalogue material and an amount in SCU; item: a product and a count in pieces. The
frame (remark, CTA, the two buttons) is the same on both halves.

The item half addresses its product by the **product key** `GET /api/v1/blueprints/products/search`
hands out, and picks it the way the material field is picked: an id, not a typed name, and the key
is dropped the moment the text changes (`REQ-APP-MARKET-007`).

**An item offer binds no stock row.** Items live in the personal inventory and
`POST /material-exchange/item-offers` takes a product key, a quantity and a remark — no
`inventoryItemId`. So the offer sheet's stock list is a material-half control and is simply not
drawn on the item half.

**Switching keeps what was typed on the other half.** Members switch back and forth to read the two
field sets, and losing a remark to that would be a punishment for looking.

> [!warning] Two drawn fields that no DTO carries — the chapter flagged the first itself
> Artboard 1 draws „Zustand" (Neu / Gebraucht) and artboard 2 draws „Bis wann"; the chapter's own
> provenance note says both labels are a proposal, not derived copy, and must be reconciled with
> the web before implementation. They cannot be: `MaterialExchangeItemReleaseRequest` carries
> `productKey`, `quantity` and `remark`, and `MaterialItemRequestCreateRequest` carries those plus
> **`minQuality`** — a field artboard 2 does not draw. Artboard 1's „Blueprint (Variante)" has no
> wire field either: a product key already identifies the product, and no write carries a variant.
> The app therefore ships the three fields that exist, uses `minQuality` on the request, and leaves
> the other three out. All on the design gap list.

**Acceptance**

- [x] The item half posts a product key and never touches the material endpoints
  (`MaterialBoardViewModelTest`).
- [x] An item offer names no stock row (`MaterialBoardViewModelTest`).
- [x] A typed product that was never picked cannot be sent (`MaterialBoardViewModelTest`).
- [ ] Observed on a device.

**Code:** `MaterialBoardRepository.searchProducts` / `.createItemOffer` / `.createItemRequest`,
`BoardKind`, `MaterialBoardScreen.ProductField`

---

### REQ-APP-MARKET-013 — The own row opens one sheet, and the withdrawal lives inside it

Design ch. 17 artboard 3: „Bisher gab es nur «Zurückziehen» und kein Update — beides liegt jetzt
hier." So the member's own row carries **Bearbeiten**, which opens a sheet holding both the edit and
the withdrawal, with the interested members listed beside them — because withdrawing is what affects
those members, which is the artboard's own reason for putting them there.

**What may change.** A request's amount, minimum quality and remark; an offer's amount and remark.
The material or item behind a row never changes — that would make it a different entry keeping the
same id and the same interested members — and is drawn fixed with that reason.

> [!warning] The artboard's „only the remark" is not the web rule
> Artboard 3 states that only the remark may be edited on an offer and attributes that to the web.
> The web's own modal (`fragments/materialboerse-modal.html`) offers the amount with an „Alles"
> shortcut and a „darf den Lagerbestand nicht überschreiten" bound, and
> `MaterialExchangeOfferUpdateRequest` **requires** `offeredAmount`. The app therefore edits the
> amount too. The stock bound stays the server's to enforce: a board row carries no stock figure to
> check against, so an over-large amount comes back as its refusal.

**Withdrawing asks only when somebody is waiting.** With interested members it confirms and names
them; with nobody waiting it withdraws straight away.

> [!warning] No undo, against the artboard
> Artboard 3 asks for a five-second undo toast. Withdrawal is `POST …/deactivate` and **no endpoint
> reactivates a row**: an undo would have to post a new entry — a different id, a new timestamp, and
> none of the interested members. Flagged rather than faked; the confirmation carries the
> „cannot be undone" sentence instead.

**Acceptance**

- [x] Editing an own offer sends the amount and the remark (`MaterialBoardViewModelTest`).
- [x] Withdrawing asks only when somebody is waiting (`MaterialBoardViewModelTest`).
- [x] A row whose version the server never sent is refused rather than written blind
  (`MaterialBoardRepository.updateOffer`).
- [ ] Observed on a device.

**Code:** `BoardSheet.EditEntry`, `MaterialBoardViewModel.onEditEntry` / `.onEntrySubmitted` /
`.onWithdrawRequested`, `MaterialBoardScreen.EditEntrySheet`

### REQ-APP-MARKET-010 — The quantity is the row's figure, not a third of a grey run

Artboard 3 puts the amount beside the material name, right-aligned, with the figure carrying the
weight and the unit small and muted: „**240** SCU". The app joined amount, quality and pledge count
into one line at one weight — so the quantity, which is what a board is scanned for, read exactly
like the two facts beside it.

The figure and the unit are two nodes now: „240" is what is compared across rows, „SCU" only says
what kind of 240 it is. What is left of the old line — quality and the pledge count — drops to the
muted style beneath the owner, and renders nothing at all when neither applies.

**Acceptance**

- [x] The figure, the unit and the remaining facts are separate nodes (`MaterialBoardScreenTest`).
- [x] An item row still counts pieces, never SCU (same, `REQ-APP-MARKET-003`).
- [x] Verified on the phone class against the rendered artboard.

**Code:** `exchange/MaterialBoardScreen.kt` (`BoardAmount`, `detailLine`)

### REQ-APP-MARKET-011 — On a tablet the board is two columns of cards

From the **expanded** breakpoint the board lays its cards out in a two-column grid; below it, one
column with the hairline between the rows.

A board card is self-contained — the material, the member, the figures, the org chip and the action
— so two fit side by side. Stretched to a tablet's full width a single column packed all of that
into the left quarter and pinned one chip at the right edge, leaving roughly three quarters of every
card empty (design round 8 §5, ruled by the owner 2026-08-28 from the three options that round
offered).

**Two columns, not a width-driven count.** Three at 1280 dp would put a card below the width its own
header row of name, figure and chips needs, and no drawing settles a wider count yet (round 9 §5).

**The breakpoint is expanded, not `contentGutter`'s medium.** Two columns need real width: at 700 dp
each card would be about 340 dp, narrower than the phone's own card. This is a different question
from the gutter's and gets a different answer — `isWideWindow()`.

**No hairline between the cards in the grid.** A rule under one card of a pair reads as a divider
across the row it is not in; the card border is the separation a grid needs. The single column keeps
its hairline, which is what chapter 10 draws.

**The footer spans both columns**, because „mehr laden" and „Ende der Liste" are statements about the
whole board rather than about one column of it. A grid that lost the span would strand the load-more
action in the left column, and the board would stop at page one with nothing saying so.

`rememberRootGridState()` is a second helper beside `rememberRootListState()` because `LazyGridState`
and `LazyListState` share no supertype carrying `animateScrollToItem`. Swapping the state with the
layout also drops the scroll position, which is right: item 40 of a one-column list is not item 40
of a two-column grid.

**Acceptance**

- [x] Every card survives the grid, including the half-empty last row of an odd count
      (`MaterialBoardWideTest`).
- [x] „Ende der Liste" and the load-more action are drawn under both columns
      (`MaterialBoardWideTest`).
- [x] Verified on the tablet: five offers as 2 + 2 + 1, the footer spanning underneath.

**Code:** `exchange/MaterialBoardScreen.kt` (`BoardGrid`, `BoardColumn`, `BoardFooter`),
`ui/RootScrollSignals.kt` (`rememberRootGridState`)

