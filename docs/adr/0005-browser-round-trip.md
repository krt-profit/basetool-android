# ADR-0005 — The browser round trip: who catches the redirect, and what survives the process

- **Status:** Proposed
- **Date:** 2026-08-18
- **Related:** [ADR-0002](0002-refresh-token-at-rest.md) · [ADR-0004](0004-session-model.md) ·
  [`docs/specs/auth.md`](../specs/auth.md) (`REQ-APP-AUTH-007`, `-008`) ·
  [`docs/ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §2.10, §3–4 ·
  [`docs/ANDROID_APP_DEV_CI.md`](../ANDROID_APP_DEV_CI.md) §6 ·
  design spec ch. 04 · RFC 8252

## Context

ADR-0004 built a login flow that hands a URL to a browser and later reads a redirect. Between those
two moments the app is not in control: the Custom Tab belongs to another app, it sits on top of this
task, and the system may kill this process behind it.

Three questions follow, and each has a wrong answer that works on a developer's device.

## Decision

**The in-flight attempt is persisted, encrypted, and single-use.** Keeping `state`, `nonce` and the
PKCE verifier in a ViewModel field is the obvious implementation and it fails only under memory
pressure — that is, on a member's phone, once, unreproducibly. They go into the same encrypted
DataStore as the refresh token: for the length of the round trip the verifier is exactly as
sensitive as the code it unlocks. `take()` reads *and* clears, because a code is redeemable once and
a consumed attempt must not be actionable again.

**A dedicated `AuthRedirectActivity` holds the intent filter, not `MainActivity`.** While the Custom
Tab is open it is on top of this task, so `MainActivity` is not where a redirect would land: with
`singleTop` the system would build a *second* `MainActivity` on top of the browser rather than
return to the running one. A `singleTask`, translucent, UI-less activity brings the task forward,
clears the browser off it, and re-launches `MainActivity` with `CLEAR_TOP` — which also creates one
when the process was killed, which is the case that matters. Putting `singleTask` on `MainActivity`
would have changed the launch semantics of every deep link and notification in the app to fix one
flow.

**The redirect URI is per flavour, and both the callback and the post-logout return are claimed.**
Production uses the verified App Link `https://profit-base.online/app/callback`; the custom scheme
`de.kartell.basetool:/oauth2redirect` exists on the dev realm only, because any installed app can
claim a custom scheme. The post-logout URI needs its own claim too — without it the browser opens
the website after a logout and the member is left looking at it. The design chapter's parenthetical
`(basetool://auth)` is illustrative; the security concept's URIs are the registered ones.

**Endpoints come from `BuildConfig` and nowhere else.** No runtime switch, no debug menu: a release
build that can be pointed at another server is a gift to whoever gets hold of a device.

## Consequences

- A redirect that does not match the flavour's configured URI is a failure with no symptom — the
  login works right up to the return and then simply doesn't. `AuthRedirectFilterTest` asserts the
  claim once per flavour against `BuildConfig`, which is the only end of that contract this repo
  controls; the realm holds the other.
- `AuthRedirectActivity` is exported, because the browser has to start it. What keeps that safe is
  that the code it carries is worthless without the verifier, which never leaves the app, and that a
  redirect not matching the pending attempt's `state` is discarded.
- Enabling `isIncludeAndroidResources` for `:app` unit tests was a prerequisite: without it
  Robolectric silently uses an empty default manifest, every intent filter resolves to nothing, and
  a manifest assertion fails for a reason unrelated to the manifest.
- Two things stay unverifiable here and are marked open: `assetlinks.json` is not served yet, so the
  production App Link does not verify and Android will show a disambiguation dialog; and while the
  realm's `basetool-android` client exists, the redirect URIs it registers have not been checked
  against the ones compiled into the flavours — a mismatch fails at the realm with
  `invalid_redirect_uri`, before any of this code runs.

## Alternatives rejected

- **The attempt in memory.** Works until the process dies behind the browser, which is exactly when
  a member notices.
- **The attempt in plain `SharedPreferences`.** The verifier is a live secret for the duration; a
  device backup or a rooted read would hand over the ability to redeem an intercepted code.
- **The filter on `MainActivity` with `singleTask`.** One flow's requirement imposed on every deep
  link and notification in the app.
- **A custom scheme in production.** Claimable by any installed app. PKCE stops a stolen code from
  being redeemed, but the phishing surface is free to close and an App Link closes it.
- **`WebView`.** Would put the realm password inside a surface this app can read, share no browser
  session, and be indistinguishable from a phishing app (RFC 8252 §8.12).
