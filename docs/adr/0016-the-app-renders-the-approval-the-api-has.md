# ADR-0016 — The bank's request rows render the approval the API has, not the one the artboards draw

- **Status:** Accepted
- **Date:** 2026-08-27
- **Deciders:** @greluc
- **Related:** `REQ-APP-BANK-008`, main repo `REQ-BANK-041`, `REQ-BANK-042`, `REQ-BANK-047`,
  design chapter 12 artboards 1 and 3

## Context

The delivered design specification is the binding UI reference, and its copy is final. Chapter 12
describes the member's booking-request surface with an **approval counter**:

- a row chip reading „1 / 2 FREIGABEN";
- a confirming button labelled „BESTÄTIGEN ( 2/2 )";
- a footnote: „Gestaffelte Freigabe-Leiter: ab 100.000 aUEC zwei Freigaben, ab 1.000.000 drei";
- the handoff note „Bestätigen … zählt die Leiter hoch (1/2 → 2/2)";
- and, on artboard 3, the threshold line „Ab 100.000 aUEC sind 2 Freigaben nötig" shown while
  **EINZAHLUNG** is the selected movement.

None of that exists in the API. `BankBookingRequestDto` carries no count of any kind. What it
carries is `requiresOwnerApproval`, `requiredApprover`, `ownerApprovalGranted` and
`ownerApprovalGrantedByHandle` — a **single** owner approval that is either outstanding or given
(main repo `REQ-BANK-041`). A bank employee's confirmation follows it; that is the second step, and
it happens on a surface the app does not carry.

The artboards' intuition is not baseless — there *is* a ladder, and the drawn numbers are close to
the real thresholds. But the ladder escalates **who** must approve, not **how many**. For the KRT
account only, `REQ-BANK-047` moves the required approver up by amount: responsible holder →
Bankleitung → Organisationsleitung. `requiredApprover` names the class the server picked.

The deposit hint is a separate contradiction: `REQ-BANK-042` makes a deposit possible against every
active account **without an approval limit**, which the web frontend states in its own words
(„Einzahlungen sind für jedes aktive Konto möglich – ohne Freigabe-Limit"). Artboard 3 nonetheless
shows the threshold line with EINZAHLUNG selected.

Three ways out were on the table: build the counter as drawn and fake the numbers client-side;
change the backend to keep a tally; or build what the API models and send the artboards back for
correction.

## Decision

**Build the real model. Change no backend. Correct the artboards.**

- No approval count is rendered anywhere. While a request is pending and flagged, the row's
  trailing chip names the class it is waiting on — „Wartet auf Kontoverantwortlichen",
  „Wartet auf Bankleitung", „Wartet auf Organisationsleitung" — and once granted it names who
  granted it. Once the request is decided the chip is the verdict instead.
- The member's actions are **grant** and **revoke**, never reject. The API has no member-facing
  reject: refusing a request is `POST /api/v1/bank/requests/{id}/reject`, which is
  `hasRole(BANK_EMPLOYEE)`. A holder who disagrees simply does not grant.
- The sheet's threshold line is absent for a deposit, absent for a caller the account exempts, and
  absent when the account sets no limit. Otherwise it states the account's own `approvalLimit` and
  flips from *what will apply* to *what now applies* as the typed amount crosses it.
- Both corrections go back to the design side as a written prompt
  (`docs/design/android/MISSING_ARTBOARD_PROMPTS_7.md`).

The owner approved both points in chat on 2026-08-27, choosing „Artboard korrigieren, App baut das
Echte" and „Hinweis nur bei Auszahlung/Transfer".

## Consequences

**Good.** The app cannot show a member a fraction that no server ever computes, and cannot offer a
control the server has no endpoint for. Naming the approver class is strictly more useful than a
count: it tells the requester who to nudge, which is what the web frontend's own copy does. And
the deposit rule now matches the web app, so the two do not disagree about when money needs
clearing.

**Bad.** The app and the artboards disagree until chapter 12 is redrawn, and anyone reading the
mockups will reach for the counter again. Two guards exist against that: `BankRequestScreenTest`
asserts, by literal, that no state of the sheet or of a row states a number of approvals or offers
a reject; and this ADR is named from `REQ-APP-BANK-008`.

**Also bad, and accepted.** „WARTET AUF KONTOVERANTWORTLICHEN" is long enough to wrap onto two
lines inside its chip on a 393 dp phone, where the drawn „1 / 2 FREIGABEN" fitted one. The
information earns the space; the artboard's chip was shorter because it carried less.

## Alternatives rejected

**Fake the counter client-side** — render `1 / 1` or `0 / 1` so the artboard's shape survives. It
would be a fraction the server never computes, and the first KRT-account request would make it
lie: the escalation there is of class, so a caller would read „1 / 1" on a request that needs the
Organisationsleitung and think a colleague could clear it.

**Add a tally to the backend.** A real change to a settled, audited approval flow (`REQ-BANK-041`,
`REQ-BANK-047`, the `V193` owner-approval columns), to satisfy a mockup rather than a need. The
count would carry no information the class does not already carry.
