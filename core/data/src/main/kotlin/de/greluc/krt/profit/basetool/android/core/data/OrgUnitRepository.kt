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
     * Reads every active org unit, of all four kinds.
     *
     * Wider than [memberships] on purpose. The order form's customer picker is the web's, and the
     * web lets a member raise an order *for* a unit they do not belong to — a Staffel ordering
     * through another Staffel is the ordinary case, not an edge one. Narrowing this to memberships
     * would silently make the app's form the smaller of the two.
     *
     * @return the units, or the classified failure.
     */
    suspend fun activeAllKinds(): ApiResult<List<OrgUnit>>
}

/**
 * Reads the member's org units from the backend.
 *
 * **`/users/me/memberships`, not `/users/{id}/memberships`.** The web sidebar builds the same list
 * from two calls — resolve the principal's id, then ask for that id's memberships — and the app
 * uses a me-scoped endpoint added for it (main repo `REQ-API-009`). One round trip instead of two
 * matters on a phone; what matters more is that the id-taking path would have had to be reachable
 * from the public API vhost, which is a default-deny allow-list precisely so that a path able to
 * name *another* member never has to be on it.
 *
 * Nothing here is cached. The list changes when an administrator changes it, which the app cannot
 * observe, and it is two small reads on a screen the member opened deliberately.
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
     * Reads the member's direct memberships.
     *
     * An entry without an id is dropped, because a unit that cannot be pinned cannot be offered —
     * and the count is logged rather than swallowed, so a server change that starts omitting ids
     * shows up as a diagnosis instead of a switcher that has quietly gone short.
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
     * Reads every active org unit, of all four kinds.
     *
     * Drops id-less entries and logs the shortfall for the same reason [memberships] does: a unit
     * with no id cannot be sent as `requestingOrgUnitId`, and a picker that has quietly gone short
     * is worse than one that says so in the log.
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
     * Reads the org unit the server considers active.
     *
     * Used as the starting point when the member has never chosen one on this device. A response
     * naming no unit is a success carrying `null`, not a failure: a member with a single unit, or
     * none, is an ordinary case rather than a broken one.
     *
     * @return the unit id or `null`.
     */
    override suspend fun serverDefault(): ApiResult<String?> =
        when (val result = reader.get(ACTIVE_ORG_UNIT_PATH, ActiveOrgUnitResponse.serializer())) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.orgUnitId)
        }

    private companion object {
        /** Log subsystem. Org-unit names are not member identities, but nothing here is logged. */
        const val LOG_TAG = "orgunit"

        /** The me-scoped switcher options (main repo `REQ-API-009`). */
        const val MEMBERSHIPS_PATH = "/api/v1/users/me/memberships"

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
        // A unit the server named with neither a name nor a shorthand would render as an empty
        // row; the id is meaningless to a member but is at least something to point at.
        name = orgUnitName ?: orgUnitShorthand ?: id,
        shorthand = orgUnitShorthand.orEmpty(),
        profitEligible = isProfitEligible == true,
        kind =
            when (kind) {
                OrgUnitMembershipOptionDto.Kind.SQUADRON -> OrgUnitKind.SQUADRON

                OrgUnitMembershipOptionDto.Kind.SPECIAL_COMMAND -> OrgUnitKind.SPECIAL_COMMAND

                OrgUnitMembershipOptionDto.Kind.BEREICH -> OrgUnitKind.BEREICH

                OrgUnitMembershipOptionDto.Kind.ORGANISATIONSLEITUNG -> OrgUnitKind.ORGANISATIONSLEITUNG

                // Absent, or a constant the reader coerced away because this build predates it.
                null -> OrgUnitKind.UNKNOWN
            },
    )
}
