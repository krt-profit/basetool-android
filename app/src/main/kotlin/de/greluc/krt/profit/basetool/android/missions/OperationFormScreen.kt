/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.OperationStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSpinner
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.fieldMessage
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the form. */
const val OPERATION_FORM_TAG: String = "operation-form"

/** Test handle for its CTA. */
const val OPERATION_FORM_SUBMIT_TAG: String = "operation-form-submit"

/** How tall the description field stands, so a paragraph is visible while it is typed. */
private const val DESCRIPTION_LINES = 4

/**
 * „Operation anlegen" and „Operation bearbeiten" (design ch. 06, artboards 15 and 16).
 *
 * One screen for both: the two writes take the same three fields, and a second layout would be two
 * places to keep in step.
 *
 * > **Two things the artboards draw that the API cannot serve, and that are therefore absent.**
 * > „Beginn" / „Ende (geplant)" have no field on either DTO — an Operation has no times of its own,
 * > they live on its Einsätze. And „Einsätze zuordnen" is not a field either: a mission joins an
 * > Operation through **its own** core section, which needs that mission's name and core version.
 * > The form says where the assignment happens instead of drawing a control that reaches nothing.
 * > Both are on the design gap list.
 *
 * @param state what the form holds.
 * @param actions what it reports.
 * @param modifier layout modifier.
 */
@Composable
fun OperationFormScreen(
    state: OperationFormState,
    actions: OperationFormActions,
    modifier: Modifier = Modifier,
) {
    ProvideScreenTopBar(
        title =
            stringResource(
                if (state.editing) R.string.operation_form_edit_title else R.string.operation_form_title,
            ),
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(OPERATION_FORM_TAG),
        contentPadding = PaddingValues(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
    ) {
        if (state.loading) {
            item(key = "loading") { KrtSpinner() }
            return@LazyColumn
        }
        item(key = "name") {
            KrtTextField(
                value = state.name,
                onValueChange = actions.onName,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.operation_form_name),
                enabled = !state.saving,
            )
        }
        item(key = "description") {
            KrtTextField(
                value = state.description,
                onValueChange = actions.onDescription,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.operation_form_description),
                enabled = !state.saving,
                minLines = DESCRIPTION_LINES,
            )
        }
        item(key = "status") {
            // Shown on both, not only on the edit: the status is required by both writes, so a
            // create that hid it would be sending a value nobody chose.
            KrtSegmentedControl(
                options = OPERATION_FORM_STATUSES.map { stringResource(it.krtLabel()) },
                selectedIndex = OPERATION_FORM_STATUSES.indexOf(state.status).coerceAtLeast(0),
                onSelect = { actions.onStatus(OPERATION_FORM_STATUSES[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "status-note") {
            KrtHint(explanation = stringResource(R.string.operation_form_status_hint))
        }
        item(key = "missions-note") {
            // Where the Einsatz assignment actually lives. Saying it is the difference between a
            // missing feature and a feature somewhere else.
            KrtHint(explanation = stringResource(R.string.operation_form_missions_hint))
        }
        item(key = "cta") {
            // In the server's words when it named the field it rejected; this form has one error
            // slot for every field, and „Konnte nicht gespeichert werden." names none of them.
            state.error?.let { error ->
                KrtFieldError(
                    text = error.fieldMessage() ?: stringResource(R.string.write_failed),
                )
            }
            KrtCtaButton(
                text =
                    stringResource(
                        if (state.editing) R.string.operation_form_save else R.string.operation_form_create,
                    ),
                onClick = actions.onSubmit,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(OPERATION_FORM_SUBMIT_TAG),
                enabled = state.submittable,
            )
        }
    }
}

/**
 * What one status is called on the segment.
 *
 * @receiver the status.
 * @return its label.
 */
private fun OperationStatus.krtLabel(): Int =
    when (this) {
        OperationStatus.PLANNED -> R.string.operation_status_planned
        OperationStatus.ACTIVE -> R.string.operation_status_active
        else -> R.string.operation_status_completed
    }

/**
 * What the Operation form reports back.
 *
 * @property onName the name changed.
 * @property onDescription the description changed.
 * @property onStatus a status was picked.
 * @property onSubmit send it.
 */
data class OperationFormActions(
    val onName: (String) -> Unit,
    val onDescription: (String) -> Unit,
    val onStatus: (OperationStatus) -> Unit,
    val onSubmit: () -> Unit,
)

/**
 * The Operation form, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun OperationFormRoute(
    viewModel: OperationFormViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OperationFormScreen(
        state = state,
        actions =
            OperationFormActions(
                onName = viewModel::onName,
                onDescription = viewModel::onDescription,
                onStatus = viewModel::onStatus,
                onSubmit = viewModel::onSubmit,
            ),
        modifier = modifier,
    )
}
