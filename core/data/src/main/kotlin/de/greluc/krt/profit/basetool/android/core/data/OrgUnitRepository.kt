/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.contract.KrtJson
import de.greluc.krt.profit.basetool.android.core.contract.model.ActiveOrgUnitResponse
import de.greluc.krt.profit.basetool.android.core.contract.model.OrgUnitMembershipOptionDto
import de.greluc.krt.profit.basetool.android.core.network.ApiReader
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.map
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient

/**
 * The org units a member may act in, and the one the server would pick for them.
 *
 * A seam of its own so the switcher's rules — which unit is active when nothing is pinned, what
 * happens when the pinned one disappears — can be exercised without a socket.
 */
interface OrgUnitSource {
    /**
     * Reads the units the member is a direct member of.
     *
     * @return the member's units, possibly empty; or a failure the caller can show.
     */
    suspend fun memberships(): ApiResult<List<OrgUnit>>

    /**
     * Reads the org unit the server considers active for this member.
     *
     * @return the unit id, `null` when the server names none; or a failure the caller can show.
     */
    suspend fun serverDefault(): ApiResult<String?>

    /**
     * Reads every active org unit of all four kinds, for the order form's customer picker, which is
     * not limited to memberships.
     *
     * @return the units, or the classified failure.
     */
    suspend fun activeAllKinds(): ApiResult<List<OrgUnit>>
}

/**
 * Reads the org units the member may work in through the me-scoped `/me/org-units`, which answers
 * what may be pinned, including for an admin (REQ-SEC-048).
 *
 * Nothing is cached.
 *
 * @property reader performs the calls and classifies their failures
 */
class OrgUnitRepository(
    private val reader: ApiReader,
) : OrgUnitSource {
    /**
     * Convenience constructor for the object graph.
     *
     * @param httpClient the API client, which supplies the bearer token and the mandatory headers
     * @param baseUrl the flavour's API origin
     */
    constructor(httpClient: OkHttpClient, baseUrl: String) : this(
        ApiReader(httpClient = httpClient, baseUrl = baseUrl, json = KrtJson, logTag = LOG_TAG),
    )

    /**
     * Reads the units the member may pin, dropping and logging the count of entries without an id.
     *
     * @return the member's units, in the order the server returned them.
     */
    override suspend fun memberships(): ApiResult<List<OrgUnit>> =
        when (val result = reader.get(MEMBERSHIPS_PATH, ListSerializer(OrgUnitMembershipOptionDto.serializer()))) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                val usable = result.value.mapNotNull { it.toModel() }
                if (usable.size != result.value.size) {
                    KrtLog.w(LOG_TAG) {
                        "${result.value.size - usable.size} of ${result.value.size} org units " +
                            "arrived without an id and cannot be pinned"
                    }
                }
                ApiResult.Success(usable)
            }
        }

    /**
     * Reads every active org unit, of all four kinds, dropping and logging the count of entries without
     * an id.
     *
     * @return the units, in the order the server returned them.
     */
    override suspend fun activeAllKinds(): ApiResult<List<OrgUnit>> =
        when (
            val result =
                reader.get(ACTIVE_ALL_KINDS_PATH, ListSerializer(OrgUnitMembershipOptionDto.serializer()))
        ) {
            is ApiResult.Failure -> {
                result
            }

            is ApiResult.Success -> {
                val usable = result.value.mapNotNull { it.toModel() }
                if (usable.size != result.value.size) {
                    KrtLog.w(LOG_TAG) {
                        "${result.value.size - usable.size} of ${result.value.size} org units " +
                            "arrived without an id and cannot be offered on the order form"
                    }
                }
                ApiResult.Success(usable)
            }
        }

    /**
     * Reads the org unit the server considers active, the starting point when none was chosen on this
     * device.
     *
     * @return the unit id, or `null` when the server names none.
     */
    override suspend fun serverDefault(): ApiResult<String?> =
        reader.get(ACTIVE_ORG_UNIT_PATH, ActiveOrgUnitResponse.serializer())
            .map { it.orgUnitId }

    private companion object {
        /** Log subsystem. Org-unit names are not member identities, but nothing here is logged. */
        const val LOG_TAG = "orgunit"

        /**
         * The org units the caller may pin, not their memberships (REQ-SEC-048).
         */
        const val MEMBERSHIPS_PATH = "/api/v1/me/org-units"

        /** The server's own idea of the caller's active unit. */
        const val ACTIVE_ORG_UNIT_PATH = "/api/v1/me/active-org-unit"

        /**
         * Every active unit, of all four kinds — the order form's two pickers.
         *
         * Names no member, which is why it may sit on the public API vhost's allow-list at all.
         */
        const val ACTIVE_ALL_KINDS_PATH = "/api/v1/org-units/active-all-kinds"
    }
}

/**
 * Maps one wire option onto the model.
 *
 * @return the unit, or `null` when it carries no id and therefore cannot be pinned.
 */
private fun OrgUnitMembershipOptionDto.toModel(): OrgUnit? {
    val id = orgUnitId ?: return null
    return OrgUnit(
        id = id,
        name = orgUnitName ?: orgUnitShorthand ?: id,
        shorthand = orgUnitShorthand.orEmpty(),
        profitEligible = isProfitEligible == true,
        kind =
            when (kind) {
                OrgUnitMembershipOptionDto.Kind.SQUADRON -> OrgUnitKind.SQUADRON
                OrgUnitMembershipOptionDto.Kind.SPECIAL_COMMAND -> OrgUnitKind.SPECIAL_COMMAND
                OrgUnitMembershipOptionDto.Kind.BEREICH -> OrgUnitKind.BEREICH
                OrgUnitMembershipOptionDto.Kind.ORGANISATIONSLEITUNG -> OrgUnitKind.ORGANISATIONSLEITUNG
                null -> OrgUnitKind.UNKNOWN
            },
    )
}
