/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the four headers every Basetool API call carries.
 *
 * They are set in one place rather than per call site because three of them are easy to forget and
 * their absence is silent rather than loud:
 *
 * - `Authorization` — omitted entirely when there is no session, so anonymous endpoints keep
 *   working instead of receiving an empty bearer.
 * - `X-Active-Org-Unit-Id` — the org-unit pin (main repo REQ-ORG-*). Omitted when nothing is
 *   pinned; the backend then falls back to the member's default, which is correct only until the
 *   member switches context, at which point a missing header silently shows the wrong squadron's
 *   data.
 * - `Accept-Language` — decides the language of the localised problem-detail bodies.
 * - `X-Correlation-Id` — one per request, echoed in the backend's log line and error body
 *   (REQ-OBS-002), which is what makes a member's screenshot traceable.
 *
 * An existing header on the request wins: a caller that sets one deliberately (a token exchange
 * carrying its own `Authorization`, a retry reusing a correlation id) is not overridden.
 *
 * @property accessTokenProvider supplies the bearer token, or `null` for anonymous calls
 * @property correlationIdFactory mints one id per request
 * @property languageTagProvider supplies the BCP 47 tag for `Accept-Language`
 * @property activeOrgUnitProvider supplies the pinned org unit, or `null`
 */
class MandatoryHeadersInterceptor(
    private val accessTokenProvider: AccessTokenProvider,
    private val correlationIdFactory: CorrelationIdFactory,
    private val languageTagProvider: LanguageTagProvider,
    private val activeOrgUnitProvider: ActiveOrgUnitProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header(HEADER_AUTHORIZATION) == null) {
            accessTokenProvider.currentAccessToken()?.let { token ->
                builder.header(HEADER_AUTHORIZATION, "Bearer $token")
            }
        }
        if (original.header(HEADER_ACTIVE_ORG_UNIT) == null) {
            activeOrgUnitProvider.activeOrgUnitId()?.let { orgUnitId ->
                builder.header(HEADER_ACTIVE_ORG_UNIT, orgUnitId)
            }
        }
        if (original.header(HEADER_ACCEPT_LANGUAGE) == null) {
            builder.header(HEADER_ACCEPT_LANGUAGE, languageTagProvider.currentLanguageTag())
        }
        if (original.header(HEADER_CORRELATION_ID) == null) {
            builder.header(HEADER_CORRELATION_ID, correlationIdFactory.newCorrelationId())
        }
        return chain.proceed(builder.build())
    }

    companion object {
        /** Bearer token header. */
        const val HEADER_AUTHORIZATION: String = "Authorization"

        /** Org-unit pin relayed to the backend's scope resolution. */
        const val HEADER_ACTIVE_ORG_UNIT: String = "X-Active-Org-Unit-Id"

        /** Language of the localised error bodies. */
        const val HEADER_ACCEPT_LANGUAGE: String = "Accept-Language"

        /** Per-request id tying a client report to a server log line. */
        const val HEADER_CORRELATION_ID: String = "X-Correlation-Id"
    }
}
