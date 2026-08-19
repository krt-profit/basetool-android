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
 * The system authentication sheet that stands in front of the app lock.
 *
 * **The app never sees a credential.** `BiometricPrompt` is a system surface: it renders above this
 * process, the fingerprint or PIN is handled by the platform, and all that comes back is a callback.
 * That is why the lock screen underneath carries no input field of its own — an app-drawn PIN pad
 * would be a credential this app could read, which the design chapter's "sits UNDER the system
 * BiometricPrompt sheet" rules out.
 *
 * **The result is used, not merely observed.** The prompt carries a `CryptoObject` wherever the
 * platform allows one, and the cipher it returns is what `AppLockKey` decrypts its sentinel with. A
 * callback whose answer is read and thrown away leaves a gate that opens on a boolean — CodeQL's
 * "insecure local authentication" — and no amount of care around that boolean fixes it.
 *
 * **Biometric or device credential, deliberately both.** `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`
 * lets the member fall back to their PIN, pattern or password through the same sheet. Restricting
 * it to biometrics alone would lock out every member whose sensor is wet, whose finger is cut, or
 * whose device has no sensor — with no way back into their own tool.
 */
object BiometricGate {
    /** Log subsystem; no authentication detail is ever written, because none reaches this process. */
    private const val LOG_TAG = "lock"

    /** What the app is willing to accept as proof, in the platform's terms. */
    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether this device can satisfy the lock at all.
     *
     * Checked before offering the setting, not after: a toggle that switches on and then locks the
     * member out of their own app is worse than a toggle that is not there. `DEVICE_CREDENTIAL` is
     * part of [ALLOWED], so this is `false` only on a device with no screen lock whatsoever.
     *
     * @param activity the hosting activity
     * @return `true` when the platform can prompt for something
     */
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system sheet, bound to [cipher].
     *
     * The lock key is auth-per-use, so the cipher travels in a `CryptoObject` and [onSuccess]
     * receives **the very cipher the prompt vouched for**. The caller still has to perform the
     * decrypt — a `true` from the callback alone opens nothing, which is the difference between a
     * cryptographic gate and a boolean one.
     *
     * Failure is reported as a **string resource rather than the platform's message**: the platform
     * text is written for the general case and its codes include several the member cannot act on.
     * A dismissed sheet is not an error at all — it reports `null`, so the screen stays as it was
     * rather than accusing the member of a failure they never made.
     *
     * @param activity the hosting activity; must be a `FragmentActivity`, which `ComponentActivity`
     *   is not — the prompt attaches to the fragment manager
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
                    // The CryptoObject's cipher is the instance the platform unlocked. The
                    // fallback cannot happen now that every supported key is auth-per-use, and is
                    // kept only so a missing CryptoObject degrades to a refused operation rather
                    // than a crash.
                    onSuccess(result.cryptoObject?.cipher ?: cipher)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    // The code is logged, the text is not: errString is user-facing platform copy
                    // and names the device owner on some OEM builds.
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

        // Always bound: minSdk 30 guarantees an auth-per-use key, and a prompt without a
        // CryptoObject would authenticate the member without authorising the operation.
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
