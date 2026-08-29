/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCombobox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHint
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Test handle for the place picker — the one field the booking cannot go without. */
const val ORDER_PRODUCTION_LOCATION_TAG: String = "order-production-location"

/**
 * „Einlagerung" — where and for whom the produced units are booked into the Lager.
 *
 * > **Not drawn by the artboard, and not optional.** Design ch. 10 artboard 15 stops at the
 * > ingredients; the endpoint's `bookIn.locationId` is `@NotNull`, so a run booked without a place
 * > would be refused. The section is on the design gap list.
 *
 * The org-unit picker only appears when the owner actually has a choice: with one membership the
 * server resolves the pool itself, and with more it answers 400 unless one is named — which is why
 * this preselects rather than offering an empty option (REQ-ORG-004).
 *
 * @param draft what is filled in.
 * @param actions what the section reports.
 */
@Composable
fun ProductionBookInSection(
    draft: ProductionDraft,
    actions: ProductionBookInActions,
) {
    val bookIn = draft.bookIn
    KrtSectionTitle(text = stringResource(R.string.order_production_bookin))
    KrtHint(explanation = stringResource(R.string.order_production_bookin_hint))
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
        Picker(
            label = stringResource(R.string.order_production_location),
            query = bookIn.locationQuery,
            options = bookIn.locations.map { it.id to it.name },
            enabled = !draft.saving,
            onQuery = actions.onLocationQuery,
            onChosen = actions.onLocation,
            modifier = Modifier.testTag(ORDER_PRODUCTION_LOCATION_TAG),
        )
        Picker(
            label = stringResource(R.string.order_production_owner),
            query = bookIn.ownerQuery,
            options = bookIn.members.map { it.id to it.name },
            enabled = !draft.saving,
            onQuery = actions.onOwnerQuery,
            onChosen = actions.onOwner,
        )
        // Left blank the units run on the acting member — the server's own default. Saying so
        // beats a field that looks unfilled.
        KrtHint(explanation = stringResource(R.string.order_production_owner_hint))
        if (bookIn.orgUnits.size > 1) {
            OrgUnitField(draft = draft, actions = actions)
        }
        KrtCheckboxRow(
            checked = bookIn.personal,
            onCheckedChange = { actions.onPersonal() },
            label = stringResource(R.string.order_production_personal),
            enabled = !draft.saving,
        )
        KrtCheckboxRow(
            checked = bookIn.allocate,
            onCheckedChange = { actions.onAllocate() },
            label = stringResource(R.string.order_production_allocate),
            // Personal stock never carries earmarks, and the combination is a 400 rather than a
            // silently dropped flag — so the row goes dead while „persönlich" is ticked.
            enabled = !draft.saving && !bookIn.personal,
        )
        KrtHint(explanation = stringResource(R.string.order_production_allocate_hint))
    }
}

/**
 * Which of the owner's units the produced stock is stamped onto.
 *
 * @param draft what is filled in.
 * @param actions what the field reports.
 */
@Composable
private fun OrgUnitField(
    draft: ProductionDraft,
    actions: ProductionBookInActions,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val units = draft.bookIn.orgUnits
    KrtSelectField(
        value = units.firstOrNull { it.id == draft.bookIn.orgUnitId }?.name.orEmpty(),
        options = units.map { KrtOption(value = it.id, label = it.name) },
        onSelect = { option ->
            actions.onOrgUnit(option.value)
            open = false
        },
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.order_production_orgunit),
        selectedValue = draft.bookIn.orgUnitId,
        enabled = !draft.saving,
    )
}

/**
 * A server-searched picker: type to narrow, tap to choose, the choice stays in the field.
 *
 * @param label the field's name.
 * @param query what is typed.
 * @param options the current matches, as id-to-label pairs.
 * @param enabled whether it takes input.
 * @param onQuery the search changed.
 * @param onChosen a row was picked, by id and label.
 * @param modifier layout modifier.
 */
@Composable
private fun Picker(
    label: String,
    query: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onQuery: (String) -> Unit,
    onChosen: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    KrtCombobox(
        query = query,
        onQueryChange = {
            onQuery(it)
            open = true
        },
        options = options.map { (id, text) -> KrtOption(id, text) },
        onSelect = { option ->
            onChosen(option.value, option.label)
            open = false
        },
        expanded = open && options.isNotEmpty(),
        onExpandedChange = { open = it },
        modifier = modifier.fillMaxWidth(),
        label = label,
        enabled = enabled,
    )
}
