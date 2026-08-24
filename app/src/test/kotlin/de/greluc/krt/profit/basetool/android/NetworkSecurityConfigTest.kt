/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Pins the network posture of both flavours against the one mistake that has no symptom.
 *
 * Every relaxation the dev build needs — cleartext to the emulator's loopback, and a trust anchor
 * for the test stack's backend certificate — is a hole in TLS validation. In a release build it
 * would be a serious one, and **nothing about a release build fails if it leaks in**: the APK
 * installs, the requests succeed, and the only difference is that
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
    private val anchorFile = File("src/dev/res/raw/$ANCHOR_RESOURCE.crt")

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
     * Every production host the app speaks to is pinned, and to **both** Let's Encrypt roots.
     *
     * The second root is the assertion that earns its place. ISRG Root X2 is the ECDSA root, and a
     * certificate that chains to it while only X1 is pinned fails to validate — so pinning one
     * root would turn an ordinary CA-side change into an outage that reaches every installed
     * build at once.
     */
    @Test
    fun `every production host is pinned to both Let's Encrypt roots`() {
        val xml = read(releaseConfig)

        listOf("api.profit-base.online", "keycloak.profit-base.online", "profit-base.online")
            .forEach { host ->
                assertTrue(
                    "$host must have a domain-config",
                    xml.contains("<domain includeSubdomains=\"false\">$host</domain>"),
                )
            }
        assertEquals(
            "each of the three hosts needs its own pin-set",
            PINNED_HOSTS,
            Regex("<pin-set").findAll(xml).count(),
        )
        assertEquals(
            "ISRG Root X1 must be pinned on all three",
            PINNED_HOSTS,
            pinCount(xml, ISRG_X1),
        )
        assertEquals(
            "ISRG Root X2 must be pinned on all three — it is the ECDSA root, and omitting it " +
                "breaks every chain issued from its intermediates",
            PINNED_HOSTS,
            pinCount(xml, ISRG_X2),
        )
    }

    /**
     * Counts a pin as an actual `<pin>` element.
     *
     * A bare substring search also matches the file's own comment, which documents both values —
     * and would have passed with the pins present in prose and absent from the config.
     *
     * @param xml the config.
     * @param pin the base64 SPKI hash.
     * @return how many pin elements carry it.
     */
    private fun pinCount(
        xml: String,
        pin: String,
    ): Int = Regex(Regex.escape("<pin digest=\"SHA-256\">$pin</pin>")).findAll(xml).count()

    /**
     * Every pin-set carries an expiry.
     *
     * Android stops enforcing an expired pin-set rather than failing the connection, and that is
     * the only safety valve this design has: if both roots were ever replaced and no update
     * shipped, the app degrades to ordinary system trust instead of going dark. A pin-set without
     * one is a permanent commitment made by accident.
     */
    @Test
    fun `no pin-set is open-ended`() {
        val xml = read(releaseConfig)

        assertEquals(
            "every pin-set needs an expiration",
            Regex("<pin-set").findAll(xml).count(),
            Regex("<pin-set expiration=").findAll(xml).count(),
        )
    }

    /**
     * The dev flavour pins nothing.
     *
     * Its certificate is a throwaway signed by a CA that was destroyed at generation time, so a
     * pin there would tie every debug build to a file in another repository — and would break the
     * moment that material is regenerated.
     */
    @Test
    fun `the dev config pins nothing`() {
        assertFalse("the dev config must carry no pin-set", read(devConfig).contains("<pin-set"))
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
     * The dev build trusts the test stack's shared anchor, which is what lets it reach the backend.
     *
     * Without it every API call fails TLS validation and surfaces as `ApiError.Network` — the app
     * tells the developer they are offline while the server is running on their own machine.
     */
    @Test
    fun `the dev config adds the shared test anchor and keeps the other stores`() {
        val xml = read(devConfig)

        assertTrue(
            "the trust anchors must sit inside debug-overrides, so a release build ignores them",
            xml.contains("<debug-overrides>"),
        )
        assertTrue(
            "the test stack's shared certificate must be bundled as an anchor",
            xml.contains("<certificates src=\"@raw/$ANCHOR_RESOURCE\""),
        )
        assertTrue(
            "the user certificate store must stay, so a hand-installed proxy CA still works",
            xml.contains("<certificates src=\"user\""),
        )
        assertTrue(
            "the system anchors must stay — debug-overrides adds to the other configs, and " +
                "dropping them would make the dev build trust ONLY the two above",
            xml.contains("<certificates src=\"system\""),
        )
    }

    /**
     * **The bundled anchor is a certificate and nothing else.**
     *
     * The guard that makes committing the anchor defensible at all. `res/raw` takes any bytes, and
     * the file it is copied from lives beside a keystore in the other repository — one wrong `cp`
     * and a private key would be committed to a public repository and have to be treated as
     * disclosed. Asserting the *absence* of key material is cheap and the mistake is silent
     * otherwise: Android would simply fail to parse it, at runtime, on somebody else's machine.
     */
    @Test
    fun `the bundled anchor carries no private key`() {
        val pem = read(anchorFile)

        assertTrue("must be a PEM certificate", pem.contains("-----BEGIN CERTIFICATE-----"))
        listOf("PRIVATE KEY", "ENCRYPTED PRIVATE KEY", "RSA PRIVATE KEY").forEach { marker ->
            assertFalse("a private key must never be bundled: found $marker", pem.contains(marker))
        }
    }

    /**
     * The bundled anchor is a CA, is current, and says out loud that it is not for production.
     *
     * Three separate ways this file could rot into a confusing failure. A non-CA certificate is not
     * usable as a trust anchor and would fail path validation with a message about the *server*. An
     * expired one breaks every developer's stack at once, on a date nobody is watching. And a
     * subject that does not name itself a test artefact is one that somebody eventually mistakes
     * for a real one.
     */
    @Test
    fun `the bundled anchor is a current, self-describing CA`() {
        val certificate =
            anchorFile.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }

        assertTrue(
            "a trust anchor has to be a CA certificate (basicConstraints CA:TRUE)",
            certificate.basicConstraints >= 0,
        )
        assertTrue(
            "the anchor has expired, which breaks every test stack at once: " +
                "regenerate with docker/test-tls/generate-test-tls.sh in the main repository",
            certificate.notAfter.after(Date()),
        )
        assertTrue(
            "the subject must name it a test artefact, so nobody mistakes it for a real CA: " +
                certificate.subjectX500Principal.name,
            certificate.subjectX500Principal.name.contains("NOT FOR PRODUCTION"),
        )
    }

    /**
     * The prod flavour bundles no certificate at all.
     *
     * `<debug-overrides>` already makes a leaked anchor inert, but that is the backstop. This is the
     * intent: the release APK does not even contain the file.
     */
    @Test
    fun `the prod source set bundles no anchor`() {
        assertFalse(
            "the shared test anchor must exist only in the dev source set",
            File("src/main/res/raw/$ANCHOR_RESOURCE.crt").exists() ||
                File("src/prod/res/raw/$ANCHOR_RESOURCE.crt").exists(),
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

    private companion object {
        /**
         * The anchor's resource name, without extension.
         *
         * Shared between the XML assertion and the file assertions so a rename cannot satisfy one
         * while breaking the other.
         */
        const val ANCHOR_RESOURCE = "basetool_test_ca"

        /** The API host, Keycloak and the web frontend. */
        const val PINNED_HOSTS = 3

        /**
         * The SPKI pin of ISRG Root X1 (RSA).
         *
         * Recomputed from a trust store rather than transcribed — see the config's own note.
         */
        const val ISRG_X1 = "C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

        /** The SPKI pin of ISRG Root X2 (ECDSA). */
        const val ISRG_X2 = "diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI="
    }
}
