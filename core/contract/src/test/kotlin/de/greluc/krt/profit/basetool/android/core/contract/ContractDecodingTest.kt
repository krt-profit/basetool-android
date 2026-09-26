/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.contract

import de.greluc.krt.profit.basetool.android.core.contract.model.BankApprovalLimitsDto
import de.greluc.krt.profit.basetool.android.core.contract.model.RegistrationStatusDto
import de.greluc.krt.profit.basetool.android.core.contract.model.TermsStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests the generated models at decode time: decimals through the registered contextual serializer without `Double`
 * rounding, and unknown fields ignored.
 */
class ContractDecodingTest {
    @Test
    fun `a decimal keeps every digit the server sent`() {
        val json = """{"allMembersLimit": 12345678901234567890.123456789}"""

        val decoded = KrtJson.decodeFromString(BankApprovalLimitsDto.serializer(), json)

        assertEquals(
            BigDecimal("12345678901234567890.123456789"),
            decoded.allMembersLimit?.value,
        )
    }

    @Test
    fun `a decimal inside a map decodes too`() {
        val json = """{"roleLimits": {"OFFICER": 250000.50, "MEMBER": 1000}}"""

        val decoded = KrtJson.decodeFromString(BankApprovalLimitsDto.serializer(), json)

        assertEquals(BigDecimal("250000.50"), decoded.roleLimits?.get("OFFICER")?.value)
        assertEquals(BigDecimal("1000"), decoded.roleLimits?.get("MEMBER")?.value)
    }

    @Test
    fun `a decimal round-trips as a JSON number, not a string`() {
        val original = BankApprovalLimitsDto(allMembersLimit = KrtDecimal(BigDecimal("42.50")))

        val encoded = KrtJson.encodeToString(BankApprovalLimitsDto.serializer(), original)

        assertTrue("expected an unquoted number, got: $encoded", encoded.contains("\"allMembersLimit\":42.50"))
        assertEquals(original, KrtJson.decodeFromString(BankApprovalLimitsDto.serializer(), encoded))
    }

    @Test
    fun `an enum decodes to its wire constant`() {
        val json = """{"approvalStatus": "ACTIVE"}"""

        val decoded = KrtJson.decodeFromString(RegistrationStatusDto.serializer(), json)

        assertEquals(RegistrationStatusDto.ApprovalStatus.ACTIVE, decoded.approvalStatus)
    }

    @Test
    fun `an enum constant this build has never heard of decodes as null, not a crash`() {
        val json = """{"approvalStatus": "SUSPENDED_PENDING_REVIEW"}"""

        val decoded = KrtJson.decodeFromString(RegistrationStatusDto.serializer(), json)

        assertNull(decoded.approvalStatus)
    }

    @Test
    fun `a field this build has never heard of is ignored`() {
        val json = """{"accepted": true, "currentVersion": "v2.1", "somethingAddedLater": 7}"""

        val decoded = KrtJson.decodeFromString(TermsStatusDto.serializer(), json)

        assertEquals(true, decoded.accepted)
        assertEquals("v2.1", decoded.currentVersion)
    }

    @Test
    fun `a field the server omits decodes as null rather than failing`() {
        val decoded = KrtJson.decodeFromString(TermsStatusDto.serializer(), "{}")

        assertNull(decoded.accepted)
        assertNull(decoded.currentVersion)
    }
}
