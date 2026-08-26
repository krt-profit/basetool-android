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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import de.greluc.krt.profit.basetool.android.ui.OfflineBand
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
    Column(modifier = modifier.fillMaxSize()) {
        when {
            detail != null -> {
                if (!state.online) {
                    OfflineBand()
                }
                MissionDetailHead(detail = detail)
                MissionTabRow(
                    selected = state.tab,
                    detail = state.detail,
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
                    )
                }
                // Design ch. 06: ONE filled CTA, bottom-anchored. It sat between the facts and the
                // tab row, where the screen's primary action scrolled away with the briefing and
                // read as one more fact about the Einsatz.
                SignUpBar(state = state, actions = actions)
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
}

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
            if (mine != null) {
                SignUpRowActions(mine = mine, state = state, actions = actions)
            }
        }
    }
}

/**
 * The two actions that need a sign-up to act on.
 *
 * @param mine the caller's own row.
 * @param state the screen.
 * @param actions what it reports back.
 */
@Composable
private fun SignUpRowActions(
    mine: MissionParticipant,
    state: MissionDetailState,
    actions: MissionSignUpActions,
) {
    if (state.checkInPossible) {
        // Check-In is the example the button ladder gives for the success style: green marks a
        // transition INTO an active state, and this is the one the whole screen exists for.
        // Checking out is the reverse and stays a ghost — green both ways would say nothing.
        if (mine.checkedIn) {
            KrtGhostButton(
                text = stringResource(R.string.mission_detail_check_out),
                onClick = actions.onToggleCheckIn,
                iconRes = DesignR.drawable.ic_krt_logout,
                modifier = Modifier.testTag(MISSION_CHECK_IN_TAG).writeAlpha(state.writable),
                enabled = state.writable,
            )
        } else {
            KrtSuccessButton(
                text = stringResource(R.string.mission_detail_check_in),
                onClick = actions.onToggleCheckIn,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.testTag(MISSION_CHECK_IN_TAG).writeAlpha(state.writable),
                enabled = state.writable,
            )
        }
    }
    // Two radios, not one toggle. The choice is between two standing states — the payout comes to
    // you, or it goes to the org treasury — and a button labelled with the OTHER state leaves a
    // member reading "Spenden" unsure whether that is what they have chosen or what they are being
    // offered. The component sheet (ch. 02 §6) draws exactly this pair.
    Row(
        modifier = Modifier.testTag(MISSION_PAYOUT_TAG).writeAlpha(state.writable),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/**
 * What the last write returned, in the app's own words.
 *
 * @param error the refusal.
 */
@Composable
private fun SignUpError(error: ApiError) {
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
 * The tab row.
 *
 * Horizontally scrollable because seven German tab labels do not fit a phone's width, and the
 * design's alternative — truncating them — would make "Teilnehmer" and "Frequenzen" indistinguishable.
 *
 * @param selected which tab is showing.
 * @param onTabSelected a tab was picked.
 */
@Composable
private fun MissionTabRow(
    selected: MissionTab,
    detail: MissionDetail?,
    onTabSelected: (MissionTab) -> Unit,
) {
    KrtPageTabs(
        tabs =
            MissionTab.entries.map { tab ->
                KrtPageTab(label = stringResource(tab.labelRes()), count = detail?.let(tab::countIn))
            },
        selectedIndex = MissionTab.entries.indexOf(selected),
        onSelect = { onTabSelected(MissionTab.entries[it]) },
        modifier = Modifier.testTag(MISSION_DETAIL_TABS_TAG),
    )
}

/**
 * The selected tab's content.
 *
 * @param state everything the screen knows.
 * @param detail the Einsatz, already known to be present.
 * @param onRetryFinances the Finanzen tab's retry.
 */
@Composable
private fun MissionTabContent(
    state: MissionDetailState,
    detail: MissionDetail,
    onRetryFinances: () -> Unit,
    finances: MissionFinanceActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(MISSION_DETAIL_CONTENT_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        when (state.tab) {
            MissionTab.OVERVIEW -> overviewTab(detail)
            MissionTab.PARTICIPANTS -> participantsTab(detail, state.mySignUp)
            MissionTab.UNITS -> unitsTab(detail)
            MissionTab.STEPS -> stepsTab(detail)
            MissionTab.OBJECTIVES -> objectivesTab(detail)
            MissionTab.FREQUENCIES -> frequenciesTab(detail)
            MissionTab.FINANCES -> financesTab(state, onRetryFinances, finances)
        }
    }
}

/**
 * Teilnehmer: the roster with its check-in marks.
 *
 * @param detail the Einsatz.
 * @param mine the caller's own row, drawn in the brand colour so they can find themselves in a
 *   roster of thirty.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.participantsTab(
    detail: MissionDetail,
    mine: MissionParticipant?,
) {
    if (detail.participants.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_participants) }
        return
    }
    items(detail.participants, key = { it.id }) { participant ->
        Column(verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = participant.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (participant.id == mine?.id) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            KrtPalette.White
                        },
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
        }
    }
}

/**
 * Einheiten: each unit, its ship and its crew.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.unitsTab(detail: MissionDetail) {
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
            unit.crew.forEach { member ->
                Text(
                    text =
                        listOf(member.name, member.roles.joinToString(" · ")).filter { it.isNotBlank() }
                            .joinToString(" — "),
                    style = MaterialTheme.typography.bodySmall,
                    color = KrtPalette.White,
                )
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
private fun androidx.compose.foundation.lazy.LazyListScope.stepsTab(detail: MissionDetail) {
    if (detail.steps.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_steps) }
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
        }
    }
}

/**
 * Ziele: the objectives with the server's own classification.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.objectivesTab(detail: MissionDetail) {
    if (detail.objectives.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_objectives) }
        return
    }
    items(detail.objectives, key = { it.id }) { objective ->
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
            // Verbatim: this build does not interpret the kind, and an unrecognised one shown as
            // it came beats a goal with no marking at all.
            objective.kind?.let { KrtChip(text = it) }
        }
    }
}

/**
 * Frequenzen: tap-to-copy, as the design specifies.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.frequenciesTab(detail: MissionDetail) {
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
                    color = KrtPalette.Gray2,
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
                color = KrtPalette.Gray2,
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
