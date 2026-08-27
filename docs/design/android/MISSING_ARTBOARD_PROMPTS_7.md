# Round 7 — chapter 12 draws an approval mechanism the API does not have

> Written 2026-08-27. Two corrections to chapter 12, both already built the corrected way in the
> app and both approved by the product owner on 2026-08-27.
> Round 6: [`MISSING_ARTBOARD_PROMPTS_6.md`](MISSING_ARTBOARD_PROMPTS_6.md).
> Earlier rounds: [`_1`](MISSING_ARTBOARD_PROMPTS.md), [`_2`](MISSING_ARTBOARD_PROMPTS_2.md),
> [`_3`](MISSING_ARTBOARD_PROMPTS_3.md), [`_4`](MISSING_ARTBOARD_PROMPTS_4.md),
> [`_5`](MISSING_ARTBOARD_PROMPTS_5.md).

---

Chapter 12's member half — artboards 1 and 3 — has been built. Almost all of it went in unchanged:
the two tabs with their count, the total band, the account cards, the full-width
„+ BUCHUNG BEANTRAGEN" bar, the sheet's three icon segments, the label that turns from „Zielkonto"
into „Konto" with the movement, the green / red / neutral amount, the placeholder, and the
ABBRECHEN + ANTRAG EINREICHEN footer at its 154 : 200 split. All measured off the artboards and
verified on three device classes.

**Two things in it describe a mechanism the backend does not implement.** The app builds the real
one and diverges from the drawing until the drawing is corrected; the divergence is recorded as
[ADR-0016](../../adr/0016-the-app-renders-the-approval-the-api-has.md) and pinned by tests. This
round asks for the artboards to follow.

---

## 1 — There is no approval counter. There never was.

**What the artboards show**

| Where | What it says |
| --- | --- |
| Artboard 1, row 1 chip | `1 / 2 FREIGABEN` |
| Artboard 1, row 1 button | `✓ BESTÄTIGEN ( 2/2 )` |
| Artboard 1, footnote | „Gestaffelte Freigabe-Leiter: ab 100.000 aUEC zwei Freigaben, ab 1.000.000 drei. Eigene Anträge kann man nie selbst freigeben." |
| Artboard 1, handoff | „Bestätigen … zählt die Leiter hoch (1/2 → 2/2)" |
| Artboard 3, under the amount | „Ab 100.000 aUEC sind 2 Freigaben nötig — der Antrag erscheint im Tab „Anträge"." |
| Artboard 5, three row chips | `1 / 2 FREIGABEN`, `0 / 1 FREIGABEN`, `1 / 3 FREIGABEN` |
| Artboard 5, row 1 button | `✓ BESTÄTIGEN (2/2)` |
| Artboard 5, footnote | „Leiter: ab 100.000 aUEC zwei, ab 1.000.000 drei Freigaben — nie den eigenen Antrag." |

**What the API has.** `BankBookingRequestDto` carries no count of any kind. Its approval fields
are `requiresOwnerApproval`, `requiredApprover`, `ownerApprovalGranted` and
`ownerApprovalGrantedByHandle`. That is **one** approval — outstanding or given. A bank employee's
confirmation follows it, and that happens on the staff surface (artboards 4–8), not here.

**The ladder is real, but it escalates the wrong axis.** For the KRT account only, `REQ-BANK-047`
moves the *class* of approver up with the amount:

| Band | Who must approve |
| --- | --- |
| at or below the bank-employee ceiling `T1` | the bank employee may self-approve |
| above `T1`, at or below the area-lead ceiling `T2` | **Bankleitung** |
| above `T2` | **Organisationsleitung** |

Every other request-capable account has one band and one approver: the account's responsible
holder. `requiredApprover` is the server's answer to *who*, and it never answers *how many*.

**What the app builds instead.** One trailing chip per row — not two; the artboard's own rows show
one, and rendering the approval chip beside a status chip overflowed on a 393 dp phone.

| Request state | Chip | Tone |
| --- | --- | --- |
| pending, flagged, not yet granted | `WARTET AUF KONTOVERANTWORTLICHEN` / `WARTET AUF BANKLEITUNG` / `WARTET AUF ORGANISATIONSLEITUNG` | warning |
| pending, granted | `FREIGEGEBEN VON <Handle>` | success |
| pending, no approval needed | `EINGEREICHT` | data |
| confirmed / rejected / withdrawn | `BESTÄTIGT` / `ABGELEHNT` / `ZURÜCKGEZOGEN` | success / danger / muted |

**And the member surface has no reject.** `POST …/requests/{id}/reject` is
`hasRole(BANK_EMPLOYEE)`. What a responsible holder can do is grant the approval and take it back
— `POST` and `DELETE` on `…/requests/{id}/owner-approval`. So artboard 1's `ABLEHNEN` +
`BESTÄTIGEN` pair is wrong twice over: „Bestätigen" is the employee's word for the employee's act,
and „Ablehnen" is an endpoint the member does not have. The app draws `FREIGABE ERTEILEN`
(success) and, once granted, `FREIGABE ZURÜCKNEHMEN` (ghost).

**Artboard 5 has the same chips but different buttons, and only the chips are wrong.** That
screen is the bank employee's queue, and `ABLEHNEN` + `BESTÄTIGEN` are exactly right there:
`POST /api/v1/bank/requests/{id}/(confirm|reject)` both exist and are `BANK_EMPLOYEE`. What has to
go is the `(2/2)` in the button label and the three `n / m FREIGABEN` chips, which should name the
approver class and whether it has granted — the same vocabulary as artboard 1.

### What we would like drawn

- Artboard 1's flagged row: **one** trailing chip naming the class being waited on, and a single
  `FREIGABE ERTEILEN` button in its place. A variant showing the granted state with
  `FREIGABE ZURÜCKNEHMEN`.
- Artboard 5's three chips in the same vocabulary, and `BESTÄTIGEN` without its counter. Its
  footnote rewritten like artboard 1's — its second half („nie den eigenen Antrag") is correct and
  is drawn correctly beside it, in the locked own-request row.
- The footnote rewritten to describe the escalation of approver class, and to keep its true second
  half („Eigene Anträge kann man nie selbst freigeben" — that part is correct and is implemented).
- The handoff note's „zählt die Leiter hoch (1/2 → 2/2)" removed.

**One thing we could not keep.** „WARTET AUF KONTOVERANTWORTLICHEN" wraps to two lines inside its
chip at 393 dp, where „1 / 2 FREIGABEN" fitted one. We took the wrap: the class is what tells a
requester who to nudge, which is what the web frontend's own copy does. **If you would rather have
one line, we need a shorter German wording for each of the three classes** — that is a copy
decision and it is yours.

---

## 2 — A deposit is never approval-limited, so artboard 3 should not say it is

Artboard 3 shows the threshold line with **EINZAHLUNG** selected. `REQ-BANK-042` makes a deposit
possible against every active account **without an approval limit**, and the web frontend says so
in its own words: „Einzahlungen sind für jedes aktive Konto möglich – ohne Freigabe-Limit."

The app therefore shows the line only for AUSZAHLUNG and TRANSFER, and only when the server gives
it something true to say:

| Condition | Line |
| --- | --- |
| kind is EINZAHLUNG | none |
| the caller is `approvalExempt` on that account | none |
| the account sets no `approvalLimit` | none |
| amount at or below the limit | „Ab {limit} aUEC ist die Freigabe des Kontoverantwortlichen nötig." |
| amount above it | „Über dem Freigabe-Limit von {limit} aUEC — der Antrag muss zuerst vom Kontoverantwortlichen freigegeben werden." |

The limit is **per caller and per account** (`approvalLimit` on the balances response), which is
what the artboard's own note meant by „Leiter aus Server-Konfig". It is never a constant, so
`100.000` in the drawing should read as a placeholder.

### What we would like drawn

- Artboard 3's EINZAHLUNG state **without** the info line.
- The AUSZAHLUNG and TRANSFER states with it, and the wording changed from a count to the
  single-approval sentence above.
- Optionally a fourth state: over the limit, in warning rather than info tone.

---

## 2b — Artboard 5's open backend question, answered from the code

Artboard 5's handoff says: „Ablehnen bleibt aktiv gezeichnet — ob der Server Self-Reject zulässt,
ist als Backend-Klärung notiert; bis dahin entscheidet der Server (403-Muster)."

**Answered: the server does permit it, and the drawing is right.** `BankBookingRequestService.reject`
gates on three things — the request is still `PENDING`, the version matches, and the caller can see
the account (`canSee`). It never compares the caller against `requestedBy`. A bank employee may
reject their own request, so `ABLEHNEN` stays drawn active, and no change is needed.

**And the neighbouring claim turns out to be true for a different reason than the drawing implies.**
„Eigene Anträge gibst du nie selbst frei" is correct, but there is no self-check anywhere in the
approval path either — `canApprove` asks only *who you are* (responsible holder / Bankleitung / OL /
admin), never who raised it. The reason a holder cannot approve their own request is that **their
own request never needs an approval at all**: `isApprovalExempt(account) == isResponsibleHolder(account)`
(ADR-0123), so for a holder `requiresOwnerApproval` is `false`, `applicableLimit` is `null` and
`requiredApprover` is `null`, for any amount and bypassing the KRT ladder. The spec puts the
rationale plainly: making them counter-sign their own request „was a no-op click that only produced
noise".

The practical consequence for the drawing is small but worth having: the locked-`Genehmigen`
state on artboard 5's own-request row is **not** a case of "you may not approve this". It is a
request that carries no approval to give. If the row is ever redrawn, „Freigabe nicht nötig" would
be truer than a lock.

## 2b2 — Artboard 5's „BESTÄTIGEN" cannot be a button

The queue's confirming CTA is drawn as a single tap. The endpoint behind it needs two things the
drawing has no control for:

| Field | Constraint | What it is |
| --- | --- | --- |
| `holderId` | **`@NotNull`** | which Verwahrer received or paid the money out. A booked deposit or withdrawal records it (REQ-BANK-040/-044). |
| `ownerApprovalConfirmed` | required for a flagged request | the employee's attestation that the responsible holder approved. Without it the server answers `BANK_OWNER_APPROVAL_REQUIRED` (REQ-BANK-041). |

A bare CTA therefore posts a body the server rejects, **every time**, on every over-limit request
and on every request at all.

The web frontend already has the modal this needs — „Antrag bestätigen … Erfasse den Halter, der
das Geld erhalten bzw. ausgezahlt hat" — with the checkbox „Freigabe durch Kontoverantwortlichen
erfolgt" beside it. The app now has a bottom sheet of the same shape:

- the holder picker (required)
- a second one for a transfer's **receiving** holder (`destinationHolderId`)
- the attestation checkbox, shown **only** when the request is flagged, with a line beside it
  stating whether the approval has in fact been granted and by whom — so the employee ticks
  something they can check
- „Notiz Bankmitarbeiter" (`staffNote`, REQ-BANK-054), optional
- ABBRECHEN + BESTÄTIGEN

### What we would like drawn

Artboard 5's confirming path as a **sheet**, not a CTA. Two states are worth having: an ordinary
request (holder + note) and a flagged one (holder + attestation + note), since the second is the
one that fails without the extra control.

The refusal path needs no change — the danger modal with a reason is exactly right, and
`reason` is required by the server too.

## 2c — The staff bank has no direct booking form, and we think that is deliberate

The owner's parity brief asks for „alle funktionen die die bank und ihre unterseiten im web
frontend haben". The web's staff bank has three direct booking forms — `POST /bank/deposits`,
`/withdrawals`, `/transfers`, plus `GET /transfer-fee-rate` — for a booking that had no request
behind it. **No artboard in chapter 12 draws any of them.**

We read the omission as intentional rather than as a gap, on the strength of artboard 4's own
handoff, which is precise about what the staff account detail adds over the member one: „das
Staff-Konto-Detail selbst = Mitglieder-Detail (Artboard 2) + Storno + Berichte". Booking is not on
that list, and the confirming of a request *is* the booking — the direct forms only cover the case
where nobody filed one.

So the app does not carry them, and this is recorded as a **known delta to the web frontend** in
`REQ-APP-BANK-007` rather than filled in by guesswork.

**Please confirm, or draw them.** If they belong in the app, they need an artboard: a deposit and a
withdrawal name a counterparty, a transfer names a target account and shows the fee rate, and none
of that has a drawn form to follow.

## 2d — Artboard 6: what deactivating a holder actually does

The handoff says the holder action is a „Halter-Entzug" whose danger modal reads „Entzieht diesem
Mitarbeiter alle Buchungsrechte auf dem Konto".

**That is what a grant does, not what this does.** `UpdateBankHolderRequest` carries one flag,
`active`, and the web frontend's own modal says what flipping it means:

> Ein inaktiver Halter kann kein neues Geld mehr zugebucht bekommen. Bestehende Bestände bleiben
> abbuchbar.

So it is **not** a removal, it takes away no rights on any account, and it is reversible — the web
has a „Halter reaktivieren" modal beside it. The app uses the web's wording, and „Deaktivieren" /
„Reaktivieren" as the labels.

Two consequences for the drawing: the danger tone is too strong for a reversible flag that removes
nothing, and the section is titled correctly already („Halter — Einheit", „nicht kontogebunden"),
which is what makes the account-level claim in the handoff read as a slip rather than a design.

**Also missing from the handoff:** closing an account is blocked by a **non-zero balance**
(„Nur Konten mit Saldo 0 können geschlossen werden"), not only by undecided requests. The app
states it beneath the disabled action rather than letting the button find out.

## 2e — Artboard 7: the matrix has three flags, and the row is what the sight is

The drawing gives each member two checkboxes, „SEHEN" and „FREIGEBEN", and the handoff adds three
notes: „Zeile existiert = mindestens ein Grant; Zeile entfernen = alle Grants … entziehen
(Danger-Modal)", „Freigeben setzt Sehen voraus — Flag-Kopplung in der UI; falls der Server sie nicht
erzwingt: Backend-Anforderung, notiert", and „CARTEL ist für alle sichtbar (REQ-BANK-037) — dort ist
das Sehen-Flag inert; Freigeben bleibt vergebbar."

**The server's shape is a `bank_account_grant` row per (member, account) with three flags** —
`can_deposit`, `can_withdraw`, `can_transfer` (REQ-BANK-009) — and nothing else. So whichever of the
two readings of „FREIGEBEN" was meant, "may book" or "may approve", neither is a column that exists:

- as **"may book"** it is three flags collapsed into one, and the three are separately enforced —
  `BankSecurityService.canDeposit` / `canWithdraw` / `canTransfer` each test their own flag;
- as **"may approve"** it has nothing behind it at all: who may approve a booking request is decided
  per request by `requiredApprover`, and REQ-BANK-047 escalates that by amount, so a per-member
  approval flag would quietly contradict the ladder.

The app therefore renders the three the server enforces, and no fourth.

**„SEHEN" is right in substance but is not a checkbox.** `BankSecurityService.canSee` is literally
`hasCapability(accountId, auth, g -> true)` — the row's existence *is* the view grant, and a row with
all three flags false is the deliberate „darf sehen, darf nichts buchen" case. Two consequences:

- Unticking the last box must **not** delete the row, or the member silently loses sight of the
  account as well. The app keeps the row and says the rule in plain text on the surface: „Wer hier
  steht, darf das Konto sehen — auch ohne ein einziges Häkchen. Sehen entziehst du, indem du den
  Eintrag entfernst."
- **The requested flag-coupling needs neither UI work nor a backend requirement.** „Freigeben setzt
  Sehen voraus" holds *structurally*: every capability check runs through `hasCapability`, which
  needs the row, and the row is the sight. There is no state in which a member may book an account
  they cannot see, so there is nothing to couple and nothing to ask the backend for. **This closes
  the handoff's open item.**

**The CARTEL note is right, and it changes the copy.** `CARTEL` is seen by every KRT member by rule
(REQ-BANK-037, `OrgUnitBankAccessService`), so there the entry only ever carried booking rights. The
app swaps both the surface note and the removal modal on that account rather than promising a sight
it cannot take away.

**The Danger-Modal is implemented as asked** — the removal is the one action here that takes
something away which no checkbox mentions.

**„+ Grant hinzufügen" is implemented, and it cannot be the restricted picker the drawing implies.**
The server requires the grantee to hold the Bank Employee role (REQ-BANK-008), but its own member
search is not filtered by that role — `/users/search-bank` is `/users/search` with a widened role
gate and nothing else. So the sheet offers every member and renders the refusal, rather than
second-guessing the server's list and hiding candidates it has no authority to judge. Two 409s are
reachable and need different sentences — no Bank Employee role, and already listed — so the RFC 7807
`code` decides, never the bare status.

**Two defects only the device could show**, both now fixed and covered: deciding create-vs-patch on
`version == 0` sent the first edit of every untouched grant as a creation (a new row's `@Version`
*is* zero) and came back `409 DUPLICATE_ENTITY`; and the refusal was silent, so the checkbox snapped
back with nothing said.

**And two the artboard comparison turned up, both app-wide and both in the design system rather than
in the bank:**

- **The open tab had no underline.** `KrtPageTabs` draws one — `.tab-nav .tab.active` is 3 px of
  accent — but the tab row scrolls horizontally, so it hands its children an *unbounded* width
  constraint, and `fillMaxWidth()` collapses to zero under an infinite maximum. The marker was in
  the composition, answered every semantics query, and was zero pixels wide on screen. Measuring the
  tab at `IntrinsicSize.Max` gives it something finite to fill.
- **Segment labels were not uppercase.** Every artboard renders them through
  `text-transform: uppercase` and the copy rules ask for uppercase labels, but the string resources
  are sentence case and only one call site (the request sheet) uppercased them by hand — so the
  bank's scope switch read „Mitglied / Verwaltung" beside a sheet that shouted. The transform now
  lives in `KrtSegmentedControl`, and the hand-rolled call site is gone.

Neither was visible in a semantics-based test, which is why both survived four tabs' worth of
screen tests. The regressions are pinned by measurement instead: `KrtPageTabsUnderlineTest` asserts
the underline is at least as wide as the tab's own padding (it measured **0.0 dp** against the old
code), and `KrtSegmentedControlCaseTest` asserts a sentence-case label is drawn uppercase.

**Layout:** three capability columns plus a handle do not fit a phone's width the way two short ones
did, so the table becomes a per-member card. The account chip row stays as drawn, made horizontally
scrollable — a unit with many accounts would otherwise lose its last one off the edge.

**One more thing the artboard cannot show:** the two grant lists in this app are not the same list.
The staff matrix is shaped by `bank_account_grant`; the member's visible accounts are shaped by
REQ-BANK-037 org-unit visibility. An account can appear on one and not the other, which is exactly
what the Übersicht's „nur über das Amt" mark reports.

## 2f — Artboard 8: the Umbuchung is not free, and it does touch an account

The transfer's explanatory line is the web frontend's own, verbatim: „Verschiebt Verwahrung zwischen
Haltern, ohne ein Konto zu berühren. Der Quell-Halter darf dabei ins Minus gehen."

The second sentence is exactly right. The first is not, and the difference is not cosmetic.
`BankLedgerService.bookHolderTransfer` charges a fee — `operation.transfer_fee_rate`, seeded at
**0.5 %** — and books it against the **KRT (CARTEL)** account, which it locks, requires active, and
requires covered. Three consequences the drawing cannot show:

- as soon as the fee rounds above zero, an account **is** touched;
- if the KRT account is missing, the transfer is refused with `BANK_ACCOUNT_CLOSED`;
- if it exists but has no cover for the fee, the transfer is refused with `BANK_OVERDRAFT`.

All three were reached on a device in that order, simply by trying the action against a test stack
that had no KRT account yet. The app's wording therefore says what happens: no account of the *unit*
is debited, the fee is charged to the KRT account, and the source holder may go negative.

**And the refusals need their own sentences.** The shared bank wording answered every 409 with
„Nicht gespeichert — gleichzeitig geändert", which is the optimistic-lock sentence and was simply
the wrong cause: a member told their transfer collided with a concurrent edit will reload and try
again, and be refused again, forever. The bank's conflict codes now each get their own answer.

**Also missing from the register (artboard 6/8):** „+ Halter registrieren". The web has it on the
same section; the app did not, which made artboard 8 unreachable on a fresh unit — no holder, no
custody, and no confirmation able to name one.

## 3 — Two smaller questions, no strong opinion

**3.1 — Does the amount field group while you type?** Artboard 3 shows `120.000` in the input. We
kept the input ungrouped (`120000`) because separators fight the caret, and grouped everything that
is *displayed* — the row reads `−90.000`. Grouping on blur would satisfy both. Worth a rule in the
README if you have a preference.

**3.2 — Artboard 1's scope segment.** „Verwaltung" is drawn as a locked-but-tappable segment beside
„Mitglied". It is not built yet, deliberately: the surface it leads to (artboards 4–8) is the next
slice, and a lock pointing at nothing is worse than no lock. It lands with that slice, per the
Kap-09 pattern the note names. No change requested — this is a status note so the omission is not
read as a miss.

---

## What is already correct and needs nothing

Recorded so the next round does not re-litigate it: the two-tab structure with the count badge, the
badge counting only undecided requests, the merged own + foreign list, the meta line
„<Handle> · Konto <Name>", the requester's own actions (BEARBEITEN, ZURÜCKZIEHEN — both real
endpoints, `PUT` and `POST …/cancel`), the sign and tint rules, the segment icons, and the whole
footer geometry. All of it went in unchanged.
