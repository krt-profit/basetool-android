/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.app.KeyguardManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileInputStream

/**
 * Gives a device without a screen lock a throwaway PIN for the duration of one test, and takes it
 * away again afterwards.
 *
 * The app lock's key is auth-bound (`setUserAuthenticationParameters(0, BIOMETRIC_STRONG or
 * DEVICE_CREDENTIAL)`), and Android refuses to create such a key at all on a device with no secure
 * lock screen. Every device the lock was walked on had one — a member who switches the lock on
 * has one by definition. A freshly booted CI emulator (`instrumented.yml`, Gradle Managed Devices)
 * has none, so without this rule [AppLockKeystoreContractTest] would fail there for a reason that
 * says nothing about the contract it pins.
 *
 * A device that already has a lock is left exactly as it was: the rule checks
 * [KeyguardManager.isDeviceSecure] first and touches nothing when it is `true`, so running the suite
 * on a developer's own emulator never changes or clears their PIN.
 *
 * The PIN is set through the instrumentation's shell (`locksettings`), which runs with the shell
 * uid and needs no permission from the app under test.
 */
class SecureLockScreenRule : TestRule {
    /**
     * Wraps [base] with the set-and-clear around it.
     *
     * @param base the test.
     * @param description the test's description, unused.
     * @return the wrapped statement.
     */
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val keyguard = instrumentation.targetContext.getSystemService(KeyguardManager::class.java)
                val setHere = !keyguard.isDeviceSecure
                if (setHere) {
                    shell("locksettings set-pin $THROWAWAY_PIN")
                    check(keyguard.isDeviceSecure) { "locksettings did not give the device a screen lock" }
                }
                try {
                    base.evaluate()
                } finally {
                    if (setHere) shell("locksettings clear --old $THROWAWAY_PIN")
                }
            }
        }

    /**
     * Runs one shell command and waits for it to finish.
     *
     * `executeShellCommand` returns as soon as the command is started; reading its output to the
     * end is what waits for it, and closing the descriptor is what releases it.
     *
     * @param command the command line.
     */
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        }
    }

    private companion object {
        /** A PIN that exists only on a CI emulator for the length of one test. Not a secret. */
        const val THROWAWAY_PIN = "147258"
    }
}
