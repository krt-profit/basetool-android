/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.auth.AppLock
import de.greluc.krt.profit.basetool.android.core.auth.SecretCipherException
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How long the app may sit in the background before it locks again (design ch. 04). */
internal val BACKGROUND_GRACE: Duration = 5.minutes

/**
 * Decides when the app is locked and holds that decision across configuration changes.
 *
 * Opening is cryptographic: [unlock] opens the gate only if the lock's sentinel decrypts with the
 * authenticated cipher. The app locks on cold start and after 5 minutes in the background, measured
 * with a monotonic timestamp supplied by the caller.
 *
 * @property lock the auth-bound Keystore key and its sentinel
 */
class AppLockViewModel(
    private val lock: AppLock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AppLockState>(AppLockState.Unknown)

    /** Whether the app is currently sealed behind the lock screen. */
    val state: StateFlow<AppLockState> = mutableState.asStateFlow()

    private var backgroundedAt: Long? = null

    /**
     * Reads the armed state and locks if the lock is on; called from the activity's `onCreate`.
     *
     * Idempotent: only a fresh view model in [AppLockState.Unknown] decides, so an activity recreate does
     * not re-lock.
     */
    fun start() {
        if (mutableState.value !is AppLockState.Unknown) {
            return
        }
        viewModelScope.launch {
            mutableState.value = if (lock.isArmed()) AppLockState.Locked() else AppLockState.Open
        }
    }

    /**
     * Records that the app went to the background.
     *
     * @param elapsedRealtimeMillis a monotonic clock reading, e.g. `SystemClock.elapsedRealtime()`
     */
    fun onBackgrounded(elapsedRealtimeMillis: Long) {
        backgroundedAt = elapsedRealtimeMillis
    }

    /**
     * Re-locks if the app was away for longer than the grace period.
     *
     * @param elapsedRealtimeMillis a monotonic reading from the same source as [onBackgrounded]
     */
    fun onForegrounded(elapsedRealtimeMillis: Long) {
        val away = backgroundedAt ?: return
        backgroundedAt = null
        KrtLog.d(LOG_TAG) { "away for ${elapsedRealtimeMillis - away} ms, state=${mutableState.value}" }
        if (mutableState.value !is AppLockState.Open) {
            return
        }
        if (elapsedRealtimeMillis - away >= BACKGROUND_GRACE.inWholeMilliseconds) {
            viewModelScope.launch {
                if (lock.isArmed()) {
                    mutableState.value = AppLockState.Locked()
                }
            }
        }
    }

    /**
     * Prepares the cipher a prompt has to authenticate.
     *
     * `null` means the key is gone or was invalidated by a new biometric enrolment, so the lock can
     * never be satisfied again.
     *
     * @return the initialised cipher, or `null` when the lock can no longer be opened
     */
    suspend fun prepareUnlock(): Cipher? {
        val cipher = lock.unlockCipher()
        if (cipher == null) {
            mutableState.value = AppLockState.Unsatisfiable
        }
        return cipher
    }

    /**
     * Opens the app **if** the authenticated cipher really decrypts the sentinel.
     *
     * @param cipher the cipher the platform vouched for
     */
    fun unlock(cipher: Cipher) {
        viewModelScope.launch {
            mutableState.value =
                if (lock.open(cipher)) {
                    AppLockState.Open
                } else {
                    KrtLog.w(LOG_TAG) { "authentication succeeded but the sentinel did not open" }
                    AppLockState.Locked(R.string.lock_error_generic)
                }
        }
    }

    /**
     * Records that an unlock attempt failed or was dismissed; the app stays locked.
     *
     * @param messageRes the string to show, or `null` to clear a previous one
     */
    fun onUnlockFailed(messageRes: Int?) {
        mutableState.value = AppLockState.Locked(messageRes)
    }

    /**
     * Disarms the lock; arming goes through [prepareArm] and [completeArm] instead.
     *
     * Arming takes effect at the next cold start or background timeout, not immediately.
     *
     * @param value `false` to disarm; `true` is ignored here, because arming needs a prompt
     */
    fun setEnabled(value: Boolean) {
        if (value) {
            return
        }
        viewModelScope.launch { lock.disarm() }
    }

    /**
     * Creates the lock key and returns the cipher a prompt must authenticate.
     *
     * Arming is two-phase because the key is auth-per-use, so encrypting the sentinel also needs an
     * authentication.
     *
     * @return the cipher for the prompt, or `null` when the device cannot create the key at all
     */
    suspend fun prepareArm(): Cipher? =
        try {
            lock.prepareArm()
        } catch (unusable: SecretCipherException) {
            KrtLog.e(LOG_TAG, unusable) { "app lock key could not be created" }
            null
        }

    /**
     * Finishes arming with the authenticated cipher.
     *
     * @param cipher the cipher the platform vouched for
     */
    fun completeArm(cipher: Cipher) {
        viewModelScope.launch {
            try {
                lock.completeArm(cipher)
            } catch (unusable: SecretCipherException) {
                KrtLog.e(LOG_TAG, unusable) { "app lock could not be armed" }
            }
        }
    }

    private companion object {
        /** Log subsystem; no key material or sentinel ever appears in a message. */
        const val LOG_TAG = "lock"
    }
}

/**
 * Whether the lock screen is in front of the app.
 */
sealed interface AppLockState {
    /** The armed state has not been read yet — nothing may be shown, locked or not. */
    data object Unknown : AppLockState

    /** No lock, or already unlocked for this session. */
    data object Open : AppLockState

    /**
     * The lock screen is up.
     *
     * @property messageRes a message from the last failed attempt, or `null`
     */
    data class Locked(
        val messageRes: Int? = null,
    ) : AppLockState

    /**
     * The lock exists but can never be opened again, because a new biometric enrolment invalidated the
     * key.
     *
     * Unlike [Locked], no retry is offered; the only way on is a fresh login.
     */
    data object Unsatisfiable : AppLockState
}
