# Design gaps, round 16 — the Direktbuchung, once it could actually be reached

**Date:** 2026-09-03 · **Previous:** `MISSING_ARTBOARD_PROMPTS_15.md`.

Three items, all on **chapter 12 artboard 9**, and all surfaced by the same thing: the sheet was
built in round 8 and has been answering `404` in production ever since, because the API vhost's
allow-list excluded its four paths on the ground that *no artboard draws them*. Artboard 9 does.
The rules are in place now (main-repo runbook phase O, ADR-0156), and the moment the sheet could be
reached, three questions the artboard settles wrongly or not at all became live.

Neither item is blocked. Each says what the app does **today**, so the design side is ratifying or
overriding a known state rather than a guess.

| § | Kind | What we need |
| --- | --- | --- |
| **D1** | Built **deliberately different** from the drawing | Ratify, or overrule |
| **E1** | **Not drawn at all** | Draw it, or ratify the fallback |
| **E2** | **Not drawn at all** — five fields the web has | Draw them, or tell us to trim |

---

## D1 · Artboard 9's role gate names the wrong role

**Where:** `12 Bank.dc.html`, artboard 9, the state list — *„403 (Rolle Bank-Management fehlt) =
gesperrt-antippbar schon am Einstieg"*.

**What the endpoints actually ask for.** All four paths behind the sheet gate on
`hasRole('BANK_EMPLOYEE')`. The three bookings add a **per-account** grant on top — `canDeposit` /
`canWithdraw` / `canTransfer` — with Bank-Management and Admin unrestricted
(`BankSecurityService.hasCapability`). Bank-Management is therefore *sufficient* and never
*necessary*.

**What that lock cost.** The app followed the artboard and locked the entry on `canManageBank`. A
plain Bankmitarbeiter holding `can_withdraw` on one account — **exactly the caller the Grants tab
exists to create** — could book directly in the web and was refused at the entry in the app. The
web has never asked for more than the page: its „Kontobewegung" CTA appears whenever at least one
active account is visible, and the per-account half is the backend's 403.

**What the app does now.** The button carries **no** role check. The Verwaltung scope is already
unreachable without `bankEmployee`, so everybody standing in front of the CTA holds what the
endpoint asks for. The per-account half is a fact about the account picked *inside* the sheet, so it
cannot be pre-empted at the entry and arrives as the 403 the sheet shows.

**„Konto anlegen" beside it keeps its Bank-Management lock**, and that is the point of the pair:
creating an account really is a management act and its endpoint gates on it. One locked CTA and one
open one, side by side, is the honest drawing.

> **What would settle it:** the state list's third bullet rewritten. Either drop the 403-at-entry
> state for this CTA entirely, or replace it with the two real refusals — „kein Bankmitarbeiter"
> (which never reaches this screen) and „keine Berechtigung auf diesem Konto" (a 403 *after* the
> account is picked, inside the sheet).

---

## E1 · A booking that is filed rather than booked has no drawn state

**Where:** `12 Bank.dc.html`, artboard 9. The state list covers four outcomes — validation-dimmed
CTA over the balance, CTA spinner while writing, 403 at the entry, and *„Erfolg = Sheet zu, neue
Buchung oben, Toast"*. There is a fifth, and the server has always had it.

**Over the KRT employee ceiling a withdrawal or an Umbuchung is not refused — it is filed.** The
backend raises a band-routed approval request instead of booking, and answers `202` with a
`pendingRequest` where a booking carries a `transaction` (main repo REQ-BANK-047, ADR-0109). Both
are 2xx. So the artboard's success state is drawn over two outcomes that differ in the one way that
matters: whether the money moved.

Until 2026-09-03 the app read neither — it treated any 2xx as success, closed the sheet and re-read
the dashboard. On a `202` the member saw the sheet close and the **old balance**, with nothing
explaining either. The web says it out loud (`approvalRequestFiledMessage()`).

**What the app does now**, as a fallback pending a drawing:

- The sheet still closes — the attempt *was* accepted, and holding the form open would suggest it
  was not.
- A `KrtToast` follows: **„Zur Freigabe eingereicht"** / *„Der Betrag liegt über deinem
  Direktbuchungs-Limit. Nichts wurde gebucht — die Buchung steht jetzt als Antrag in der Freigabe.
  Der Kontostand ändert sich erst nach der Freigabe."*
- It carries its own **acknowledgement** (OK) rather than a timeout, and it survives the dashboard
  reload that the booking itself triggers. A message saying the money did not move is one a member
  must not be able to miss.

> **What would settle it:** a fifth state on artboard 9 — or, better, a decision on whether the
> ceiling belongs *ahead* of the CTA. The sheet already draws „Stand nach Buchung" live; if the
> ceiling were known client-side it could say „wird zur Freigabe eingereicht" **before** the tap,
> which is where the artboard puts every other consequence („keine zweite Freigabe" stands above
> the CTA for exactly that reason). That needs a wire field the app does not have today — the
> ceiling is not on any response it reads — so it is a backend ask, not a redraw. Tell us which of
> the two you want and we will file the ask.

---

## E2 · Five fields of the web's „Kontobewegung" that artboard 9 does not draw

**Where:** `12 Bank.dc.html`, artboard 9. The drawing has the segment, the account, the amount, the
holder, the note and — on a transfer — the second account and holder. The web's modal has five more,
and a booking made from the app carried less than the same booking made in a browser.

They are built now, because the gap was worse than a missing field: `REQ-APP-BANK-012` and the
0.2.1 changelog both told members the counterparty and the reason were „now sent". They were on the
wire and nothing could fill them.

| Field | Modes | Why it is not optional to us |
| --- | --- | --- |
| **Begründung** | Auszahlung · Umbuchung | **Required** on a `CARTEL` / `CARTEL_BANK` / `SPECIAL` account. Without it the server answers `BANK_JUSTIFICATION_REQUIRED` — a 409 collected after everything else is typed. This is not a nicety; it is what makes a KRT withdrawal possible from a phone at all. |
| **Notiz Bankmitarbeiter** | all three | Internal, redacted from the org unit's own members (REQ-BANK-054). A second field, not a longer note: the two have different readers. |
| **Einzahler / Empfänger** | Einzahlung · Auszahlung | REQ-BANK-044. Either a member picked from the bank's own user search, or — behind „Kein Tool-Account" — a typed name. |
| **Einheit** | Einzahlung · Auszahlung | Which unit the counterparty acted for; independent of the two above. |
| **Aufteilung** | Einzahlung | Toggle plus a 1–100 share, with a live preview. |

**How they are laid out today**, pending a drawing: the counterparty is a labelled group
(„Einzahler" / „Empfänger") holding the toggle, then either the picker or the name field, then the
unit select and one hint. The split is a toggle that reveals its percentage and a preview line, the
same shape as the fee block directly beneath it. Begründung and Notiz Bankmitarbeiter are plain
fields in the flow, each with its hint.

> **What would settle it:** artboard 9 redrawn with the five, in whatever order and grouping the
> chapter wants — or a decision to **trim** some of them for a phone. Trimming is a real answer:
> the split and the unit are the two we would miss least. Begründung is the one that cannot go,
> because the server refuses without it.

> [!note] The sheet is getting long
> Nine fields on a deposit with the counterparty group open. The web has the room of a modal on a
> desktop; this is a bottom sheet on a 412 dp phone. If the chapter wants a fold („Weitere
> Angaben") we will build it — that is a layout decision, and the artboard is where it belongs.
