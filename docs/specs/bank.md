# Bank — the org accounts a member may see

> **Doc type:** Living spec · **Area:** `REQ-APP-BANK-*` · **Design:** `docs/design/android/12 Bank.dc.html`
> **Server contract:** main repo `REQ-API-009`, `REQ-BANK-016` (the drawn balance line), `REQ-BANK-037`
> **Related:** [`api-contract.md`](api-contract.md)

The Konten list and one account with its ledger. **Read-only**: requesting a booking, approving one
and rejecting one are mutations behind a staged approval ladder and belong to Phase 3.

---

### REQ-APP-BANK-001 — The member-facing paths, never the bank-employee ones

`/api/v1/bank/accounts/…` lists **every** account in the organisation and is gated on a bank role.
The app reads `/api/v1/org-units/bank/…`, which answers with the accounts this caller may actually
see: the ones public to everyone (`REQ-BANK-037`) plus those they hold a view grant for.

**Acceptance**

- [x] The list read is asserted by path, so a future edit cannot quietly point it at the wider one
  (`BankRepositoryTest`).
- [x] An empty list is an ordinary answer, not a failure — a member with no grant may legitimately
  see nothing.

**Code:** `BankRepository`

---

### REQ-APP-BANK-002 — The account and its ledger fail together

Both reads carry the same `canSee` gate, so splitting their states would model a case the server
cannot produce. It would also produce a worse screen: a balance over a missing ledger reads as an
account with no history rather than one that did not load.

**Acceptance**

- [x] A failed ledger fails the screen and leaves no half-rendered account (`BankViewModelTest`).
- [x] A **failed continuation** — an older page — keeps what is on screen: that is a different
  thing from the first read failing.
- [x] `403` and `404` are worded differently from an outage.

**Code:** `BankAccountViewModel`

---

### REQ-APP-BANK-003 — The sign comes from the booking kind, never from the digits

The ledger is append-only and stores every amount as a **positive magnitude**. Reading a sign off
the number would show every withdrawal as a deposit.

`DEPOSIT` is money in, `WITHDRAWAL` is money out, and every other kind — `TRANSFER`,
`HOLDER_TRANSFER`, `REVERSAL`, `WIPE_RESET`, or one this build has never seen — renders **without a
sign and in the neutral colour**. Colouring an unclassified line would state a direction nobody
checked.

The tints are `SuccessText` and `DangerText`, not the `Success`/`Danger` container colours: those
are fills and fail contrast as text on the dark ground.

**Acceptance**

- [x] A deposit reads `+12.400`, a withdrawal `−3.200`, an unknown kind `500` (`BankScreenTest`).
- [x] The kind's own wording is shown when a booking carries no note, and an untranslated kind falls
  back to the raw server value rather than to an empty line.

**Code:** `BankBooking.incoming`, `BookingRow`

---

### REQ-APP-BANK-004 — The balance line is drawn, not charted

The design says so and the main repo's `REQ-BANK-016` requires it: the server sends the points and
the client draws a polyline. No chart framework enters this app for one line on a card — it would be
a dependency, a theme to fight, and under this repo's privacy gate a decision rather than a detail.

A line needs two points; fewer draws nothing rather than a dot pretending to be a trend. A flat
series draws a straight line through the middle instead of dividing by a zero span.

The points are kept as doubles, unlike every amount in this app. Nothing is ever printed from them —
they are the polyline's coordinates — so the precision a decimal buys has no reader.

**Acceptance**

- [x] Fewer than two points draws nothing.
- [x] A constant series does not divide by zero.
- [x] The canvas carries a content description, since a drawing has no text of its own.

**Code:** `Sparkline`

---

### REQ-APP-BANK-005 — Amounts are formatted by the shared formatter, never recomputed

The same `formatAmount` the Einsatz Finanzen tab and an Operation's roll-up use — moved to a shared
package when the third area needed it, because a member reading the same figure on two screens must
read the same figure. `BigDecimal` throughout; no `Double` ever touches money.

**Acceptance**

- [x] Every digit the server sent survives the mapping (`BankRepositoryTest`).
- [x] `84200.0000` renders as `84.200` in German (`BankScreenTest`).

**Code:** `common/Amounts.kt`

---

## Known gaps, stated rather than omitted

- **The Anträge tab is absent.** Listing booking requests would be a read, but approving and
  rejecting them are mutations behind a staged ladder (two approvals from 100 000 aUEC, three from
  1 000 000, never your own request). A tab that showed the queue while the actions lived elsewhere
  would invite a member to try.
- **"Buchung beantragen" is absent** for the same reason.
- **The holders breakdown of the design's account detail** — "Verwahrung, summiert auf den
  Kontostand" — is not shown: `/org-units/bank/accounts/{id}` carries the account and its counters,
  not the per-holder split, and the app does not add money up.
- **No date filter on the ledger.** The endpoint takes `from`/`to`; no control offers them yet, and
  they ship with the shared date-range picker.

## Contract-set dependency (main repo)

`GET /api/v1/org-units/bank/balances`, `/accounts/{id}` and `/accounts/{id}/transactions` are in the
`REQ-API-009` contract set and the vhost allow-list. `sparkline` is frozen because the design draws
it and there is no chart framework to fall back on; `holderHandle` because it is the ledger row's
"who".

---

### REQ-APP-BANK-006 — The account's own settings, and the server decides who sees them

`GET …/org-units/bank/accounts/{id}/settings` answers with **what the caller may change**:
`canSetTarget` and `canConfigureVisibility`. The app offers exactly those controls and works out no
role of its own — which member is responsible for an account is a per-account fact (the
`STAFFELLEITER` of the owning Staffel, the SK lead of a Spezialkommando, the `BEREICHSLEITER` of an
Area), and reproducing that ladder in the client would be a second, weaker copy of a server rule.

**A settings read that fails costs the controls, not the screen.** The account and its ledger are
what the member came for; the flags default to "may not", which is the safe direction. Observed on
a device: a member with a view grant but no responsibility got `403` on the settings read and the
account rendered without the action.

Three writes, all answering with the whole snapshot — the version moves with each one, and so does
what the caller may do next, so nothing is patched:

- `PUT …/balance-target`, version-echoed. **An emptied field clears the target** rather than setting
  one of zero: those are different instructions and the screen never offers the second. The editor
  opens on the whole-number form, because the wire carries `250000.0000` and the field takes digits.
- `POST` / `DELETE …/visibility/role/{roleCode}` per bucket.
- `PUT …/visibility/all-members/{enabled}`.

**An account type whose visibility is fixed says so** rather than showing an empty section: "cannot
be configured" and "you may not configure it" are different sentences, and `visibilityConfigurable`
is the field that tells them apart.

**Acceptance**

- [x] Nothing is written when the server's flags say the caller may not (`BankViewModelTest`).
- [x] The target write echoes the version, and an emptied field clears (`BankViewModelTest`).
- [x] A role bucket toggles against what the account already grants (`BankViewModelTest`).
- [x] A failed settings read leaves the screen intact and the controls away (`BankViewModelTest`).
- [x] A fixed-visibility account says so (`BankScreenTest`).
- [x] **Observed on a device (2026-08-23):** without responsibility the settings read answered
  `403` and no control appeared; as the Staffelleiter the target was set (`200`, persisted as
  `250000.0000`), a role bucket granted (`200`) and the all-members switch flipped (`200`).

**Code:** `BankRepository.settings` / `.setBalanceTarget` / `.setRoleVisibility` /
`.setAllMembersVisibility`, `BankAccountViewModel`

---

### REQ-APP-BANK-007 — The bank-employee surface stays out

`/api/v1/bank/**` — deposits, withdrawals, transfers, the request queue, the reversal — is
`hasRole(BANK_EMPLOYEE)` throughout and is **not** on the app's allow-list. This is the same
decision as [`REQ-APP-BANK-001`](bank.md), restated because phase 3 is when it would have been easy
to slip: the member-facing `/org-units/bank/**` paths gained writes, and the employee ones sit one
prefix away.

A bank employee uses the web app. The app carries what a member has.

**Acceptance**

- [x] No `/api/v1/bank/**` path is in the contract set or the vhost allow-list (main repo
  `ExternalContractTest`, `docs/API_VHOST_ROLLOUT_RUNBOOK.md`).
