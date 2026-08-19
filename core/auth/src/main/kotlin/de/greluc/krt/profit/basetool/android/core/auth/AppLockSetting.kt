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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The app lock's armed state — which is the **sealed sentinel**, not a boolean.
 *
 * A `Boolean` was the first design here and it was the wrong one: a flag records that the member
 * *wants* a lock, and nothing in the app can then tell whether a lock actually stands. What is
 * stored instead is the ciphertext [AppLockKey.arm] produced. Its presence means a key exists;
 * opening the app means decrypting it back, which Keystore only permits after a real
 * authentication. "Is the lock armed" and "can the lock be satisfied" therefore stop being two
 * facts that can disagree.
 *
 * **Not a secret**, despite being ciphertext: its plaintext is a constant in [AppLockKey], so the
 * blob reveals nothing. It goes in the same DataStore as the refresh token but without the token
 * cipher — encrypting it would let a Keystore failure lock a member *out* of a convenience feature.
 *
 * It lives in the token store so logout's wipe reaches it: a device handed on with the app signed
 * out should not still ask the next person for a fingerprint.
 *
 * @property dataStore the same preferences store the refresh token uses
 */
class AppLockSetting(
    private val dataStore: DataStore<Preferences>,
) {
    /** Emits whether the lock is armed, and again whenever that changes. */
    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY] != null }

    /**
     * Reads the sealed sentinel.
     *
     * @return the blob to hand to [AppLockKey.unlockCipher], or `null` when the lock is off
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun sealedSentinel(): ByteArray? =
        dataStore.data.first()[KEY]?.let { stored ->
            try {
                Base64.decode(stored)
            } catch (malformed: IllegalArgumentException) {
                // A blob that cannot even be decoded is one no key could open. Reading it as "no
                // lock" is the only outcome that does not strand the member behind a broken gate.
                KrtLog.w(LOG_TAG, malformed) { "stored app-lock sentinel is not decodable" }
                null
            }
        }

    /**
     * Arms the lock with a freshly sealed sentinel.
     *
     * @param sealed the ciphertext from [AppLockKey.arm]
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun arm(sealed: ByteArray) {
        dataStore.edit { it[KEY] = Base64.encode(sealed) }
    }

    /**
     * Disarms the lock.
     */
    suspend fun disarm() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        /** Log subsystem; the sentinel's ciphertext is never written to a log. */
        const val LOG_TAG = "lock"

        /** Preference key; distinct from the token entry so a wipe can target either. */
        val KEY = stringPreferencesKey("app_lock_sentinel")
    }
}
