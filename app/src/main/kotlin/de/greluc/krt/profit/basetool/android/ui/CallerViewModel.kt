/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one read of who the caller is, for the whole app.
 *
 * Held here rather than in each screen's ViewModel: the point of ADR-0011 is that every surface
 * decides from the same record, and a per-screen read is how the Lager ended up deciding from none
 * at all.
 *
 * A failed read leaves the record `null`, which every consumer must treat as *unknown* rather than
 * as *forbidden* — see [LocalCaller]. There is no retry ladder for the same reason: the record is
 * an improvement on the screens, not a precondition for them, and a screen that waited for it would
 * be worse off than one that simply lets the server answer.
 *
 * @property source where the record comes from.
 */
class CallerViewModel(
    private val source: IdentitySource,
) : ViewModel() {
    private val mutableState = MutableStateFlow<Identity?>(null)

    /** Who the caller is, or `null` while that is unknown. */
    val caller: StateFlow<Identity?> = mutableState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads the record, dropping whatever was cached.
     *
     * Called when the app returns to the foreground. A role granted while it sat in the background
     * would otherwise only take effect after a sign-out, and telling a member to sign out and back
     * in to receive a permission somebody just gave them is not an instruction worth giving.
     */
    fun refresh() {
        source.forget()
        viewModelScope.launch {
            val result = source.me()
            if (result is ApiResult.Success) {
                mutableState.value = result.value
            }
        }
    }
}
