/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

/**
 * The one assertion about the dev build's TLS that cannot be made off a device.
 *
 * `NetworkSecurityConfigTest` proves the anchor is bundled and referenced. It cannot prove that
 * **Android applies it**, because the network security config is a property of the running process:
 * the platform installs an NSC-aware `X509TrustManager` as the default, and neither the JVM nor
 * Robolectric does. Until this ran, the trust anchor's effect was asserted only by reading Android's
 * documentation — and the failure it guards against has no error message of its own. TLS fails
 * before the request carries a byte, the app maps it to `ApiError.Network`, and the screen tells a
 * developer they are offline while the server runs on their own machine.
 *
 * This is deliberately not a test of the API. It asks the backend's health endpoint for one reason:
 * it needs no session, so a handshake failure cannot be confused with an authorization failure.
 *
 * **Running it**: bring the test stack up, forward the port into the emulator, then
 * `./gradlew :app:connectedDevDebugAndroidTest`. Without the stack the test reports itself skipped
 * rather than failed — a handshake cannot be judged against a server that is not there, and a red
 * bar for "you did not start docker" trains people to ignore red bars.
 *
 * ```
 * docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml \
 *     --profile dev up -d
 * adb reverse tcp:11261 tcp:11261
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TestStackTlsHandshakeTest {
    /**
     * The dev build completes a TLS handshake with the test stack's backend.
     *
     * A green run means the committed anchor in `res/raw` reached the platform trust manager
     * through `<debug-overrides>`. Before it was bundled, this same request failed with
     * `CertPathValidatorException: Trust anchor for certification path not found`.
     */
    @Test
    fun theDevBuildTrustsTheTestStackCertificate() {
        val connection =
            URI("${BuildConfig.API_BASE_URL}$HEALTH_PATH").toURL().openConnection()
                as HttpsURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS

        try {
            val status = connection.responseCode

            // Any status at all means the handshake completed and the request was answered; which
            // status it is belongs to the backend's own tests, not to this one.
            assertTrue("expected an HTTP response, got $status", status > 0)
            assertTrue(
                "the connection must actually be TLS, or this test proves nothing",
                connection.cipherSuite.isNotBlank(),
            )
        } catch (handshake: SSLHandshakeException) {
            // Re-thrown rather than assumed away: the stack IS reachable, so this is the failure
            // this test exists to catch, and skipping it would hide exactly the regression.
            throw AssertionError(
                "the dev build does not trust the test stack's certificate — check that " +
                    "res/raw/basetool_test_ca.crt matches docker/test-tls/ in the main repository",
                handshake,
            )
        } catch (unreachable: ConnectException) {
            assumeNoException("test stack not reachable at ${BuildConfig.API_BASE_URL}", unreachable)
        } catch (timeout: SocketTimeoutException) {
            assumeNoException("test stack did not answer at ${BuildConfig.API_BASE_URL}", timeout)
        } catch (io: IOException) {
            assumeNoException("test stack not reachable at ${BuildConfig.API_BASE_URL}", io)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /**
         * Needs no session, so a handshake failure cannot be mistaken for an authorization failure.
         */
        const val HEALTH_PATH = "/actuator/health"

        /** Short: the server is on the same machine, so a slow answer means it is not there. */
        const val TIMEOUT_MILLIS = 5000
    }
}
