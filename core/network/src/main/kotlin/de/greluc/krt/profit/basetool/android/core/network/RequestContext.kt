/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

/**
 * Supplies the bearer token for outgoing API calls.
 *
 * Deliberately **not** a suspending function: it is called from an OkHttp interceptor, which is
 * synchronous, and wrapping a coroutine there means `runBlocking` on a network thread. The auth
 * module satisfies this by keeping the current access token in memory (security concept §4 — the
 * access token never touches disk) and refreshing it out of band, so the read is a field access.
 *
 * Returning `null` means "no session": the interceptor then sends no `Authorization` header at all
 * rather than an empty one, which is what the anonymous endpoints expect.
 */
fun interface AccessTokenProvider {
    /**
     * The access token to present, or `null` when no session exists.
     *
     * @return the raw JWT without the `Bearer ` prefix, or `null`
     */
    fun currentAccessToken(): String?
}

/**
 * Mints the per-request correlation id echoed to the backend.
 *
 * The backend logs it (`X-Correlation-Id`, main repo REQ-OBS-002) and returns it on error bodies,
 * so a member's report can be tied to one server-side log line without asking them for anything but
 * the id shown on the error screen.
 */
fun interface CorrelationIdFactory {
    /**
     * A fresh correlation id for exactly one request.
     *
     * @return an opaque id; the backend treats it as a string and never parses it
     */
    fun newCorrelationId(): String
}

/**
 * Supplies the BCP 47 language tag for `Accept-Language`.
 *
 * The backend localises problem-detail titles and details, so this decides which language a member
 * sees in an error. It follows the in-app language setting rather than the device locale, because
 * the app offers an explicit language choice (design spec ch. 13).
 */
fun interface LanguageTagProvider {
    /**
     * The language tag to request, e.g. `de-DE`.
     *
     * @return a BCP 47 tag; never blank
     */
    fun currentLanguageTag(): String
}

/**
 * Supplies the pinned org unit for `X-Active-Org-Unit-Id`.
 *
 * Multi-org-unit tenancy (main repo REQ-ORG-*) scopes almost every read to the caller's active org
 * unit. The header is the client's half of that: without it the backend falls back to the member's
 * default, which is right for a fresh install and wrong the moment a member switches context.
 */
fun interface ActiveOrgUnitProvider {
    /**
     * The active org unit's id, or `null` before one has been chosen.
     *
     * @return the org unit id, or `null` to let the backend decide
     */
    fun activeOrgUnitId(): String?
}
