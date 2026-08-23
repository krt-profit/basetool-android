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
 * Without it the app works for exactly as long as the realm's access-token lifespan — an hour on
 * the `iri` realm — and then every screen turns into "Signal Lost" at once, with no way back short
 * of restarting the app. That is not a network failure and must not be shown as one: the session is
 * still valid, only the short-lived half of it has expired.
 *
 * It sits **before** [MandatoryHeadersInterceptor] so that the header interceptor reads whatever
 * token the refresh has just produced, both on the first attempt and on the retry.
 *
 * Two halves, and both are needed:
 *
 * - **Before the call**, a token that is spent (or about to be) is exchanged, so the ordinary case
 *   costs no failed request at all.
 * - **After a 401**, the token is exchanged and the call is made once more. The clock the expiry
 *   was judged against can be wrong, and a token can be revoked before it expires; in both cases
 *   the server's rejection is the only reliable signal there is.
 *
 * Exactly one retry. A second 401 is answered by the server rejecting a token it has just minted,
 * which no amount of retrying repairs — and a loop here would hammer the realm on every screen.
 *
 * Both lambdas block the calling OkHttp thread, which is the thread that would otherwise be waiting
 * on the socket anyway. They must not be called from the token endpoint's own client (see
 * [KrtHttpClient.createTokenClient], which drops every interceptor for that reason).
 *
 * @property currentToken the access token as it stands right now, or `null` when anonymous.
 * @property refreshIfSpent exchanges the token when it is at or near its expiry; a no-op otherwise.
 * @property refreshAfterRejection is handed the token the server refused and answers with a usable
 *   one, or `null` when the session could not be renewed at all. It returns the token another
 *   caller already fetched when one did, so a burst of parallel 401s costs one refresh.
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
            // The body of the rejected response is never read, so it has to be closed by hand or
            // the connection leaks out of the pool.
            response.close()
            chain.proceed(chain.request())
        }
    }

    private companion object {
        /** The status that says the token, not the request, was the problem. */
        const val HTTP_UNAUTHORIZED = 401
    }
}
