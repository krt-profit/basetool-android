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
 * What the generated models are worth is decided at **decode** time, so that is what is tested.
 *
 * A generated model compiles by construction; none of the failures that matter show up there. The
 * ones below all do, and every one of them was reachable while writing this module:
 *
 * - a decimal marked `@Contextual` whose serializer is not registered — compiles, throws on the
 *   first bank balance a member opens;
 * - a decimal read through a `Double` — decodes, and is wrong by a cent in a double-entry ledger;
 * - an unknown field — decodes today, throws the day the server adds one, on phones that cannot
 *   be redeployed.
 */
class ContractDecodingTest {
    @Test
    fun `a decimal keeps every digit the server sent`() {
        val json = """{"allMembersLimit": 12345678901234567890.123456789}"""

        val decoded = KrtJson.decodeFromString(BankApprovalLimitsDto.serializer(), json)

        // Through a Double this arrives as 1.2345678901234567E19 — the same number to a physicist
        // and a different balance to an auditor.
        assertEquals(
            BigDecimal("12345678901234567890.123456789"),
            decoded.allMembersLimit?.value,
        )
    }

    @Test
    fun `a decimal inside a map decodes too`() {
        // The case that failed to compile before `KrtDecimal` existed: `@Contextual` on a property
        // describes the property's own type, never a type argument, so a Map's values need a type
        // that carries its own serializer.
        val json = """{"roleLimits": {"OFFICER": 250000.50, "MEMBER": 1000}}"""

        val decoded = KrtJson.decodeFromString(BankApprovalLimitsDto.serializer(), json)

        assertEquals(BigDecimal("250000.50"), decoded.roleLimits?.get("OFFICER")?.value)
        assertEquals(BigDecimal("1000"), decoded.roleLimits?.get("MEMBER")?.value)
    }

    @Test
    fun `a decimal round-trips as a JSON number, not a string`() {
        val original = BankApprovalLimitsDto(allMembersLimit = KrtDecimal(BigDecimal("42.50")))

        val encoded = KrtJson.encodeToString(BankApprovalLimitsDto.serializer(), original)

        // The server declares these `type: number`; quoting them would change the wire shape and
        // be refused on the first write this app ever sends.
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
        // THE reason `coerceInputValues` is on, and it is not a nicety. Generated enums are
        // strict: kotlinx.serialization throws on an unrecognised constant. The status below is
        // read on the LOGIN path, so a fourth approval status added on the server would crash
        // every phone at the one moment a member cannot work around it. Coercion turns that into
        // `null`, which the repository maps to its own UNKNOWN — "not cleared", so the safe
        // outcome is the gate rather than the app.
        val json = """{"approvalStatus": "SUSPENDED_PENDING_REVIEW"}"""

        val decoded = KrtJson.decodeFromString(RegistrationStatusDto.serializer(), json)

        assertNull(decoded.approvalStatus)
    }

    @Test
    fun `a field this build has never heard of is ignored`() {
        // REQ-API-009 makes additive change explicitly free, so the reader has to survive it: a
        // released build sits on a phone for weeks and cannot be fixed forward.
        val json = """{"accepted": true, "currentVersion": "v2.1", "somethingAddedLater": 7}"""

        val decoded = KrtJson.decodeFromString(TermsStatusDto.serializer(), json)

        assertEquals(true, decoded.accepted)
        assertEquals("v2.1", decoded.currentVersion)
    }

    @Test
    fun `a field the server omits decodes as null rather than failing`() {
        // Every generated property is nullable with a null default, because the document marks
        // almost nothing `required`. That is a fact about the contract, not a bug in the models —
        // and it means "absent" is a state each repository has to decide the meaning of, rather
        // than something the type system settles.
        val decoded = KrtJson.decodeFromString(TermsStatusDto.serializer(), "{}")

        assertNull(decoded.accepted)
        assertNull(decoded.currentVersion)
    }
}
