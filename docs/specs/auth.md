> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-08-18.
> **Owner area:** AUTH · **Related:** [`../ANDROID_APP_SECURITY.md`](../ANDROID_APP_SECURITY.md) §3–4,
> [`api-contract.md`](api-contract.md), ADR-0001 / ADR-0002 (this repo),
> main repo ADR-0131 (refresh-only DPoP), `REQ-SEC-030`

# Authentication — tokens, keys and proofs

The app is a public OAuth client on a member's phone. Everything here follows from two facts: it
holds no client secret, and the device it runs on can be lost while unlocked, locked, or restored
onto someone else's hardware.

## Requirements

### REQ-APP-AUTH-001 — The access token never touches disk

It lives in memory for its five-minute lifetime and is gone when the process dies. Persisting it
would add a second secret at rest to save at most one refresh, on a token that is short-lived by
design (main repo `REQ-SEC-030`).

**Acceptance**

- [x] No component writes the access token anywhere: the only store in `core:auth` is
  `RefreshTokenStore`, and its API takes a refresh token.
- [ ] A lint or detekt gate makes writing it impossible rather than merely absent. **Open.**

### REQ-APP-AUTH-002 — The refresh token is encrypted by a non-exportable device key

AES-256-GCM with a key generated in the Android Keystore; the ciphertext (IV ‖ ciphertext, Base64)
lives in a Preferences DataStore. Three key properties carry the protection:

- **Non-exportable** — the key never enters the app process, so a ciphertext that reaches another
  device cannot be decrypted there.
- **`setUnlockedDeviceRequired(true)`** — while the device is locked the token is cryptographically
  unusable. It costs nothing: the app refreshes only in the foreground, because there is no push
  channel (decision Q2).
- **StrongBox where available**, falling back to a TEE-backed key when the device has no secure
  element. Requesting it unconditionally and catching `StrongBoxUnavailableException` is the check —
  an SDK-level guard would be dead code at minSdk 29.

`androidx.security:security-crypto` is deliberately not used: deprecated, final release 1.1.0, no
successor.

**Reading is allowed to fail, and failure is not an error.** A key invalidated by a new biometric
enrolment, a locked device, a blob restored from elsewhere — all three mean "no usable session".
`RefreshTokenStore.read()` answers `null` and clears the unusable blob rather than throwing, because
the alternative is a crash loop on start-up where a login prompt belongs.

**Acceptance**

- [x] Round-trip, overwrite and clear behave as specified (`RefreshTokenStoreTest`).
- [x] An undecryptable token reads as `null` **and** is cleared, so the failure is paid once.
- [ ] The Keystore implementation itself is exercised on a device. **Open** — `KeystoreSecretCipher`
  cannot run on a JVM; the seam is at `SecretCipher` so everything above it is tested, and the key's
  hardware binding needs the instrumented suite (Gradle Managed Devices).

### REQ-APP-AUTH-003 — DPoP proofs are sent on token requests only, and timed by server clock

The realm binds **only the refresh token** (main repo ADR-0131). A voluntarily sent proof makes
Keycloak bind the access token as well, and the backend's bearer filter rejects an access token
carrying `cnf.jkt` — so a proof on an ordinary API call breaks the next request. Proofs therefore
belong on `/token` and nowhere else, which is why the proof factory is used by the token client and
is not an interceptor.

`iat` comes from `ServerClock` (`REQ-APP-API-004`), never the device clock: Keycloak allows a 10 s
proof lifetime with 15 s of skew, and a phone a minute off would fail to log in with a symptom that
reads as "login is broken".

Each proof carries a fresh `jti`; Keycloak rejects a replay, so a reused id would make the second
refresh of a session fail intermittently and only in the field.

**Acceptance**

- [x] `htm`/`htu`/`iat`/`jti` and the `dopp+jwt` header shape verify against the embedded key
  (`DpopProofFactoryTest`).
- [x] `iat` follows an offset server clock.
- [x] Two proofs never share a `jti`.
- [x] The private half of the key is never serialised into the header.
- [x] The proof is attached to the token request and to nothing else — `TokenClient` is the only
  caller of the factory, and it builds its own HTTP client precisely so no interceptor can add one
  (`REQ-APP-AUTH-006`).
- [x] A server-issued `DPoP-Nonce` is echoed back, and a `use_dpop_nonce` refusal is retried exactly
  once (RFC 9449 §8.3, `TokenClientTest`). The realm demands no nonce today; the handling exists so
  that enabling one is not a client-breaking change.
- [x] The authorization request carries `dpop_jkt`, the thumbprint of the same key
  (`AuthorizationRequestTest`) — RFC 9449 §10 defence in depth, so an intercepted code cannot be
  redeemed against a different key.
- [x] The proof is accepted by a live realm under the refresh-only policy, from this app's own
  request — code exchange and refresh both, verified on device. `TokenResult.AccessTokenBound` never
  fired, so the realm returned `token_type: Bearer`: refresh token bound, access token plain, which
  is the posture the backend's bearer filter requires.
- [x] A refresh **without** the key is refused, and so is one signed by a different key — measured
  against a live realm with `scripts/verify-dpop-binding.py` in the main repo: no proof gives
  `invalid_dpop_proof: DPoP proof is missing`, a foreign key gives `invalid_grant: DPoP confirmation
  doesn't match DPoP proof`. The same run shows the refresh token carrying `cnf.jkt` and the access
  token carrying none, which is the split the backend's bearer filter depends on. The binding is
  therefore load-bearing rather than decorative: a stolen refresh token is useless without the
  device key.

### REQ-APP-AUTH-004 — The token file is excluded from backup in both rule sets

minSdk 29 spans two backup worlds: `backup_rules.xml` governs API ≤ 30, `data_extraction_rules.xml`
governs API 31+, and the latter needs the exclusion in **both** its `cloud-backup` and
`device-transfer` sections — `allowBackup=false` alone does not reliably stop a device-to-device
transfer.

The excluded path must be the file DataStore actually writes: `datastore/krt_tokens.preferences_pb`,
not the bare store name. `AuthDataStore.RELATIVE_PATH` publishes it and `BackupExclusionTest` compares
the XML against it, because this is the failure with no symptom — a stale path breaks no build and
simply starts uploading a refresh token.

**Acceptance**

- [x] Both rule files exclude the path, and the extraction rules do so in both sections
  (`BackupExclusionTest`).
- [x] The path names the `datastore/` subdirectory and the `.preferences_pb` file.
- [ ] A restored backup is observed not to contain the file. **Open** — device-level verification,
  Phase 5.

### REQ-APP-AUTH-005 — Logout wipes the token and the key

Clearing the stored ciphertext is not enough on its own: a key left behind can decrypt any copy of
that ciphertext that escaped. Logout therefore deletes the DataStore entry **and** the Keystore
entry, in addition to the Keycloak end-session call and the best-effort refresh-token revocation.

**Acceptance**

- [x] `RefreshTokenStore.clear()` removes the entry; `KeystoreSecretCipher.deleteKey()` removes the
  key.
- [x] The revocation call and the RP-initiated logout URL exist and are tested
  (`TokenClient.revokeRefreshToken`, `TokenClient.endSessionUri`). Revocation cannot fail a logout:
  it answers `false` and logs, because what protects the device is the local wipe and a phone with
  no connectivity must still be able to log out.
- [x] The three steps run in one defined order (`AuthSession.logout`, `AuthSessionTest`): the
  in-memory session is dropped **first**, so a logout is instant and cannot be undone by a slow
  network; the revocation follows while the token is still known; the local wipe of blob **and** key
  comes last. A refused revocation does not stop any of it.
- [x] `deleteKey()` is part of the `SecretCipher` contract rather than an implementation detail — a
  cipher that cannot be wiped cannot back a logout.
- [x] The end-session URL is opened, and the realm session really ends — verified on device against
  a live Keycloak: after signing out, the next login attempt is answered with the credential form
  rather than a silent redirect, which is the only outward sign that the SSO cookie is gone.
- [x] The DPoP signing key is destroyed with the rest. It lives outside `AuthSession`'s reach, so
  `AuthContainer.logout()` deletes it after the session wipe; the refresh token is bound to it and
  leaving it alive would leave the binding alive.

### REQ-APP-AUTH-006 — Token requests use their own HTTP client, and every answer is a named state

**The API client must not be reused for token requests.** It carries
`MandatoryHeadersInterceptor`, which attaches `Authorization: Bearer <access token>` to everything
it sees; Keycloak reads an `Authorization` header on its token endpoint as an attempt at client
authentication and answers `invalid_client`. A refresh would therefore succeed exactly once — on
the first login, when no access token exists yet — and fail for the rest of the install's life.
`KrtHttpClient.createTokenClient` derives a client that keeps the connection pool, the dispatcher
and the timeouts and drops the interceptors, re-adding only `ServerTimeInterceptor`: this traffic is
the only kind that observes **Keycloak's** clock, and Keycloak is the party that judges a proof's
`iat`.

**The endpoints are derived from the issuer, not discovered.** Keycloak's URL layout is fixed, so a
`/.well-known/openid-configuration` fetch would only add a round trip to the login path and another
way for it to fail. Deriving them also makes an omission enforceable: `OidcConfiguration` has no
`userinfo` property, and under the refresh-only DPoP policy Keycloak answers **HTTP 500** there
(security concept §4, constraint 1). Profile claims come from the ID token.

**Every outcome is a state, and the ones that look alike are kept apart.** `invalid_grant` and a
realm misconfiguration both arrive as HTTP 400: the first means "show the login screen", the second
means a login that cannot succeed, and collapsing them produces either a dead end or an infinite
loop. A 2xx carrying `token_type` other than `Bearer` is a *third* case — the per-client "Require
DPoP bound tokens" switch overriding the client policy — whose natural symptom is that every
subsequent API call 401s, pointing at the wrong component entirely.

**Acceptance**

- [x] The token client sends no `Authorization`, correlation-id or org-unit header, and the API
  client still sends all of them (`KrtHttpClientTest`).
- [x] The token client updates the `ServerClock` from the realm's `Date` header.
- [x] `invalid_grant` maps to a session that ended; any other OAuth error stays a rejection; a
  refusal with no OAuth body keeps its status (`TokenClientTest`).
- [x] `token_type` other than `Bearer` is reported as such instead of being handed on.
- [x] A 2xx that is not a grant — the captive-portal case — is malformed, not an empty session.
- [x] A transport failure is distinguishable from a refusal, so only it can read as "offline".
- [ ] The states are rendered by the screens that own them. **Open** — lands with the login flow.

### REQ-APP-AUTH-007 — One login attempt, three per-attempt secrets, and a session that survives a tunnel

Login is Authorization Code + PKCE **S256** in a browser (RFC 8252), never a WebView. Each attempt
mints three values that must not outlive it or be reused: the PKCE verifier redeems the code, the
`state` ties the redirect to this attempt, and the `nonce` ties the ID token to it. They live
together in one `AuthorizationRequest`, so losing one of them is a compile error rather than a
quietly weakened login.

**`state` is checked before anything else in a redirect is read.** A redirect the app did not start
must not be able to steer the flow — not even into an error screen of its choosing.

**The `nonce` is verified against the ID token, or it is decoration.** A token whose `nonce` does not
match was not minted for this login; nothing is stored and no session starts.

**The redirect is parsed at string level, not with a URL parser.** Production redirects to a verified
App Link, but the dev realm registers the custom scheme `de.kartell.basetool:/oauth2redirect`, which
an HTTP URL parser refuses outright — parsing with one would break every login on the build the flow
is developed against.

**Refreshing is single-flight, and only `invalid_grant` ends a session.** Several screens loading at
once would each notice the expiry and each start a refresh; one mutex plus a re-check inside it means
they wait and then find it done. A refresh that fails because the phone is in a tunnel leaves the
stored token untouched and reports `SessionState.Stale` — the UI shows a retry, not a password
prompt. Only a refusal from the realm clears the stored token.

**A refresh response with no `refresh_token` keeps the stored one.** The realm does not rotate
(main repo REQ-SEC-012), so omission is legitimate, and overwriting the field with `null` would
discard the only way back into the session.

**Acceptance**

- [x] The verifier is 43 characters of unreserved entropy and the challenge is its base64url SHA-256,
  recomputed independently in `PkceChallengeTest`; the verifier never appears in `toString`.
- [x] The authorization request asks for `code` with `S256`, the registered redirect, the client's
  scopes **without** `offline_access`, and `dpop_jkt` (`AuthorizationRequestTest`).
- [x] `state`, `nonce` and the verifier differ between attempts.
- [x] A redirect with a foreign `state` yields `StateMismatch` whether it carries a code or an error.
- [x] A custom-scheme redirect is read exactly like an `https` one.
- [x] An ID token with a mismatched `nonce` is refused and stores nothing (`AuthSessionTest`).
- [x] Concurrent `refreshIfNeeded()` calls produce exactly one token request — asserted by a test
  verified to fail when the lock is removed.
- [x] An unreachable realm leaves the stored token in place and yields `Stale`; `invalid_grant`
  clears it and yields `SignedOut`.
- [x] A grant without a `refresh_token` keeps the stored one.
- [x] The Custom Tab launch and the redirect activity (`REQ-APP-AUTH-008`).
- [ ] The chapter-04 screens — login, approval-pending, terms, app-lock. **Open** — this
  requirement covers the flow's logic; the screens that drive it follow.
- [ ] The ID token's signature is not verified. **Accepted, not open** — OIDC Core §3.1.3.7 permits
  it when the token comes directly from the token endpoint over TLS, which is the only way this app
  obtains one (ADR-0004).

### REQ-APP-AUTH-008 — The browser round trip: a Custom Tab, a claimed redirect, and an attempt that outlives the process

Login happens in a **Custom Tab, never a WebView** (RFC 8252 §8.12). A WebView would put the
member's realm password inside a surface this app controls and can read, share no session with the
browser, and be indistinguishable from a phishing app. The toolbar is `#141414` so the realm's dark
login page does not sit inside light chrome (design spec ch. 04).

**The pending attempt is persisted, encrypted, and single-use.** While the Custom Tab is in front,
Android may kill this process — on a low-memory phone it will. The `state`, `nonce` and PKCE
verifier must therefore survive it, or the redirect cannot be recognised and the code cannot be
redeemed. They live in the same encrypted store as the refresh token, because the verifier is
exactly as sensitive as the code it unlocks for the length of the round trip. `take()` reads *and*
clears: a code is redeemable once, so a consumed attempt must not be actionable again.

**The redirect URI is per flavour and is claimed by a dedicated activity.** Production uses the
verified App Link `https://profit-base.online/app/callback`, which no other app can claim because
the domain publishes this app's signing-certificate digest; the custom scheme
`de.kartell.basetool:/oauth2redirect` is registered on the dev realm only, since a custom scheme is
claimable by any installed app (security concept §3). The design chapter's parenthetical
`(basetool://auth)` is illustrative — the registered URIs are the ones above, and they are pinned
on both ends: the realm refuses an unregistered redirect, and this end is asserted by a test.

**The post-logout return must be claimed too**, or the browser opens the website after a logout and
the member is left looking at it.

A **separate** activity holds the filter rather than `MainActivity`: while the Custom Tab is open it
sits on top of the task, so a `singleTop` `MainActivity` would be created a *second* time on top of
the browser instead of returning to the running one. `singleTask` on a translucent, UI-less activity
brings the task forward and re-launches `MainActivity` with `CLEAR_TOP`. Giving `MainActivity`
itself `singleTask` would change the launch semantics of every deep link and notification to fix one
flow.

Endpoints come from `BuildConfig` and nowhere else — no runtime switch, no debug menu. A release
build that can be pointed at another server is a gift to whoever gets hold of a device (DEV_CI §6).

**Acceptance**

- [x] The attempt survives a process restart, asserted by a second store instance reading what the
  first wrote (`PendingAuthorizationTest`).
- [x] `take()` consumes it; a second redirect finds nothing.
- [x] An unreadable attempt is discarded rather than thrown, like an unreadable refresh token.
- [x] The flavour's configured redirect resolves to exactly one activity of this app, and so does
  its post-logout redirect (`AuthRedirectFilterTest`, run once per flavour).
- [x] Login opens a Custom Tab; no `WebView` exists anywhere in the app.
- [ ] A CI gate forbids `WebView` outright rather than relying on its absence. **Open** — same gate
  as `REQ-APP-AUTH-001`'s access-token check.
- [ ] `/.well-known/assetlinks.json` is served, so the production App Link verifies. **Open** —
  server-side, main repo (exposure plan A7). Until then Android shows a disambiguation dialog
  instead of opening the app.
- [x] The redirect URIs match the ones the realm registers, checked against the main repo's
  `scripts/provision-keycloak-mobile-client.py`: production registers exactly
  `https://profit-base.online/app/callback`, and the test profile adds
  `de.kartell.basetool:/oauth2redirect` plus a loopback.
- [x] The post-logout URI is one the client accepts, given its `"+"` setting.
- [ ] The dev issuer's host and port. **Open** — unlike the redirect URIs it is not pinned by the
  provisioning script; `https://10.0.2.2:18443/realms/iri` follows DEV_CI §6 and is confirmed the
  first time the app runs against a local stack.
- [ ] The live client matches the script that provisions it. **Open** — the script is the source of
  truth this check used, not the realm itself, and `docs/keycloak/realm-config.reference.json` in
  the main repo predates the client and cannot stand in for it; refreshing that snapshot would make
  the realm's actual state checkable from the repo.

### REQ-APP-AUTH-009 — A session is not admission: the account gate stands between the two

A valid token says who somebody is. It does not say they may use the app. The backend refuses every
gated endpoint while a registration is unapproved (main repo REQ-SEC-017) and, separately, while the
Terms of Use in force are unaccepted (REQ-SEC-028) — **both as HTTP 403**, distinguished only by the
stable problem code. The app therefore **asks before it renders**: `GET
/api/v1/users/me/registration-status`, whose whole purpose is to be reachable by a caller whose only
authority is `ROLE_PENDING_APPROVAL`.

**The app screen must not be composed while the gate is closed.** Rendering it underneath an overlay
would set every one of its loads going against endpoints guaranteed to answer 403, and the member
would watch a dashboard fill with failures that name nothing. `AccountGate` therefore takes the app
as a lambda and composes it only once the member is cleared.

**An unrecognised status keeps the gate shut.** A server that adds a fourth approval state must not
be able to admit a client that predates it, and it must not crash one either — the wire value is
parsed as a string, an unknown one becomes `ApprovalStatus.UNKNOWN`, and `UNKNOWN` is not cleared.

**A `PENDING_APPROVAL` refusal is the answer, not an error.** Whether the status endpoint is itself
refused depends on the deployment's filter order, and both outcomes mean the same thing. Reporting
one of them as a failure would show a connectivity screen to a member who is merely waiting.

**A failed re-read keeps the last known state.** Replacing the waiting screen with an error the
moment a poll misses would make a lost minute of connectivity look like the account had been reset.
Only a *first* read with nothing behind it surfaces as unreadable — and that state says the question
could not be asked, never that the member is waiting.

**Polling, because there is no push.** The design chapter promises "Automatische Prüfung alle 60 s —
Push bei Freigabe". The second half is struck: the app has no push channel at all (resolved decision
Q2), so an approval reaches the screen through the poll or not at all, and promising a notification
that cannot arrive would leave a member waiting on their lock screen. The loop stops the moment the
member is cleared — otherwise every install would ask once a minute, forever, for an answer that no
longer changes anything.

Two elements of the design frame are **absent for want of data, not by preference**: the "Eingereicht
— vor 2 Std. · via Discord" row (`RegistrationStatusDto` carries the status and nothing else) and the
rejection reason (administrators record one, but no endpoint exposes it to the rejected member). The
account row survives because `preferred_username` comes from the ID token the app already holds, so
it renders while every gated endpoint refuses.

**Acceptance**

- [x] An approved account clears the gate; a pending or rejected one does not
  (`AccountGateRepositoryTest`, `AccountGateViewModelTest`).
- [x] An unknown status, and a body with no status field at all, keep the gate closed.
- [x] A `PENDING_APPROVAL` problem body reads as pending; a plain `FORBIDDEN` 403 stays a failure.
- [x] An unreadable 200 body is reported as a server fault, not as a network one — telling a member
  to check their connection when the server answered is advice that cannot help.
- [x] The poll stops once the member is cleared, and a second `start()` does not add a competing
  loop.
- [x] An approval that lands mid-wait opens the gate without a manual refresh.
- [x] A failed re-read keeps the waiting screen; a failed first read surfaces as unreadable.
- [x] The terms gate, as the second half of this requirement. The wording is **never carried in the
  APK**: it is read from `GET /api/v1/terms/document` together with the version an acceptance is
  recorded against (main repo ADR-0138), which the backend grew for exactly this. A bundled copy
  would show the text this build was compiled with while the server records consent against whatever
  it currently has in force — and over GitHub Releases that drift is the steady state, not a risk.
  Reading one wording and agreeing to another is not informed consent.
- [x] The document is fetched **only when consent is missing** — a member who accepted months ago
  does not pay for a download to be told so (`TermsGateViewModelTest`).
- [x] A document that cannot be read is a **hard stop**, never an emptier gate: asking somebody to
  agree to a blank page is not asking for consent. The status read is tolerant by comparison, and
  the asymmetry is deliberate.
- [x] A 200 that still reports no consent keeps the gate closed. Trusting the HTTP status over the
  payload would wave a member through without their consent on record, and the next API call would
  bounce them straight back.
- [x] A failed acceptance keeps the wording on screen with a message rather than replacing it with
  an error page — the text they just read is what they need in order to try again.
- [x] The CTA is disabled until the box is ticked, with **no scroll-to-bottom gate** (design ch. 04):
  a forced scroll measures that a finger moved, not that anything was read.
- [x] The tick survives rotation (`rememberSaveable`), so a disabled CTA never becomes a mystery.
- [x] Declining names its consequence in a danger modal before signing out.
- [ ] Observed end to end against a live backend. **Open** — needs a test-realm user with no
  acceptance on record, and the main repo's document endpoint merged.
- [ ] Observed against a live backend with a genuinely pending account. **Open** — needs a second
  test-realm user held in the approval queue.
