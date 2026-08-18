/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The login attempt that is currently out in the browser.
 *
 * **This exists because the app can die while the Custom Tab is in front.** The browser is another
 * task; Android is free to kill this process behind it, and on a low-memory phone it will. When the
 * redirect brings the app back, the `state`, `nonce` and PKCE verifier that the attempt was started
 * with must still be there — without them the code cannot be redeemed and the redirect cannot even
 * be recognised as ours. Keeping them in memory would make login work on a developer's device and
 * fail, unreproducibly, on a member's.
 *
 * **Encrypted, because the verifier is a secret for the length of the round trip.** It is what
 * redeems the authorization code, so between launch and redirect it deserves the same treatment as
 * the refresh token: the same [SecretCipher], the same store.
 *
 * **Single use.** [take] reads *and* clears. A code can be redeemed exactly once, so an attempt
 * that has been consumed is finished; leaving it behind would let a stale or replayed redirect be
 * acted on a second time.
 *
 * @property dataStore where the encrypted attempt lives — the same store as the refresh token
 * @property cipher the Keystore-backed cipher in production, a fake in tests
 * @property json format for the stored triple
 */
class PendingAuthorization(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val json: Json = Json,
) {
    /**
     * Remembers the attempt about to be handed to the browser.
     *
     * Call it **before** launching the Custom Tab: after the launch the process may not run again
     * until the redirect arrives.
     *
     * @param request the attempt whose redirect will be checked against this
     * @throws SecretCipherException if the device cannot encrypt right now, in which case nothing
     *   is written and the caller should not launch the browser
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun save(request: AuthorizationRequest) {
        val stored =
            StoredAttempt(
                state = request.state,
                nonce = request.nonce,
                verifier = request.pkce.verifier,
                challenge = request.pkce.challenge,
                url = request.url,
            )
        val plain = json.encodeToString(StoredAttempt.serializer(), stored)
        val encoded = Base64.encode(cipher.encrypt(plain.encodeToByteArray()))
        dataStore.edit { preferences -> preferences[KEY_PENDING] = encoded }
    }

    /**
     * Consumes the pending attempt.
     *
     * @return the attempt, or `null` when there is none or it can no longer be read — which for the
     *   caller means the same thing: this redirect cannot be completed and the member starts over
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun take(): AuthorizationRequest? {
        val encoded = storedValue() ?: return null
        clear()
        return decode(encoded)
    }

    /**
     * Forgets the pending attempt.
     *
     * Called when a login is abandoned — the member backed out of the Custom Tab, or the redirect
     * turned out not to be ours.
     */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEY_PENDING) }
    }

    /**
     * Reads the raw stored value.
     *
     * @return the Base64 ciphertext, or `null` when nothing is stored or the file is unreadable
     */
    private suspend fun storedValue(): String? =
        try {
            dataStore.data.first()[KEY_PENDING]
        } catch (io: IOException) {
            KrtLog.w(LOG_TAG, io) { "pending authorization store unreadable" }
            null
        }

    /**
     * Decrypts and parses a stored attempt.
     *
     * @param encoded the Base64 ciphertext
     * @return the attempt, or `null` when it cannot be read
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decode(encoded: String): AuthorizationRequest? =
        try {
            val plain = cipher.decrypt(Base64.decode(encoded)).decodeToString()
            val stored = json.decodeFromString(StoredAttempt.serializer(), plain)
            AuthorizationRequest(
                url = stored.url,
                state = stored.state,
                nonce = stored.nonce,
                pkce = PkceChallenge(verifier = stored.verifier, challenge = stored.challenge),
            )
        } catch (unusable: SecretCipherException) {
            // Same three ordinary states as the refresh token: key invalidated, device locked, blob
            // from another device. The login is simply started again.
            KrtLog.w(LOG_TAG, unusable) { "pending authorization is unusable, discarding it" }
            null
        } catch (malformed: IllegalArgumentException) {
            KrtLog.w(LOG_TAG, malformed) { "pending authorization is malformed, discarding it" }
            null
        }

    private companion object {
        /** Log subsystem; the verifier never appears in a message. */
        const val LOG_TAG = "auth"

        /** DataStore key holding the Base64 of the encrypted attempt. */
        val KEY_PENDING = stringPreferencesKey("pending_authorization")
    }
}

/**
 * The in-flight attempt as it is stored.
 *
 * A separate type from [AuthorizationRequest] on purpose: this one is a wire format that has to
 * stay readable across app updates, and pinning it here means a change to the domain object cannot
 * silently invalidate every login that is out in a browser at the time.
 *
 * @property state the CSRF value the redirect must echo
 * @property nonce the value the ID token must carry
 * @property verifier the PKCE verifier that redeems the code
 * @property challenge the challenge derived from it, kept so the request can be rebuilt whole
 * @property url the authorization URL, kept for diagnostics and for a retry without a new attempt
 */
@Serializable
private data class StoredAttempt(
    val state: String,
    val nonce: String,
    val verifier: String,
    val challenge: String,
    val url: String,
)
