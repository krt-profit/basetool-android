/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the network posture of both flavours against the one mistake that has no symptom.
 *
 * Every relaxation the dev build needs — cleartext to the emulator's loopback, and the user
 * certificate store as a trust anchor for the test stack's self-signed backend — is a hole in TLS
 * validation. In a release build it would be a serious one, and **nothing about a release build
 * fails if it leaks in**: the APK installs, the requests succeed, and the only difference is that
 * a proxy on the member's network can now read them.
 *
 * Two mechanisms keep that from happening, and the tests below pin both because either alone can be
 * undone by an ordinary-looking edit:
 *
 * - the relaxations live in the **dev source set**, which the prod flavour never sees;
 * - they sit in `<debug-overrides>`, which Android honours only for `android:debuggable="true"`.
 *
 * The second is the backstop for somebody editing the wrong file, so the main config is asserted to
 * be free of both — not because it would be fatal today, but because a `debug-overrides` block
 * appearing there is exactly the kind of copy-paste nobody reviews twice.
 *
 * Read as text rather than parsed: the property being asserted is which strings appear in which
 * file, and an XML parser would add ceremony without adding an assertion.
 */
class NetworkSecurityConfigTest {
    private val releaseConfig = File("src/main/res/xml/network_security_config.xml")
    private val devConfig = File("src/dev/res/xml/network_security_config.xml")

    /**
     * The release posture forbids cleartext outright.
     */
    @Test
    fun `the main config forbids cleartext`() {
        val xml = read(releaseConfig)

        assertTrue(
            "the base config must set cleartextTrafficPermitted=\"false\"",
            xml.contains("<base-config cleartextTrafficPermitted=\"false\""),
        )
        assertFalse(
            "no domain in the main config may permit cleartext",
            xml.contains("cleartextTrafficPermitted=\"true\""),
        )
    }

    /**
     * The release posture carries no debug override.
     *
     * Android would ignore one in a release build anyway. It is asserted because its *presence*
     * would mean somebody edited the main source set intending to change the dev build, and the
     * next relaxation they add might not be one Android ignores.
     */
    @Test
    fun `the main config carries no debug overrides`() {
        assertFalse(
            "debug-overrides belongs in the dev source set, not here",
            read(releaseConfig).contains("<debug-overrides>"),
        )
    }

    /**
     * The dev build trusts the user certificate store, which is what lets it reach the test stack.
     *
     * Without it every API call fails TLS validation and surfaces as `ApiError.Network` — the app
     * tells the developer they are offline while the server is running on their own machine.
     */
    @Test
    fun `the dev config adds the user store as a trust anchor`() {
        val xml = read(devConfig)

        assertTrue(
            "the trust anchor must sit inside debug-overrides, so a release build ignores it",
            xml.contains("<debug-overrides>"),
        )
        assertTrue(
            "the user certificate store must be trusted",
            xml.contains("<certificates src=\"user\""),
        )
        assertTrue(
            "the system anchors must stay — debug-overrides adds to the other configs, and " +
                "dropping them would make the dev build trust ONLY hand-installed CAs",
            xml.contains("<certificates src=\"system\""),
        )
    }

    /**
     * The dev cleartext exception reaches the loopback hosts and nothing else.
     */
    @Test
    fun `the dev cleartext exception is limited to the emulator's host routes`() {
        val xml = read(devConfig)

        assertTrue(
            "the dev base config must still forbid cleartext by default",
            xml.contains("<base-config cleartextTrafficPermitted=\"false\""),
        )
        listOf("127.0.0.1", "localhost", "10.0.2.2").forEach { host ->
            assertTrue("$host must be reachable over cleartext for the test stack", xml.contains(">$host<"))
        }
        assertFalse(
            "includeSubdomains would widen the exception past the three loopback hosts",
            xml.contains("includeSubdomains=\"true\""),
        )
    }

    /**
     * Reads a config file.
     *
     * @param file the file to read; the Gradle `Test` task runs with the module directory as its
     *   working directory, so the paths above are module-relative
     * @return its contents
     */
    private fun read(file: File): String {
        assertTrue("${file.path} must exist", file.isFile)
        return file.readText()
    }
}
