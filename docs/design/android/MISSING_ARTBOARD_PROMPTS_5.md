# Round 5 — the web-parity surface the bundle does not draw yet

> Written 2026-08-27, at the start of the **web-parity programme**: every function the web frontend
> offers a non-admin member — reading and writing, every sub-function — has to exist in the app,
> scoped by the same roles and permissions, with no delta. Two areas are excluded by the owner:
> **Beförderung** and the **admin area**.
> Rounds 1–4: [`MISSING_ARTBOARD_PROMPTS.md`](MISSING_ARTBOARD_PROMPTS.md),
> [`_2`](MISSING_ARTBOARD_PROMPTS_2.md), [`_3`](MISSING_ARTBOARD_PROMPTS_3.md),
> [`_4`](MISSING_ARTBOARD_PROMPTS_4.md).

---

This round is different from the previous four. Those were corrections to screens you had drawn.
This one is a **request for screens**, because a function-by-function comparison against the web
frontend turned up whole surfaces the bundle has never covered — and, in three places, artboards
that draw a simpler action than the API actually performs.

Everything below was read out of the running code: the backend's DTOs and `@PreAuthorize`
annotations, the frontend's page controllers and their JavaScript. Where I quote a rule, it is the
rule the server enforces, not a preference.

I have rendered and looked at every artboard I refer to. Where I say "the artboard draws X", X is
what is on screen, not what the caption says.

---

## Part A — three artboards that draw less than the endpoint does

These are the urgent ones, because an app built faithfully to the current drawing would be
**wrong**, not merely incomplete.

### A1. Chapter 09, artboard 2 — „UMBUCHEN" draws one target; the API has three

The artboard's Umbuchen state shows a single field, **„UMBUCHEN NACH"**, whose value reads
„Persönlich → Geteilt (Bereich Profit)". That is the *personal ↔ shared* rebook, which is a real
operation — but it is a **different endpoint** from the Umbuchen the org-wide Lager performs, and
the drawing currently conflates them.

What the server actually offers, from `InventoryItemBookOutDto`:

| Field | Applies to | Rule |
| --- | --- | --- |
| `targetUserId` | `TRANSFER` | the receiving member |
| `targetLocationId` | `TRANSFER` | the destination place |
| `targetOwningOrgUnitId` | `TRANSFER` | which org-unit **pool** the moved row lands in |
| `mergeStock` | `TRANSFER` | SCU only — a `PIECE` material always merges |
| `jobOrderReductions` / `missionReductions` | all three types | see [A3](#a3-chapter-09-artboard-2--the-herkunft-planner-is-not-drawn-at-all) |

and the personal ↔ shared move is a **separate** call, `POST /inventory/{id}/personal-rebook`,
reachable only from „Mein Lager" (`/inventory/my`) because it is owner-scoped.

Three further rules the drawing has nowhere to put:

- **A transfer must change either the owner or the place.** Submitting one that changes neither is
  rejected with 400 („Transfer must change either the user or the location"). The app already
  guards this inline; the artboard should show where the message sits.
- **The org-unit picker offers the *destination* user's memberships across all four kinds** —
  Staffel, SK, Bereich, OL. The web fetches `/users/{id}/memberships?allKinds=true` for exactly
  that reason. It is **preset** to the row's current owning org unit, so submitting without
  touching it leaves the stock where it is, and it is **hidden** only when the target has no
  membership at all.
- **`mergeStock` is offered only for an SCU material.** For `PIECE` the server merges regardless,
  so a checkbox there would be a control with no effect.

**What I need:** artboard 2's Umbuchen state redrawn with the three target fields and the
conditional merge toggle, plus a separate state (or artboard) for the personal ↔ shared rebook that
says which page it belongs to. If you would rather split „Umbuchen" into two named actions in the
UI, say so — that is a product decision and I will not guess it.

### A2. Chapter 11, artboard 2 — „IN LAGER BUCHEN" is drawn as one tap; it is a form

The artboard shows a single orange CTA and an annotation reading:

> „In Lager buchen" legt pro Material einen Lager-Eintrag mit Qualität an (Quelle: Order #7841) und
> markiert die Order als eingelagert. Zuordnung zu Aufträgen/Einsätzen **danach im Lager** (Kap. 09).

The second sentence is not what the endpoint does. `RefineryOrderStoreItemDto` is submitted **once
per material** and carries:

| Field | Required | Note |
| --- | --- | --- |
| `materialId` | yes | fixed, from the yield |
| `locationId` | yes | where it lands — **not** implied by the order |
| `quality` | yes | 0–1000 |
| `amount` | yes | **overrides** the calculated output; the member corrects the real yield here |
| `userId` | no | the receiving member, if not the order owner |
| `jobOrderId` | no | **the allocation, made here** — not afterwards in the Lager |
| `note` | no | ≤ 1000 chars, lands on the inventory row |
| `owningOrgUnitId` | conditional | **required (400 otherwise) when the receiver holds more than one membership**; pre-filled with the order's own org unit |
| `personal` | no | private stock instead of shared |

Two hard rules: **`personal` and `jobOrderId` are mutually exclusive** (400), and a personal row
never receives the refinery order's automatic mission earmark.

**What I need:** a store dialog artboard — one editable row per yielded material, the conditional
org-unit picker, the personal toggle and its mutual exclusion with the order picker, and the
corrected annotation. A one-tap CTA cannot carry a corrected amount, and the corrected amount is
the whole reason a member opens this screen.

### A3. Chapter 09, artboard 2 — the „Herkunft" planner is not drawn at all

Every book-out and every transfer may carry `jobOrderReductions` and `missionReductions`
(REQ-INV-027, „Variante C"). The web renders a whole section for it, shared by Ausbuchen and
Umbuchen on both Lager pages (`inventory-herkunft.js`).

The reason it exists: an entry's stock is tagged **twice, independently** — once by job order, once
by mission. When X leaves the entry, X has to be sourced per dimension. The member says how much
comes from each tag; the remainder comes from that dimension's not-yet-assigned rest.

The contract, which the drawing has to make satisfiable:

| Rule | On violation |
| --- | --- |
| Per dimension, the sum of the tag reductions ≤ X | 400 |
| Each reduction fits its own slice | 400 |
| The rest absorbs what the tags did not cover (`X − sum ≤ rest`) | 422 |
| An omitted or all-zero plan means „take it from the rest first" | the default |

One shape has no choice in it: **exactly one tag and no rest**. Every unit leaving must come from
that tag, so the web fills the field from the amount and **locks** it rather than demanding it.

And on a `SELL`, the mission reductions additionally **split the proceeds**: mission *j* is credited
`sellAmount × amount_j / amount` for missions the seller takes part in, the rest staying personal.
The web shows this as a live hint under the sale amount.

**What I need:** a Herkunft section for the phone. The web does it with a table of tag rows plus a
„rest" chip per dimension; on 411 dp that will not survive as a table. This is the one place in this
round where I would rather have your form than propose mine — the constraint is that a member must
be able to see, at a glance, whether their plan is valid and where the remainder is coming from.

---

## Part B — the bank surface for staff, which the bundle does not cover

Chapter 12 is titled „Bank — **Mitglieder**-Oberfläche", and that is exactly what its three
artboards are: the org-unit bank a member sees (`/org-unit-bank`, `MEMBER_OR_ABOVE`), its account
detail, and the booking request. Those are drawn well and the tiered-approval annotation on
artboard 1 — two approvals from 100 000 aUEC, three from 1 000 000, never your own request — is a
rule I could not have inferred from the API.

But the web has a **second bank**, for the people who run it, and none of it is drawn:

| Web page | Gate | What it is |
| --- | --- | --- |
| `/bank` | `BANK_EMPLOYEE` | the staff dashboard — every account of the unit, not only the granted ones |
| `/bank/accounts/{id}` | `BANK_EMPLOYEE` | the staff account view |
| `/bank/holders/{id}` | `BANK_EMPLOYEE` | a holder's custody across accounts |
| `/bank/requests` | `BANK_EMPLOYEE` | the request queue |
| `/bank/manage` | `BANK_EMPLOYEE` | account lifecycle — create, close, reopen; holders; with `BANK_MANAGEMENT` checks inside |
| `/bank/grants` | `BANK_MANAGEMENT` | who may see and who may approve what |

The write surface behind them (`BankProxyController`): deposits, withdrawals, transfers,
holder-to-holder transfers, **transaction reversal**, request confirm and reject, account create /
close / reopen, holder create, and grant management. Plus two reports — a per-account statement and
a three-month export.

**What I need, in priority order:**

1. **The staff dashboard** (`/bank`) — it is the entry point and it decides the shape of everything
   under it.
2. **The request queue** (`/bank/requests`) as a staff surface. Chapter 12 artboard 1 already draws
   an approval card, and its annotation says approval actions appear „nur mit Approver-Grant". I
   need to know whether the staff queue is that same card at a different scope, or its own thing.
3. **Account lifecycle** (`/bank/manage`) — create, close, reopen, and the holder rows.
4. **Grants** (`/bank/grants`), management-only.
5. **Holder detail** (`/bank/holders/{id}`).

Two things I will not guess:

- **Whether the staff bank is a separate destination or a mode of the member bank.** On the web
  they are separate pages with separate gates. On a phone, a member who is *also* a bank employee
  would then carry two bank entries in one nav. Your call.
- **What a bank employee sees that a member does not**, per screen. The role gate tells me who may
  open the page; it does not tell me which columns are staff-only. The web pages differ in more than
  scope and I would rather have the drawing than reverse-engineer the intent.

> [!note] The app's bank is read-only today
> It reads `/org-units/bank/balances`, one account and its transactions. Everything above is new
> surface, so there is no legacy shape constraining your drawing.

---

## Part C — the refinery create form

Owner example 3: „in der raffinerie müssen neue raffinerieaufträge angelegt werden können mit allen
angaben wie im web frontend." Chapter 11 has two artboards — „Meine Orders" and the detail — and no
create.

What the web's form carries (`refinery-orders-create.html` + `RefineryOrderDto`):

- **location** (required) and **refining method** (required), the method showing its three ratings —
  cost, speed, yield
- **goods**: a non-empty list, each row an input material with quantity, an output material with
  quantity, plus optional quality and yield-bonus percent
- **timing**: `startedAt`, and a duration entered as hours + minutes with a **computed „ends at"**
  shown live
- **money**: `expenses`, `otherExpenses`, `oreSales`, with a **live profit preview**
- optional **mission** link and **owner**, and a `status`
- the **SC-Extractor import**: the web offers a file drop and a deep link that pre-fills the whole
  form from a `RefineryExtract` JSON

**What I need:** a create artboard. Two questions inside it that are yours to answer:

- **Does the extractor import belong on the phone at all?** The extractor is a Windows desktop app;
  its handoff arrives through the ingest gateway and is consumed once in a browser. If the phone
  cannot receive it, the create form is a manual form and should not draw an import affordance.
- **How much of the money block does a phone show?** Three currency fields plus a live profit
  preview is a lot of vertical space for values a member often leaves at zero.

---

## Part D — what else the mapping has turned up so far

Not yet detailed here, because the areas above come first and I would rather send you one accurate
list than four speculative ones. Recorded so nothing is lost:

- **Mein Lager** (`/inventory/my`) — the personal ↔ shared dimension, the bulk bar spanning
  collapsed stacks and later pages, and bulk Ausbuchen (`POST /inventory/bulk-checkout`).
  Chapter 09's artboards 5–10 do draw a bulk Umbuchen, and artboard 6 names its own scope in the
  sheet: „12 Einträge · **Modus LOCATION** · POST inventory/bulk-rebook", with „Ziel (Ort)" as the
  only field. So the two personal modes — `PERSONALIZE` and `DEPERSONALIZE` — are undrawn, and even
  inside `LOCATION` the endpoint also accepts `targetUserId`, `targetOwningOrgUnitId` and
  `mergeStock`, none of which the sheet offers. Worth one revision of 6–7 rather than new frames.
- **Aufträge** — the create form and the material-demand view have no artboard.
- **Hangar** — the org-unit fleet page (`hangar-squadron.html`) has no artboard.
- **Blueprint availability** (`/blueprint-overview`) — no artboard; it is the oversight-scoped
  aggregate, which is a narrower scope than every other list in the app.
- **The materials reference** — `materials`, `material-detail`, `material-collection`,
  `item-collection`, and the profit calculation. All member-visible, none drawn.

---

## How to answer

Same as previous rounds: a new bundle, or an annotation on the existing chapters, whichever is less
work for you. For Part A the annotation may be enough — those are corrections. Parts B and C need
frames.

Where I have asked a question rather than requested a frame, the question is genuine: I will not
build a guess into the app and discover the disagreement three screens later.
