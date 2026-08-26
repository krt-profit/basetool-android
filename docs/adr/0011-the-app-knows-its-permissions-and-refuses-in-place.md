# ADR-0011 — The app knows its permissions, and refuses in place instead of hiding

> **Status:** Accepted · **Date:** 2026-08-26 · **Deciders:** @greluc
> **Design:** `docs/design/android/02 Components.dc.html` §1 (the ladder's disabled state), §7 (modal
> rules), ch. 14 (Access denied)
> **Related:** `REQ-APP-AUTH-013`, `REQ-APP-AUTH-009`, `ROLES_AND_PERMISSIONS.md` in the main repo,
> [`0008-generated-wire-models.md`](0008-generated-wire-models.md)

## Context

Until now the app kept almost nothing about who the caller is. `Identity` carries a user id and two
booleans — `logistician`, `missionManager` — and its documentation gives the reason: *"No name, no
email and no role list: two facts answer every question the app asks of this record, and holding the
rest would put personal data in memory for the lifetime of the process."*

Three things were wrong with that in practice.

**The premise does not hold for roles.** `/api/v1/users/me` — a call the app already makes — returns
`roles` **and** `permissions`, and the app parses that response and drops both lists. The access
token it must hold anyway carries `realm_access.roles`. The roles are in the process either way; the
only thing the app achieved by dropping them was not being able to read them. A capability list is a
statement about the session, not a personal detail of the kind the privacy gate (`ANDROID_APP_PLAN`
§7) exists to keep out.

**The two facts it did keep were barely used.** Three of twelve ViewModels fetch the identity at
all. The Lager fetches none — which is why the Zuordnung sheet and the bulk Umbuchen shipped with no
permission check whatsoever.

**The result is an app that offers what it cannot do.** A plain `KRT Member` holds `HANGAR_READ`,
`HANGAR_WRITE` and `MISSION_READ`. On org-owned stock the app shows them *Zuordnen*, *Buchen* and,
after a long press, *Umbuchen* — and every one of those answers 403. A member who taps three
controls and is refused three times learns that the app is not to be trusted about what it offers,
which is a worse outcome than never having offered.

## Decision

**The app reads `permissions` and keeps them for the session**, alongside the two membership grants
it already had, in one app-wide holder rather than per screen.

**A permission-gated control stays visible, is rendered in the disabled style, and names its reason
when tapped.** Not hidden. Two reasons: the design system draws a disabled state for every rung of
the button ladder and would otherwise never use it, and a feature a member cannot see is a feature
they cannot ask to be granted — which in an organisation that hands out roles by hand is the more
expensive failure.

**"Disabled" here is a visual state, not `enabled = false`.** A Compose control with
`enabled = false` receives no tap, and a control that cannot be tapped cannot explain itself. The
gated control is therefore rendered at the disabled alpha of the ladder and remains clickable; the
tap performs no write and states what is missing. This distinction is the whole mechanism and must
not be "simplified" back to `enabled = false`.

**The reason names the grant, not the HTTP status.** „Dafür brauchst du die Logistik-Rolle" is
actionable — a member knows whom to ask. „403" and „Keine Berechtigung" are not.

**Where the permission depends on the row rather than the role, the app decides from what it holds
and lets the server settle the rest.** The Lager's server-side rule is *own row, or edit rights on
that row's org unit*; the app has the row's holder, its org unit and the caller's grants, so it can
answer the common cases locally. Where it genuinely cannot know, the control stays enabled and the
refusal is reported in the app's own words — the existing behaviour, now the exception rather than
the rule.

## Consequences

A member sees the whole feature set and learns from the app itself which parts their role opens.
Support questions move from "why did it fail" to "how do I get that role", which is a question with
an answer.

The cost is that every gated control now carries a reason string, and reasons are copy that has to
be maintained in two languages. That is deliberate: the alternative is a grey button that says
nothing, which is the worst of both options.

Two risks worth naming. The permission list is a **hint, never the gate** — the server remains the
authority, and any screen that treats a locally computed permission as sufficient is a defect, not
an optimisation. And the list is read once per session, so a role granted while the app is open does
not take effect until the next identity read; the app therefore re-reads it on resume rather than
caching it for the process lifetime.

## Alternatives considered

**Hide gated controls.** Cleaner screens and the first instinct. Rejected because the org grants
roles by hand: a member who never sees „Zuordnen" has no way to discover that the Logistik role
exists, and the person who could grant it never gets asked.

**Keep the status quo and let 403 do the talking.** No client-side model to keep in step with the
server. Rejected on the evidence above — three refusals in a row on ordinary Lager work is exactly
the acceptance problem this ADR was raised for.

**Read the roles out of the access token instead of `/users/me`.** Fewer moving parts, and the token
is already in memory. Rejected as the primary source: the token carries Keycloak realm roles, while
authorization is expressed in the backend's own `permissions` vocabulary plus per-org-unit
membership flags, and the mapping between them lives in the backend. Deriving it in the client would
put a second, drifting copy of the role matrix in the app.
