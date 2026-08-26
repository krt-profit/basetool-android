/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.hangar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.data.FleetImportResult
import de.greluc.krt.profit.basetool.android.core.data.HangarSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Fleetview-Import screen holds.
 *
 * @property pasted the export typed or pasted into the box.
 * @property fileName the picked file's name, or `null` when nothing was picked. Kept so the screen
 *   can name what is armed — "eine Datei" would leave a member unsure which one.
 * @property fileBytes the picked file's content, held in memory because the upload happens on the
 *   member's next tap rather than on the pick.
 * @property uploading whether an import is in flight.
 * @property result the last import's tally, shown as a modal until dismissed.
 * @property error the last refusal, if any.
 * @property online whether the device has a route to the server.
 */
data class FleetImportState(
    val pasted: String = "",
    val fileName: String? = null,
    val fileBytes: ByteArray? = null,
    val uploading: Boolean = false,
    val result: FleetImportResult? = null,
    val error: ApiError? = null,
    val online: Boolean = true,
) {
    /** Whether there is anything to send. */
    val submittable: Boolean
        get() = !uploading && online && (fileBytes != null || pasted.isNotBlank())

    /**
     * Value equality over the byte array too.
     *
     * A `data class` compares an array by identity, which would make two states holding the same
     * export compare unequal and re-emit forever. Spelled out rather than dropping the array,
     * because the bytes have to survive until the member taps Importieren.
     *
     * @param other the state to compare with.
     * @return whether both hold the same import.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val that = other as? FleetImportState ?: return false
        return pasted == that.pasted &&
            fileName == that.fileName &&
            fileBytes.contentEquals(that.fileBytes) &&
            uploading == that.uploading &&
            result == that.result &&
            error == that.error &&
            online == that.online
    }

    /**
     * The hash that goes with [equals].
     *
     * @return a hash over the same fields, with the array hashed by content.
     */
    override fun hashCode(): Int {
        var hash = pasted.hashCode()
        hash = 31 * hash + (fileName?.hashCode() ?: 0)
        hash = 31 * hash + fileBytes.contentHashCode()
        hash = 31 * hash + uploading.hashCode()
        hash = 31 * hash + (result?.hashCode() ?: 0)
        hash = 31 * hash + (error?.hashCode() ?: 0)
        hash = 31 * hash + online.hashCode()
        return hash
    }
}

/**
 * Drives the Fleetview import.
 *
 * The two ways in — a picked `.json` and a pasted export — end in the same upload, because the
 * server takes one file part either way. Picking a file wins over the box when both are filled: a
 * member who has just chosen a file has said what they mean more recently than the text still
 * sitting in a field they scrolled past.
 *
 * @property source the hangar reads and writes.
 * @property connectivity whether the device has a route to the server.
 */
class FleetImportViewModel(
    private val source: HangarSource,
    connectivity: Connectivity,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FleetImportState())

    /** What the screen renders. */
    val state: StateFlow<FleetImportState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.value = mutableState.value.copy(online = online)
            }
        }
    }

    /**
     * Records what the member typed or pasted.
     *
     * @param text the export text.
     */
    fun onPasted(text: String) {
        mutableState.value = mutableState.value.copy(pasted = text, error = null)
    }

    /**
     * Records a picked file.
     *
     * @param name the file's display name.
     * @param bytes its content.
     */
    fun onFilePicked(
        name: String,
        bytes: ByteArray,
    ) {
        mutableState.value = mutableState.value.copy(fileName = name, fileBytes = bytes, error = null)
    }

    /** Drops a picked file, so the box takes over again. */
    fun onFileCleared() {
        mutableState.value = mutableState.value.copy(fileName = null, fileBytes = null)
    }

    /** Closes the result modal. */
    fun onResultDismissed() {
        mutableState.value = mutableState.value.copy(result = null)
    }

    /**
     * Uploads whatever is armed.
     *
     * The paste is sent under a name of the app's own making rather than a blank one: the
     * server logs the part's filename, and "(eingefügt)" in that log is the difference between
     * knowing an import came from a paste and guessing at it.
     */
    fun onImport() {
        val current = mutableState.value
        if (!current.submittable) {
            return
        }
        val bytes = current.fileBytes ?: current.pasted.toByteArray()
        val name = current.fileName ?: PASTED_FILE_NAME
        mutableState.value = current.copy(uploading = true, error = null)
        viewModelScope.launch {
            when (val result = source.importFleetview(fileName = name, bytes = bytes)) {
                is ApiResult.Success -> {
                    mutableState.value =
                        FleetImportState(online = mutableState.value.online, result = result.value)
                }

                is ApiResult.Failure -> {
                    mutableState.value =
                        mutableState.value.copy(uploading = false, error = result.error)
                }
            }
        }
    }

    private companion object {
        /** The name a pasted export is uploaded under. */
        const val PASTED_FILE_NAME = "fleetview-paste.json"
    }
}
