/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.lock

import de.greluc.krt.profit.basetool.android.core.auth.AppLock
import de.greluc.krt.profit.basetool.android.core.auth.AuthenticatedCipher
import de.greluc.krt.profit.basetool.android.core.auth.SecretCipherException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The lock's two invariants: **when** it seals, and **what it takes to open**.
 *
 * The second is the one CodeQL caught the earlier revision on. Opening used to be a state
 * assignment the callback triggered; it is now a decrypt that only an authenticated key can perform,
 * so the tests assert that a "successful" authentication whose decrypt fails does **not** open the
 * app. A lock that can be opened by anything other than the decrypt is not a lock.
 *
 * The grace period is the other half. Its failure modes are opposite and both bad — too eager and
 * it fires on every task switch until the member turns it off, too lax and the phone sits unlocked
 * on a desk — so the boundary is asserted from both sides.
 *
 * Robolectric because the refusal paths log through the project facade, which calls
 * `android.util.Log` — unmocked in a plain JVM test, so those two cases would fail on the
 * diagnostic instead of the assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Any string resource; these tests are about state, not about which message it carries. */
    private val someMessage = 1_234

    /**
     * **API 29 defers the cipher, and that must not read as a broken lock.**
     *
     * The regression this whole type exists for. A time-bound key throws
     * `UserNotAuthenticatedException` from `Cipher.init` until the member has authenticated, so no
     * cipher can be produced before the prompt. An earlier revision spelled that as `null` — the
     * same value that means "this lock can never be opened again" — and the app therefore reported
     * a perfectly good lock as unsatisfiable and refused to arm one at all, on the entire minSdk
     * platform. Both unit tests and the emulator's API 37 image stayed green.
     */
    @Test
    fun `a deferred cipher is not an unsatisfiable lock`() =
        runTest(dispatcher) {
            val lock = FakeLock(armed = true, deferred = true)
            val viewModel = AppLockViewModel(lock)
            viewModel.start()
            advanceUntilIdle()

            val request = viewModel.prepareUnlock()
            advanceUntilIdle()

            assertEquals(AuthenticatedCipher.Deferred, request)
            assertTrue(
                "a deferral must leave the lock openable, not unsatisfiable",
                viewModel.state.value is AppLockState.Locked,
            )
        }

    /**
     * Arming on the deferred platform still arms.
     *
     * The prompt carries no `CryptoObject` there, so `completeArm` receives `null` and the lock has
     * to build its own cipher afterwards. If that path were missing, the API-29 switch would look
     * like it worked and guard nothing.
     */
    @Test
    fun `arming works without a cipher to bind`() =
        runTest(dispatcher) {
            val lock = FakeLock(armed = false, deferred = true)
            val viewModel = AppLockViewModel(lock)
            viewModel.start()
            advanceUntilIdle()

            assertEquals(AuthenticatedCipher.Deferred, viewModel.prepareArm())
            viewModel.completeArm(null)
            advanceUntilIdle()

            assertTrue("expected the lock to be armed", lock.armed)
        }

    /**
     * A lock whose answers the test dictates.
     *
     * @property armed what `isArmed()` reports
     * @property cipher what `unlockCipher()` returns; `null` models an invalidated key
     * @property opens what `open()` reports for that cipher
     * @property armThrows whether arming fails, as it does on a device that cannot create the key
     * @property deferred models the API-29 platform: a time-bound key that cannot be initialised
     *   into a cipher until the member has authenticated, so both preparation calls answer
     *   [AuthenticatedCipher.Deferred] rather than handing one over
     */
    private class FakeLock(
        var armed: Boolean,
        private val cipher: Cipher? = null,
        private val opens: Boolean = true,
        private val armThrows: Boolean = false,
        private val deferred: Boolean = false,
    ) : AppLock {
        var openCalls = 0
            private set

        override suspend fun isArmed(): Boolean = armed

        override suspend fun prepareArm(): AuthenticatedCipher {
            if (armThrows) {
                throw SecretCipherException("no auth-bound key on this device", null)
            }
            return if (deferred) {
                AuthenticatedCipher.Deferred
            } else {
                AuthenticatedCipher.Bound(Cipher.getInstance("AES/GCM/NoPadding"))
            }
        }

        override suspend fun completeArm(cipher: Cipher?) {
            armed = true
        }

        override suspend fun disarm() {
            armed = false
        }

        override suspend fun unlockCipher(): AuthenticatedCipher? =
            when {
                deferred -> AuthenticatedCipher.Deferred
                cipher != null -> AuthenticatedCipher.Bound(cipher)
                else -> null
            }

        override suspend fun open(cipher: Cipher?): Boolean {
            openCalls++
            return opens
        }
    }

    /**
     * A cipher instance to pass around; nothing here ever calls it.
     *
     * @return an uninitialised AES/GCM cipher
     */
    private fun anyCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    /**
     * Installs the test dispatcher as `Dispatchers.Main`, which `viewModelScope` uses.
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * Restores the real main dispatcher.
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * With no lock armed, the app opens straight away.
     */
    @Test
    fun `an unarmed lock never seals the app`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = false))

            viewModel.start()
            advanceUntilIdle()

            assertEquals(AppLockState.Open, viewModel.state.value)
        }

    /**
     * With the lock armed, a cold start is locked.
     */
    @Test
    fun `a cold start with the lock armed is locked`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true))

            viewModel.start()
            advanceUntilIdle()

            assertEquals(AppLockState.Locked(), viewModel.state.value)
        }

    /**
     * Nothing is decided before the armed state has been read.
     *
     * Rendering the app for the instant before the answer arrives would flash its contents past
     * somebody the lock exists to exclude.
     */
    @Test
    fun `nothing is decided before the armed state is read`() {
        val viewModel = AppLockViewModel(FakeLock(armed = true))

        assertEquals(AppLockState.Unknown, viewModel.state.value)
    }

    /**
     * A successful decrypt opens the app.
     */
    @Test
    fun `a decrypt that returns the sentinel opens the app`() =
        runTest(dispatcher) {
            val lock = FakeLock(armed = true, cipher = anyCipher(), opens = true)
            val viewModel = AppLockViewModel(lock)
            viewModel.start()
            advanceUntilIdle()

            viewModel.unlock(anyCipher())
            advanceUntilIdle()

            assertEquals(AppLockState.Open, viewModel.state.value)
            assertEquals(1, lock.openCalls)
        }

    /**
     * **A successful authentication whose decrypt fails does not open the app.**
     *
     * The whole reason the lock stopped being a boolean: the platform saying yes is not the thing
     * that opens the gate, the sentinel coming back is.
     */
    @Test
    fun `authentication without a working decrypt does not open the app`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true, cipher = anyCipher(), opens = false))
            viewModel.start()
            advanceUntilIdle()

            viewModel.unlock(anyCipher())
            advanceUntilIdle()

            assertTrue("expected the app to stay locked", viewModel.state.value is AppLockState.Locked)
        }

    /**
     * An invalidated key is a dead end, reported as such.
     *
     * A new biometric enrolment destroys the key, so retrying can only fail; the state is kept
     * apart from [AppLockState.Locked] so the screen can drop the retry button and offer the way
     * out instead.
     */
    @Test
    fun `an invalidated key becomes unsatisfiable rather than a failed attempt`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true, cipher = null))
            viewModel.start()
            advanceUntilIdle()

            val cipher = viewModel.prepareUnlock()
            advanceUntilIdle()

            assertNull(cipher)
            assertEquals(AppLockState.Unsatisfiable, viewModel.state.value)
        }

    /**
     * A short trip to another app does not re-lock.
     */
    @Test
    fun `a brief switch away does not re-lock`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true, cipher = anyCipher()))
            viewModel.start()
            advanceUntilIdle()
            viewModel.unlock(anyCipher())
            advanceUntilIdle()

            viewModel.onBackgrounded(0)
            viewModel.onForegrounded(30.seconds.inWholeMilliseconds)
            advanceUntilIdle()

            assertEquals(AppLockState.Open, viewModel.state.value)
        }

    /**
     * Beyond the grace period it re-locks.
     */
    @Test
    fun `a long absence re-locks`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true, cipher = anyCipher()))
            viewModel.start()
            advanceUntilIdle()
            viewModel.unlock(anyCipher())
            advanceUntilIdle()

            viewModel.onBackgrounded(0)
            viewModel.onForegrounded(6.minutes.inWholeMilliseconds)
            advanceUntilIdle()

            assertEquals(AppLockState.Locked(), viewModel.state.value)
        }

    /**
     * Exactly at the boundary counts as expired.
     *
     * Pinned because `>` and `>=` are equally plausible readings of "after 5 minutes", and the safer
     * one is the one that locks.
     */
    @Test
    fun `exactly the grace period re-locks`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true, cipher = anyCipher()))
            viewModel.start()
            advanceUntilIdle()
            viewModel.unlock(anyCipher())
            advanceUntilIdle()

            viewModel.onBackgrounded(0)
            viewModel.onForegrounded(BACKGROUND_GRACE.inWholeMilliseconds)
            advanceUntilIdle()

            assertEquals(AppLockState.Locked(), viewModel.state.value)
        }

    /**
     * A long absence with the lock disarmed does not lock.
     *
     * The armed state is re-read on the way back rather than remembered, so disarming the lock and
     * putting the phone down leaves no delayed lock behind it.
     */
    @Test
    fun `a long absence with the lock disarmed does not lock`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = false))
            viewModel.start()
            advanceUntilIdle()

            viewModel.onBackgrounded(0)
            viewModel.onForegrounded(6.minutes.inWholeMilliseconds)
            advanceUntilIdle()

            assertEquals(AppLockState.Open, viewModel.state.value)
        }

    /**
     * Coming back without having gone away is a no-op.
     *
     * `onStart` fires on the very first launch too, before any `onStop`, and treating that as a
     * five-minute absence would lock an app that had just been opened.
     */
    @Test
    fun `foregrounding without a prior backgrounding changes nothing`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true, cipher = anyCipher()))
            viewModel.start()
            advanceUntilIdle()
            viewModel.unlock(anyCipher())
            advanceUntilIdle()

            viewModel.onForegrounded(10.minutes.inWholeMilliseconds)
            advanceUntilIdle()

            assertEquals(AppLockState.Open, viewModel.state.value)
        }

    /**
     * A failed attempt keeps the lock up and carries its message.
     */
    @Test
    fun `a failed unlock stays locked and reports why`() =
        runTest(dispatcher) {
            val viewModel = AppLockViewModel(FakeLock(armed = true))
            viewModel.start()
            advanceUntilIdle()

            viewModel.onUnlockFailed(someMessage)

            assertEquals(AppLockState.Locked(someMessage), viewModel.state.value)
        }

    /**
     * Arming does not seal the app in the member's face.
     */
    @Test
    fun `arming the lock does not lock immediately`() =
        runTest(dispatcher) {
            val lock = FakeLock(armed = false)
            val viewModel = AppLockViewModel(lock)
            viewModel.start()
            advanceUntilIdle()

            val request = viewModel.prepareArm()
            viewModel.completeArm((request as AuthenticatedCipher.Bound).cipher)
            advanceUntilIdle()

            assertEquals(AppLockState.Open, viewModel.state.value)
            assertTrue("expected the lock to be armed for next time", lock.armed)
        }

    /**
     * **Arming without an authenticated cipher arms nothing.**
     *
     * The regression this whole two-phase shape exists for. An earlier revision sealed the session
     * key inline while creating the auth-per-use Keystore key, which Keystore refuses with "Key
     * user not authenticated" — on every device, and invisibly to every unit test, because the
     * Keystore is not exercised off a device. `setEnabled(true)` therefore does nothing at all now;
     * the only route in is prepareArm + a prompt + completeArm.
     */
    @Test
    fun `setEnabled cannot arm the lock on its own`() =
        runTest(dispatcher) {
            val lock = FakeLock(armed = false)
            val viewModel = AppLockViewModel(lock)
            viewModel.start()
            advanceUntilIdle()

            viewModel.setEnabled(true)
            advanceUntilIdle()

            assertTrue("arming must require an authenticated cipher", !lock.armed)
        }

    /**
     * A device that cannot create an auth-bound key does not end up with a toggle that guards
     * nothing.
     */
    @Test
    fun `a device that cannot create the key reports it instead of half-arming`() =
        runTest(dispatcher) {
            val lock = FakeLock(armed = false, armThrows = true)
            val viewModel = AppLockViewModel(lock)
            viewModel.start()
            advanceUntilIdle()

            val cipher = viewModel.prepareArm()
            advanceUntilIdle()

            assertNull("no cipher means no prompt and no arming", cipher)
            assertEquals(AppLockState.Open, viewModel.state.value)
            assertTrue("the lock must not report itself armed", !lock.armed)
        }
}
