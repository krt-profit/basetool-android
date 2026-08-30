/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.GameItemStock
import de.greluc.krt.profit.basetool.android.core.data.InventorySource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Log subsystem. A holder's name is member data and never reaches the log. */
private const val LOG_TAG = "game-item-stock"

/** How far the read has got. */
sealed interface GameItemPhase {
    /** On its way. */
    data object Loading : GameItemPhase

    /** It arrived; an empty list is a result. */
    data object Ready : GameItemPhase

    /**
     * It did not.
     *
     * @property error what went wrong.
     */
    data class Failed(
        val error: ApiError,
    ) : GameItemPhase
}

/**
 * Everything the Game-Item screen draws.
 *
 * @property items every game item the org unit holds, as one complete list.
 * @property query the search term.
 * @property kind the picked category, or `null` for „Alle".
 * @property expanded which row has its stacks open, or `null`.
 * @property phase how far the read has got.
 * @property refreshing whether a pull-to-refresh is running.
 */
data class GameItemStockState(
    val items: List<GameItemStock> = emptyList(),
    val query: String = "",
    val kind: String? = null,
    val expanded: String? = null,
    val phase: GameItemPhase = GameItemPhase.Loading,
    val refreshing: Boolean = false,
) {
    /**
     * The categories the chip row offers.
     *
     * Built from the values that turn up rather than from a hardcoded list: `kind` is free text on
     * the wire, so a fixed set would silently hide whatever the catalogue grows next.
     */
    val kinds: List<String> get() = items.mapNotNull { it.kind }.distinct().sorted()

    /**
     * The rows the list draws.
     *
     * Filtering here is **complete**, not a narrowing of a page: the whole list arrives in one
     * call, so there is no cap to declare (ADR-0104 is satisfied by the read, not by a notice).
     */
    val visible: List<GameItemStock>
        get() =
            items
                .filter { kind == null || it.kind == kind }
                .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    /** How many pieces the visible rows come to. */
    val totalAmount: Double get() = visible.sumOf { it.amount }
}

/**
 * Drives „Game-Items" (design ch. 09 artboard 21, `REQ-APP-INV-*`).
 *
 * **Read-only, and a surface of its own under „Mehr".** The Lager tree groups by *material*, where
 * a game item counted in pieces disappears between SCU figures; the question here is „how many do
 * we have and where", which is a different question with a different unit.
 *
 * @property source the grouped read.
 */
class GameItemStockViewModel(
    private val source: InventorySource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GameItemStockState())

    /** What the screen draws. */
    val state: StateFlow<GameItemStockState> = mutableState.asStateFlow()

    private var loadedOnce = false

    /** Reads the list, the first time the screen is opened. */
    fun loadOnce() {
        if (loadedOnce) {
            return
        }
        loadedOnce = true
        read()
    }

    /** Reads it again. */
    fun onRetry() {
        read()
    }

    /** Pull-to-refresh. */
    fun onRefresh() {
        mutableState.value = mutableState.value.copy(refreshing = true)
        read()
    }

    /**
     * The search changed.
     *
     * @param query what was typed.
     */
    fun onQueryChanged(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
    }

    /**
     * A category chip was tapped; tapping the active one clears it.
     *
     * @param kind the category, or `null` for „Alle".
     */
    fun onKindChanged(kind: String?) {
        val current = mutableState.value.kind
        mutableState.value = mutableState.value.copy(kind = if (current == kind) null else kind)
    }

    /**
     * A row was tapped.
     *
     * It opens **in place** rather than jumping into the Lager tree the artboard names: that tree
     * is material-grouped and has no item mode, so there is nowhere to jump to. The stacks it would
     * have shown are already in this row's own answer.
     *
     * @param item the row.
     */
    fun onToggleExpanded(item: GameItemStock) {
        val current = mutableState.value.expanded
        mutableState.value = mutableState.value.copy(expanded = if (current == item.id) null else item.id)
    }

    /** Performs the read. */
    private fun read() {
        viewModelScope.launch {
            when (val result = source.gameItemStock()) {
                is ApiResult.Success -> {
                    mutableState.value =
                        mutableState.value.copy(
                            items = result.value.sortedBy { it.name },
                            phase = GameItemPhase.Ready,
                            refreshing = false,
                        )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the game-item stock could not be read: ${result.error}" }
                    mutableState.value =
                        mutableState.value.copy(
                            phase = GameItemPhase.Failed(result.error),
                            refreshing = false,
                        )
                }
            }
        }
    }
}
