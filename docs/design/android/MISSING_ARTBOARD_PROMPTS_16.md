# Design gaps, round 16 — the Direktbuchung, once it could actually be reached

**Date:** 2026-09-03 · **Previous:** `MISSING_ARTBOARD_PROMPTS_15.md`.

Two items, both on **chapter 12 artboard 9**, and both surfaced by the same thing: the sheet was
built in round 8 and has been answering `404` in production ever since, because the API vhost's
allow-list excluded its four paths on the ground that *no artboard draws them*. Artboard 9 does.
The rules are in place now (main-repo runbook phase O, ADR-0156), and the moment the sheet could be
reached, two questions the artboard settles wrongly or not at all became live.

Neither item is blocked. Each says what the app does **today**, so the design side is ratifying or
overriding a known state rather than a guess.

| § | Kind | What we need |
| --- | --- | --- |
| **D1** | Built **deliberately different** from the drawing | Ratify, or overrule |
| **E1** | **Not drawn at all** | Draw it, or ratify the fallback |

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
