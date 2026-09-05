# Design gaps, round 17 — Einstellungen, once its two account rows could be read at all

**Date:** 2026-09-05 · **Previous:** `MISSING_ARTBOARD_PROMPTS_16.md`.

Two items on **chapter 13, artboard 2**, both surfaced the same way: „Auszahlungspräferenz" and
„Blueprints mit Org teilen" were greyed out on every account since the first release, because the
reads they are drawn from were refused at the API vhost (main repo, runbook phase Q). Repairing
that made the rows real for the first time — and the moment they were, two things the artboard
settles wrongly or not at all became live.

Neither item is blocked. Each says what the app does **today**, so the design side is ratifying or
overruling a known state rather than a guess.

| § | Kind | What we need |
| --- | --- | --- |
| **D1** | Built **deliberately different** from the drawing | Ratify, or overrule |
| **E1** | **Not drawn at all** — a fourth state the rows have | Draw it, or ratify the fallback |

---

## D1 · „Blueprints mit Org teilen" has moved to KONTO, and the artboard and the requirement never agreed

**Where:** `13 Einstellungen.dc.html`, artboard 2 — the row currently sits under **APP**, between
„Sprache" and the rest of the device settings.

**The two documents disagreed, and had since the screen was built.**

- `REQ-APP-SET-010` is titled *„Three account rows, and the one version two of them share"* and
  opens: *„Design ch. 13, artboard 2 puts three things in KONTO beyond the member's name. Two of
  them are server values; the third is the scope the top bar already shows."*
- **Artboard 2 draws two.** KONTO holds „Aktive Org-Einheit" and „Auszahlungspräferenz"; „Blueprints
  mit Org teilen" is under APP.

The app followed the artboard, which is the right default — the handoff outranks prose. Nobody
caught the requirement, because its sentence reads as a *description* of the drawing rather than a
rule, and describing it wrongly is a quiet failure.

**Moved to KONTO on owner instruction (2026-09-05), recorded as ADR-0021.** The reasoning, so the
design side can weigh it rather than just receive it:

- **APP holds what lives on the device and survives a logout** — language, app lock, screenshot
  policy. Blueprint sharing lives on the member's account: it is written to the server, it is
  visible to the organisation, and it follows them to any device they sign in on.
- **It shares one optimistic-lock version with „Auszahlungspräferenz"** directly above it. The two
  fail together, their retry is one action, and a member who sees one refuse has an immediate
  reason to look at the other. A section boundary hid that.

> **What would settle it:** artboard 2 redrawn with three rows under KONTO — or a decision that the
> drawing was right and the requirement's sentence is what should change. If you keep it in APP,
> say what the grouping means, because „lives on the device" is the only reading we could find that
> the current drawing satisfies, and this row does not.

---

## E1 · A row whose value could not be READ has no drawn state

**Where:** `13 Einstellungen.dc.html`, artboard 2. The two account rows have two drawn states — a
value, and „Noch nicht gewählt" for a value nobody has set. There is a third, and it is the one
that shipped for months.

**Both rows are drawn `enabled` only once their value has arrived**, and that is correct: a write
echoes the optimistic-lock version the read returned, so acting without one would either be refused
by the server or — on a row still at version `0` — succeed by accident against a version nobody
read.

**But a failed read renders identically to an unset value.** From the first release until
2026-09-05 the vhost admitted neither path, so both reads answered `404`, the app logged it, and
both rows sat greyed out reading „Noch nicht gewählt". Not a failure a member can report: two
settings simply appeared to have no value and no way to get one.

**What the app does now**, as a fallback pending a drawing:

- The rows stay **shut** — that part was always right.
- Below them, inside the KONTO group: *„Diese beiden Werte konnten nicht geladen werden. Sie liegen
  auf dem Server, nicht auf dem Gerät — bis sie da sind, bleiben beide Zeilen gesperrt."*
- A **„Erneut versuchen"** outline button, the same shape the licences screen already uses for a
  failed load, disabled while the retry is in flight.
- A refused **write** gets the same treatment — it had no place on the screen either: *„Die Änderung
  wurde nicht gespeichert. Die Zeile zeigt weiterhin, was der Server zuletzt bestätigt hat."*

> **What would settle it:** a third state on the two rows, or a ratification of the block below
> them. Worth deciding deliberately: an inline state per row is tidier but says the same sentence
> twice, and these two rows fail *together* by construction — they share the read and the version —
> so one message for both may be the more honest drawing rather than the lazier one.
