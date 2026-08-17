# core:network

HTTP layer: OkHttp/Retrofit + kotlinx.serialization, DTOs generated from the committed
backend `openapi.json` (contract drift = compile error), RFC 7807 problem parser (stable
`code` + `correlationId`), auth/header interceptors (`Authorization`,
`X-Active-Org-Unit-Id`, `Accept-Language`, `X-Correlation-Id`), SSE client for
`/api/v1/notifications/stream`, `PageResponse` page-walking helpers.
