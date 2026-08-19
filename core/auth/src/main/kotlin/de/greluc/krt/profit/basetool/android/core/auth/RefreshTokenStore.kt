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
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The refresh token at rest: encrypted by [SecretCipher], stored as ciphertext in DataStore.
 *
 * The access token is **not** here and never will be — it lives in memory only (security concept
 * §4), because it expires in five minutes and persisting it would add a second secret at rest to
 * save one refresh.
 *
 * The ciphertext is Base64-encoded because Preferences DataStore stores strings, not blobs. That is
 * an encoding, not a protection: everything that protects the token happened in the cipher.
 *
 * **Reading a stored token is allowed to fail, and failure is not an error.** The Keystore key is
 * bound to an unlocked device and dies on a new biometric enrolment, and a blob restored from
 * another device was never decryptable here. All three mean the same thing — no usable session —
 * and [read] answers `null` for each rather than throwing, after wiping the unusable blob so the
 * next read is cheap.
 *
 * **One failure is emphatically not of that kind.** With the app lock armed the blob carries an
 * outer layer that only an authenticated unlock removes ([SessionEnvelope]), and a read before that
 * unlock raises [AppLockedException]. That token is perfectly good. Wiping it — which every other
 * failure here does — would log the member out for not yet having put their finger on the sensor,
 * so that branch answers `null` and leaves the blob alone.
 *
 * @property dataStore where the ciphertext lives; the caller owns its file location and lifecycle
 * @property cipher the Keystore-backed cipher in production, a fake in tests
 */
class RefreshTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    /**
     * Stores [refreshToken], replacing any previous one.
     *
     * @param refreshToken the token exactly as the token endpoint returned it
     * @throws SecretCipherException if the device cannot encrypt right now, in which case nothing
     *   is written — a half-written secret would read as a corrupt one later
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun write(refreshToken: String) {
        val encoded = Base64.encode(cipher.encrypt(refreshToken.encodeToByteArray()))
        dataStore.edit { preferences -> preferences[KEY_REFRESH_TOKEN] = encoded }
    }

    /**
     * Reads the stored refresh token.
     *
     * @return the token, or `null` when none is stored or the stored one is no longer usable
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun read(): String? {
        val stored = storedCiphertext() ?: return null
        return try {
            cipher.decrypt(Base64.decode(stored)).decodeToString()
        } catch (locked: AppLockedException) {
            // MUST come before the SecretCipherException branch, and must NOT clear. The token is
            // fine; the app lock simply has not been opened yet, and wiping here would log a member
            // out for the crime of not having authenticated. Answering null is safe because the
            // only caller reads this to decide whether a session can be restored, and the lock gate
            // sits ahead of that decision — a read reaching this branch is a caller that got the
            // order wrong, not a member who lost their session.
            // The exception carries no detail worth a stack trace — it IS the detail — so its
            // message goes into the line rather than the throwable channel, which DEBUG has
            // no overload for anyway.
            KrtLog.d(LOG_TAG) { "refresh token is sealed and the app lock is not open: ${locked.message}" }
            null
        } catch (unusable: SecretCipherException) {
            // Key invalidated, device locked, or a blob from another device. Drop it: keeping an
            // undecryptable blob means paying for the failure on every future read, and the member
            // has to log in again either way.
            KrtLog.w(LOG_TAG, unusable) { "stored refresh token is unusable, clearing it" }
            clear()
            null
        } catch (malformed: IllegalArgumentException) {
            KrtLog.w(LOG_TAG, malformed) { "stored refresh token is not valid Base64, clearing it" }
            clear()
            null
        }
    }

    /**
     * Removes the stored token.
     *
     * Part of logout, and of every path that discovers the stored token is unusable. Deleting the
     * Keystore key is the cipher's job and is done separately, so that a wipe does not depend on
     * having found every copy of the ciphertext.
     */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(KEY_REFRESH_TOKEN) }
    }

    /**
     * Reads the raw stored string.
     *
     * @return the Base64 ciphertext, or `null` when nothing is stored or the file cannot be read
     */
    private suspend fun storedCiphertext(): String? =
        try {
            dataStore.data.first()[KEY_REFRESH_TOKEN]
        } catch (io: IOException) {
            // A DataStore that cannot be read is indistinguishable from an empty one for our
            // purposes, and a login prompt is a better outcome than a crash on start-up.
            KrtLog.w(LOG_TAG, io) { "token store unreadable" }
            null
        }

    private companion object {
        /** Log subsystem; token material is never part of a message. */
        const val LOG_TAG = "auth"

        /** DataStore key holding the Base64 of the encrypted refresh token. */
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token_ciphertext")
    }
}
