# Operationen — list and detail

> **Doc type:** Living spec · **Area:** `REQ-APP-OPS-*` · **Design:** `docs/design/android/06 Missionen.dc.html` (§1 segment, §5 detail)
> **Server contract:** main repo `REQ-API-009` (contract set), `REQ-SEC-037` (vhost allow-list)
> **Related:** [`missions.md`](missions.md) (`REQ-APP-MIS-*`), [`api-contract.md`](api-contract.md)

The Operationen list — the second half of the Einsätze screen's segment — and the Operation
detail. Everything here is **read-only**: the design's manager payout toggles are mutations and
belong to Phase 3, which is why this screen shows the payout *state* and no action.

---

### REQ-APP-OPS-001 — The segment navigates; it does not toggle

The "Einsätze / Operationen" control above both lists switches **navigation destination**. Both
halves are already destinations in their own right — Einsätze in the bottom bar, Operationen in the
"Mehr" list (design ch. 03) — so a local toggle would give each list a second address and leave the
navigation bar highlighting a root the member is no longer on.

The two view models are hoisted to the activity, so switching back and forth shows the list rather
than reloading it.

**Acceptance**

- [x] The segment renders above the search field on both screens, spanning the row
  (`KrtSegmentedControl(stretch = true)`) — the fixed 52 dp of design ch. 13 cannot hold the word
  "Operationen".
- [x] Tapping the half that is already selected does nothing; tapping the other navigates.
- [x] `KrtDestination.OperationDetail` maps to `KrtDestination.Operations` in `SUB_DESTINATIONS`, so
  an open Operation keeps the bar on the list it was opened from.

**Code:** `ListSegmentBar`, `OperationsScreen`, `MissionsScreen`, `BasetoolNavHost`

---

### REQ-APP-OPS-002 — The list is loaded when the segment is first opened, not before

The Operationen list sits behind a segment. Loading it on construction would spend a request on
every app start for a member who never taps it. `loadOnce()` is called by the graph when the
destination is shown and is idempotent: coming back to a list that is already there shows it.
Pull-to-refresh is how a member asks for fresh rows.

**Acceptance**

- [x] Constructing the view model issues no request (`OperationsViewModelTest`).
- [x] Showing the segment twice reads once.
- [x] `onRefresh` re-reads while keeping the rows on screen.

**Code:** `OperationsViewModel.loadOnce`

---

### REQ-APP-OPS-003 — The row is thinner than the design mock, by owner decision

The design's list row shows "2 Einsätze · 18 Teilnehmer" and a payout chip. `OperationDto` carries
none of it, and the backend's own documentation states why: the bulk endpoints "have no reason to
spend the extra count query". The web list shows the same three fields this row does.

Widening the backend — a dedicated list DTO with `missionCount`, `participantCount` and a payout
state, computed in grouped queries per page — was put to the repository owner on 2026-08-22 and
**declined**. The counts live on the detail, where they are loaded anyway.

This is therefore an **approved deviation** from design ch. 06 §1, recorded here rather than left as
a silent difference between the mock and the screen.

**Acceptance**

- [x] The row draws name, status badge and description; nothing that would need a second read.
- [x] No per-row request is made — the failure mode this decision rules out is N+1 over the network.

**Code:** `Operation`, `OperationRow`

---

### REQ-APP-OPS-004 — The list groups by running versus finished, not by date

An Operation has no start time of its own; the server filters on its earliest and latest linked
Einsatz. A date grouping would therefore have to invent a date, and "Vergangene aus" — the Einsatz
list's chip — has no meaning here at all: the finished ones are the list's second group.

Grouping is applied to the rows **already loaded**, so a heading never claims more than the page
behind it holds.

**Acceptance**

- [x] `PLANNED` and `ACTIVE` fall under "Laufend"; `COMPLETED` and `CANCELED` under "Abgeschlossen"
  (`OperationsScreenTest`).
- [x] An empty group renders no heading.
- [x] `OperationQuery` carries no `includePast` — a flag that silently does nothing on half a
  screen is worse than its absence.

**Code:** `Operation.isRunning`, `OperationsList`

---

### REQ-APP-OPS-005 — `CANCELED` here, `CANCELLED` on an Einsatz

The backend spells the terminal status with one `L` on an Operation and two on a mission. The client
mirrors the server rather than tidying it: matching the wire value is exactly what keeps the badge
off `UNKNOWN`, and "correcting" the spelling here would break the case the mapping exists for.

**Acceptance**

- [x] A `CANCELED` row maps to `OperationStatus.CANCELED` and is not running
  (`OperationRepositoryTest`).
- [x] An unrecognised status maps to `UNKNOWN` and the badge shows the raw server value — an
  untranslated word is a smaller failure than a missing badge, which reads as "no status".

**Code:** `OperationStatus.from`

---

### REQ-APP-OPS-006 — The detail is three reads with one outcome

`GET /operations/{id}`, `/finance-summary` and `/payouts` are fetched together and fail together.
Unlike the Einsatz detail — whose Finanzen tab sits behind a *second* permission and therefore has
its own load state — all three of these carry the identical
`isAuthenticated() and canSeeOperation(#id)` gate. A member who may open the Operation may read all
of it, so a split state would model a case the server cannot produce.

The head needs the payouts anyway: the participant count and the per-head share come from there.

**Acceptance**

- [x] A refusal on any of the three fails the screen rather than rendering a head over missing
  figures (`OperationRepositoryTest`) — an Operation shown with no roll-up claims it earned nothing.
- [x] One scrolling page, not tabs: three short sections a member reads together.
- [x] Refresh keeps the content on screen while it runs.

**Code:** `OperationRepository.overview`, `OperationDetailViewModel`

---

### REQ-APP-OPS-007 — "Dein Anteil" is found by user id, never by name

A payout row is keyed by the **backend user id**. The app holds the Keycloak `sub`, which is a
different identifier, so it reads its own id once from `GET /api/v1/users/me` and matches on that.

Matching by name was the alternative and is wrong: the server sends `displayName` when a member set
one and `username` otherwise, so a name match would work for some members and silently fail for
exactly those who personalised their profile — and two members may carry the same display name,
which is free text.

The identity read is **never fatal**. The screen's subject is the Operation; a failed lookup costs
one line, not the screen. "Du bist nicht beteiligt" is claimed **only** when the id is actually
known — saying it because a request failed would be a statement about the member made out of an
outage.

**Acceptance**

- [x] Two rows with the same display name are told apart by id (`OperationDetailViewModelTest`).
- [x] A failed identity read leaves the phase `Ready` and shows a dash, not the "not involved"
  sentence.
- [x] A refresh does not re-read an id it already has, and does retry one that is still missing.
- [x] Only the id is kept in memory — the response also carries the member's email, roles and rank.

**Code:** `IdentityRepository`, `OperationDetailState.myPayout`

---

### REQ-APP-OPS-008 — The roll-up shows net and donations, not an income/expense split

The design mock shows Einnahmen / Ausgaben / Netto. The server's roll-up carries one figure per
Einsatz and one for the Operation; there is no split anywhere in the API. Deriving it would mean
fetching every finance entry of every Einsatz and adding money up on the device — a per-Einsatz
round trip and a figure this client computed rather than read.

The web operation page shows the same net-plus-donations pair. Second approved deviation from ch. 06
§5, on the same reasoning as `REQ-APP-OPS-003`.

The per-head share is the server's own per-row figure, **not** the net divided by the head count:
the split is weighted by how long each member actually took part, so dividing here would print a
number the payout list contradicts row by row.

**Acceptance**

- [x] Net and donations come from the server verbatim; nothing is added, divided or rounded on the
  device.
- [x] The share label states the participant count so the figure is readable as a per-head one.

**Code:** `RollupBlock`

---

### REQ-APP-OPS-009 — A capped list and a provisional figure both say so

Two server fields qualify a number rather than carrying one, and both are surfaced:

- **`truncated`** — the per-Einsatz roll-up is capped (main repo ADR-0104). The note has to say that
  the net figure above still covers every Einsatz, or a member reads the shortfall into the total.
- **`payoutPreliminary`** — some Einsatz of this Operation has no actual end time, so the payout
  figures may still rebalance. It is authoritative on the detail endpoint and nowhere else.

`payoutPreliminary` is nullable and `null` means **not computed**. Nothing is claimed in that case:
a warning invented from an absent field puts a caveat on figures that may well be final.

**Acceptance**

- [x] `truncated = true` renders the note; `false` renders nothing (`OperationsScreenTest`).
- [x] `payoutPreliminary = true` renders the warning; `null` renders nothing.
- [x] A missing `truncated` is read as `false` rather than as unknown-and-therefore-warn.

**Code:** `OperationDetailHead`, `OperationDetailBody`

---

### REQ-APP-OPS-010 — Amounts are formatted, never recomputed

The wire carries a fixed-scale decimal (`86400.0000`). It is parsed as `BigDecimal`, stripped of the
padding zeros and grouped for the locale by the shared `formatAmount` of `REQ-APP-MIS-011` — no
`Double` ever touches it, which is how a total gains a rounding error the server never had.

A payout row's chip carries the fact that explains its amount: "Verzicht" for a donating member
(whose share went to the org treasury, so the payout is zero by construction), otherwise whether it
has been paid. "Offen" is drawn neutral, not as a problem.

**Acceptance**

- [x] Every digit the server sent survives the mapping (`OperationRepositoryTest`, 17-digit guard in
  `MissionAmountsTest`).
- [x] `86400.0000` renders as `86.400` in German (`OperationsScreenTest`).

**Code:** `MissionAmounts.formatAmount`, `PayoutRow`

---

### REQ-APP-OPS-011 — Refused, gone and broken are three different sentences

As on the Einsatz detail: `403` is "this Operation is not yours to see", `404` is a stale link, and
anything else is an outage. One message for all three would leave a member retrying something that
will never succeed.

**Acceptance**

- [x] Each maps to its own title and message (`OperationsScreenTest`).
- [x] The classified cause is never shown to the member; it is logged instead.

**Code:** `OperationDetailFailure`

---

## Known gaps, stated rather than omitted

- **The manager payout toggles are absent.** Design ch. 06 §5 shows them with an asymmetric gate —
  marking is immediate, taking it back needs a confirmation modal naming the consequence. Both are
  mutations (Phase 3), and the write path `PUT /operations/{id}/payouts/paid-out` is deliberately
  **not** on the API vhost's allow-list.
- **The per-Einsatz row shows no participant count.** `MissionListDto` does not carry one either, so
  the row states the name and the result. Tapping it opens the Einsatz, where the count is on the
  head.
- **No date-range chip.** `OperationQuery` carries `from`/`until` and the repository sends them, but
  no chip opens a picker yet; it ships with the bottom-sheet pickers of design ch. 02, together with
  the Einsatz list's.

## Contract-set dependency (main repo)

`GET /api/v1/operations/search`, `/{id}`, `/{id}/finance-summary`, `/{id}/payouts` and
`GET /api/v1/users/me` are in the `REQ-API-009` contract set and the vhost allow-list as of the main
repo's "freeze the Operationen reads" change. The allow-list lives in the NPM admin database and is
applied by hand; until the block is pasted, these screens work against the test stack only, and the
nightly `edge-deny-probe` is what reports that it has not been.

---

### REQ-APP-OPS-012 — The roll-up's "Anteil je Teilnehmer" is what was earned, and a range when the rows differ

The figure beside `Anteil (N)` is each participant's **earned** share: `shareAmount` for a member
taking the payout, `donatedAmount` for one who waived it. When those differ across the rows — the
pool is split by how long each member took part — both ends are named (`2.075 – 4.150`) instead of
one of them.

The server sets `shareAmount` to zero for a donating participant by construction and moves the
figure to `donatedAmount`. Reading the first payout row's `shareAmount` therefore printed
`ANTEIL (2): 0` on an Operation whose first row happened to be a donor, directly above a payout
list showing what everybody actually earned — found on a device. Dividing the net by the head count
instead would print a different wrong number, since the split is weighted.

**Acceptance**

- [x] A donating first row yields the amount they gave away, not zero (`OperationsScreenTest`).
- [x] Unequal shares render as `min – max` (`OperationsScreenTest`, `OperationRepositoryTest`).
- [x] No range at all when any participant has no share figure: one computed from a subset would
  understate the spread without saying so (`OperationRepositoryTest`).
- [x] **Observed on a device (2026-08-22)** on an Operation with one donating and one paid-out
  participant.

---

### REQ-APP-OPS-013 — The payout confirmation is a mission manager's, and the rescind is not predicted

`PUT /api/v1/operations/{id}/payouts/paid-out`. The gate carries **two** roles in one expression:
confirming needs `MISSION_MANAGER`, and taking a confirmation back needs `OFFICER` or `ADMIN` on
top. `/users/me` answers the first and not the second.

So the app offers the control to a mission manager, in **both** directions, and names the refusal
when the second one comes back — *"Für diese Auszahlung fehlt dir die Berechtigung."* Hiding the
rescind from every mission manager would hide it from the officers who may use it; offering it
without naming the refusal would read as the app being broken.

A payout row the server sent without a participant key carries no action: it cannot be addressed,
and a control that can only 400 is not a control.

**The Operation is re-read after a confirmation, not patched.** The payout totals move with it.

**Acceptance**

- [x] A non-mission-manager is offered no confirmation (`OperationDetailViewModelTest`,
  `OperationsScreenTest`).
- [x] A row without a participant key is offered none either (`OperationDetailViewModelTest`).
- [x] A confirmation re-reads the Operation (`OperationDetailViewModelTest`).
- [x] A `403` is worded as this payout's refusal (`OperationsScreenTest`).
- [x] **Observed on a device (2026-08-23):** confirmed (`200`, `OFFEN` → `AUSGEZAHLT`), then the
  rescind refused with `403` and the sentence shown — the caller being a mission manager and not an
  officer.

**Code:** `OperationRepository.setPaidOut`, `OperationDetailViewModel.onTogglePaidOut`
