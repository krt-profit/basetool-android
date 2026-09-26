/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * The org unit the member is acting in, sent on every request as `X-Active-Org-Unit-Id`.
 *
 * Backed by `SharedPreferences` because `MandatoryHeadersInterceptor` reads it synchronously on an
 * OkHttp thread. It is session state: [clear] runs with logout, and the file is excluded from backup
 * and device transfer.
 *
 * @property preferences the app-private preference file this store owns exclusively
 */
class ActiveOrgUnitStore(
    private val preferences: SharedPreferences,
) {
    /**
     * Convenience constructor that opens the store's own preference file.
     *
     * @param context any context; the application context is used.
     */
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
    )

    /**
     * The pinned org unit, readable from any thread.
     *
     * @return the pinned org-unit id, or `null` when none is pinned or all units are chosen; [isAllChosen]
     *   tells the two apart
     */
    fun current(): String? = preferences.getString(KEY, null)?.takeIf { it != ALL }

    /**
     * Whether the member deliberately chose to act across all their org units.
     *
     * Sends the same request as having no pin, but survives a restart instead of a unit being resolved
     * and pinned.
     *
     * @return whether "Alle Org-Einheiten" is the standing choice.
     */
    fun isAllChosen(): Boolean = preferences.getString(KEY, null) == ALL

    /**
     * Pins an org unit.
     *
     * The value is visible to [current] before this returns; only the disk write is deferred.
     *
     * @param orgUnitId the unit to act in.
     */
    fun pin(orgUnitId: String) {
        preferences.edit().putString(KEY, orgUnitId).apply()
    }

    /**
     * Records that the member wants **all** of their org units at once.
     *
     * No header goes out afterwards, so the backend answers with the union of their memberships —
     * never a unit they do not belong to (design ch. 02, artboard 7: „Alle Org-Einheiten").
     */
    fun pinAll() {
        preferences.edit().putString(KEY, ALL).apply()
    }

    /**
     * Removes the pin **and** the "all" choice, so the app resolves a scope from scratch.
     */
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    companion object {
        /**
         * The preference file, without the `.xml` suffix Android appends.
         *
         * Published so `BackupExclusionTest` can check the backup rules against [BACKUP_PATH].
         */
        const val FILE_NAME: String = "krt_active_org_unit"

        /** The path a backup rule must exclude, relative to the app's `shared_prefs/` directory. */
        const val BACKUP_PATH: String = "$FILE_NAME.xml"

        /** Preference key; the file holds nothing else. */
        private const val KEY = "active_org_unit_id"

        /**
         * Sentinel for „all org units", stored under the same key.
         *
         * Not a UUID and not a valid org-unit id, so it can never collide with one — and a build
         * that failed to understand it would fall through to "no pin", which is the same scope on
         * the wire rather than a wrong one.
         */
        private const val ALL = "__all__"
    }
}
