/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.OperationDraft
import de.greluc.krt.profit.basetool.android.core.data.OperationSource
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Log tag for the Operation form. */
private const val LOG_TAG = "OperationForm"

/** What the server accepts as a name. */
private const val NAME_MAX = 200

/** And as a description. */
private const val DESCRIPTION_MAX = 2000

/**
 * The statuses the form offers: `CANCELED` and the read-side fallback `UNKNOWN` are not offered.
 */
val OPERATION_FORM_STATUSES: List<OperationStatus> =
    listOf(OperationStatus.PLANNED, OperationStatus.ACTIVE, OperationStatus.COMPLETED)

/**
 * Everything the Operation form holds.
 *
 * @property name what it is called.
 * @property description the free text.
 * @property status where it stands; required on both writes, which is why it is always shown.
 * @property version the optimistic lock when editing, `null` when raising.
 * @property editing whether this rewrites an Operation or raises one.
 * @property loading whether the Operation being edited is still being read.
 * @property saving whether the write is in flight.
 * @property saved the id of the Operation that was written, once it is.
 * @property error the last refusal.
 */
data class OperationFormState(
    val name: String = "",
    val description: String = "",
    val status: OperationStatus = OperationStatus.PLANNED,
    val version: Long? = null,
    val editing: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val saved: String? = null,
    val error: ApiError? = null,
) {
    /** Whether the form may be sent — the server's own rule: a name, and nothing else. */
    val submittable: Boolean
        get() = !saving && !loading && name.isNotBlank()

    /**
     * The form as the wire takes it.
     *
     * @return the draft.
     */
    fun toDraft(): OperationDraft =
        OperationDraft(
            name = name.trim(),
            description = description.trim().takeIf { it.isNotEmpty() },
            status = status,
            version = version,
        )
}

/**
 * Drives „Operation anlegen" and „Operation bearbeiten" (REQ-APP-OPS-014) with one form for both.
 *
 * There are no time fields and no Einsatz assignment: an Operation has no times of its own, and an
 * Einsatz joins one through its own Kern section.
 *
 * @property source where the two writes go.
 * @property operationId the Operation being rewritten, or `null` when raising one.
 */
class OperationFormViewModel(
    private val source: OperationSource,
    private val operationId: String? = null,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(OperationFormState(editing = operationId != null, loading = operationId != null))

    /** What the screen draws. */
    val state: StateFlow<OperationFormState> = mutableState.asStateFlow()

    init {
        prefill()
    }

    /**
     * The name changed.
     *
     * @param value what was typed.
     */
    fun onName(value: String) {
        mutableState.update { it.copy(name = value.take(NAME_MAX), error = null) }
    }

    /**
     * The description changed.
     *
     * @param value what was typed.
     */
    fun onDescription(value: String) {
        mutableState.update { it.copy(description = value.take(DESCRIPTION_MAX), error = null) }
    }

    /**
     * The status was picked.
     *
     * @param value which one.
     */
    fun onStatus(value: OperationStatus) {
        mutableState.update { it.copy(status = value, error = null) }
    }

    /** Sends the form. */
    fun onSubmit() {
        val current = mutableState.value
        if (!current.submittable) {
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val id = operationId
            val result =
                if (id == null) {
                    source.create(current.toDraft())
                } else {
                    when (val write = source.update(id, current.toDraft())) {
                        is ApiResult.Success -> ApiResult.Success(id)
                        is ApiResult.Failure -> write
                    }
                }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(saving = false, saved = result.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the Operation could not be written: ${result.error}" }
                    mutableState.update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /** Reads the Operation being edited and fills the form with it. */
    private fun prefill() {
        val id = operationId ?: return
        viewModelScope.launch {
            when (val result = source.overview(id)) {
                is ApiResult.Success -> {
                    val operation = result.value.detail
                    mutableState.update {
                        it.copy(
                            name = operation.name,
                            description = operation.description.orEmpty(),
                            status = operation.status,
                            version = operation.version,
                            loading = false,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the Operation could not be read for editing: ${result.error}" }
                    mutableState.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }
}
