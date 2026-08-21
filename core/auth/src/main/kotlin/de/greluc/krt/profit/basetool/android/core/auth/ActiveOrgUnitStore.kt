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
 * The org unit the member is currently acting in — the value every request carries as
 * `X-Active-Org-Unit-Id`.
 *
 * **It is read synchronously, and that decides the storage.** `MandatoryHeadersInterceptor` runs on
 * an OkHttp dispatcher thread and cannot suspend, so the pin has to be readable from an arbitrary
 * thread, immediately. `SharedPreferences` is the API whose contract is exactly that; DataStore's is
 * the opposite, and both ways of bridging that gap were tried on a device and rejected:
 *
 * - **Mirroring the DataStore value in memory** left the mirror empty until somebody happened to
 *   call a suspending `load()` — and the only caller ran *after* the first requests of a cold
 *   start. Measured: the first three requests of every launch went out with no header at all.
 *   Nothing visible broke, because every screen on that path is me-scoped; the first scoped read
 *   added to start-up would have shown the wrong scope with no error anywhere.
 * - **Seeding the mirror with `runBlocking` on first read** closed that hole and opened a worse
 *   one: it deadlocks whenever the calling thread is the one DataStore's scope runs on. The first
 *   test written against it hung, which is a generous way to find out.
 *
 * The pin is **not a UI preference**: it is part of a member's session. A device handed to a second
 * member must not leave them looking at the first one's Staffel, so [clear] is called by the same
 * logout that wipes the refresh token, and the file is excluded from backup and device transfer
 * alongside the token store.
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
     * The pinned org unit.
     *
     * Safe from any thread, including an OkHttp dispatcher one: `SharedPreferences` serves reads
     * from memory once the file is loaded, and that load happens when this object is built.
     *
     * @return the pinned org-unit id, or `null` when the member has not chosen one.
     */
    fun current(): String? = preferences.getString(KEY, null)

    /**
     * Pins an org unit.
     *
     * The value is visible to [current] before this returns; only the disk write is deferred.
     *
     * @param orgUnitId the unit to act in.
     */
    fun pin(orgUnitId: String) {
        // apply(), not commit(): the in-memory value is updated synchronously — which is what
        // `current()` reads — and only the disk write is deferred off the calling thread.
        preferences.edit().putString(KEY, orgUnitId).apply()
    }

    /**
     * Removes the pin, so the backend decides the scope again.
     */
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    companion object {
        /**
         * The preference file, without the `.xml` suffix Android appends.
         *
         * Published because the protection depends on two files agreeing: the backup rules have to
         * exclude this file by name, and a rename here with no matching edit there fails nothing at
         * build time — it just starts carrying one member's org scope onto another member's device.
         * `BackupExclusionTest` compares the rules against [BACKUP_PATH] for that reason.
         */
        const val FILE_NAME: String = "krt_active_org_unit"

        /** The path a backup rule must exclude, relative to the app's `shared_prefs/` directory. */
        const val BACKUP_PATH: String = "$FILE_NAME.xml"

        /** Preference key; the file holds nothing else. */
        private const val KEY = "active_org_unit_id"
    }
}
