# ADR-0012 — Sign-out asks before it wipes, and only in Einstellungen

> **Status:** Accepted · **Date:** 2026-08-26 · **Deciders:** @greluc
> **Design:** `docs/design/android/13 Einstellungen.dc.html` (the "Abmelden" button and the
> "Lokale Daten löschen" danger modal)
> **Related:** `REQ-APP-SET-010`, `REQ-APP-AUTH-005`, `REQ-APP-AUTH-009`, `REQ-APP-AUTH-010`,
> [`0002-refresh-token-at-rest.md`](0002-refresh-token-at-rest.md),
> [`0010-screenshot-protection-is-member-switchable.md`](0010-screenshot-protection-is-member-switchable.md)

## Context

Design chapter 13 draws sign-out as a full-width `btn-quiet-danger` at the foot of the settings
column, below the Fan Kit band and above the version footer, and the app renders it there. The
chapter draws it **without** a confirmation: the only modal on the artboard belongs to "Lokale Daten
löschen", a control that has not shipped because there is no offline cache to delete yet
(`REQ-APP-SET-001`).

So the artboard pairs the confirmation with the wrong one of the two destructive controls — and it
does so for a defensible reason at the time it was drawn. What the chapter could not weigh is what
sign-out costs on this app specifically. `REQ-APP-AUTH-005` does not merely drop a session cookie:
it deletes the encrypted refresh token, the non-exportable Keystore key that decrypts it and the
DPoP signing key the token is bound to. Nothing on the device restores any of that. The way back is
the full browser round trip through the realm's sign-in form (`REQ-APP-AUTH-008`) — on a phone,
with a password manager, on whatever connection the member has.

That cost sits under a scrolling list of reversible toggles — language, app lock, screenshots — every
one of which is undone by tapping it again. The last control on that list is not, and it is the
widest tap target on the screen.

The alternatives were weaker:

- **Leave it unconfirmed, as drawn.** The artboard is binding on colour, type, spacing, states and
  copy, and it is right about all of those here. It is silent on this question rather than opinionated
  about it: it never had to reconcile the button with `REQ-APP-AUTH-005`, which was specified
  elsewhere.
- **Move it out of easy reach** — into an overflow menu, or behind the account row. That contradicts
  the chapter on placement, which *is* an explicit design statement, and it makes the deliberate exit
  harder for everyone in order to protect against a slip.
- **A toast with an undo.** There is nothing to undo. By the time the toast renders, the key is gone.

## Decision

**Sign-out in Einstellungen opens a danger-tone `KrtModal` before it calls `onLogout`; the button
itself does not move, change rung, or change label.**

The modal follows the tone's existing copy rule — name the consequence, do not ask a yes/no
question. The body states that the session ends, that the stored sign-in key is deleted from this
device, and that the next sign-in runs through the browser form again. The confirm action reads
"Jetzt abmelden" rather than "Abmelden", because the screen's own button already carries the latter
and two identical labels on screen at once are ambiguous to a member and to a test locator alike.

**The gates' sign-out stays unconfirmed, deliberately.** On the approval-pending, gate-unavailable
and locked screens (`REQ-APP-AUTH-009`, `REQ-APP-AUTH-010`), sign-out is not one control among many —
it is the only way forward, offered precisely because the member is stuck. Nothing is scrollable
past it and nothing else is tappable, so there is no mis-tap to protect against; a confirmation there
buys no safety and taxes the one escape hatch. The asymmetry is the point: the question exists where
the tap is plausibly accidental, and not where it is the whole purpose of the screen.

Recorded as an ADR because chapter 13 is the binding UI reference and adding an interaction it does
not draw is a deviation, however small. `REQ-APP-SET-010` is added in the same change and
`REQ-APP-AUTH-005` gains the matching acceptance bullet, so no spec is left contradicting the code.

## Consequences

- Signing out costs one extra tap, forever, for everyone. That is the price and it is not recovered
  by familiarity — accepted, because the failure it prevents is unrecoverable and the action is rare.
- The artboard and the app now differ on this screen. Anyone reading chapter 13 as the source of
  truth will find a modal that is not drawn there; this ADR is the reason, and `REQ-APP-SET-010` is
  where it is specified. If the chapter is ever redrawn, the confirmation should be drawn in.
- When "Lokale Daten löschen" eventually ships with the first read cache, it arrives with the modal
  chapter 13 already drew for it. Two adjacent danger modals with different consequences will then
  sit on one screen, and their copy has to keep them apart — the wipe leaves the member signed in,
  this one does not.
- The question is held in `rememberSaveable`, so a rotation re-asks rather than silently dropping it.
  A dropped confirmation would read as a tap that did nothing.
