/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.common.formatAmount
import de.greluc.krt.profit.basetool.android.common.formatSignedAmount
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtEmptyState
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFieldError
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtFilterChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtHairlineRule
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtKeyValueRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtLoadingIndicator
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOrgBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusBadge
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtStatusTone
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.network.ApiError
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
 * @param onRetryFinances the Finanzen tab's retry.
 * @param actions what the caller may do to their own sign-up.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    state: MissionDetailState,
    onTabSelected: (MissionTab) -> Unit,
    onRefresh: () -> Unit,
    onRetryFinances: () -> Unit,
    actions: MissionSignUpActions,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    // Bound so the smart cast below survives; `state.phase` is a property read and Kotlin will not
    // narrow it across the branch.
    val phase = state.phase
    Column(modifier = modifier.fillMaxSize()) {
        when {
            detail != null -> {
                if (!state.online) {
                    OfflineBand()
                }
                MissionDetailHead(detail = detail)
                SignUpBar(state = state, actions = actions)
                MissionTabRow(selected = state.tab, onTabSelected = onTabSelected)
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MissionTabContent(
                        state = state,
                        detail = detail,
                        onRetryFinances = onRetryFinances,
                    )
                }
            }

            phase is MissionDetailPhase.Failed -> {
                MissionDetailFailure(error = phase.error)
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
 */
data class MissionSignUpActions(
    val onToggleSignUp: () -> Unit,
    val onToggleCheckIn: () -> Unit,
    val onTogglePayoutPreference: () -> Unit,
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        state.error?.let { error -> SignUpError(error = error) }
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            KrtCtaButton(
                text =
                    stringResource(
                        if (mine == null) {
                            R.string.mission_detail_sign_up
                        } else {
                            R.string.mission_detail_withdraw
                        },
                    ),
                onClick = actions.onToggleSignUp,
                modifier = Modifier.testTag(MISSION_SIGN_UP_TAG).writeAlpha(state.writable),
                enabled = state.writable,
            )
            if (mine != null) {
                SignUpRowActions(mine = mine, state = state, actions = actions)
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
            )
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
        KrtGhostButton(
            text =
                stringResource(
                    if (mine.checkedIn) {
                        R.string.mission_detail_check_out
                    } else {
                        R.string.mission_detail_check_in
                    },
                ),
            onClick = actions.onToggleCheckIn,
            modifier = Modifier.testTag(MISSION_CHECK_IN_TAG).writeAlpha(state.writable),
            enabled = state.writable,
        )
    }
    KrtGhostButton(
        text =
            stringResource(
                if (mine.donating == true) {
                    R.string.mission_detail_take_payout
                } else {
                    R.string.mission_detail_donate
                },
            ),
        onClick = actions.onTogglePayoutPreference,
        modifier = Modifier.testTag(MISSION_PAYOUT_TAG).writeAlpha(state.writable),
        enabled = state.writable,
    )
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
                    is ApiError.OptimisticLock -> R.string.conflict_body
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
 * The sticky head: title, status, org badge and the fact band.
 *
 * @param detail the Einsatz.
 */
@Composable
private fun MissionDetailHead(detail: MissionDetail) {
    val zone = remember { ZoneId.systemDefault() }
    val time = remember(zone) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(KrtSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.titleLarge,
                color = KrtPalette.White,
                modifier = Modifier.weight(1f),
            )
            KrtStatusBadge(text = detail.statusLabel(), tone = detail.statusTone())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm)) {
            detail.orgUnitShorthand?.takeIf { it.isNotBlank() }?.let { KrtOrgBadge(text = it) }
            detail.operationName?.let { KrtChip(text = it, tone = KrtChipTone.Info) }
        }
        Text(
            text =
                pluralStringResource(
                    R.plurals.mission_detail_signups,
                    detail.registeredParticipants,
                    detail.registeredParticipants,
                    detail.checkedInParticipants,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
        // Only the facts the server actually gave. An empty "Ort —" row states nothing and costs a
        // line of a head that has to stay small enough to leave the content room.
        val facts =
            buildList {
                detail.meetingTime?.let { add(stringResource(R.string.mission_detail_fact_meeting) to time.format(it)) }
                detail.plannedStartTime?.let {
                    add(stringResource(R.string.mission_detail_fact_join) to time.format(it))
                }
                detail.plannedEndTime?.let { add(stringResource(R.string.mission_detail_fact_end) to time.format(it)) }
                detail.meetingPoint?.let { add(stringResource(R.string.mission_detail_fact_place) to it) }
                detail.partyLeadName?.let { add(stringResource(R.string.mission_detail_fact_lead) to it) }
            }
        facts.forEach { (label, value) -> KrtKeyValueRow(label = label, value = value) }
        KrtHairlineRule()
    }
}

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
    onTabSelected: (MissionTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = KrtSpacing.md)
                .testTag(MISSION_DETAIL_TABS_TAG),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
    ) {
        MissionTab.entries.forEach { tab ->
            KrtFilterChip(
                text = stringResource(tab.labelRes()),
                selected = tab == selected,
                onClick = { onTabSelected(tab) },
            )
        }
    }
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
            MissionTab.FINANCES -> financesTab(state.finances, onRetryFinances)
        }
    }
}

/**
 * Übersicht: the briefing text, or the note that it is members-only.
 *
 * @param detail the Einsatz.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.overviewTab(detail: MissionDetail) {
    item {
        KrtSectionTitle(text = stringResource(R.string.mission_detail_description))
    }
    item {
        // An outsider read carries no description (ADR-0034). Saying so beats a blank section,
        // which reads as an Einsatz nobody bothered to describe.
        Text(
            text = detail.description ?: stringResource(R.string.mission_detail_description_hidden),
            style = MaterialTheme.typography.bodyMedium,
            color = if (detail.description != null) KrtPalette.White else KrtPalette.TextMuted,
        )
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
    phase: MissionFinancesPhase,
    onRetry: () -> Unit,
) {
    when (phase) {
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
            financeContent(phase.finances)
        }
    }
}

/**
 * The Finanzen tab once it has loaded.
 *
 * @param finances the totals and the entries.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.financeContent(finances: MissionFinances) {
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
        }
    }
    if (finances.entries.isEmpty()) {
        item { EmptyTab(R.string.mission_detail_empty_finances) }
        return
    }
    items(finances.entries, key = { it.id }) { entry ->
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
private fun MissionDetail.statusLabel(): String =
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
private fun MissionDetail.statusTone(): KrtStatusTone =
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
        onRetryFinances = viewModel::onRetryFinances,
        actions =
            MissionSignUpActions(
                onToggleSignUp = viewModel::onToggleSignUp,
                onToggleCheckIn = viewModel::onToggleCheckIn,
                onTogglePayoutPreference = viewModel::onTogglePayoutPreference,
            ),
        modifier = modifier,
    )
}
