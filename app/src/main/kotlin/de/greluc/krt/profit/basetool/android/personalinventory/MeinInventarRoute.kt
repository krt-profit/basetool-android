/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.personalinventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.ConflictOn

/** Test handle for the Items/Blueprints segment. */
const val MEIN_INVENTAR_SEGMENT_TAG: String = "mein-inventar-segment"

/** Which half of the screen is showing. */
private const val TAB_ITEMS = 0

/**
 * „Mein Inventar & Blueprints": one screen with an Items half and a Blueprints half.
 *
 * Each half keeps its own view model; the chosen tab survives process death.
 *
 * @param items drives the Items half.
 * @param blueprints drives the Blueprints half.
 * @param modifier layout modifier.
 */
@Composable
fun MeinInventarRoute(
    items: PersonalInventoryViewModel,
    blueprints: PersonalBlueprintsViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_ITEMS) }
    val itemsState by items.state.collectAsStateWithLifecycle()
    val blueprintsState by blueprints.state.collectAsStateWithLifecycle()

    LaunchedEffect(tab) {
        if (tab == TAB_ITEMS) items.loadOnce() else blueprints.loadOnce()
    }

    Column(modifier = modifier.fillMaxSize()) {
        KrtSegmentedControl(
            options =
                listOf(
                    stringResource(R.string.blueprints_tab_items),
                    stringResource(R.string.blueprints_tab_blueprints),
                ),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8)
                    .testTag(MEIN_INVENTAR_SEGMENT_TAG),
            stretch = true,
        )

        if (tab == TAB_ITEMS) {
            PersonalInventoryScreen(
                state = itemsState,
                onQueryChanged = items::onQueryChanged,
                onRefresh = items::onRefresh,
                onRetryNow = items::onRetry,
                onLoadMore = items::onLoadMore,
                onCreate = items::onCreate,
                onEdit = items::onEdit,
                onDelete = items::onDeleteRequested,
                selection =
                    PersonalSelectionActions(
                        onToggle = items::onToggleSelected,
                        onSelectAll = items::onSelectAll,
                        onClear = items::onSelectionCleared,
                        onDelete = items::onBulkDeleteRequested,
                    ),
            )
        } else {
            PersonalBlueprintsScreen(
                state = blueprintsState,
                onQueryChanged = blueprints::onQueryChanged,
                onRefineryChanged = blueprints::onRefineryChanged,
                onRefresh = blueprints::onRefresh,
                onRetryNow = blueprints::onRetry,
                onLoadMore = blueprints::onLoadMore,
                onAdd = blueprints::onAdd,
                onEdit = blueprints::onEdit,
                onDelete = blueprints::onDeleteRequested,
                onSelect = blueprints::onSelect,
                bulk =
                    BlueprintBulkActions(
                        onStartSelection = blueprints.selection::start,
                        onToggleSelected = blueprints.selection::toggle,
                        onSelectAll = blueprints.selection::selectAll,
                        onCancelSelection = blueprints.selection::cancel,
                        onAskDelete = { blueprints.selection.ask(true) },
                        onDismissDelete = { blueprints.selection.ask(false) },
                        onConfirmDelete = blueprints.selection::confirm,
                        onImportOpen = blueprints.import::open,
                        onImportFile = blueprints.import::onFile,
                        onImportApply = blueprints.import::apply,
                        onImportDismiss = blueprints.import::dismiss,
                    ),
            )
        }
    }

    (itemsState.editor as? EditorState.Open)?.let { editor ->
        ConflictOn(
            error = editor.error,
            onReload = {
                items.onEditorDismissed()
                items.onRefresh()
            },
        )
        PersonalInventoryEditor(
            editor = editor,
            locations = itemsState.locations,
            onName = items::onNameChanged,
            onQuantity = items::onQuantityChanged,
            onStep = items::onQuantityStepped,
            onNote = items::onNoteChanged,
            onLocationQuery = items::onLocationQueryChanged,
            onLocationChosen = items::onLocationChosen,
            onSave = items::onSave,
            onDismiss = items::onEditorDismissed,
        )
    }
    itemsState.pendingDelete?.let { item ->
        PersonalInventoryDeleteModal(
            item = item,
            deleting = itemsState.deleting,
            onConfirm = items::onDeleteConfirmed,
            onDismiss = items::onDeleteDismissed,
        )
    }
    if (itemsState.confirmingBulkDelete) {
        PersonalBulkDeleteModal(
            count = itemsState.selection.size,
            busy = itemsState.deleting,
            onConfirm = items::onBulkDeleteConfirmed,
            onDismiss = items::onBulkDeleteDismissed,
        )
    }
    BackHandler(enabled = itemsState.selecting, onBack = items::onSelectionCleared)

    val blueprintEditor = blueprintsState.editor
    ConflictOn(
        error =
            (blueprintEditor as? BlueprintEditor.Adding)?.error
                ?: (blueprintEditor as? BlueprintEditor.Editing)?.error,
        onReload = {
            blueprints.onEditorDismissed()
            blueprints.onRefresh()
        },
    )
    if (blueprintEditor is BlueprintEditor.Adding) {
        BlueprintAddSheet(
            editor = blueprintEditor,
            onQuery = blueprints::onProductQueryChanged,
            onChosen = blueprints::onProductChosen,
            onNote = blueprints::onNoteChanged,
            onSave = blueprints::onSave,
            onDismiss = blueprints::onEditorDismissed,
        )
    } else if (blueprintEditor is BlueprintEditor.Editing) {
        BlueprintNoteSheet(
            editor = blueprintEditor,
            onNote = blueprints::onNoteChanged,
            onSave = blueprints::onSave,
            onDismiss = blueprints::onEditorDismissed,
        )
    }
    blueprintsState.pendingDelete?.let { entry ->
        BlueprintRemoveModal(
            entry = entry,
            deleting = blueprintsState.deleting,
            onConfirm = blueprints::onDeleteConfirmed,
            onDismiss = blueprints::onDeleteDismissed,
        )
    }
}
