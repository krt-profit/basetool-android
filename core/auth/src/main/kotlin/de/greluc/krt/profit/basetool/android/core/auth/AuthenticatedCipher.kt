/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import javax.crypto.Cipher

/**
 * What a `BiometricPrompt` has to authenticate, and whether it can be handed a cipher at all.
 *
 * The two platform paths do not merely differ in strength, they differ in **order**, and expressing
 * that as a nullable `Cipher` is what broke API 29 outright:
 *
 * - **API 30+** — the key is *auth-per-use*. `Cipher.init` succeeds before any authentication, and
 *   the resulting cipher goes into a `CryptoObject`, so the platform vouches for that exact
 *   operation. [Bound].
 * - **API 29** — the key is *time-bound* (`setUserAuthenticationValidityDurationSeconds`), and
 *   `Cipher.init` itself throws `UserNotAuthenticatedException` until an authentication has
 *   happened. The cipher can only be built **after** the prompt returns. [Deferred].
 *
 * An earlier revision returned `Cipher?` from both preparation calls, where `null` already meant
 * "this lock can never be opened again". On API 29 the init threw, a broad catch turned it into
 * `null`, and the app concluded the lock was unsatisfiable — so on the whole minSdk platform the
 * lock could neither be armed nor opened, while every unit test stayed green, because the Keystore
 * is not exercised off a device. This type exists so a failure cannot be spelled the same way as a
 * deliberate deferral again.
 */
sealed interface AuthenticatedCipher {
    /**
     * The prompt binds this cipher through a `CryptoObject` (API 30+).
     *
     * @property cipher the initialised cipher the platform will vouch for
     */
    data class Bound(
        val cipher: Cipher,
    ) : AuthenticatedCipher

    /**
     * The prompt runs without a `CryptoObject`; the cipher is built once it succeeds (API 29).
     *
     * The binding is looser — *a recent* authentication rather than *this* one — but it is still
     * cryptographic: outside the validity window the Keystore refuses the operation outright.
     */
    data object Deferred : AuthenticatedCipher
}
