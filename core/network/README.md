# core:network

HTTP layer: OkHttp/Retrofit + kotlinx.serialization, DTOs generated from the committed
backend `openapi.json` (contract drift = compile error), RFC 7807 problem parser (stable
`code` + `correlationId`), auth/header interceptors (`Authorization`,
`X-Active-Org-Unit-Id`, `Accept-Language`, `X-Correlation-Id`,
`User-Agent: basetool-android/<semver>`), SSE client for `/api/v1/notifications/stream`,
`PageResponse` page-walking helpers.

Binding security constraints (`docs/ANDROID_APP_SECURITY.md` §4/§6):

- **No OkHttp disk cache.** The Room read cache is the only persistence layer for member data —
  an HTTP cache would be a second copy outside every wipe path (logout, "Lokale Daten löschen").
- **Backoff with full jitter** on 429 (honouring `Retry-After`), 503 and SSE reconnects; a
  non-idempotent write is never retried automatically (the `version` echo turns a blind replay
  into a 409 anyway).
- Cleartext is off by construction: every flavor's Network Security Config sets
  `cleartextTrafficPermitted="false"`; only the `dev` flavor adds `<debug-overrides>` for the
  local test stack's self-signed certificate.
