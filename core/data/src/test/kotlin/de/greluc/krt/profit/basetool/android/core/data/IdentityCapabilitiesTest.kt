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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the bank's two flags come from, and where they deliberately do **not**.
 *
 * The first attempt derived them from `UserDto.roles`, applying Spring's
 * `ADMIN > BANK_MANAGEMENT > BANK_EMPLOYEE` hierarchy on the client. That could never have worked,
 * and a device run proved it: the me-response reports role **display names** — `"Bank Employee"`,
 * not `BANK_EMPLOYEE` — the bank roles carry no permissions at all, and the hierarchy lives in the
 * server's `SecurityConfig`. The scope segment showed its padlock to a user who holds the role.
 *
 * `GET /api/v1/me/capabilities` answers both questions with the hierarchy already applied. These
 * tests pin that the client asks it and keeps no rule of its own — including the inverse case a
 * role-name reading would get wrong, and the refusal that must lock rather than open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityCapabilitiesTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.close()
    }

    /**
     * Reads the identity against a me-response and a capabilities answer.
     *
     * @param roles what the me-response reports as assigned; deliberately populated, so a
     *   regression that starts reading them again is visible.
     * @param capabilities the capabilities body, or `null` to make that read fail.
     * @param meExtras extra me-response fields, appended raw. Used to state the membership flags
     *   explicitly so a regression that starts reading them again shows up as a failure.
     * @return the identity the app assembled.
     */
    private suspend fun identityWith(
        roles: String,
        capabilities: String?,
        meExtras: String = "",
    ): Identity {
        server.enqueue(
            MockResponse.Builder()
                .code(HTTP_OK)
                .addHeader("Content-Type", "application/json")
                .body("""{"id":"$USER_ID","roles":[$roles]$meExtras}""")
                .build(),
        )
        server.enqueue(
            capabilities?.let {
                MockResponse.Builder()
                    .code(HTTP_OK)
                    .addHeader("Content-Type", "application/json")
                    .body(it)
                    .build()
            } ?: MockResponse.Builder().code(HTTP_SERVER_ERROR).build(),
        )
        val repository =
            IdentityRepository(
                httpClient = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
        val result = repository.me()
        assertTrue("the me-read should succeed", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }

    @Test
    fun `an admin reaches Logistician and Mission-Manager without holding either membership`() =
        runTest {
            // The defect this whole change exists for. The me-response's isLogistician /
            // isMissionManager report whether a STAFFEL MEMBERSHIP ROW carries the flag, and an
            // admin holds no Staffel membership by design — so both were false and the app greyed
            // out the Lager writes, the Auftrag writes and the payout confirmation for the one role
            // that may do everything. The me-response below still says false, on purpose: if a
            // regression starts reading it again, this test goes red.
            val identity =
                identityWith(
                    roles = """"Admin"""",
                    meExtras = ""","isLogistician":false,"isMissionManager":false""",
                    capabilities =
                        """{"canViewBankStaff":true,"canManageBank":true,""" +
                            """"isLogisticianOrAbove":true,"isMissionManagerOrAbove":true,""" +
                            """"isAdmin":true}""",
                )

            assertTrue(identity.logistician)
            assertTrue(identity.missionManager)
            assertTrue(identity.admin)
        }

    @Test
    fun `an officer reaches both roles too, and is not an admin`() =
        runTest {
            val identity =
                identityWith(
                    roles = """"Officer"""",
                    meExtras = ""","isLogistician":false,"isMissionManager":false""",
                    capabilities =
                        """{"canViewBankStaff":false,"canManageBank":false,""" +
                            """"isLogisticianOrAbove":true,"isMissionManagerOrAbove":true,""" +
                            """"isAdmin":false}""",
                )

            assertTrue(identity.logistician)
            assertTrue(identity.missionManager)
            assertFalse(identity.admin)
        }

    @Test
    fun `a plain member reaches neither, even though the me-response would have said nothing`() =
        runTest {
            val identity =
                identityWith(
                    roles = """"KRT Member"""",
                    capabilities =
                        """{"canViewBankStaff":false,"canManageBank":false,""" +
                            """"isLogisticianOrAbove":false,"isMissionManagerOrAbove":false,""" +
                            """"isAdmin":false}""",
                )

            assertFalse(identity.logistician)
            assertFalse(identity.missionManager)
            assertFalse(identity.admin)
        }

    @Test
    fun `a failed capabilities read locks the grants rather than guessing them`() =
        runTest {
            // The narrower reading: an outage must not hand somebody a control the server refuses.
            // The me-response deliberately claims the membership flags are set.
            val identity =
                identityWith(
                    roles = """"KRT Member"""",
                    meExtras = ""","isLogistician":true,"isMissionManager":true""",
                    capabilities = null,
                )

            assertFalse(identity.logistician)
            assertFalse(identity.missionManager)
            assertFalse(identity.admin)
        }

    @Test
    fun `a bank employee sees the staff surface but not the lifecycle`() =
        runTest {
            val identity =
                identityWith(
                    roles = """"KRT Member","Bank Employee"""",
                    capabilities = """{"canViewBankStaff":true,"canManageBank":false}""",
                )

            assertTrue(identity.bankEmployee)
            assertFalse(identity.bankManagement)
        }

    @Test
    fun `the flags come from the capabilities, not from the role names beside them`() =
        runTest {
            // The role list says nothing about the bank; the server says the caller runs it. This
            // is the Bankleitung case, whose display name is "Bank Management" and whose code the
            // client never sees.
            val identity =
                identityWith(
                    roles = """"KRT Member"""",
                    capabilities = """{"canViewBankStaff":true,"canManageBank":true}""",
                )

            assertTrue(identity.bankEmployee)
            assertTrue(identity.bankManagement)
        }

    @Test
    fun `a role list that mentions the bank does not by itself open anything`() =
        runTest {
            // The inverse guard: a client that went back to reading role names would pass the test
            // above and fail this one.
            val identity =
                identityWith(
                    roles = """"Bank Employee","Bank Management"""",
                    capabilities = """{"canViewBankStaff":false,"canManageBank":false}""",
                )

            assertFalse(identity.bankEmployee)
            assertFalse(identity.bankManagement)
        }

    @Test
    fun `an ordinary member holds neither`() =
        runTest {
            val identity =
                identityWith(
                    roles = """"KRT Member","Officer"""",
                    capabilities = """{"canViewBankStaff":false,"canManageBank":false}""",
                )

            assertFalse(identity.bankEmployee)
            assertFalse(identity.bankManagement)
        }

    @Test
    fun `a capabilities read that fails locks the scope rather than opening it`() =
        runTest {
            val identity = identityWith(roles = """"Bank Employee"""", capabilities = null)

            // The member record loaded, so the identity is usable; what could not be learned is
            // simply not offered.
            assertFalse(identity.bankEmployee)
            assertFalse(identity.bankManagement)
        }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_SERVER_ERROR = 500

        /** Any well-formed id; the flags are what this test is about. */
        const val USER_ID = "11111111-2222-4333-8444-555555555555"
    }
}
