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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomCtaBar
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtBottomSheet
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldLabel
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHudBox
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModalTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTab
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPageTabs
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRetryCountdown
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSegmentedControl
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSuccessButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.navigation.ProvideScreenTopBar
import de.greluc.krt.profit.basetool.android.ui.ConflictOn
import de.greluc.krt.profit.basetool.android.ui.DISABLED_WRITE_ALPHA
import de.greluc.krt.profit.basetool.android.ui.DenialState
import de.greluc.krt.profit.basetool.android.ui.DenialToast
import de.greluc.krt.profit.basetool.android.ui.Gate
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
import de.greluc.krt.profit.basetool.android.ui.rememberDenialState
import de.greluc.krt.profit.basetool.android.ui.rememberGated
import kotlinx.coroutines.launch
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
 * One Einsatz in full (design spec ch. 06 §2), read-only.
 *
 * The head and the tab row stay put; only the tab's content scrolls, which is what the design
 * means by a sticky head. Signing up, checking in and adding a finance entry are mutations and
 * belong to Phase 3 — this screen deliberately carries no call to action.
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
    // Bound so the smart cast below survives; `state.phase` is a property read and Kotlin will not
    // narrow it across the branch.
    val phase = state.phase
    // Design ch. 14's conflict dialog, once for the screen rather than at each place an error
    // is drawn: every one of them reads this same state, and a member must not be able to miss
    // a refused save under a scrolled form.
    ConflictOn(error = state.error, onReload = onRefresh)
    state.joinSheet?.let { ConflictOn(error = it.error, onReload = onRefresh) }
    // Taking a manager off withdraws a right, so it asks first and names the person — the artboard's
    // own distinction from changing the Einsatzleitung, which is replaced rather than taken away.
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
    // Boxed so the refusal can overlay the content. The toast belongs to the SCREEN and not to the
    // route above it: this is the composable that draws the locked controls, so it is the one that
    // has to be able to explain them — and a screen test that can reach the lock can then reach the
    // explanation too, which is the half that makes the lock worth anything.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                detail != null -> {
                    if (!state.online) {
                        OfflineBand()
                    }
                    MissionDetailHead(detail = detail)
                    MissionLifecycleBand(
                        detail = detail,
                        next = state.lifecycleNext,
                        enabled = state.writable && !state.saving,
                        denials = roster.denials,
                        onAsk = admin.onAskLifecycle,
                    )
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
                        // weight, not fillMaxSize: the tab content takes what is left after the head,
                        // the tab row and the CTA bar, so the bar stays on screen instead of being
                        // pushed off by a long Ablauf.
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        MissionTabContent(
                            state = state,
                            detail = detail,
                            onRetryFinances = onRetryFinances,
                            finances = finances,
                            roster = roster,
                            structure = structure,
                            admin = admin,
                            timeline = timeline,
                            members = members,
                        )
                    }
                    // Design ch. 06: ONE filled CTA, bottom-anchored. It sat between the facts and the
                    // tab row, where the screen's primary action scrolled away with the briefing and
                    // read as one more fact about the Einsatz.
                    //
                    // Not on the Verwaltung tab. That tab carries its own primary action — starting
                    // the Einsatz, and three section saves — and a filled „Anmelden" pinned over
                    // them is a second primary about a different subject, which is the same rule
                    // being broken from the other side. It also covered the form's last field.
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
                    // A busy server gets the countdown of chapter 14; anything else gets the ordinary
                    // failure state, because a countdown in front of a 403 promises a retry that will
                    // answer exactly the same.
                    val retryIn = state.retryIn
                    if (retryIn != null) {
                        KrtRetryCountdown(
                            secondsLeft = retryIn,
                            title = stringResource(R.string.retry_busy_title),
                            message = stringResource(R.string.retry_busy_message, retryIn),
                            retryLabel = stringResource(R.string.retry_now),
                            onRetry = onRetryNow,
                            modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
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
 * Everything the Einsatz detail floats over its content.
 *
 * All six are owned by the **screen** rather than by a tab, and for one reason: a sheet or a modal
 * owned by a `LazyColumn` item dies the moment that item recycles, which on a long Ablauf happens
 * while the member is still looking at it.
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
 * A single object rather than five parameters threaded through three composables — the row is deep
 * in a `LazyListScope`, and each new manager action would otherwise widen every signature between
 * here and it.
 *
 * @property canManage whether the caller may act on another member's row; the server's own verdict.
 * @property enabled whether a write may run **right now** — online and not already saving. Separate
 *   from [canManage] on purpose: offline is not a missing grant, and the refusal must not claim it
 *   is.
 * @property checkInPossible whether the Einsatz has actually started, which is what the server
 *   requires before it accepts a check-in at all.
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
 * @property onJoinConfirmed the sign-up sheet was sent.
 * @property onJoinDismissed the sign-up sheet was closed without signing up.
 */
data class MissionSignUpActions(
    val onToggleSignUp: () -> Unit,
    val onToggleCheckIn: () -> Unit,
    val onTogglePayoutPreference: () -> Unit,
    val onJoinPayout: (Boolean) -> Unit,
    val onDesiredFunction: (MissionJobType) -> Unit,
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
 * The band under the head: what the caller may do to their own participation.
 *
 * It sits above the tabs rather than inside the roster because it is about the caller and not
 * about the list — and because a member opening an Einsatz to sign up should not have to find the
 * right tab first.
 *
 * Check-in and the payout preference appear only once there is a sign-up to apply them to. An
 * action that would 404 for want of a row is not an action.
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
            Box(modifier = Modifier.padding(horizontal = KrtSpacing.md)) {
                SignUpError(error = error)
            }
        }
        // Only once the Einsatz has actually started: the server refuses a check-in before then,
        // and a control that can only return a refusal is a control that lies. Saying why beats
        // an action that is simply absent.
        if (mine != null && !state.checkInPossible) {
            Text(
                text = stringResource(R.string.mission_detail_check_in_not_yet),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
                modifier = Modifier.padding(horizontal = KrtSpacing.md),
            )
        }
        // The payout preference is a standing SETTING, not an action, and it used to sit in the
        // action row beside two buttons. Three items in a `KrtBottomCtaBar` — which is an End-aligned
        // row with no weights of its own — left „ABMELDEN" about one character wide, so it wrapped
        // to a column of single letters. It is drawn above the bar now, where two German radio
        // labels have the width they need and the bar is back to being what it is drawn as.
        if (mine != null) {
            PayoutPreference(mine = mine, state = state, actions = actions)
        }
        KrtBottomCtaBar {
            KrtCtaButton(
                text =
                    stringResource(
                        if (mine == null) {
                            R.string.mission_detail_sign_up
                        } else {
                            R.string.mission_detail_withdraw
                        },
                    ),
                // The artboard's CTA carries the login glyph beside its label; signing up is an
                // entry, and the icon says so before the word is read.
                iconRes =
                    if (mine == null) DesignR.drawable.ic_krt_login else DesignR.drawable.ic_krt_logout,
                onClick = actions.onToggleSignUp,
                modifier =
                    Modifier
                        .testTag(MISSION_SIGN_UP_TAG)
                        .weight(1f)
                        .writeAlpha(state.writable),
                enabled = state.writable,
            )
            if (mine != null && state.checkInPossible) {
                CheckInAction(mine = mine, state = state, actions = actions)
            }
        }
    }
}

/**
 * Checking in, and back out — the second action of the bar, and only once the Einsatz has started.
 *
 * It carries a weight so the row divides evenly between the two buttons. `KrtBottomCtaBar` is an
 * End-aligned row and distributes nothing by itself: without weights the first button keeps its
 * measured width and the second is squeezed into whatever is left, which is how a label ends up
 * one letter per line.
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
    // Check-In is the example the button ladder gives for the success style: green marks a
    // transition INTO an active state, and this is the one the whole screen exists for.
    // Checking out is the reverse and stays a ghost — green both ways would say nothing.
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
 * Where the caller's share of this Einsatz goes.
 *
 * Two radios, not one toggle. The choice is between two standing states — the payout comes to you,
 * or it goes to the org treasury — and a button labelled with the OTHER state leaves a member
 * reading „Spenden" unsure whether that is what they have chosen or what they are being offered.
 * The component sheet (ch. 02 §6) draws exactly this pair.
 *
 * Drawn on the bar's own ground, directly above it, so the pair reads as belonging to the sign-up
 * rather than to the tab content it sits over — and with a label, because two bare radios above a
 * button bar do not say what they decide.
 *
 * @param mine the caller's own row.
 * @param state the screen.
 * @param actions what it reports back.
 */
@Composable
private fun PayoutPreference(
    mine: MissionParticipant,
    state: MissionDetailState,
    actions: MissionSignUpActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KrtPalette.Gray4)
                .padding(horizontal = KrtSpacing.md, vertical = KrtSpacing.sm)
                .testTag(MISSION_PAYOUT_TAG)
                .writeAlpha(state.writable),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.mission_detail_payout_label),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
        // Wrapping, not a fixed row: „Auszahlung an mich" and „An die Organisation spenden" are
        // long enough together that a narrow phone would otherwise clip the second label.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md)) {
            KrtRadioRow(
                selected = mine.donating != true,
                onSelect = { if (mine.donating == true) actions.onTogglePayoutPreference() },
                label = stringResource(R.string.mission_detail_payout_self),
                enabled = state.writable,
            )
            KrtRadioRow(
                selected = mine.donating == true,
                onSelect = { if (mine.donating != true) actions.onTogglePayoutPreference() },
                label = stringResource(R.string.mission_detail_payout_org),
                enabled = state.writable,
            )
        }
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
    KrtFieldError(
        text =
            stringResource(
                when (error) {
                    is ApiError.OptimisticLock -> R.string.conflict_inline
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
private fun Modifier.writeAlpha(writable: Boolean): Modifier =
    alpha(if (writable) 1f else DISABLED_WRITE_ALPHA)

/**
 * The tab row — all eight, always.
 *
 * Horizontally scrollable because eight German tab labels do not fit a phone's width, and the
 * design's alternative — truncating them — would make "Teilnehmer" and "Frequenzen"
 * indistinguishable.
 *
 * > **Verwaltung is LOCKED for a non-manager, never hidden** (design ch. 06 artboard 6). An
 * > earlier build hid it, on the argument that a member who does not run this Einsatz is not one
 * > grant away from running it. The designer rejected that on 2026-08-29 and the rule stands as
 * > `REQ-APP-AUTH-013` always stated it: this organisation grants roles by hand, and **a function
 * > nobody sees is never requested**. The app's own Bank had it right all along.
 * >
 * > A tap on the locked tab does **not** open it. It raises the corner-bracket toast naming the
 * > Missions-Manager role, and the active tab stays where it was — which is why the gate lives
 * > here rather than only inside the tab.
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
                    // 45 % alpha PLUS a lock glyph: alpha alone is indistinguishable from a
                    // loading state, which is why the design system pairs the two.
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
 */
@Composable
private fun MissionTabContent(
    state: MissionDetailState,
    detail: MissionDetail,
    onRetryFinances: () -> Unit,
    finances: MissionFinanceActions,
    roster: MissionRosterActions,
    structure: MissionStructureActions,
    admin: MissionAdminActions,
    timeline: MissionTimelineActions,
    members: MissionMemberActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(MISSION_DETAIL_CONTENT_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        when (state.tab) {
            MissionTab.OVERVIEW -> overviewTab(detail)

            MissionTab.PARTICIPANTS -> participantsTab(detail, state.mySignUp, roster)

            MissionTab.UNITS -> unitsTab(detail, structure)

            MissionTab.STEPS -> stepsTab(detail, timeline)

            MissionTab.OBJECTIVES -> objectivesTab(detail, timeline)

            MissionTab.FREQUENCIES -> frequenciesTab(detail, structure)

            MissionTab.FINANCES -> financesTab(state, onRetryFinances, finances)

            // The form is filled by `onTabSelected` as the tab is entered, so it is present
            // whenever this tab is. Null-safe rather than forced: a state restored with the tab
            // already selected must draw an empty tab, never crash the screen.
            MissionTab.ADMIN -> state.adminForm?.let { adminTab(it, state.writable, admin, members) }
        }
    }
}

/**
 * Teilnehmer: the roster with its check-in marks, and — for a manager — the per-row actions the
 * design draws ("Manager sehen die Check-In-Aktion je Zeile; Mitglieder nur den eigenen Status",
 * chapter 06, artboard 2).
 *
 * @param detail the Einsatz.
 * @param mine the caller's own row, drawn in the brand colour so they can find themselves in a
 *   roster of thirty.
 * @param roster what a manager may do to a row, and what to say when they may not.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.participantsTab(
    detail: MissionDetail,
    mine: MissionParticipant?,
    roster: MissionRosterActions,
) {
    if (detail.participants.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_participants) }
        return
    }
    items(detail.participants, key = { it.id }) { participant ->
        ParticipantRow(participant = participant, isMine = participant.id == mine?.id, roster = roster)
    }
}

/**
 * One roster row: who, whether they are in, what they fly, and what they asked to fly.
 *
 * @param participant the row.
 * @param isMine whether it is the caller's own.
 * @param roster the manager's actions and their gate.
 */
@Composable
private fun ParticipantRow(
    participant: MissionParticipant,
    isMine: Boolean,
    roster: MissionRosterActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = participant.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isMine) MaterialTheme.colorScheme.primary else KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            KrtChip(
                text =
                    stringResource(
                        if (participant.checkedIn) {
                            R.string.mission_detail_checked_in
                        } else {
                            R.string.mission_detail_not_checked_in
                        },
                    ),
                tone = if (participant.checkedIn) KrtChipTone.Success else KrtChipTone.Muted,
            )
        }
        participant.role?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
        }
        // The wish is drawn beside the assignment („Wunsch: {{ p.jobWish }}") and is the whole
        // reason a manager can assign anything sensibly. It is shown only when it differs from what
        // is already assigned — repeating the same word twice tells nobody anything.
        participant.desiredJobName
            ?.takeIf { it != participant.role }
            ?.let {
                Text(
                    text = stringResource(R.string.mission_detail_wish, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
        ParticipantManagerActions(participant, roster)
    }
}

/**
 * The row's manager controls: check the member in or out, switch their payout, assign their job.
 *
 * All three render for **everyone** and are locked for a caller who may not manage, per the design
 * ("Ohne Missions-Manager-Rolle rendert das Funktions-Select gesperrt — antippbar, der Toast nennt
 * die Rolle"). Hiding them was the rejected alternative: this organisation grants roles by hand,
 * and a control nobody can see is one nobody asks to be given.
 *
 * @param participant the row.
 * @param roster the actions and the gate.
 */
@Composable
private fun ParticipantManagerActions(
    participant: MissionParticipant,
    roster: MissionRosterActions,
) {
    val gate =
        Gate(
            allowed = roster.canManage,
            reason = stringResource(R.string.gate_role_mission_manager),
            detail = stringResource(R.string.gate_role_mission_manager_detail),
        )
    val (checkInDim, checkInClick) =
        rememberGated(gate, { roster.onCheckIn(participant.id) }, roster.denials)
    val (payoutDim, payoutClick) =
        rememberGated(gate, { roster.onPayout(participant.id) }, roster.denials)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KrtGhostButton(
            text =
                stringResource(
                    if (participant.checkedIn) {
                        R.string.mission_detail_check_out_row
                    } else {
                        R.string.mission_detail_check_in_row
                    },
                ),
            onClick = checkInClick,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = checkInDim.alpha(if (roster.enabled) 1f else DISABLED_WRITE_ALPHA),
            // The server refuses a check-in before the Einsatz has actually started, so the control
            // is not offered before then — the same rule the caller's own check-in follows.
            enabled = roster.enabled && roster.checkInPossible,
        )
        KrtGhostButton(
            text = stringResource(R.string.mission_detail_payout_row),
            onClick = payoutClick,
            iconRes = if (gate.allowed) null else DesignR.drawable.ic_krt_lock,
            modifier = payoutDim.alpha(if (roster.enabled) 1f else DISABLED_WRITE_ALPHA),
            enabled = roster.enabled,
        )
    }
    ParticipantFunctionSelect(participant, gate, roster)
}

/**
 * „Funktion an Bord": the chips a manager assigns from.
 *
 * The catalogue is only read for a caller who may assign, so for everyone else this draws the
 * assignment as a single locked chip rather than an empty row — a locked control with nothing in it
 * would say less than the plain text above it already does.
 *
 * @param participant the row.
 * @param gate whether the caller may assign, and why not.
 * @param roster the actions and the catalogue.
 */
@Composable
private fun ParticipantFunctionSelect(
    participant: MissionParticipant,
    gate: Gate,
    roster: MissionRosterActions,
) {
    if (roster.jobTypes.isEmpty()) {
        return
    }
    Text(
        text = stringResource(R.string.mission_detail_function_label),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
    // The same control as the sign-up sheet's, for the same reason it is a FlowRow there: five
    // Funktionen do not fit one phone line, and a horizontal scroller would hide the ones past the
    // edge behind a gesture nothing announces.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        roster.jobTypes.forEach { jobType ->
            val (dim, click) =
                rememberGated(gate, { roster.onFunction(participant.id, jobType) }, roster.denials)
            KrtFilterChip(
                text = jobType.name,
                selected = participant.plannedJobTypeId == jobType.id,
                onClick = click,
                modifier = dim.alpha(if (roster.enabled) 1f else DISABLED_WRITE_ALPHA),
                // Never `enabled = false`: a chip that cannot be tapped cannot say why it is dim,
                // which is the whole point of the locked pattern (ADR-0011, artboard 14). Offline
                // is the one case that does disable it — there the answer is the connection, not a
                // grant, and the toast would name the wrong thing.
                enabled = roster.enabled,
            )
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
    item { UnitComposer(structure) }
    if (detail.units.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_units) }
        return
    }
    items(detail.units, key = { it.id }) { unit ->
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = unit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f),
                )
                if (unit.highValue) {
                    KrtChip(text = stringResource(R.string.mission_detail_unit_hvu), tone = KrtChipTone.Warning)
                }
                KrtChip(text = pluralStringResource(R.plurals.mission_detail_unit_crew, unit.crew.size, unit.crew.size))
            }
            unit.shipName?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
            }
            unit.responsibleName?.let {
                Text(
                    text = stringResource(R.string.mission_detail_unit_lead, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            }
            UnitRowActions(unit = unit, structure = structure)
            CrewAdd(unit = unit, roster = detail.participants, structure = structure)
            unit.crew.forEach { member ->
                Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = KrtPalette.White,
                    )
                    // The roles are the picker now rather than a joined string: for a manager the
                    // chips ARE the reading of them, selected meaning held. For everybody else they
                    // render locked, which reads the same and says why on a tap.
                    CrewRoleSelect(unitId = unit.id, member = member, structure = structure)
                    StructureRemove(
                        label = stringResource(R.string.mission_struct_remove_crew),
                        structure = structure,
                        onRemove = { structure.onRemoveCrew(unit.id, member.id) },
                    )
                }
            }
            KrtHairlineRule()
        }
    }
}

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
    items(detail.steps, key = { it.id }) { step ->
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f),
                )
                if (step.done) {
                    KrtChip(text = stringResource(R.string.mission_detail_step_done), tone = KrtChipTone.Success)
                }
            }
            step.meta?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
            }
            StepRowActions(
                step = MissionStepEdit(id = step.id, title = step.title, meta = step.meta),
                done = step.done,
                timeline = timeline,
            )
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
    items(detail.objectives, key = { it.id }) { objective ->
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = objective.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KrtPalette.White,
                    modifier = Modifier.weight(1f),
                )
                // A kind the app knows gets its German label; one it does not is shown verbatim.
                // Both halves matter: „SECONDARY" is a wire constant and has no business on a
                // German screen now that the picker has a word for it — and a goal whose kind the
                // app does not recognise must still be marked rather than silently unlabelled.
                objective.kind?.let { KrtChip(text = it.kindLabel()) }
            }
            ObjectiveRowActions(
                objective =
                    MissionObjectiveEdit(
                        id = objective.id,
                        title = objective.title,
                        kind = objective.kind.toObjectiveKind(),
                    ),
                timeline = timeline,
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
    item { FrequencyComposer(structure) }
    if (detail.frequencies.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_frequencies) }
        return
    }
    items(detail.frequencies, key = { it.id }) { frequency ->
        val clipboard = LocalClipboard.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
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
                    }
                    .padding(vertical = KrtSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = frequency.type.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.TextMuted,
                modifier = Modifier.weight(1f),
            )
            // Data tone: the value stays white, never orange — a frequency is a readout, not an
            // action (design system, chip canon).
            KrtChip(text = frequency.value, tone = KrtChipTone.Data)
            StructureRemove(
                label = stringResource(R.string.mission_struct_remove_freq),
                structure = structure,
                onRemove = { structure.onRemoveFrequency(frequency.id) },
            )
        }
    }
}

/**
 * Finanzen: the totals band and the entries, on their own load state.
 *
 * @param phase how far the money has got.
 * @param onRetry retries just this tab.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.financesTab(
    state: MissionDetailState,
    onRetry: () -> Unit,
    actions: MissionFinanceActions,
) {
    when (val phase = state.finances) {
        is MissionFinancesPhase.Idle, is MissionFinancesPhase.Loading -> {
            item { KrtLoadingIndicator(text = stringResource(R.string.mission_detail_tab_finances)) }
        }

        is MissionFinancesPhase.Failed -> {
            item {
                // A refusal here is ordinary — a member may see the Einsatz and not its books — so
                // it gets its own sentence rather than the generic outage copy.
                KrtEmptyState(
                    iconRes = DesignR.drawable.ic_krt_bank,
                    title = stringResource(R.string.mission_detail_tab_finances),
                    message =
                        stringResource(
                            if (phase.error is ApiError.Forbidden) {
                                R.string.mission_detail_finance_forbidden
                            } else {
                                R.string.mission_detail_error_message
                            },
                        ),
                    actionText =
                        if (phase.error is ApiError.Forbidden) null else stringResource(R.string.missions_retry),
                    onAction = if (phase.error is ApiError.Forbidden) null else onRetry,
                )
            }
        }

        is MissionFinancesPhase.Ready -> {
            financeContent(phase.finances, state, actions)
        }
    }
}

/**
 * The Finanzen tab once it has loaded.
 *
 * @param finances the totals and the entries.
 * @param state the screen, for who the caller is and whether a write may be offered.
 * @param actions what the tab reports back.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.financeContent(
    finances: MissionFinances,
    state: MissionDetailState,
    actions: MissionFinanceActions,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            KrtKeyValueRow(
                label = stringResource(R.string.mission_detail_finance_income),
                value = formatSignedAmount(finances.incomeSum.orEmpty(), income = true),
            )
            KrtKeyValueRow(
                label = stringResource(R.string.mission_detail_finance_expense),
                value = formatSignedAmount(finances.expenseSum.orEmpty(), income = false),
            )
            // The net carries no sign of its own: it is a balance, and a leading plus on a positive
            // result would read as a third booking rather than as the sum of the two above it.
            KrtKeyValueRow(
                label = stringResource(R.string.mission_detail_finance_net),
                value = formatAmount(finances.total.orEmpty()),
            )
            KrtHairlineRule()
            // Booking needs a participant to book against, and the only one the app may name is
            // the caller's own. A member who has not signed up is told that rather than shown a
            // button that answers 403.
            if (state.mySignUp == null) {
                Text(
                    text = stringResource(R.string.mission_detail_finance_needs_signup),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.TextMuted,
                )
            } else {
                KrtCtaButton(
                    text = stringResource(R.string.mission_detail_finance_add),
                    onClick = actions.onAdd,
                    modifier =
                        Modifier
                            .testTag(MISSION_FINANCE_ADD_TAG)
                            .writeAlpha(state.bookingPossible),
                    enabled = state.bookingPossible,
                )
            }
            state.error?.let { error -> SignUpError(error = error) }
        }
    }
    if (finances.entries.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_finances) }
        return
    }
    items(finances.entries, key = { it.id }) { entry ->
        FinanceEntryRow(
            entry = entry,
            mine = entry.participantId != null && entry.participantId == state.mySignUp?.id,
            writable = state.writable,
            actions = actions,
        )
    }
}

/**
 * One booking, with the two actions the caller has on their own.
 *
 * Someone else's booking is theirs to change: the server refuses an edit by anyone but the owner
 * or an admin, and the app does not offer what it knows will be refused.
 *
 * @param entry the booking.
 * @param mine whether it hangs off the caller's own sign-up.
 * @param writable whether a write may be offered at all.
 * @param actions what the row reports back.
 */
@Composable
private fun FinanceEntryRow(
    entry: MissionFinanceEntry,
    mine: Boolean,
    writable: Boolean,
    actions: MissionFinanceActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.note.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = KrtPalette.White,
            )
            entry.participantName?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = KrtPalette.TextMuted)
            }
        }
        KrtChip(
            text = formatSignedAmount(entry.amount, entry.income),
            tone = if (entry.income) KrtChipTone.Success else KrtChipTone.Danger,
        )
        if (mine) {
            KrtGhostButton(
                text = stringResource(R.string.mission_detail_finance_edit),
                onClick = { actions.onEdit(entry) },
                modifier = Modifier.testTag(MISSION_FINANCE_EDIT_TAG).writeAlpha(writable),
                enabled = writable,
            )
            KrtGhostButton(
                text = stringResource(R.string.mission_detail_finance_delete),
                onClick = { actions.onDelete(entry) },
                modifier = Modifier.testTag(MISSION_FINANCE_DELETE_TAG).writeAlpha(writable),
                enabled = writable,
            )
        }
    }
}

/**
 * The booking form.
 *
 * The direction is a segment rather than a signed amount: a minus typed into a number field is a
 * character a member can lose, and the sign is what decides whether the Einsatz earned or spent.
 *
 * @param draft what the form holds.
 * @param state the screen, for the save gate and the last refusal.
 * @param actions what it reports back.
 */
@Composable
private fun FinanceEntrySheet(
    draft: FinanceEntryDraft,
    state: MissionDetailState,
    actions: MissionFinanceActions,
) {
    // Artboard 06.4 gives the entry one tone throughout: green for an Einnahme, red for an Ausgabe,
    // on the chosen segment and on the amount alike. The sign is never typed - it IS the segment -
    // and the hint under the field says so, which is why it changes with the choice.
    val tone = if (draft.income) KrtPalette.Success else KrtPalette.Danger
    val amountTone = if (draft.income) KrtPalette.SuccessText else KrtPalette.DangerText
    KrtBottomSheet(
        onDismiss = actions.onDismiss,
        modifier = Modifier.testTag(MISSION_FINANCE_SHEET_TAG),
        title = stringResource(R.string.mission_detail_finance_title),
    ) {
        // The sheet scrolls: with the keyboard up on a small phone the form is taller than what is
        // left of the screen, and a save button that cannot be reached is a form that cannot be
        // submitted.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        ) {
            KrtFieldLabel(text = stringResource(R.string.mission_detail_finance_type))
            KrtSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.mission_detail_finance_type_income),
                        stringResource(R.string.mission_detail_finance_type_expense),
                    ),
                selectedIndex = if (draft.income) 0 else 1,
                onSelect = { actions.onIncome(it == 0) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
                stretch = true,
                activeColor = tone,
                activeContentColor = KrtPalette.White,
            )
            KrtTextField(
                value = draft.amount,
                onValueChange = actions.onAmount,
                label = stringResource(R.string.mission_detail_finance_amount),
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textAlign = TextAlign.End,
                tabularFigures = true,
                valueStyle = MaterialTheme.typography.headlineSmall.copy(color = amountTone),
            )
            Text(
                text =
                    stringResource(
                        if (draft.income) {
                            R.string.mission_detail_finance_amount_hint_income
                        } else {
                            R.string.mission_detail_finance_amount_hint_expense
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = KrtPalette.TextMuted,
            )
            KrtTextField(
                value = draft.note,
                onValueChange = actions.onNote,
                label = stringResource(R.string.mission_detail_finance_note),
                placeholder = stringResource(R.string.mission_detail_finance_note_hint),
                enabled = !state.saving,
            )
            state.error?.let { error -> SignUpError(error = error) }
            Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
                KrtGhostButton(
                    text = stringResource(R.string.personal_inventory_cancel),
                    onClick = actions.onDismiss,
                    enabled = !state.saving,
                )
                KrtCtaButton(
                    text = stringResource(R.string.personal_inventory_save),
                    onClick = actions.onSave,
                    iconRes = DesignR.drawable.ic_krt_save,
                    modifier = Modifier.testTag(MISSION_FINANCE_SAVE_TAG),
                    enabled = draft.submittable && state.writable,
                )
            }
        }
    }
}

/**
 * The line a tab shows when the server had nothing for it.
 *
 * @param messageRes what to say.
 */
@Composable
private fun EmptyTab(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        color = KrtPalette.TextMuted,
        modifier = Modifier.padding(vertical = KrtSpacing.md),
    )
}

/**
 * The whole screen when the Einsatz could not be read.
 *
 * Three different sentences, because these are three different facts: refused, gone, or broken.
 * One generic message for all of them would tell a member to try again on an Einsatz they will
 * never be allowed to see.
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
        modifier = Modifier.fillMaxSize().padding(KrtSpacing.lg),
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
    // One refusal at a time, owned by the screen: the roster is a LazyColumn, and a toast owned by
    // a row would vanish the moment that row scrolled out from under it.
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
                onChange = viewModel.structure::change,
                onAddUnit = viewModel.structure::addUnit,
                onRemoveUnit = viewModel.structure::removeUnit,
                onAddFrequency = viewModel.structure::addFrequency,
                onRemoveFrequency = viewModel.structure::removeFrequency,
                onConfirmRemoveManager = viewModel.structure::confirmRemoveManager,
                onDismissRemoveManager = viewModel.structure::dismissRemoveManager,
                onRemoveCrew = viewModel.structure::removeCrew,
                onEditUnit = { unit ->
                    // The rename reuses the composer at the top of the tab, filled from the unit —
                    // including its version, because the write is a replace and a guessed counter
                    // would overwrite a concurrent rename instead of colliding with it.
                    viewModel.structure.change {
                        it.copy(
                            unitName = unit.name,
                            unitHighValue = unit.highValue,
                            editingUnitId = unit.id,
                            editingUnitVersion = unit.version,
                        )
                    }
                },
                onSaveUnit = { unitId, version ->
                    val draft = state.structure
                    viewModel.structure.updateUnit(unitId, draft.unitName, draft.unitHighValue, version)
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
                sorting = state.timeline.sorting,
                denials = denials,
                onChange = viewModel.timeline::change,
                onCompose = viewModel.timeline::compose,
                onSaveStep = viewModel.timeline::saveStep,
                onEditStep = viewModel.timeline::editStep,
                onToggleStep = viewModel.timeline::toggleStep,
                onRemoveStep = viewModel.timeline::removeStep,
                onMoveStep = viewModel.timeline::moveStep,
                onSaveObjective = viewModel.timeline::saveObjective,
                onEditObjective = viewModel.timeline::editObjective,
                onRemoveObjective = viewModel.timeline::removeObjective,
                onMoveObjective = viewModel.timeline::moveObjective,
                onToggleSorting = viewModel.timeline::toggleSorting,
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
 * @return the count the tab chip shows, or `null` for a tab whose content is not a list — Übersicht
 *   is prose and Finanzen is loaded separately, so a figure there would be either meaningless or a
 *   promise the screen cannot keep before the second read lands.
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
 * „Anmelden" — the sheet that collects what a sign-up carries with it.
 *
 * One tap used to do it. Design ch. 06, artboard 3 makes it a sheet because two answers belong to
 * the moment of signing up and are awkward to find afterwards: where the share goes, and which
 * function the member would like on board.
 *
 * **The function is a wish, not a claim.** The artboard says so in as many words — „Optional —
 * Wunsch (desired), keine Zusage" — and the sheet repeats it under the chips, because a row of
 * pickable roles reads like an assignment unless something says otherwise. The mission's leadership
 * sets the planned function on the participants tab; this only records what was asked for.
 *
 * A refusal keeps the sheet and everything in it: nothing was written, and re-answering two
 * questions to retry is a charge for the server's reply.
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
            modifier = Modifier.fillMaxWidth().padding(KrtSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.md),
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
                // FlowRow: five Funktionen do not fit one phone line, and a horizontal scroller
                // would hide the ones past the edge behind a gesture nothing announces.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
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
 * A section heading inside the sign-up sheet.
 *
 * Neither [KrtFieldLabel][de.greluc.krt.profit.basetool.android.core.designsystem.component
 * .KrtFieldLabel], which is sentence-case body text for a single field, nor
 * [KrtSectionTitle][de.greluc.krt.profit.basetool.android.core.designsystem.component
 * .KrtSectionTitle], which fills the rest of its line with a rule. The artboard's sheet headings
 * are short uppercase labels with nothing after them.
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
