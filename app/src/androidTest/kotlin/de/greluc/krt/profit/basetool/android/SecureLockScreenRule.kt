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
 * Gives a device without a screen lock a throwaway PIN for the duration of one test and removes it
 * afterwards.
 *
 * Android refuses to create the app lock's auth-bound key without a secure lock screen, which a fresh
 * CI emulator lacks. A device where [KeyguardManager.isDeviceSecure] is already `true` is left
 * untouched. The PIN is set through the instrumentation shell's `locksettings`.
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
                    val output = shell("locksettings set-pin $THROWAWAY_PIN")
                    check(keyguard.isDeviceSecure) {
                        val feature =
                            instrumentation.targetContext.packageManager
                                .hasSystemFeature(SECURE_LOCK_SCREEN_FEATURE)
                        "locksettings did not give the device a screen lock; it answered " +
                            "\"${output.trim()}\"; $SECURE_LOCK_SCREEN_FEATURE=$feature"
                    }
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
     * Reading the output to the end is what waits for the command; closing the descriptor releases it.
     *
     * @param command the command line.
     * @return what the command printed.
     */
    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }
        }

    private companion object {
        /** A PIN that exists only on a CI emulator for the length of one test. Not a secret. */
        const val THROWAWAY_PIN = "147258"

        /** The platform feature a device needs before it can hold a PIN, pattern or password. */
        const val SECURE_LOCK_SCREEN_FEATURE = "android.software.secure_lock_screen"
    }
}
