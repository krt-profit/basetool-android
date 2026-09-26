/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.common.formatSignedAmount
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinanceEntry
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionFrequency
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChoiceChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDataValue
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTile
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFigureTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIconButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOutlineButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTabs
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusDot
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStepState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSuccessButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtTheme
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.DenialToast
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.fieldMessage
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the tab row, so a screen test can find it without matching localised copy. */
const val MISSION_DETAIL_TABS_TAG: String = "mission-detail-tabs"

/** Test handle for the scrolling content beneath the tabs. */
const val MISSION_DETAIL_CONTENT_TAG: String = "mission-detail-content"

/** Test handle for the sign-up action. */
const val MISSION_SIGN_UP_TAG: String = "mission-sign-up"

/** Test handle for the check-in action. */
const val MISSION_CHECK_IN_TAG: String = "mission-check-in"

/** Test handle for the payout-preference action. */
const val MISSION_PAYOUT_TAG: String = "mission-payout"

/** Test handle for the Finanzen tab's add action. */
const val MISSION_FINANCE_ADD_TAG: String = "mission-finance-add"

/** Test handle for a booking's edit action. */
const val MISSION_FINANCE_EDIT_TAG: String = "mission-finance-edit"

/** Test handle for a booking's delete action. */
const val MISSION_FINANCE_DELETE_TAG: String = "mission-finance-delete"

/** Test handle for the booking form. */
const val MISSION_FINANCE_SHEET_TAG: String = "mission-finance-sheet"

/** Test handle for the booking form's save action. */
const val MISSION_FINANCE_SAVE_TAG: String = "mission-finance-save"

/**
 * One Einsatz in full (design spec ch. 06 §2), with a sticky head and tab row over the scrolling
 * tab content.
 *
 * @param state what to draw.
 * @param onTabSelected a tab was picked.
 * @param onRefresh pull-to-refresh.
 * @param onRetryNow the member pressed the manual retry of the chapter-14 countdown.
 * @param onRetryFinances the Finanzen tab's retry.
 * @param actions what the caller may do to their own sign-up.
 * @param finances what they may do to the Einsatz's money.
 * @param roster what a manager may do to a roster row.
 * @param admin what a manager may do to the Einsatz itself.
 * @param structure what a manager may do to its Einheiten and Frequenzen.
 * @param timeline what a manager may do to its Ablauf and Ziele.
 * @param members the one picker behind the party lead, the managers and „Teilnehmer hinzufügen".
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    state: MissionDetailState,
    onTabSelected: (MissionTab) -> Unit,
    onRefresh: () -> Unit,
    onRetryNow: () -> Unit,
    onRetryFinances: () -> Unit,
    actions: MissionSignUpActions,
    finances: MissionFinanceActions,
    roster: MissionRosterActions,
    admin: MissionAdminActions,
    structure: MissionStructureActions,
    timeline: MissionTimelineActions,
    members: MissionMemberActions,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val phase = state.phase
    ConflictOn(error = state.error, onReload = onRefresh)
    state.joinSheet?.let { ConflictOn(error = it.error, onReload = onRefresh) }
    state.structure.removingManager?.let { manager ->
        KrtModal(
            title = stringResource(R.string.mission_member_remove_manager_title),
            confirmText = stringResource(R.string.mission_struct_remove_manager),
            onConfirm = structure.onConfirmRemoveManager,
            onDismiss = structure.onDismissRemoveManager,
            tone = KrtModalTone.Danger,
            modifier = Modifier.testTag(MISSION_MANAGER_REMOVE_MODAL_TAG),
        ) {
            Text(
                text = stringResource(R.string.mission_member_remove_manager_body, manager.name),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                detail != null -> {
                    if (!state.online) {
                        OfflineBand()
                    }
                    MissionDetailHead(detail = detail)
                    MissionTabRow(
                        selected = state.tab,
                        detail = state.detail,
                        canManage = state.canManage,
                        denials = roster.denials,
                        onTabSelected = onTabSelected,
                    )
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        MissionTabContent(
                            state = state,
                            detail = detail,
                            onRetryFinances = onRetryFinances,
                            actions = actions,
                            finances = finances,
                            roster = roster,
                            structure = structure,
                            admin = admin,
                            timeline = timeline,
                            members = members,
                        )
                    }
                    if (state.tab != MissionTab.ADMIN) {
                        SignUpBar(state = state, actions = actions)
                    }
                    state.entryDraft?.let { draft ->
                        FinanceEntrySheet(draft = draft, state = state, actions = finances)
                    }
                    state.joinSheet?.let { sheet ->
                        MissionJoinSheet(
                            sheet = sheet,
                            subject = detail.name,
                            onPayout = actions.onJoinPayout,
                            onFunction = actions.onDesiredFunction,
                            onConfirm = actions.onJoinConfirmed,
                            onDismiss = actions.onJoinDismissed,
                        )
                    }
                }

                phase is MissionDetailPhase.Failed -> {
                    val retryIn = state.retryIn
                    if (retryIn != null) {
                        KrtRetryCountdown(
                            secondsLeft = retryIn,
                            title = stringResource(R.string.retry_busy_title),
                            message = stringResource(R.string.retry_busy_message, retryIn),
                            retryLabel = stringResource(R.string.retry_now),
                            onRetry = onRetryNow,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
                        )
                    } else {
                        MissionDetailFailure(error = phase.error)
                    }
                }

                else -> {
                    KrtLoadingIndicator(
                        text = stringResource(R.string.mission_detail_title),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        MissionDetailOverlays(
            state = state,
            admin = admin,
            structure = structure,
            members = members,
            timeline = timeline,
        )
        DenialToast(state = roster.denials)
    }
}

/**
 * Everything the Einsatz detail floats over its content, owned by the screen so a recycled list
 * item cannot dismiss it.
 *
 * @param state what the screen knows.
 * @param admin what the Verwaltung tab can do.
 * @param structure what a manager may do to Einheiten and crew.
 * @param members the one picker behind the three member-shaped writes.
 * @param timeline what a manager may do to the Ablauf and the Ziele.
 */
@Composable
private fun MissionDetailOverlays(
    state: MissionDetailState,
    admin: MissionAdminActions,
    structure: MissionStructureActions,
    members: MissionMemberActions,
    timeline: MissionTimelineActions,
) {
    MemberPickerSheet(members = members)
    UnitComposeSheet(structure = structure)
    FrequencyComposeSheet(structure = structure)
    UnitRenameSheet(structure = structure)
    state.structure.crewPickerUnitId?.let { unitId ->
        state.detail?.units?.firstOrNull { it.id == unitId }?.let { unit ->
            CrewPickerSheet(
                unit = unit,
                roster = state.detail.participants,
                structure = structure,
            )
        }
    }
    when (state.timeline.composing) {
        true -> StepEditorSheet(timeline = timeline)
        false -> ObjectiveEditorSheet(timeline = timeline)
        null -> Unit
    }
    state.lifecycleAsk?.let { next ->
        MissionLifecycleConfirm(
            next = next,
            registered = state.detail?.registeredParticipants ?: 0,
            onConfirm = admin.onConfirmLifecycle,
            onDismiss = admin.onDismissLifecycle,
        )
    }
    state.adminForm?.let { form ->
        form.conflict?.let { conflict ->
            MissionSectionConflictModal(
                conflict = conflict,
                onKeepMine = admin.onKeepMine,
                onReload = admin.onReload,
            )
        }
    }
}

/**
 * What a manager may do to somebody else's roster row, and what to say when they may not.
 *
 * @property canManage whether the caller may act on another member's row; the server's own verdict.
 * @property enabled whether a write may run right now — online and not already saving; distinct
 *   from [canManage].
 * @property checkInPossible whether the Einsatz has started, which the server requires for a
 *   check-in.
 * @property jobTypes the Funktionen a manager may assign; empty for a caller who may not.
 * @property denials where a refused tap is announced.
 * @property onCheckIn check the named row in or out.
 * @property onPayout switch the named row's share between paid out and donated.
 * @property onFunction assign the named row a job, or clear it by tapping the assigned one.
 */
data class MissionRosterActions(
    val canManage: Boolean,
    val enabled: Boolean,
    val checkInPossible: Boolean,
    val jobTypes: List<MissionJobType>,
    val denials: DenialState,
    val onCheckIn: (String) -> Unit,
    val onPayout: (String) -> Unit,
    val onFunction: (String, MissionJobType) -> Unit,
)

/**
 * Everything the Einsatz screen reports about the caller's own sign-up.
 *
 * @property onToggleSignUp they signed up, or withdrew.
 * @property onToggleCheckIn they checked in, or back out.
 * @property onTogglePayoutPreference they switched their share between paid out and donated.
 * @property onJoinPayout the sign-up sheet's payout choice changed.
 * @property onDesiredFunction a function chip in the sign-up sheet was tapped.
 * @property onChangeDesiredFunction the caller changed the job they wish for AFTER signing up,
 *   from their own roster row's sheet. A different call from [onDesiredFunction], which only edits
 *   the not-yet-sent sign-up draft.
 * @property onJoinConfirmed the sign-up sheet was sent.
 * @property onJoinDismissed the sign-up sheet was closed without signing up.
 */
data class MissionSignUpActions(
    val onToggleSignUp: () -> Unit,
    val onToggleCheckIn: () -> Unit,
    val onTogglePayoutPreference: () -> Unit,
    val onJoinPayout: (Boolean) -> Unit,
    val onDesiredFunction: (MissionJobType) -> Unit,
    val onChangeDesiredFunction: (MissionJobType) -> Unit,
    val onJoinConfirmed: () -> Unit,
    val onJoinDismissed: () -> Unit,
)

/**
 * What the Finanzen tab reports back.
 *
 * @property onAdd a new booking was started.
 * @property onEdit a booking was opened.
 * @property onDelete a booking was removed.
 * @property onIncome the direction changed.
 * @property onAmount the amount changed.
 * @property onNote the note changed.
 * @property onSave the booking was saved.
 * @property onDismiss the editor was closed.
 */
data class MissionFinanceActions(
    val onAdd: () -> Unit,
    val onEdit: (MissionFinanceEntry) -> Unit,
    val onDelete: (MissionFinanceEntry) -> Unit,
    val onIncome: (Boolean) -> Unit,
    val onAmount: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The band under the head with the caller's own participation actions.
 *
 * Check-in and the payout preference appear only once the caller has signed up.
 *
 * @param state the screen.
 * @param actions what it reports back.
 */
@Composable
private fun SignUpBar(
    state: MissionDetailState,
    actions: MissionSignUpActions,
) {
    val mine = state.mySignUp
    Column(modifier = Modifier.fillMaxWidth()) {
        state.error?.let { error ->
            Box(modifier = Modifier.padding(horizontal = KrtSpacing.s12)) {
                SignUpError(error = error)
            }
        }
        if (mine != null && !state.checkInPossible) {
            Text(
                text = stringResource(R.string.mission_detail_check_in_not_yet),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.padding(horizontal = KrtSpacing.s12),
            )
        }
        KrtBottomCtaBar {
            SignUpAction(mine = mine, state = state, actions = actions)
            if (mine != null && state.checkInPossible) {
                CheckInAction(mine = mine, state = state, actions = actions)
            }
        }
    }
}

/**
 * Signing up, and back out again — the bar's first action; only „Anmelden" is drawn filled.
 *
 * @param mine the caller's own row, or `null` when they have not signed up.
 * @param state the screen.
 * @param actions what it reports back.
 */
@Composable
private fun RowScope.SignUpAction(
    mine: MissionParticipant?,
    state: MissionDetailState,
    actions: MissionSignUpActions,
) {
    val modifier =
        Modifier
            .testTag(MISSION_SIGN_UP_TAG)
            .weight(1f)
            .writeAlpha(state.writable)
    if (mine == null) {
        KrtCtaButton(
            text = stringResource(R.string.mission_detail_sign_up),
            iconRes = DesignR.drawable.ic_krt_login,
            onClick = actions.onToggleSignUp,
            modifier = modifier,
            enabled = state.writable,
        )
    } else {
        KrtGhostButton(
            text = stringResource(R.string.mission_detail_withdraw),
            iconRes = DesignR.drawable.ic_krt_logout,
            onClick = actions.onToggleSignUp,
            modifier = modifier,
            enabled = state.writable,
        )
    }
}

/**
 * Checking in and back out, shown only once the Einsatz has started; weighted to share the row
 * evenly.
 *
 * @param mine the caller's own row.
 * @param state the screen.
 * @param actions what it reports back.
 */
@Composable
private fun RowScope.CheckInAction(
    mine: MissionParticipant,
    state: MissionDetailState,
    actions: MissionSignUpActions,
) {
    val modifier =
        Modifier
            .testTag(MISSION_CHECK_IN_TAG)
            .weight(1f)
            .writeAlpha(state.writable)
    if (mine.checkedIn) {
        KrtGhostButton(
            text = stringResource(R.string.mission_detail_check_out),
            onClick = actions.onToggleCheckIn,
            iconRes = DesignR.drawable.ic_krt_logout,
            modifier = modifier,
            enabled = state.writable,
        )
    } else {
        KrtSuccessButton(
            text = stringResource(R.string.mission_detail_check_in),
            onClick = actions.onToggleCheckIn,
            iconRes = DesignR.drawable.ic_krt_check,
            modifier = modifier,
            enabled = state.writable,
        )
    }
}

/**
 * What the last write returned, in the app's own words.
 *
 * Module-internal rather than private: every mutating surface of the Einsatz reports its refusals
 * the same way, and the Verwaltung tab lives in its own file.
 *
 * @param error the refusal.
 */
@Composable
internal fun SignUpError(error: ApiError) {
    val named = error.fieldMessage()
    KrtFieldError(
        text =
            named ?: stringResource(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
                    is ApiError.Conflict -> R.string.refused_inline
                    is ApiError.Forbidden -> R.string.mission_detail_not_allowed
                    else -> R.string.write_failed
                },
            ),
    )
}

/**
 * Fades a control that cannot be used right now.
 *
 * @param writable whether a write may be offered.
 * @return the modifier.
 */
internal fun Modifier.writeAlpha(writable: Boolean): Modifier =
    alpha(if (writable) 1f else DISABLED_WRITE_ALPHA)

/**
 * The horizontally scrollable row of all eight tabs.
 *
 * Verwaltung is drawn locked, never hidden, for a non-manager (REQ-APP-AUTH-013); tapping it raises
 * the toast naming the Missions-Manager role and keeps the current tab.
 *
 * @param selected which tab is showing.
 * @param detail the Einsatz, for the per-tab counts.
 * @param canManage whether the Verwaltung tab may be opened.
 * @param denials where the refused tap is announced.
 * @param onTabSelected a tab was picked — called only for a tab the caller may open.
 */
@Composable
private fun MissionTabRow(
    selected: MissionTab,
    detail: MissionDetail?,
    canManage: Boolean,
    denials: DenialState,
    onTabSelected: (MissionTab) -> Unit,
) {
    val gate =
        Gate(
            allowed = canManage,
            reason = stringResource(R.string.gate_role_mission_manager),
            detail = stringResource(R.string.gate_role_mission_manager_detail),
        )
    val tabs = MissionTab.entries
    KrtPageTabs(
        tabs =
            tabs.map { tab ->
                val locked = tab == MissionTab.ADMIN && !canManage
                KrtPageTab(
                    label = stringResource(tab.labelRes()),
                    count = detail?.let(tab::countIn),
                    locked = locked,
                )
            },
        selectedIndex = tabs.indexOf(selected).coerceAtLeast(0),
        onSelect = { index ->
            val tab = tabs[index]
            if (tab == MissionTab.ADMIN && !gate.allowed) {
                denials.raise(gate)
            } else {
                onTabSelected(tab)
            }
        },
        modifier = Modifier.testTag(MISSION_DETAIL_TABS_TAG),
    )
}

/**
 * The selected tab's content.
 *
 * @param state everything the screen knows.
 * @param detail the Einsatz, already known to be present.
 * @param onRetryFinances the Finanzen tab's retry.
 * @param finances what the caller may do to the Einsatz's money.
 * @param roster what a manager may do to a roster row.
 * @param structure what a manager may do to its Einheiten and Frequenzen.
 * @param admin what a manager may do to the Einsatz itself.
 * @param timeline what a manager may do to its Ablauf and Ziele.
 * @param members the one picker behind the three member-shaped writes.
 * @param actions the sign-up's own actions - the Teilnehmer tab carries the caller's payout choice.
 */
@Composable
private fun MissionTabContent(
    state: MissionDetailState,
    detail: MissionDetail,
    onRetryFinances: () -> Unit,
    actions: MissionSignUpActions,
    finances: MissionFinanceActions,
    roster: MissionRosterActions,
    structure: MissionStructureActions,
    admin: MissionAdminActions,
    timeline: MissionTimelineActions,
    members: MissionMemberActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(MISSION_DETAIL_CONTENT_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(KrtSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        when (state.tab) {
            MissionTab.OVERVIEW -> {
                overviewTab(detail)
            }

            MissionTab.PARTICIPANTS -> {
                participantsTab(
                    detail = detail,
                    mine = state.mySignUp,
                    roster = roster,
                    own =
                        MissionOwnRoleActions(
                            onPayout = actions.onTogglePayoutPreference,
                            onDesired = actions.onChangeDesiredFunction,
                        ),
                )
            }

            MissionTab.UNITS -> {
                unitsTab(detail, structure)
            }

            MissionTab.STEPS -> {
                stepsTab(detail, timeline)
            }

            MissionTab.OBJECTIVES -> {
                objectivesTab(detail, timeline)
            }

            MissionTab.FREQUENCIES -> {
                frequenciesTab(detail, structure)
            }

            MissionTab.FINANCES -> {
                financesTab(state, onRetryFinances, finances)
            }

            MissionTab.ADMIN -> {
                state.adminForm?.let {
                    adminTab(
                        form = it,
                        writable = state.writable,
                        actions = admin,
                        members = members,
                        lifecycle =
                            MissionLifecycleUi(
                                detail = detail,
                                next = state.lifecycleNext,
                                denials = roster.denials,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Einheiten: each unit, its ship and its crew.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.unitsTab(
    detail: MissionDetail,
    structure: MissionStructureActions,
) {
    item { StructureError(structure) }
    if (detail.units.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_units) }
    }
    items(detail.units, key = { it.id }) { unit ->
        KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
            UnitHeader(unit = unit, structure = structure)
            Column(
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s8),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            ) {
                unit.responsibleName?.let {
                    Text(
                        text = stringResource(R.string.mission_detail_unit_lead, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.TextMuted,
                    )
                }
                unit.crew.forEach { member ->
                    CrewRow(
                        unit = unit,
                        member = member,
                        roster = detail.participants,
                        structure = structure,
                    )
                }
                CrewAdd(unit = unit, roster = detail.participants, structure = structure)
            }
        }
    }
    item { UnitAdd(structure) }
}

/**
 * A Ziel's row height — artboard 06-2 draws it at 52 dp, above the 48 dp control floor because the
 * row carries a chip and a button group rather than a single line of text.
 */
private val OBJECTIVE_ROW_HEIGHT = 52.dp

/**
 * Ablauf: the checklist.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.stepsTab(
    detail: MissionDetail,
    timeline: MissionTimelineActions,
) {
    if (detail.steps.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_steps) }
        item { TimelineListActions(R.string.mission_step_add, MISSION_STEP_ADD_TAG, timeline) }
        return
    }
    item {
        Column {
            val now = detail.steps.indexOfFirst { !it.done }
            detail.steps.forEachIndexed { index, step ->
                KrtStepRow(
                    number = index + 1,
                    state =
                        when {
                            step.done -> KrtStepState.Done
                            index == now -> KrtStepState.Now
                            else -> KrtStepState.Ahead
                        },
                    title = step.title,
                    meta = step.meta,
                    connected = index < detail.steps.lastIndex,
                ) {
                    StepRowActions(
                        step = MissionStepEdit(id = step.id, title = step.title, meta = step.meta),
                        done = step.done,
                        timeline = timeline,
                        position = RowPosition(first = index == 0, last = index == detail.steps.lastIndex),
                    )
                }
            }
        }
    }
    item { TimelineListActions(R.string.mission_step_add, MISSION_STEP_ADD_TAG, timeline) }
}

/**
 * Ziele: the objectives with the server's own classification.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.objectivesTab(
    detail: MissionDetail,
    timeline: MissionTimelineActions,
) {
    if (detail.objectives.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_objectives) }
        item {
            TimelineListActions(R.string.mission_objective_add, MISSION_OBJECTIVE_ADD_TAG, timeline)
        }
        return
    }
    itemsIndexed(detail.objectives, key = { _, row -> row.id }) { index, objective ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(KrtPalette.Gray4)
                    .border(KrtSpacing.hairline, KrtPalette.Gray3)
                    .defaultMinSize(minHeight = OBJECTIVE_ROW_HEIGHT)
                    .padding(horizontal = KrtSpacing.s14, vertical = KrtSpacing.s4),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = objective.title,
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.Gray1,
                modifier = Modifier.weight(1f),
            )
            objective.kind?.let { KrtChip(text = it.kindLabel(), tone = it.kindTone()) }
            ObjectiveRowActions(
                objective =
                    MissionObjectiveEdit(
                        id = objective.id,
                        title = objective.title,
                        kind = objective.kind.toObjectiveKind(),
                    ),
                timeline = timeline,
                position =
                    RowPosition(first = index == 0, last = index == detail.objectives.lastIndex),
            )
        }
    }
    item { TimelineListActions(R.string.mission_objective_add, MISSION_OBJECTIVE_ADD_TAG, timeline) }
}

/**
 * Frequenzen: tap-to-copy, as the design specifies.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.frequenciesTab(
    detail: MissionDetail,
    structure: MissionStructureActions,
) {
    item { StructureError(structure) }
    if (detail.frequencies.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_frequencies) }
    }
    items(detail.frequencies, key = { it.id }) { frequency ->
        val clipboard = LocalClipboard.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val copy = {
            scope.launch {
                clipboard.setClipEntry(
                    androidx.compose.ui.platform.ClipEntry(
                        android.content.ClipData.newPlainText(
                            frequency.type.orEmpty(),
                            frequency.value,
                        ),
                    ),
                )
            }
            Unit
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(KrtPalette.Gray4)
                    .border(KrtSpacing.hairline, KrtPalette.Gray3)
                    .clickable(onClick = copy)
                    .defaultMinSize(minHeight = KrtSpacing.denseRow)
                    .padding(horizontal = KrtSpacing.s14, vertical = KrtSpacing.s4),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrtIcon(
                id = DesignR.drawable.ic_krt_antenna,
                contentDescription = null,
                size = FREQUENCY_GLYPH,
                tint = KrtPalette.TextMuted,
            )
            Text(
                text = frequency.type.orEmpty().krtUppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = KrtPalette.TextMuted,
                modifier = Modifier.weight(1f),
            )
            KrtChip(text = frequency.value, tone = KrtChipTone.Data)
            KrtIconButton(
                iconRes = DesignR.drawable.ic_krt_clipboard_check,
                label = stringResource(R.string.mission_freq_copy),
                onClick = copy,
            )
            FrequencyRemove(frequency = frequency, structure = structure)
        }
    }
    item { FrequencyAdd(structure) }
}

/** The antenna beside a frequency — 20 px in artboard 06-2. */
private val FREQUENCY_GLYPH = 20.dp

/**
 * Removes a frequency, as the trailing icon button of its row.
 *
 * @param frequency the row.
 * @param structure the actions, for the gate and the refusal slot.
 */
@Composable
private fun FrequencyRemove(
    frequency: MissionFrequency,
    structure: MissionStructureActions,
) {
    val gate =
        Gate(
            allowed = structure.canManage,
            reason = stringResource(R.string.gate_role_mission_manager),
            detail = stringResource(R.string.gate_role_mission_manager_detail),
        )
    val (dim, click) =
        rememberGated(gate, { structure.onRemoveFrequency(frequency.id) }, structure.denials)
    KrtIconButton(
        iconRes = if (gate.allowed) DesignR.drawable.ic_krt_trash else DesignR.drawable.ic_krt_lock,
        label = stringResource(R.string.mission_struct_remove_freq),
        onClick = click,
        modifier = dim,
        enabled = structure.enabled,
    )
}

/**
 * The line a tab shows when the server had nothing for it.
 *
 * @param messageRes what to say.
 */
@Composable
internal fun EmptyTab(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(vertical = KrtSpacing.s12),
    )
}

/**
 * The whole screen when the Einsatz could not be read, with distinct text for refused, gone and
 * broken.
 *
 * @param error what went wrong.
 */
@Composable
private fun MissionDetailFailure(error: ApiError) {
    val (titleRes, messageRes) =
        when (error) {
            is ApiError.Forbidden -> {
                R.string.mission_detail_error_forbidden_title to R.string.mission_detail_error_forbidden_message
            }

            is ApiError.NotFound -> {
                R.string.mission_detail_error_missing_title to R.string.mission_detail_error_missing_message
            }

            else -> {
                R.string.mission_detail_error_title to R.string.mission_detail_error_message
            }
        }
    KrtEmptyState(
        iconRes = DesignR.drawable.ic_krt_target,
        title = stringResource(titleRes),
        message = stringResource(messageRes),
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.s16),
    )
}

/**
 * The string resource naming this tab.
 *
 * @return the resource id.
 */
private fun MissionTab.labelRes(): Int =
    when (this) {
        MissionTab.OVERVIEW -> R.string.mission_detail_tab_overview
        MissionTab.PARTICIPANTS -> R.string.mission_detail_tab_participants
        MissionTab.UNITS -> R.string.mission_detail_tab_units
        MissionTab.STEPS -> R.string.mission_detail_tab_steps
        MissionTab.OBJECTIVES -> R.string.mission_detail_tab_objectives
        MissionTab.FREQUENCIES -> R.string.mission_detail_tab_frequencies
        MissionTab.FINANCES -> R.string.mission_detail_tab_finances
        MissionTab.ADMIN -> R.string.mission_detail_tab_admin
    }

/**
 * The badge text for this Einsatz.
 *
 * @return the translated status, or the raw server value when this build does not know it.
 */
@Composable
internal fun MissionDetail.statusLabel(): String =
    if (status == MissionStatus.UNKNOWN) {
        rawStatus.orEmpty()
    } else {
        stringResource(
            when (status) {
                MissionStatus.PLANNED -> R.string.missions_status_planned
                MissionStatus.ACTIVE -> R.string.missions_status_active
                MissionStatus.COMPLETED -> R.string.missions_status_completed
                MissionStatus.CANCELLED -> R.string.missions_status_cancelled
                MissionStatus.UNKNOWN -> R.string.missions_title
            },
        )
    }

/**
 * The badge tone for this Einsatz.
 *
 * @return the design system's tone; an unknown status is drawn as planned rather than as a problem.
 */
internal fun MissionDetail.statusTone(): KrtStatusTone =
    when (status) {
        MissionStatus.PLANNED, MissionStatus.UNKNOWN -> KrtStatusTone.Planned
        MissionStatus.ACTIVE -> KrtStatusTone.Active
        MissionStatus.COMPLETED -> KrtStatusTone.Completed
        MissionStatus.CANCELLED -> KrtStatusTone.Cancelled
    }

/**
 * The Einsatz detail, bound to its view model.
 *
 * @param viewModel drives the screen.
 * @param modifier layout modifier.
 */
@Composable
fun MissionDetailRoute(
    viewModel: MissionDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val denials = rememberDenialState()
    MissionDetailScreen(
        state = state,
        onTabSelected = viewModel::onTabSelected,
        onRefresh = viewModel::onRefresh,
        onRetryNow = viewModel::onRetry,
        onRetryFinances = viewModel::onRetryFinances,
        actions =
            MissionSignUpActions(
                onToggleSignUp = viewModel::onToggleSignUp,
                onToggleCheckIn = viewModel::onToggleCheckIn,
                onTogglePayoutPreference = viewModel::onTogglePayoutPreference,
                onJoinPayout = viewModel::onJoinPayout,
                onDesiredFunction = viewModel::onDesiredFunction,
                onChangeDesiredFunction = { job ->
                    state.mySignUp?.takeIf { state.writable }?.let { viewModel.roster.wish(it, job) }
                },
                onJoinConfirmed = viewModel::onJoinConfirmed,
                onJoinDismissed = viewModel::onJoinSheetDismissed,
            ),
        finances =
            MissionFinanceActions(
                onAdd = viewModel::onAddEntry,
                onEdit = viewModel::onEditEntry,
                onDelete = viewModel::onDeleteEntry,
                onIncome = viewModel::onEntryIncomeChanged,
                onAmount = viewModel::onEntryAmountChanged,
                onNote = viewModel::onEntryNoteChanged,
                onSave = viewModel::onSaveEntry,
                onDismiss = viewModel::onDismissEntry,
            ),
        roster =
            MissionRosterActions(
                canManage = state.canManage,
                enabled = state.writable,
                checkInPossible = state.checkInPossible,
                jobTypes = state.rosterJobTypes,
                denials = denials,
                onCheckIn = { viewModel.roster.checkIn(it, state.checkInPossible) },
                onPayout = viewModel.roster::payout,
                onFunction = viewModel.roster::assign,
            ),
        structure =
            MissionStructureActions(
                canManage = state.canManage,
                enabled = state.writable && !state.structure.busy,
                draft = state.structure,
                denials = denials,
                shipOptions = state.unitShips,
                memberOptions = state.unitMembers,
                onChange = viewModel.structure::change,
                onAddUnit = viewModel.structure::addUnit,
                onRemoveUnit = viewModel.structure::removeUnit,
                onAddFrequency = viewModel.structure::addFrequency,
                onRemoveFrequency = viewModel.structure::removeFrequency,
                onConfirmRemoveManager = viewModel.structure::confirmRemoveManager,
                onDismissRemoveManager = viewModel.structure::dismissRemoveManager,
                onRemoveCrew = viewModel.structure::removeCrew,
                onEditUnit = { unit ->
                    viewModel.structure.change {
                        it.copy(
                            unitName = unit.name,
                            editingUnitId = unit.id,
                            editingUnitVersion = unit.version,
                            editingUnitOriginalName = unit.name,
                            editingUnitHighValue = unit.highValue,
                            unitFields = unit.fields,
                        )
                    }
                },
                onSaveUnit = { unitId, version ->
                    val draft = state.structure
                    viewModel.structure.updateUnit(
                        unitId,
                        draft.unitName,
                        draft.editingUnitHighValue,
                        version,
                        draft.unitFields,
                    )
                },
                onSetCrewRoles = viewModel.structure::setCrewRoles,
                onAddCrew = viewModel.structure::addCrew,
                onOpenCrewPicker = { viewModel.structure.openCrewPicker(it.id) },
                onDismissCrewPicker = viewModel.structure::dismissCrewPicker,
                crewJobTypes = state.crewJobTypes,
            ),
        timeline =
            MissionTimelineActions(
                canManage = state.canManage,
                enabled = state.writable && !state.timeline.busy,
                draft = state.timeline,
                denials = denials,
                onChange = viewModel.timeline::change,
                onCompose = viewModel.timeline::compose,
                onSaveStep = viewModel.timeline::saveStep,
                onEditStep = viewModel.timeline::editStep,
                onToggleStep = viewModel.timeline::toggleStep,
                onRemoveStep = viewModel.timeline::removeStep,
                onMoveStep = viewModel.timeline::moveStep,
                onDuplicateStep = viewModel.timeline::duplicateStep,
                onSaveObjective = viewModel.timeline::saveObjective,
                onEditObjective = viewModel.timeline::editObjective,
                onRemoveObjective = viewModel.timeline::removeObjective,
                onMoveObjective = viewModel.timeline::moveObjective,
                onDuplicateObjective = viewModel.timeline::duplicateObjective,
                onCancel = viewModel.timeline::cancel,
            ),
        members =
            MissionMemberActions(
                canManage = state.canManage,
                enabled = state.writable && !state.structure.busy,
                state = state.memberPicker,
                denials = denials,
                onOpen = viewModel.memberPicker::open,
                onQuery = viewModel.memberPicker::query,
                onPick = viewModel.memberPicker::pick,
                onDismiss = viewModel.memberPicker::dismiss,
                partyLeadName = state.detail?.partyLeadName,
                managers = state.detail?.managers.orEmpty(),
                onRemoveManager = viewModel.structure::askRemoveManager,
            ),
        admin =
            MissionAdminActions(
                onChange = viewModel.admin::change,
                onToggle = viewModel.admin::toggle,
                onSave = viewModel.admin::save,
                onAskLifecycle = viewModel.lifecycle::ask,
                onConfirmLifecycle = viewModel.lifecycle::confirm,
                onDismissLifecycle = viewModel.lifecycle::dismiss,
                onCorrectStart = viewModel.admin::correctStart,
                onCancelCorrectStart = viewModel.admin::cancelCorrectStart,
                onEndMission = viewModel.admin::endMission,
                onCancelEndMission = viewModel.admin::cancelEndMission,
                onKeepMine = viewModel.admin::keepMine,
                onReload = viewModel.admin::reloadAfterConflict,
            ),
        modifier = modifier,
    )
}

/**
 * How many rows this tab holds.
 *
 * @param detail the Einsatz as read.
 * @return the count the tab chip shows, or `null` for Übersicht and Finanzen.
 */
private fun MissionTab.countIn(detail: MissionDetail): Int? =
    when (this) {
        MissionTab.OVERVIEW -> null
        MissionTab.PARTICIPANTS -> detail.participants.size
        MissionTab.UNITS -> detail.units.size
        MissionTab.STEPS -> detail.steps.size
        MissionTab.OBJECTIVES -> detail.objectives.size
        MissionTab.FREQUENCIES -> detail.frequencies.size
        MissionTab.FINANCES -> null
        MissionTab.ADMIN -> null
    }

/**
 * „Anmelden" — the sheet that collects the payout destination and an optional desired function
 * (design ch. 06, artboard 3).
 *
 * The function is a wish, not an assignment. A refusal keeps the sheet and its contents.
 *
 * @param sheet what has been collected so far.
 * @param subject the mission and its time, drawn under the title.
 * @param onPayout the share's destination changed.
 * @param onFunction a function chip was tapped; the same one again clears it.
 * @param onConfirm the sign-up was sent.
 * @param onDismiss the sheet was closed without signing up.
 */
@Composable
private fun MissionJoinSheet(
    sheet: JoinSheet,
    subject: String,
    onPayout: (Boolean) -> Unit,
    onFunction: (MissionJobType) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(R.string.mission_join_title),
        modifier = Modifier.testTag(MISSION_JOIN_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s12),
        ) {
            Text(
                text = subject,
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            JoinSectionLabel(text = stringResource(R.string.mission_join_payout))
            KrtRadioRow(
                selected = !sheet.donate,
                onSelect = { onPayout(false) },
                label = stringResource(R.string.mission_detail_payout_self),
                supporting = stringResource(R.string.mission_join_payout_self_hint),
                enabled = !sheet.saving,
            )
            KrtRadioRow(
                selected = sheet.donate,
                onSelect = { onPayout(true) },
                label = stringResource(R.string.mission_detail_payout_org),
                supporting = stringResource(R.string.mission_join_payout_org_hint),
                enabled = !sheet.saving,
            )
            if (sheet.jobTypes.isNotEmpty()) {
                JoinSectionLabel(text = stringResource(R.string.mission_join_function))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                    verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
                ) {
                    sheet.jobTypes.forEach { jobType ->
                        KrtFilterChip(
                            text = jobType.name,
                            selected = sheet.desired?.id == jobType.id,
                            onClick = { onFunction(jobType) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.mission_join_function_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            sheet.error?.let { SignUpError(error = it) }
            KrtCtaButton(
                text = stringResource(R.string.mission_join_confirm),
                onClick = onConfirm,
                iconRes = DesignR.drawable.ic_krt_login,
                enabled = !sheet.saving,
                modifier = Modifier.fillMaxWidth().testTag(MISSION_JOIN_CONFIRM_TAG),
            )
            KrtGhostButton(
                text = stringResource(R.string.personal_inventory_cancel),
                onClick = onDismiss,
                enabled = !sheet.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.mission_join_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
        }
    }
}

/**
 * A short uppercase section heading inside the sign-up sheet, with no rule after it.
 *
 * @param text the heading.
 */
@Composable
private fun JoinSectionLabel(text: String) {
    Text(
        text = text.krtUppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = KrtPalette.Gray1,
    )
}

/** Test handle for the sign-up sheet. */
const val MISSION_JOIN_SHEET_TAG: String = "mission-join-sheet"

/** Test handle for its confirm button. */
const val MISSION_JOIN_CONFIRM_TAG: String = "mission-join-confirm"
