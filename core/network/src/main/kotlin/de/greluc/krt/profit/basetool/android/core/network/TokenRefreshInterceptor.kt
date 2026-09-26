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
 * Keeps the bearer token alive across a session that outlives one access token.
 *
 * Refreshes a spent token before the call, and after a 401 refreshes and retries exactly once. Sits
 * before [MandatoryHeadersInterceptor]; never used on the token client
 * ([KrtHttpClient.createTokenClient]).
 *
 * @property currentToken the access token as it stands right now, or `null` when anonymous.
 * @property refreshIfSpent exchanges the token when it is at or near its expiry; a no-op otherwise.
 * @property refreshAfterRejection is handed the refused token and returns a usable one, or `null`
 *   when the session cannot be renewed; parallel 401s share one refresh.
 */
class TokenRefreshInterceptor(
    private val currentToken: () -> String?,
    private val refreshIfSpent: () -> Unit,
    private val refreshAfterRejection: (String?) -> String?,
) : Interceptor {
    /**
     * Refreshes around one call.
     *
     * @param chain the call.
     * @return the response, from the retry when one was made.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        refreshIfSpent()
        val sent = currentToken()
        val response = chain.proceed(chain.request())
        val renewed =
            if (response.code == HTTP_UNAUTHORIZED && sent != null) {
                refreshAfterRejection(sent)?.takeIf { it != sent }
            } else {
                null
            }
        return if (renewed == null) {
            response
        } else {
            response.close()
            chain.proceed(chain.request())
        }
    }

    private companion object {
        /** The status that says the token, not the request, was the problem. */
        const val HTTP_UNAUTHORIZED = 401
    }
}
