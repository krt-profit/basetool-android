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
