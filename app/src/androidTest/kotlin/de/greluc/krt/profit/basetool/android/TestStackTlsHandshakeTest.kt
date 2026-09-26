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
 * Verifies on a device that the dev build's bundled trust anchor is applied by the platform's
 * network security config.
 *
 * It calls the backend's session-free health endpoint, so a handshake failure cannot be confused
 * with an authorization failure. Without a reachable test stack the test is skipped, not failed.
 * Run it with the test stack up and the port forwarded:
 *
 * ```
 * docker compose --env-file .env.test -f docker-compose.yml -f docker-compose.test.yml \
 *     --profile dev up -d
 * adb reverse tcp:11261 tcp:11261
 * ./gradlew :app:connectedDevDebugAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TestStackTlsHandshakeTest {
    /**
     * The dev build completes a TLS handshake with the test stack's backend.
     *
     * A green run means the committed anchor in `res/raw` reached the platform trust manager through
     * `<debug-overrides>`.
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

            assertTrue("expected an HTTP response, got $status", status > 0)
            assertTrue(
                "the connection must actually be TLS, or this test proves nothing",
                connection.cipherSuite.isNotBlank(),
            )
        } catch (handshake: SSLHandshakeException) {
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
