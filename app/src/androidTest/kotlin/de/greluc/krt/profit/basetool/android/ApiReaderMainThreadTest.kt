/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies on a device that a response is handled off the main thread (REQ-APP-API-008).
 *
 * `StrictMode` with `penaltyDeath()` on network access turns any main-thread socket write, such as
 * the `RST_STREAM` OkHttp sends when an unread HTTP/2 response is closed, into a failed test.
 */
@RunWith(AndroidJUnit4::class)
class ApiReaderMainThreadTest {
    private lateinit var server: MockWebServer
    private lateinit var reader: ApiReader
    private lateinit var previousPolicy: StrictMode.ThreadPolicy

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        reader =
            ApiReader(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
                json = KrtJson,
                logTag = "strictmode-test",
            )
        previousPolicy = StrictMode.getThreadPolicy()
    }

    @After
    fun tearDown() {
        StrictMode.setThreadPolicy(previousPolicy)
        server.close()
    }

    /**
     * A body-less write, driven from the main thread, must not touch the socket there.
     *
     * `postAccepted` is the shape that broke: it never reads the body, so closing the response is
     * what writes the reset. The server answers `200` with a body on purpose — a `204` leaves
     * nothing to reset and would pass even with the defect present.
     */
    @Test
    fun aBodyLessWriteDoesNotTouchTheSocketOnTheMainThread() =
        runBlocking {
            server.enqueue(
                MockResponse.Builder().code(HTTP_OK).body("{\"ignored\":true}").build(),
            )

            val result =
                withContext(Dispatchers.Main) {
                    armDeathOnNetwork()
                    reader.postAccepted("/api/v1/probe", Unit, Unit.serializer())
                }

            assertTrue("the write should have succeeded: $result", result is ApiResult.Success)
        }

    /**
     * The same for a read, which parses the body.
     *
     * `body.string()` is network access by every definition StrictMode uses; that it usually
     * returns from a buffer is why this never crashed and why it still has to be asserted.
     */
    @Test
    fun aReadDoesNotTouchTheSocketOnTheMainThread() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(HTTP_OK).body("\"ok\"").build())

            val result =
                withContext(Dispatchers.Main) {
                    armDeathOnNetwork()
                    reader.get("/api/v1/probe", String.serializer())
                }

            assertTrue("the read should have succeeded: $result", result is ApiResult.Success)
        }

    /** Makes any network access on this thread fatal for the rest of the test. */
    private fun armDeathOnNetwork() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectNetwork()
                .penaltyDeath()
                .build(),
        )
    }

    private companion object {
        const val HTTP_OK = 200
    }
}
