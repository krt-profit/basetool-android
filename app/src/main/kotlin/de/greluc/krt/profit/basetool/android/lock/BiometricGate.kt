/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import javax.crypto.Cipher

/**
 * The system `BiometricPrompt` that stands in front of the app lock.
 *
 * The platform handles the credential; the app only receives the `CryptoObject` cipher, which
 * `AppLockKey` must use to decrypt its sentinel. Both `BIOMETRIC_STRONG` and `DEVICE_CREDENTIAL` are
 * allowed, so the member can fall back to PIN, pattern or password.
 */
object BiometricGate {
    /** Log subsystem; no authentication detail is ever written, because none reaches this process. */
    private const val LOG_TAG = "lock"

    /** What the app is willing to accept as proof, in the platform's terms. */
    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether this device can satisfy the lock at all; `false` only without any screen lock.
     *
     * @param activity the hosting activity
     * @return `true` when the platform can prompt for something
     */
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system sheet, bound to [cipher] through a `CryptoObject`.
     *
     * [onSuccess] receives the authenticated cipher, with which the caller must still decrypt. Failures
     * are reported as the app's own string resources; a dismissed sheet reports `null`.
     *
     * @param activity the hosting activity; must be a `FragmentActivity`, which the prompt attaches to
     * @param cipher the initialised decrypt cipher for the lock's sentinel
     * @param onSuccess invoked on the main thread with the authenticated cipher
     * @param onFailure invoked with a message resource, or `null` when the member simply dismissed
     */
    fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: (Int?) -> Unit,
    ) {
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result.cryptoObject?.cipher ?: cipher)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    KrtLog.d(LOG_TAG) { "unlock refused, code $errorCode" }
                    onFailure(
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED,
                            -> null

                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                            -> R.string.lock_error_lockout

                            else -> R.string.lock_error_generic
                        },
                    )
                }
            }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.lock_title))
                .setSubtitle(activity.getString(R.string.lock_body))
                .setAllowedAuthenticators(ALLOWED)
                .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
