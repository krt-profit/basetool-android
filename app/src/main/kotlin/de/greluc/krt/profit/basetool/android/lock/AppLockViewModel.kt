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
 * Decides when the app is locked, and holds that decision across configuration changes.
 *
 * **Opening is a cryptographic act, not a state assignment.** [unlock] takes the cipher the platform
 * authenticated and only opens the gate if the lock's sentinel decrypts with it. There is
 * deliberately no method that simply sets the state to open: a lock whose gate can be opened by a
 * boolean is one mis-ordered transition away from opening on its own, which is what CodeQL's
 * "insecure local authentication" describes and what the earlier revision of this class did.
 *
 * The rule for *when* to lock is the design chapter's "cold start **plus** after 5 minutes in the
 * background", and both halves matter for different reasons. Cold start is the obvious one. The
 * grace period is what makes the feature usable: a member who switches to Discord to read a
 * briefing and comes back four seconds later must not be re-prompted, or the lock is off within a
 * day.
 *
 * **Elapsed time comes from the caller**, as a monotonic timestamp, so the grace period cannot be
 * defeated by changing the device clock and the whole rule is testable without waiting.
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
     * Reads the armed state and locks if the lock is on.
     *
     * Called once per process from the activity's `onCreate`, which is what makes the cold-start
     * half of the rule true: a process that has just started has no unlocked state to inherit.
     */
    fun start() {
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
        // The one decision in this class with no visible trace when it goes wrong: too eager and
        // the lock is unusable, too lax and it silently stops guarding. The reading is a monotonic
        // duration, not a wall clock, so it carries nothing about the member.
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
     * A `null` means the key is gone or was invalidated by a new biometric enrolment. That is not a
     * failed attempt — it is a lock that can never be satisfied again, so the screen switches to the
     * one action that still works.
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
                    // The platform said yes and the session key still did not come back, so
                    // the key no longer matches the blob. Not something the member did.
                    KrtLog.w(LOG_TAG) { "authentication succeeded but the sentinel did not open" }
                    AppLockState.Locked(R.string.lock_error_generic)
                }
        }
    }

    /**
     * Records that an unlock attempt failed or was dismissed.
     *
     * The app stays locked — the screen keeps its retry button. This exists so the screen can say
     * something rather than looking as though the tap did nothing.
     *
     * @param messageRes the string to show, or `null` to clear a previous one
     */
    fun onUnlockFailed(messageRes: Int?) {
        mutableState.value = AppLockState.Locked(messageRes)
    }

    /**
     * Disarms the lock. Arming goes through [prepareArm] and [completeArm] instead.
     *
     * Arming does **not** lock immediately either: the member is holding an unlocked device at that
     * moment, and sealing the app in their face would be a strange reward for switching a security
     * feature on. It takes effect at the next cold start or background timeout.
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
     * Arming is two-phase because the key is auth-per-use: Keystore refuses to encrypt with it
     * without an authentication, exactly as it refuses to decrypt. An earlier revision sealed
     * inline here and failed on every device with `Key user not authenticated` while every unit
     * test stayed green — the Keystore is not exercised off a device.
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
     * The lock exists but can never be opened again.
     *
     * Reached when a new biometric enrolment invalidated the key
     * (`setInvalidatedByBiometricEnrollment`, security concept §4). Kept apart from [Locked] because
     * retrying is pointless: the only route on is a fresh login, and offering an unlock button here
     * would send the member round a loop that cannot end.
     */
    data object Unsatisfiable : AppLockState
}
