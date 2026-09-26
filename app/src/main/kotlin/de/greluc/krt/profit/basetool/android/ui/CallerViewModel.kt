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
 * The single app-wide read of who the caller is (ADR-0011).
 *
 * A failed read leaves the record `null`, which consumers treat as unknown (see [LocalCaller]);
 * there is no automatic retry.
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
     * Called when the app returns to the foreground, so a newly granted role takes effect without a
     * sign-out.
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
