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
 * What the store had to say about the refresh token: present, absent, or sealed behind an app lock not yet opened.
 *
 * A locked token is not "no session" and must never lead to signing the member out.
 */
sealed interface StoredRefreshToken {
    /** A token that was read and decrypted. */
    data class Present(
        val token: String,
    ) : StoredRefreshToken

    /** Nothing stored, or what was stored is no longer usable and has been dropped. */
    data object Absent : StoredRefreshToken

    /**
     * A stored token sealed by the app lock, read before the lock was opened.
     *
     * The token is intact and this is not a session state; a caller that cannot wait must do nothing.
     */
    data object Locked : StoredRefreshToken
}

/**
 * The refresh token at rest: encrypted by [SecretCipher], Base64-encoded in DataStore.
 *
 * A blob that cannot be decrypted is wiped and read as [StoredRefreshToken.Absent]; a blob sealed by
 * the app lock ([SessionEnvelope]) is left alone and read as [StoredRefreshToken.Locked]. The access
 * token is never stored.
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
     * @return what the store had; [StoredRefreshToken.Locked] is emphatically not "no session"
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun read(): StoredRefreshToken {
        val stored = storedCiphertext() ?: return StoredRefreshToken.Absent
        return try {
            StoredRefreshToken.Present(cipher.decrypt(Base64.decode(stored)).decodeToString())
        } catch (locked: AppLockedException) {
            KrtLog.i(LOG_TAG) { "refresh token is sealed, the app lock is not open yet: ${locked.message}" }
            StoredRefreshToken.Locked
        } catch (unusable: SecretCipherException) {
            KrtLog.w(LOG_TAG, unusable) { "stored refresh token is unusable, clearing it" }
            clear()
            StoredRefreshToken.Absent
        } catch (malformed: IllegalArgumentException) {
            KrtLog.w(LOG_TAG, malformed) { "stored refresh token is not valid Base64, clearing it" }
            clear()
            StoredRefreshToken.Absent
        }
    }

    /**
     * The token, or `null` for anything else.
     *
     * Only for arming and disarming the lock; every other caller must handle
     * [StoredRefreshToken.Locked] explicitly.
     *
     * @return the token when one was read, `null` when it was absent or sealed
     */
    suspend fun readTokenOrNull(): String? = (read() as? StoredRefreshToken.Present)?.token

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
