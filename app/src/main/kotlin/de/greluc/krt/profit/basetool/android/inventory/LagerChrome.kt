/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.data.InventoryEntry
import de.greluc.krt.profit.basetool.android.core.data.InventoryStack
import de.greluc.krt.profit.basetool.android.core.data.LagerScope
import de.greluc.krt.profit.basetool.android.core.data.LocationOption
import de.greluc.krt.profit.basetool.android.core.data.PersonalFilter
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtButtonStyles
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCountBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtMenuItem
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOverflowMenu
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtToast
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.LocalKrtBottomBarInset
import de.greluc.krt.profit.basetool.android.ui.DENIAL_TOAST_MS
import de.greluc.krt.profit.basetool.android.ui.isWideWindow
import kotlinx.coroutines.delay
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Org-Lager | Mein Lager segment. */
const val LAGER_SCOPE_TAG: String = "lager-scope"

/** Test handle for the filter row. */
const val LAGER_FILTER_ROW_TAG: String = "lager-filter-row"

/** Test handle for the top bar's filter toggle. */
const val LAGER_FILTER_TOGGLE_TAG: String = "lager-filter-toggle"

/** Test handle for the location filter sheet. */
const val LAGER_LOCATION_SHEET_TAG: String = "lager-location-sheet"

/** Height of the scope segment — artboard 1 draws it at 44 dp. */
private val SCOPE_SEGMENT_HEIGHT = 44.dp

/**
 * The segment that switches the Lager between the org's stock and the caller's own (design ch. 19,
 * artboard 1).
 *
 * @param scope the scope showing.
 * @param onScope a segment was tapped.
 */
@Composable
internal fun LagerScopeSegment(
    scope: LagerScope,
    onScope: (LagerScope) -> Unit,
) {
    KrtSegmentedControl(
        options = listOf(stringResource(R.string.lager_scope_org), stringResource(R.string.lager_scope_my)),
        selectedIndex = if (scope == LagerScope.MY) 1 else 0,
        onSelect = { onScope(if (it == 1) LagerScope.MY else LagerScope.ORG) },
        stretch = true,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = KrtSpacing.s16, end = KrtSpacing.s16, top = KrtSpacing.s12)
                .height(SCOPE_SEGMENT_HEIGHT)
                .testTag(LAGER_SCOPE_TAG),
    )
}

/**
 * What the filter row reports.
 *
 * @property onPersonal a stock-kind chip was tapped.
 * @property onWithStockOnly the „Nur mit Bestand" chip was tapped.
 * @property onLocations the place selection changed.
 */
internal data class LagerFilterActions(
    val onPersonal: (PersonalFilter) -> Unit,
    val onWithStockOnly: (Boolean) -> Unit,
    val onLocations: (Set<String>) -> Unit,
)

/**
 * The horizontally scrolling filter chips of the Lager (artboard 1): the stock kind and the place in
 * „Mein Lager", „Nur mit Bestand" in the Org-Lager.
 *
 * @param state the tree's state.
 * @param actions what the chips report.
 */
@Composable
internal fun LagerFilterRow(
    state: InventoryState,
    actions: LagerFilterActions,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = KrtSpacing.s16, end = KrtSpacing.s16, top = KrtSpacing.s10)
                .testTag(LAGER_FILTER_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.scope == LagerScope.MY) {
            val personal = state.filter.personal
            KrtFilterChip(
                text = stringResource(R.string.lager_filter_all),
                selected = personal == PersonalFilter.ALL,
                onClick = { actions.onPersonal(PersonalFilter.ALL) },
            )
            KrtFilterChip(
                text = stringResource(R.string.lager_filter_personal_only),
                selected = personal == PersonalFilter.PERSONAL_ONLY,
                onClick = { actions.onPersonal(PersonalFilter.PERSONAL_ONLY) },
            )
            KrtFilterChip(
                text = stringResource(R.string.lager_filter_shared_only),
                selected = personal == PersonalFilter.SHARED_ONLY,
                onClick = { actions.onPersonal(PersonalFilter.SHARED_ONLY) },
            )
            KrtFilterChip(
                text = stringResource(R.string.lager_filter_location, locationLabel(state)),
                selected = state.filter.locationIds.isNotEmpty(),
                onClick = { picking = true },
            )
        } else {
            KrtFilterChip(
                text = stringResource(R.string.inventory_with_stock_only),
                selected = state.withStockOnly,
                onClick = { actions.onWithStockOnly(!state.withStockOnly) },
            )
        }
    }
    if (picking) {
        LocationFilterSheet(
            options = state.locationOptions,
            chosen = state.filter.locationIds,
            onApply = {
                picking = false
                actions.onLocations(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The place chip's value: „Alle" for none or every place, the place's own name for one, otherwise
 * how many (REQ-INV-037).
 *
 * @param state the tree's state.
 * @return the label.
 */
@Composable
private fun locationLabel(state: InventoryState): String {
    val chosen = state.filter.locationIds
    val options = state.locationOptions
    return when {
        chosen.isEmpty() || (options.isNotEmpty() && chosen.size >= options.size) -> {
            stringResource(R.string.lager_filter_all)
        }

        chosen.size == 1 -> {
            options.firstOrNull { it.id in chosen }?.name ?: pluralStringResource(R.plurals.lager_filter_chosen, 1, 1)
        }

        else -> {
            pluralStringResource(R.plurals.lager_filter_chosen, chosen.size, chosen.size)
        }
    }
}

/**
 * The place multi-select: every place the caller's stock sits at, from an unfiltered read so an
 * active filter never narrows its own options.
 *
 * @param options the places.
 * @param chosen the places kept now.
 * @param onApply the selection to keep; empty keeps every place.
 * @param onDismiss the sheet was closed without applying.
 */
@Composable
private fun LocationFilterSheet(
    options: List<LocationOption>,
    chosen: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable(chosen) { mutableStateOf(chosen.toList()) }
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.lager_filter_location_title),
        centred = isWideWindow(),
        modifier = Modifier.testTag(LAGER_LOCATION_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        ) {
            if (options.isEmpty()) {
                Muted(stringResource(R.string.lager_filter_location_none))
            }
            options.forEach { option ->
                KrtCheckboxRow(
                    checked = option.id in picked,
                    onCheckedChange = { on -> picked = if (on) picked + option.id else picked - option.id },
                    label = option.name,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = KrtSpacing.s8),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            ) {
                KrtGhostButton(
                    text = stringResource(R.string.lager_filter_reset),
                    onClick = { onApply(emptySet()) },
                    modifier = Modifier.weight(1f),
                )
                KrtCtaButton(
                    text = stringResource(R.string.lager_filter_apply),
                    onClick = { onApply(picked.toSet()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The top bar's filter toggle; while the row is collapsed it counts the filter values it hides
 * (REQ-INV-037).
 *
 * @param open whether the row is showing.
 * @param hidden how many filter values are set.
 * @param onToggle the toggle was tapped.
 */
@Composable
internal fun LagerFilterToggle(
    open: Boolean,
    hidden: Int,
    onToggle: () -> Unit,
) {
    Box {
        KrtIconButton(
            iconRes = DesignR.drawable.ic_krt_filter,
            label =
                stringResource(if (open) R.string.lager_filter_hide else R.string.lager_filter_show),
            onClick = onToggle,
            style = KrtButtonStyles.chrome,
            modifier = Modifier.testTag(LAGER_FILTER_TOGGLE_TAG),
        )
        if (!open && hidden > 0) {
            KrtCountBadge(count = hidden, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * The line above the tree: how many groups, and in „Mein Lager" that only the caller's rows are
 * shown (artboard 1).
 *
 * @param state the tree's state.
 */
@Composable
internal fun LagerCountLine(state: InventoryState) {
    val count = state.visibleGroups.size
    Text(
        text =
            if (state.scope == LagerScope.MY) {
                pluralStringResource(R.plurals.lager_count_my, count, count)
            } else {
                pluralStringResource(R.plurals.lager_count_org, count, count)
            }.krtUppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(start = KrtSpacing.s16, end = KrtSpacing.s16, top = KrtSpacing.s12),
    )
}

/**
 * The chips under a stack's title: „Persönlich", its unit as a pill or „Keine Einheit", and
 * „Gestohlen" (artboard 1). A shared stack in „Mein Lager" shows its pool; the Org-Lager shows only
 * what sets a stack apart.
 *
 * @param stack the stack.
 * @param scope which Lager the stack is shown in.
 */
@Composable
internal fun StackChips(
    stack: InventoryStack,
    scope: LagerScope,
) {
    val showUnit = scope == LagerScope.MY
    if (!stack.personal && !stack.stolen && !showUnit) {
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
        modifier = Modifier.padding(top = KrtSpacing.s4),
    ) {
        if (stack.personal) {
            KrtChip(text = stringResource(R.string.inventory_personal), tone = KrtChipTone.Muted)
        }
        if (showUnit) {
            UnitPill(name = stack.owningOrgUnitName)
        }
    }
}

/**
 * What the Lager screen reports beyond the Org-Lager tree: the scope segment, the „Mein Lager"
 * filters and the row actions.
 *
 * @property scoped whether the scope segment is drawn at all.
 * @property onScope a scope was chosen.
 * @property onPersonal a stock-kind filter chip was tapped.
 * @property onLocations the place filter changed.
 * @property onResetFilters every filter is to be cleared.
 * @property onRebook a row is to move between personal and the shared Lager.
 * @property onOrgUnit a personal row's unit is to change.
 * @property onOpenOrder an Auftrag's collection is to open, where the delivery flag is set.
 */
data class LagerScreenActions(
    val scoped: Boolean = false,
    val onScope: (LagerScope) -> Unit = {},
    val onPersonal: (PersonalFilter) -> Unit = {},
    val onLocations: (Set<String>) -> Unit = {},
    val onResetFilters: () -> Unit = {},
    val onRebook: (InventoryEntry) -> Unit = {},
    val onOrgUnit: (InventoryEntry) -> Unit = {},
    val onOpenOrder: (String) -> Unit = {},
)

/**
 * An entry's `⋮` in „Mein Lager": the rebooking its flag allows, „Einheit ändern" for a personal
 * row, and one entry per Auftrag the row is earmarked for, which opens that Auftrag's collection
 * where the delivery status is set. An entry that cannot apply stays visible with its reason.
 *
 * @param entry the row.
 * @param online whether a write can be sent.
 * @param lager where the choices go.
 */
@Composable
internal fun EntryMoreMenu(
    entry: InventoryEntry,
    online: Boolean,
    lager: LagerScreenActions,
) {
    var open by rememberSaveable(entry.id) { mutableStateOf(false) }
    val earmarked = entry.jobOrderAllocations.isNotEmpty() || entry.missionAllocations.isNotEmpty()
    val items =
        buildList {
            add(
                KrtMenuItem(
                    label =
                        stringResource(
                            if (entry.personal) R.string.lager_entry_to_shared else R.string.lager_entry_to_personal,
                        ),
                    iconRes = DesignR.drawable.ic_krt_swap,
                    enabled = online && (entry.personal || !earmarked),
                    reason =
                        stringResource(R.string.lager_entry_to_personal_earmarked).takeIf {
                            !entry.personal && earmarked
                        },
                    onClick = { lager.onRebook(entry) },
                ),
            )
            add(
                KrtMenuItem(
                    label = stringResource(R.string.lager_entry_org_unit),
                    iconRes = DesignR.drawable.ic_krt_users,
                    enabled = online && entry.personal,
                    reason = stringResource(R.string.lager_entry_org_unit_shared).takeUnless { entry.personal },
                    onClick = { lager.onOrgUnit(entry) },
                ),
            )
            entry.jobOrderAllocations.forEach { allocation ->
                add(
                    KrtMenuItem(
                        label = stringResource(R.string.lager_entry_order, allocation.label),
                        iconRes = DesignR.drawable.ic_krt_external_link,
                        onClick = { lager.onOpenOrder(allocation.targetId) },
                    ),
                )
            }
        }
    KrtOverflowMenu(
        items = items,
        contentDescription = stringResource(R.string.lager_entry_more),
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.testTag(LAGER_ENTRY_MORE_TAG),
    )
}

/**
 * Says where a personal book-in went when it was made from the Org-Lager, which does not show it;
 * the toast's four seconds are the denial toast's (design ch. 19, artboard 3).
 *
 * @param shown whether to show it.
 * @param onShown the notice has been on screen long enough.
 */
@Composable
internal fun PersonalBookedToast(
    shown: Boolean,
    onShown: () -> Unit,
) {
    if (!shown) {
        return
    }
    LaunchedEffect(Unit) {
        delay(DENIAL_TOAST_MS)
        onShown()
    }
    Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.BottomCenter) {
        KrtToast(
            title = stringResource(R.string.lager_booked_personal_title),
            message = stringResource(R.string.lager_booked_personal_message),
            modifier =
                Modifier
                    .padding(horizontal = KrtSpacing.s16)
                    .padding(bottom = KrtSpacing.s16 + LocalKrtBottomBarInset.current)
                    .testTag(LAGER_PERSONAL_TOAST_TAG),
        )
    }
}

/** Test handle for the personal book-in's toast. */
const val LAGER_PERSONAL_TOAST_TAG: String = "lager-personal-toast"

/** Test handle for an entry's `⋮` in „Mein Lager". */
const val LAGER_ENTRY_MORE_TAG: String = "lager-entry-more"

/**
 * A row's owning unit as the design system's one pill, or the muted „Keine Einheit" chip when the
 * row has none — synced stock arrives that way.
 *
 * @param name the unit's name, or `null` without one.
 */
@Composable
internal fun UnitPill(name: String?) {
    if (name == null) {
        KrtChip(text = stringResource(R.string.lager_no_unit), tone = KrtChipTone.Muted)
    } else {
        KrtOrgBadge(text = name, compact = true)
    }
}
