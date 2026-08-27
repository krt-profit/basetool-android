# Bank — the org accounts a member may see

> **Doc type:** Living spec · **Area:** `REQ-APP-BANK-*` · **Design:** `docs/design/android/12 Bank.dc.html`
> **Server contract:** main repo `REQ-API-009`, `REQ-BANK-016` (the drawn balance line), `REQ-BANK-037`
> **Related:** [`api-contract.md`](api-contract.md)

The Konten list, one account with its ledger, and the member's own booking requests — raising one,
correcting it, withdrawing it, and granting the responsible holder's approval on somebody else's.
**Rejecting** a request is not on the member surface: it is a bank employee's act, and it lives on
the staff one ([`REQ-APP-BANK-007`](bank.md)) — which the app now carries, but which a plain member
never sees.

---

### REQ-APP-BANK-001 — The member surface reads the member paths

`/api/v1/bank/accounts/…` lists **every** account in the organisation and is gated on a bank role.
The member surface reads `/api/v1/org-units/bank/…`, which answers with the accounts this caller
may actually see: the ones public to everyone (`REQ-BANK-037`) plus those they hold a view grant
for.

> [!note] Amended 2026-08-27 alongside [`REQ-APP-BANK-007`](bank.md)
> This used to read "never the bank-employee ones", when the app had no staff surface at all. It
> now has one, and the rule it was really protecting is unchanged: **the member half never reads a
> staff path.** A caller who is not a bank employee sees exactly what they saw before, because the
> wider list is behind a scope segment the server's own roles decide.

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

### REQ-APP-BANK-007 — The bank-employee surface, and the line that still holds

> [!note] **Amended 2026-08-27, owner-approved.** This requirement previously read "the
> bank-employee surface stays out" and kept **all** of `/api/v1/bank/**` off the app. The owner
> named the staff bank as a required function — „die bank für bankmitarbeiter und bankleitung mit
> allen funktionen die die bank und ihre unterseiten im web frontend haben" — and, asked separately
> which endpoints that should put on the public mobile vhost, chose the full set the design draws.
> The amendment is recorded here **before** the code that depends on it, per `CLAUDE.md`.

The app carries the staff bank that **design chapter 12 draws** — artboards 4 to 8: the dashboard,
the request queue with confirm and reject, the account lifecycle, the grants matrix, the holder
detail with its transfer, plus the staff account detail's two additions over the member one
(reversal and the two reports).

**The design defines the scope, and three things stay out because of it.**

1. **`/api/v1/bank/admin/**` — permanently.** Wipe-reset and the bank audit log are the admin
   area, which is web-only by owner decision (`ANDROID_APP_PLAN` Q6) and is named in the owner's
   own carve-out („außer beförderung und admin bereich").
2. **The direct booking forms** — `POST /bank/deposits`, `/withdrawals`, `/transfers` and
   `GET /transfer-fee-rate`. No artboard draws them, and artboard 4's handoff is explicit about
   what the staff account detail adds over the member one: „+ Storno + Berichte". A booking that
   had no request is therefore still a browser act. **This is a known delta to the web frontend**
   and is raised with the design side rather than guessed at
   ([`MISSING_ARTBOARD_PROMPTS_7.md`](../design/android/MISSING_ARTBOARD_PROMPTS_7.md)).
3. **`PATCH /bank/accounts/{id}/approval-tiers`** — the KRT ladder editor. The app's four tabs are
   ÜBERSICHT · ANTRÄGE · KONTEN · GRANTS; the web's „KRT-Freigaben" tab is not among them.

**Every admitted path is named individually and anchored.** `/api/v1/bank` is **not** in the
vhost's read-only family, so naming a path opens every verb the backend serves on it — which is
what the lifecycle and grants writes need. The safety comes from the allow-list defaulting to
`404`: nothing under `/bank` is reachable that is not named, and `/bank/admin/**` is never named.

**Acceptance**

- [x] `/api/v1/bank/admin/**` is in neither the contract set nor the vhost allow-list, and the
  nightly edge probe asserts it still answers `404` (main repo `ExternalContractTest`,
  `docs/API_VHOST_ROLLOUT_RUNBOOK.md`, `edge-deny-probe`).
- [x] No direct booking path (`/bank/deposits`, `/bank/withdrawals`, `/bank/transfers`,
  `/bank/transfer-fee-rate`) is admitted, and the probe asserts each still answers `404`.
- [x] The staff surface is reached only by a caller the **server** calls a bank employee; the app
  derives the flag from the roles on `GET /api/v1/users/me` and treats it as a hint, never a gate
  (ADR-0011).

---

### REQ-APP-BANK-008 — Booking requests: one approval, by a named class, never a count

The Anträge tab and the request sheet — design chapter 12, artboards 1 and 3.

**What the member can do.** Raise a request (`POST /org-units/bank/requests`), correct their own
while it is still pending and unapproved (`PUT …/{id}`), withdraw it (`POST …/{id}/cancel`), and —
on an account they are responsible for — grant or revoke the owner approval
(`POST` / `DELETE …/{id}/owner-approval`). That is the whole member surface, and it is the whole
of what the web frontend offers a member.

The create, the edit and the withdrawal echo the request's `version`; **the two approval verbs do
not, because the server takes no body on either**. The grant is idempotent and the state it sets
does not depend on what the client last read, so there is nothing to collide over.

**A responsible holder's own request is never flagged.** `isApprovalExempt` is
`isResponsibleHolder` (main repo ADR-0123): for a holder, `requiresOwnerApproval` is `false`,
`applicableLimit` is `null` and `requiredApprover` is `null` — for any amount, bypassing the KRT
ladder. That, and not any self-check, is why nobody approves their own request; there is no
self-check anywhere in the server's approval path. The app's own rule — a request on both reads is
the caller's own and carries no approval action — is belt-and-braces on top of it.

**Own and foreign are two reads, one list.** `GET …/requests` returns what the caller raised;
`GET …/requests/foreign` returns what sits on accounts they are responsible for. The **server**
decides the second set, so membership of it — not any rule the app applies — is what puts the
approval action on a row. A request on both reads counts as the caller's own: nobody approves
their own.

**The approval model is two-step and single-vote** (main repo `REQ-BANK-041`). A request over the
caller's limit is flagged (`requiresOwnerApproval`); one holder of the class named in
`requiredApprover` grants it (`ownerApprovalGranted`); only then may a bank employee confirm it.
For the KRT account the amount ladder (`REQ-BANK-047`) escalates **which class** must grant —
responsible holder → Bankleitung → Organisationsleitung — and never how many must. The row's chip
therefore names the class it is waiting on. **There is no approval counter anywhere in this
feature**, and the artboards' „1 / 2 FREIGABEN" is a mechanism the API does not have; see
[ADR-0016](../adr/0016-the-app-renders-the-approval-the-api-has.md).

**A deposit is never approval-limited** (main repo `REQ-BANK-042`), so the sheet's threshold line
is absent while EINZAHLUNG is selected, absent for a caller the account exempts, and absent when
the account sets no limit. Otherwise it states the account's own `approvalLimit` and flips from
*what will apply* to *what now applies* as the typed amount crosses it.

**Eligibility comes from the server.** A withdrawal or transfer may only name an account whose
`canRequest` is set; a deposit may name any active one.

**Acceptance**

- [x] Own and foreign merge into one list, own rows are never actionable, and a request on both
  reads stays the caller's (`BankRequestsViewModelTest`).
- [x] The tab badge counts only `PENDING` requests, so nothing the member cannot clear leaves a
  badge behind (`BankRequestsViewModelTest`).
- [x] The create, the edit and the withdrawal echo the version they read; the two approval
  verbs send no body at all (`BankRequestsViewModelTest`).
- [x] No state of the sheet or of a row states a number of approvals; the chip names the approver
  class (`BankRequestScreenTest`).
- [x] A deposit, an exempt caller and a limitless account each get no threshold line
  (`BankRequestScreenTest`).
- [x] A holder may approve and revoke but is offered no reject (`BankRequestScreenTest`).
- [x] An approved request of one's own can no longer be edited, only withdrawn
  (`BankRequestScreenTest`).
- [x] The edit sheet opens on a typeable amount rather than the server's storage scale
  (`BankRequestsViewModelTest`).

**Reachability.** None of these paths were on the mobile vhost's allow-list, and the local test
stack is reached **directly**, with no vhost in front of it — so a full device verification cannot
show the gap. The main repo's `API_VHOST_ROLLOUT_RUNBOOK.md` § Phase K adds them, and until that
block is applied to the host the feature works in development and answers `404` in production.
The same is true of `/api/v1/users/{id}/memberships`, which the Lager's Umbuchen picker needs.

**Code:** `BankRepository` (`BankRequestSource`), `BankRequestsViewModel`, `BankRequestsTab`,
`BankRequestSheet`

---

### REQ-APP-BANK-009 — The staff queue, and why confirming cannot be a button

The Verwaltung scope's **Anträge** tab — design chapter 12, artboard 5 — and the two decisions a
bank employee makes on it.

**The queue is the same read the Übersicht's counter is aggregated from**
([`REQ-APP-BANK-008`](bank.md) has the member half). One scope, one state: a badge that counted a
different read from the list it labels would be a defect waiting for a slow page.

**Confirming is a sheet, not a button, and the artboard is wrong about that.** Artboard 5 draws
`✓ BESTÄTIGEN` as a bare CTA. `ConfirmBankBookingRequest.holderId` is `@NotNull` — a booked deposit
or withdrawal records which Verwahrer received or paid the money out (main repo REQ-BANK-040/-044)
— and an over-limit request is additionally refused with `BANK_OWNER_APPROVAL_REQUIRED` unless the
employee attests that the responsible holder approved (REQ-BANK-041). A bare CTA would post a body
the server rejects, every time. The web frontend has a modal for exactly this („Antrag bestätigen …
Erfasse den Halter, der das Geld erhalten bzw. ausgezahlt hat"), and so does the app. The gap went
to the design side rather than being coded around.

The sheet carries the holder picker, a second one for a transfer's receiving holder, the
attestation checkbox **only** when the request is flagged, and the employee's own note
(REQ-BANK-054). Beside the checkbox it states what the server already knows — whether the
responsible holder has granted their approval — so the employee ticks a box they can check rather
than one they must take on trust.

**Refusing needs a reason.** `RejectBankBookingRequest.reason` is required and the requester is
shown it, so an empty one is not sent. It is asked for in the danger modal, which names the
consequence: no money moves.

**`ABLEHNEN` and `BESTÄTIGEN` are correct here**, unlike on artboard 1. This is the surface that
has `POST …/confirm` and `POST …/reject` behind it; the member surface has neither.

**Acceptance**

- [x] A confirmation without a holder is not submittable, and neither is a flagged one without the
  attestation or a transfer without its receiving holder (`BankStaffViewModelTest`).
- [x] The confirmation sends the holder, the note and the version it read
  (`BankStaffViewModelTest`).
- [x] A refusal with a blank reason is not sent at all; one with a reason sends it trimmed, with
  the version (`BankStaffViewModelTest`).
- [x] Only **active** holders are offered — an inactive one is kept for the ledger's sake, not for
  a new booking (`BankStaffViewModelTest`).
- [x] The staff queue shows both decisions and still no approval counter
  (`BankStaffScreenTest`).
- [x] A queue too long to walk out says so **where it ends**, rather than stopping silently
  (`BankStaffScreenTest`, ADR-0104).

**Code:** `BankRepository` (`BankStaffSource`), `BankStaffViewModel`, `BankStaffQueue`

---

### REQ-APP-BANK-010 — The staff dashboard is two screens, and the second one has no totals

`GET /api/v1/bank/dashboard` answers differently by role (main repo REQ-BANK-010), and the app has
to render both shapes rather than one:

| Caller | Accounts | `totals` |
| --- | --- | --- |
| **Bank-Management** (or admin) | **every** account in the organisation | the aggregate strip |
| a plain **bank employee** | exactly the accounts they hold a bank grant for | **`null`** |

**An absent strip is not a strip of zeroes.** Folding `null` into `BankStaffTotals(null, 0, 0)` had
the screen tell an employee „GESAMT 0 aUEC · 0 Konten" — a claim that the organisation's bank is
empty, made to the person least able to check it. Found by running it: the member tab showed an
account while the staff tab showed none and asserted zero underneath.

Three consequences the screen honours:

1. The KPI band renders **only** when the server sent one.
2. The empty state distinguishes the two emptinesses. For management, no accounts means the
   organisation runs none. For an employee it almost always means **they hold no grant**, and
   saying "this org unit runs no bank account" would be false.
3. **„ohne eigenen View-Grant" is management-only.** An employee's list is already grant-shaped, so
   every row would carry the mark and it would say nothing. Only a caller who sees beyond their own
   grants can have a row they reach purely through their office.

Note the two grant systems are **different**: the staff list is shaped by `bank_account_grant`, the
member list by the org-unit visibility of `REQ-BANK-037`. An account can therefore be on the member
list and absent from the staff one — the reverse of the naive assumption.

**Acceptance**

- [x] An employee gets no aggregate strip, and no zeroes standing in for one
  (`BankStaffScreenTest`).
- [x] An employee's rows carry no view-grant mark; management's do (`BankStaffScreenTest`).
- [x] The empty state names the right emptiness for each (`BankStaffOverview`).
- [x] Verified on a device against a locally built backend: a bank employee with no grant sees the
  refusal-shaped empty state, and one with a grant sees the account and still no strip.

**Code:** `BankRepository` (`BankStaffDashboard.totals` is nullable), `BankStaffOverview`

---

### REQ-APP-BANK-011 — The account lifecycle, drawn locked rather than hidden

The Verwaltung scope's **Konten** tab — design chapter 12, artboard 6: the account list with its
lifecycle, and the unit's holder register beneath it.

**Reads are the employee's, writes are Bank-Management's.** Which the caller is comes from the
server (`BankStaffDashboard.management`), never from a role the app worked out. Without the role
the actions are drawn **locked, not hidden** (chapter-09 pattern): a padlock that answers when
tapped — „Dafür brauchst du die Rolle Bank-Management." A member who cannot see a control cannot
learn that the surface exists or which role opens it.

**Every write echoes the version it read.** That is why the tab has its own read
(`GET /bank/accounts`) rather than reusing the dashboard's rows, which carry no `version`.

**Closing is reversible, so nothing here asks the member to type anything.** The type-to-confirm
hurdle is reserved for what cannot be undone. Two preconditions the server enforces are stated in
advance rather than left for the button to discover:

- **A non-zero balance blocks closing** („Nur Konten mit Saldo 0 können geschlossen werden") — the
  row says so beneath the disabled action.
- Undecided booking requests block it too; that one surfaces as the server's refusal.

**Deactivating a holder is not a removal.** `UpdateBankHolderRequest` flips one flag, and the
wording is the web frontend's own: an inactive holder can have no *new* money assigned to them,
what they already hold stays withdrawable. Nothing about their rights on an account changes —
that is what a grant does, and it lives on the Grants tab.

**A creation names the caller's pinned org unit** („Einheit (vorbelegt)"). A caller who has pinned
*all* units has no single answer, so the creation is refused rather than opened against a unit they
never chose. The app creates `ORG_UNIT` accounts only; `AREA`, `CARTEL`, `CARTEL_BANK` and
`SPECIAL` exist on the wire and stay the web's.

**Acceptance**

- [x] Closing, reopening, renaming and a holder's activation each echo the version they read
  (`BankLifecycleViewModelTest`).
- [x] A rename to blank is not sent (`BankLifecycleViewModelTest`).
- [x] With all units pinned, no account is created (`BankLifecycleViewModelTest`).
- [x] A refused write keeps the confirmation open and states why (`BankLifecycleViewModelTest`).
- [x] A holder register that cannot be read leaves the accounts standing
  (`BankLifecycleViewModelTest`).
- [x] Verified on a device: without Bank-Management the row actions and the CTA are drawn with
  padlocks and answer when tapped.

**Code:** `BankRepository` (`BankLifecycleSource`), `BankLifecycleViewModel`, `BankLifecycleTab`

---

### REQ-APP-BANK-012 — The grants matrix has three flags, and the row is the sight

The Verwaltung scope's **Grants** tab — design chapter 12, artboard 7: who may book on which
account. („Grants", not „Freigaben" — in this app „Freigabe" already means approving a booking
request, and the design chapter keeps the two words apart.)

**A grant is one `bank_account_grant` row per (member, account) with three independent flags** —
`can_deposit`, `can_withdraw`, `can_transfer` (REQ-BANK-009). The app renders exactly those three
and no fourth: the drawn „FREIGEBEN" column has no counterpart on the server under either reading
of the word. See `MISSING_ARTBOARD_PROMPTS_7.md` § 2e.

**The row's existence is the view grant.** `BankSecurityService.canSee` is
`hasCapability(accountId, auth, g -> true)`, so a row with all three flags false is the deliberate
„darf sehen, darf nichts buchen" case. The app therefore never deletes a row on the way to zero
flags, and states the rule as plain text on the surface rather than behind a tooltip.

**Taking sight away is a removal**, offered as „Eintrag entfernen" and confirmed by a danger modal —
it is the one action here whose consequence no checkbox on the card mentions.

**On the CARTEL account the entry never carried the sight.** Every KRT member sees that account by
rule (REQ-BANK-037), so both the surface note and the removal modal swap to copy that promises only
what the server delivers: booking rights go, sight does not.

**The flag coupling the handoff asks for is structural, not UI.** „Freigeben setzt Sehen voraus"
cannot be violated: every capability check runs through `hasCapability`, which requires the row, and
the row is the sight. No UI coupling and no backend requirement follow from it.

**Reads are the employee's, writes are Bank-Management's**, and as everywhere in the staff surface
the app draws what the server answered (ADR-0016). Without the role the **tab** is locked-tappable
and answers with a toast naming the role (artboards 4 and 7); the padlocked controls inside it are
the fallback for the case where the role is lost while the tab is already open.

**Whether a change is a creation or a patch comes from the matrix read, never from the version.** A
freshly inserted `bank_account_grant` carries `@Version` zero, so `version == 0` does not mean "not
on the server yet" — deciding on it sends the first edit of every untouched grant as a creation and
earns `409 DUPLICATE_ENTITY`. `BankGrant.exists` carries the fact instead.

**A refused change is stated, not swallowed.** Without it the checkbox simply snaps back, which
reads as a broken app rather than a server that said no.

**Known gap: the tab cannot add an entry.** Artboard 7's „+ Grant hinzufügen" is not implemented —
it needs a member picker restricted to holders of the Bank Employee role, which the server requires
for a creation (REQ-BANK-008). Until it lands, the removal modal says so instead of promising a
re-entry the app cannot perform.

A **per-member card** replaces the drawn table: three capability columns plus a handle do not fit a
phone's width the way two short ones did. The account selector stays the drawn chip row, made
horizontally scrollable so a unit with many accounts does not lose its last one off the edge.

**Acceptance**

- [x] Three capability rows render and no approval one (`BankGrantsScreenTest`).
- [x] Unticking the last flag sends a change, not a removal (`BankLifecycleViewModelTest`).
- [x] The removal asks first and only then sends (`BankLifecycleViewModelTest`).
- [x] A flag change echoes the version it read (`BankLifecycleViewModelTest`).
- [x] The view-grant note is plain text on the surface, not a tooltip (`BankGrantsScreenTest`).
- [x] On a `CARTEL` account the copy claims no sight it cannot take away (`BankGrantsScreenTest`).
- [x] Without Bank-Management the controls are drawn locked and answer when tapped
  (`BankGrantsScreenTest`).
- [x] Switching accounts replaces the matrix rather than appending to it
  (`BankLifecycleViewModelTest`).
- [x] A row the server already holds is patched even when its version is zero
  (`BankLifecycleViewModelTest`).
- [x] A refused flag change reaches the state rather than being swallowed
  (`BankLifecycleViewModelTest`).
- [x] Verified on a device against the local test stack: a Bank Employee **without** Bank Management
  (`test-admin`) gets the locked-tappable tab and the role toast; Bank Management
  (`test-bank-management`) gets the KPI band, the matrix, a `PATCH` that lands (`200`, version
  bumped, both directions) and the danger modal.
- [ ] „+ Grant hinzufügen" (artboard 7) — not implemented; see the known gap above.

**Code:** `BankStaffRepository` (`BankGrantSource`), `BankLifecycleViewModel`, `BankGrantsTab`
