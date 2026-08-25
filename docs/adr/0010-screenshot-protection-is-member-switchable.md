# ADR-0010 — Screenshot protection becomes member-switchable, fail-closed

> **Status:** Accepted · **Date:** 2026-08-25 · **Deciders:** @greluc
> **Design:** `docs/design/android/13 Einstellungen.dc.html` (settings rows), ch. 04 (FLAG_SECURE)
> **Related:** `krt-profit/basetool-android#81`, `REQ-APP-AUTH-010`, `REQ-APP-SET-009`,
> [`0006-minsdk-30.md`](0006-minsdk-30.md)

## Context

`FLAG_SECURE` has been set app-wide and unconditionally since the first screen shipped. The
reasoning has not changed: the capture that matters is the one nobody takes deliberately — the
recents thumbnail the system grabs whenever the app leaves the foreground, which then sits in the
launcher for anyone who picks the phone up.

What the reasoning missed is the capture the project actually wants. A tester filed #81 after
trying to attach a picture of a defect to a bug report and getting a black rectangle. That is not an
edge case here: crash reporting is local-only by owner decision, with no automatic channel of any
kind, so a screenshot is frequently the entire report. The protection was suppressing the evidence
the same protection's defects need.

The alternatives on the table were all worse than they look:

- **Allow capture in the `dev` flavour only.** Testers install the signed `prod` APK from GitHub
  Releases — that is the whole distribution model — so this would exempt exactly the people who
  never hit the problem.
- **Allow capture on unauthenticated screens.** The bug worth photographing is almost always behind
  the login, and the rule would be invisible: a member would learn it by two screenshots working
  and the third not.
- **A debug gesture or a hidden build flag.** An undocumented switch is a switch nobody can be told
  about, and the tester who needs it is by definition not reading the source.

## Decision

**The member may allow screen capture, from Einstellungen, and the default stays blocked.**

Three properties make this a weakening we can defend rather than a hole:

1. **Fail closed.** `MainActivity` sets `FLAG_SECURE` *before* `setContent` and clears it only after
   the stored preference has been read and says to. Reading is asynchronous, so waiting for it
   would leave the first frames unprotected; starting blocked means a slow or failed read costs a
   screenshot that does not work, never one that silently does. An unset key reads as blocked, so a
   fresh install is protected without anyone choosing anything.
2. **Explicit and reversible, in one place.** One row under "App", beside the app lock, phrased as
   the thing the member wants to do ("Screenshots erlauben") with the cost in the subtitle. The
   preference is collected for the activity's life, so the change lands on the current screen rather
   than at the next start — a switch that appears not to work gets flipped twice and left on.
3. **Its own store.** The preference lives in `krt_settings`, not the token store a logout wipes. It
   is a property of the device and its owner, not of a session; a tester who signs out and back in
   must not silently get the block back. The value says nothing about the member, so it needs
   neither the Keystore nor a backup-exclusion rule — backups are off app-wide regardless.

Recorded as an ADR because it is a deliberate reduction of a security property that a requirement
stated as unconditional. `REQ-APP-AUTH-010` is amended in the same change rather than left to
contradict the code.

## Consequences

- A member who switches it on accepts that the recents thumbnail shows their data. The subtitle says
  so; nothing else in the app is allowed to lean on the flag's presence, which was already the rule.
- `FLAG_SECURE` remains hardening rather than a guarantee (near 70 % effective at API 30 and below
  by Google's own figures), so the switch changes the size of an already-imperfect defence, not its
  kind.
- The state is not observable to the backend and is not reported anywhere. A screenshot in a bug
  report therefore carries no signal that protection was off at the time — acceptable, because
  nothing acts on that fact.
- If a future surface genuinely must never be captured — a one-time recovery code, say — it has to
  set the flag on its own window for its own lifetime. The app-wide flag is no longer a guarantee it
  can inherit.
