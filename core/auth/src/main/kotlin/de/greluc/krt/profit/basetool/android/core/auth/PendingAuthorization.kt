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
 * The login attempt that is currently out in the browser, persisted so its `state`, `nonce` and PKCE verifier survive
 * the process being killed behind the Custom Tab.
 *
 * Encrypted with the same [SecretCipher] and store as the refresh token. [peek] reads without
 * consuming; [clear] removes the attempt once its redirect has been judged, so it is used once.
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
     * Reads the pending attempt without consuming it, so a foreign start of the exported [AuthRedirectActivity] cannot
     * destroy it.
     *
     * The caller calls [clear] once the redirect has been judged.
     *
     * @return the attempt, or `null` when there is none or it can no longer be read
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun peek(): AuthorizationRequest? {
        val encoded = storedValue() ?: return null
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
 * The in-flight attempt as stored: a wire format separate from [AuthorizationRequest] so it stays readable across app
 * updates.
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
