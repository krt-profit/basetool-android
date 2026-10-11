/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Materialsammelübersicht's writes: the verb and exact path of the unlinks and the delivered
 * flag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaterialCollectionRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MaterialCollectionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            MaterialCollectionRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * The next recorded request as `METHOD target`.
     *
     * @return the verb and the request target, space-separated.
     */
    private fun nextCall(): String = server.takeRequest().let { "${it.method} ${it.target}" }

    @Test
    fun `an entry is unlinked from its order as an allocation`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())

            val result = repository.unlinkEntry("o1", "i1")

            assertEquals("DELETE /api/v1/orders/o1/allocations/i1", nextCall())
            assertTrue(result is ApiResult.Success)
        }

    @Test
    fun `a material is unlinked from its order and the delivered flag is patched on the entry`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(HTTP_NO_CONTENT).build())
            server.enqueue(
                MockResponse.Builder()
                    .code(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .body("{}")
                    .build(),
            )

            repository.unlinkMaterial("o1", "m1")
            repository.setDelivered(entryId = "i1", orderId = "o1", delivered = true, version = 2)

            assertEquals("DELETE /api/v1/orders/o1/materials/m1", nextCall())
            assertEquals("PATCH /api/v1/inventory/i1/delivered", nextCall())
        }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
    }
}
