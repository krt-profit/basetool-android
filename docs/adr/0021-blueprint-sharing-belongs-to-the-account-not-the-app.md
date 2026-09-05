# ADR-0021 — „Blueprints mit Org teilen" belongs to KONTO, and artboard 13-2 is overruled

- **Status:** Accepted
- **Date:** 2026-09-05
- **Deciders:** @greluc
- **Related:** ADR-0010 (the precedent for deciding which section a settings row sits in),
  `REQ-APP-SET-011`, design ch. 13 artboard 2,
  `docs/design/android/MISSING_ARTBOARD_PROMPTS_17.md` § D1,
  main repo `docs/API_VHOST_ROLLOUT_RUNBOOK.md` § Phase Q

## Context

Einstellungen groups its rows into **KONTO**, **APP** and **RECHTLICH**. Two of its rows are not
device preferences at all: „Auszahlungspräferenz" and „Blueprints mit Org teilen" are columns of
the backend's `User` entity, read and written over the API, sharing one optimistic-lock version.

The two documents that govern the screen disagreed about where the second one goes, and had done
since it was built:

- **`REQ-APP-SET-011`** is titled *„Three account rows, and the one version two of them share"* and
  opens: *„Design ch. 13, artboard 2 puts three things in KONTO beyond the member's name. Two of
  them are server values; the third is the scope the top bar already shows."* The two server values
  are the payout preference and blueprint sharing.
- **Artboard 2 itself** draws only two rows under KONTO — the active org unit and the payout
  preference — and puts „Blueprints mit Org teilen" under **APP**, next to „Sprache".

The implementation followed the artboard, which is the correct default: the design handoff outranks
behavioural prose. Nobody noticed the requirement said otherwise, because the sentence describes
the artboard rather than prescribing a layout, and reads as a summary of it.

The contradiction surfaced on 2026-09-05, when the owner asked for the row to be moved to KONTO
while both rows were being repaired for an unrelated reason (phase Q — the reads they are drawn
from were refused at the API vhost, so both sat greyed out on every account).

## Decision

**„Blueprints mit Org teilen" moves to KONTO, directly below „Auszahlungspräferenz".** Artboard 2 is
overruled on this point; `REQ-APP-SET-011` was right and needs no change to its rule.

## Consequences

**The grouping now means one thing instead of two.** APP holds what lives on the device and survives
a logout — language, app lock, screenshot policy. KONTO holds what lives on the member's account.
Blueprint sharing is the second kind: it is written to the server, it is visible to the
organisation, and it follows the member to any device they sign in on. Placing it beside „Sprache"
grouped it by *where the control is* rather than by *what it changes*.

**The two rows that share a version now sit together**, which is the practical half. They share one
optimistic-lock counter, they fail together, and their retry is one action; a member who sees one
refuse has an immediate reason to look at the other. Separated by a section boundary, that
relationship was invisible.

**One row moves out of APP, and APP loses nothing else.** The trailing hairline moves with it.

**KONTO no longer hinges on the member's name, and that had to change with the move.** The group was
wrapped in `if (accountName != null)` — harmless while it held one row that *was* the name, and not
harmless once it holds the two account settings and the notice explaining why they are shut: a
member whose ID-token username had not resolved would have lost the explanation in exactly the
state it exists for. The group now renders when it has anything to show, and the name is one row
inside it rather than its precondition.

**This is a deviation from the binding handoff and is filed as one** — round 17 § D1 — so the design
side can redraw artboard 2 or say no. Until they do, the app and the requirement agree and the
artboard is the odd one out, which is the honest state rather than a silent divergence.

**What this ADR does not decide:** whether the *requirement's* sentence should keep describing the
artboard. It is now dated and marked as the point where the two parted; rewriting it to claim the
artboard always drew three rows would be a quiet rewrite of history, which the vault rule forbids
and the same reasoning forbids here.
