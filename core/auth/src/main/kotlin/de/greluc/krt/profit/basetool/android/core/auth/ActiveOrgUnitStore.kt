/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The org unit the member is currently acting in — the value every request carries as
 * `X-Active-Org-Unit-Id`.
 *
 * It lives in **`core:auth`, in the token store**, for a reason that is easy to get wrong: the pin
 * is not a UI preference, it is part of a member's session. A device handed to a second member must
 * not leave them looking at the first one's Staffel, so it has to be reached by the same wipe that
 * removes the refresh token (see [AuthContainer]'s logout) — and putting it in a settings store
 * that survives sign-out would do exactly the wrong thing.
 *
 * **It is read synchronously.** `MandatoryHeadersInterceptor` runs on an OkHttp dispatcher thread
 * and cannot suspend, so the value is mirrored into memory: [load] seeds it once at start-up,
 * [pin]/[clear] keep it, and collecting [pinned] refreshes it too. A miss is not a correctness
 * problem — the header is simply absent and the backend falls back to the member's own default —
 * but it would silently show the wrong scope, so the seeding is not optional.
 *
 * @property dataStore the token DataStore, so a logout wipe reaches this too
 */
class ActiveOrgUnitStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Volatile
    private var cached: String? = null

    /** Emits the pinned org-unit id, and again whenever it changes; `null` when none is pinned. */
    val pinned: Flow<String?> =
        dataStore.data.map { it[KEY] }.onEach { cached = it }

    /**
     * Reads the stored pin into memory.
     *
     * Called once while the object graph is built, so the first request of a cold start already
     * carries the header rather than the one after it.
     *
     * @return the pinned id, or `null`.
     */
    suspend fun load(): String? {
        cached = dataStore.data.first()[KEY]
        return cached
    }

    /**
     * The pin as the request interceptor sees it.
     *
     * @return the pinned id, or `null` when none is pinned or [load] has not run yet.
     */
    fun current(): String? = cached

    /**
     * Pins an org unit.
     *
     * @param orgUnitId the unit to act in.
     */
    suspend fun pin(orgUnitId: String) {
        dataStore.edit { it[KEY] = orgUnitId }
        cached = orgUnitId
    }

    /**
     * Removes the pin, so the backend decides the scope again.
     */
    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
        cached = null
    }

    private companion object {
        /** Preference key; distinct from the token entry so a wipe can target either. */
        val KEY = stringPreferencesKey("active_org_unit_id")
    }
}
