/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDateTimeField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPanelHeader
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Verwaltung tab's content. */
const val MISSION_ADMIN_SHEET_TAG: String = "mission-admin-sheet"

/** Test handle for the „Einsatz läuft jetzt" action. */
const val MISSION_ADMIN_START_TAG: String = "mission-admin-start"

/** A multi-line briefing field: a one-line box visually truncates prose (design ch. 06 artboard 7). */
private val DESCRIPTION_MIN = 88.dp

/** How many lines the briefing field shows before it scrolls. */
private const val MIN_BRIEFING_LINES = 3

/**
 * What the Verwaltung tab can do.
 *
 * Opening and closing are absent on purpose: the form's lifecycle belongs to the tab, so
 * `MissionDetailViewModel.onTabSelected` fills it on arrival and clears it on departure.
 *
 * @property onChange a field changed, naming the section so its head can say „Geändert".
 * @property onToggle a section was folded open or shut.
 * @property onSave one section is to be saved.
 * @property onAskStart „Einsatz läuft jetzt" was pressed — opens the confirmation.
 * @property onStart the confirmation was accepted.
 * @property onDismissStart the confirmation was declined.
 * @property onCorrectStart the started time is to be corrected.
 * @property onCancelCorrectStart that correction was abandoned.
 * @property onKeepMine a conflict is resolved by re-writing the member's own version.
 * @property onReload a conflict is resolved by taking the server's.
 */
data class MissionAdminActions(
    val onChange: (MissionSection, (MissionAdminForm) -> MissionAdminForm) -> Unit,
    val onToggle: (MissionSection) -> Unit,
    val onSave: (MissionSection) -> Unit,
    val onAskStart: () -> Unit,
    val onStart: () -> Unit,
    val onDismissStart: () -> Unit,
    val onCorrectStart: () -> Unit,
    val onCancelCorrectStart: () -> Unit,
    val onKeepMine: () -> Unit,
    val onReload: () -> Unit,
)

/**
 * Verwaltung: the Einsatz itself, as four folded sections.
 *
 * **Composition ratified 2026-08-29** (design ch. 06 artboards 7–12). It shipped first as a flat
 * stack — four sections and three saves inside one item, with a single error slot at the foot. The
 * drawing that came back makes each section a panel header (Kern open, the rest closed, no
 * accordion), puts its save **inside** it, and gives its head a state chip, so a folded section
 * still says whether it is started, changed, saving, saved or in conflict.
 *
 * The tab is reachable only for a caller the server says may manage; `MissionTabRow` draws it
 * **locked rather than hidden** for everyone else.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now — online, and nothing already in flight.
 * @param actions what the tab can do.
 * @param members the one picker behind the party lead, the managers and „Teilnehmer hinzufügen".
 */
fun LazyListScope.adminTab(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
    members: MissionMemberActions,
) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(MISSION_ADMIN_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
        ) {
            Hint(text = stringResource(R.string.mission_admin_section_hint))
            AdminSection(MissionSection.CORE, form, actions) { CoreFields(form, writable, actions) }
            AdminSection(MissionSection.SCHEDULE, form, actions) { ScheduleFields(form, writable, actions) }
            AdminSection(MissionSection.FLAGS, form, actions) { FlagsFields(form, writable, actions) }
            AdminSection(MissionSection.PEOPLE, form, actions) {
                Hint(text = stringResource(R.string.mission_member_hint))
                MemberSection(members = members)
            }
            form.error?.let { SignUpError(error = it) }
        }
    }
}

/**
 * One folded section: its head, its state chip, and its body when open.
 *
 * @param section which one.
 * @param form the form, for the fold and the state.
 * @param actions what the tab can do.
 * @param body what the section holds.
 */
@Composable
private fun AdminSection(
    section: MissionSection,
    form: MissionAdminForm,
    actions: MissionAdminActions,
    body: @Composable () -> Unit,
) {
    val open = form.expanded.contains(section)
    Column(modifier = Modifier.fillMaxWidth()) {
        KrtPanelHeader(
            title = stringResource(section.titleRes()),
            expanded = open,
            onToggle = { actions.onToggle(section) },
            stateChip = { SectionStateChip(section, form) },
        )
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
            ) {
                body()
            }
        }
    }
}

/**
 * What a section's head says about itself.
 *
 * Two kinds of chip share the slot: the section's **standing** value (started or not, internal or
 * open) and its **write** state (saving, saved, changed, conflict). The write state wins while it
 * lasts, because it is the newer fact.
 *
 * @param section which one.
 * @param form the form.
 */
@Composable
private fun SectionStateChip(
    section: MissionSection,
    form: MissionAdminForm,
) {
    when (form.stateOf(section)) {
        MissionSectionState.SAVING -> {
            KrtChip(text = stringResource(R.string.mission_admin_state_saving), tone = KrtChipTone.Muted)
        }

        MissionSectionState.SAVED -> {
            KrtChip(
                text = stringResource(R.string.mission_admin_state_saved, form.savedAt[section].orEmpty()),
                tone = KrtChipTone.Success,
            )
        }

        MissionSectionState.DIRTY -> {
            KrtChip(text = stringResource(R.string.mission_admin_state_dirty), tone = KrtChipTone.Warning)
        }

        MissionSectionState.CONFLICT -> {
            KrtChip(text = stringResource(R.string.mission_admin_state_conflict), tone = KrtChipTone.Danger)
        }

        MissionSectionState.IDLE -> {
            StandingChip(section, form)
        }
    }
}

/**
 * The section's own standing value, shown when no write state overrides it.
 *
 * @param section which one.
 * @param form the form.
 */
@Composable
private fun StandingChip(
    section: MissionSection,
    form: MissionAdminForm,
) {
    // Only two sections have a standing value: Kern has none worth a chip, and Personen's counts
    // live on its own rows. Written as two guards rather than an exhaustive `when` with an empty
    // branch, which is an unused expression the compiler rejects under -Werror.
    if (section == MissionSection.SCHEDULE) {
        KrtChip(
            text =
                if (form.started) {
                    stringResource(R.string.mission_admin_running_since, form.actualStart.toKrtStartedAt())
                } else {
                    stringResource(R.string.mission_admin_not_started_chip)
                },
            tone = if (form.started) KrtChipTone.Success else KrtChipTone.Muted,
        )
    }
    if (section == MissionSection.FLAGS) {
        KrtChip(
            text =
                stringResource(
                    if (form.internal) {
                        R.string.mission_admin_visibility_internal
                    } else {
                        R.string.mission_admin_visibility_open
                    },
                ),
            tone = KrtChipTone.Muted,
        )
    }
}

/**
 * Kern: what the Einsatz is called, what it is about, and where everyone gathers.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun CoreFields(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    KrtTextField(
        value = form.name,
        onValueChange = { v -> actions.onChange(MissionSection.CORE) { it.copy(name = v) } },
        label = stringResource(R.string.mission_admin_name),
        enabled = writable,
    )
    KrtTextField(
        value = form.description,
        onValueChange = { v -> actions.onChange(MissionSection.CORE) { it.copy(description = v) } },
        modifier = Modifier.heightIn(min = DESCRIPTION_MIN),
        label = stringResource(R.string.mission_admin_description),
        enabled = writable,
        minLines = MIN_BRIEFING_LINES,
    )
    KrtTextField(
        value = form.meetingPoint,
        onValueChange = { v -> actions.onChange(MissionSection.CORE) { it.copy(meetingPoint = v) } },
        label = stringResource(R.string.mission_admin_meeting_point),
        enabled = writable,
    )
    SectionSave(R.string.mission_admin_save_core, MissionSection.CORE, form, writable, actions.onSave)
}

/**
 * Zeitplan: three planned times as date/time pairs, and the start as a state plus an action.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun ScheduleFields(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    ScheduleTime(
        labelRes = R.string.mission_admin_meeting_time,
        date = form.meetingDate,
        time = form.meetingClock,
        writable = writable,
        onDate = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(meetingDate = v) } },
        onTime = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(meetingClock = v) } },
    )
    ScheduleTime(
        labelRes = R.string.mission_admin_planned_start,
        date = form.plannedStartDate,
        time = form.plannedStartClock,
        writable = writable,
        onDate = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(plannedStartDate = v) } },
        onTime = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(plannedStartClock = v) } },
    )
    ScheduleTime(
        labelRes = R.string.mission_admin_planned_end,
        date = form.plannedEndDate,
        time = form.plannedEndClock,
        writable = writable,
        onDate = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(plannedEndDate = v) } },
        onTime = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(plannedEndClock = v) } },
    )
    ActualStart(form, writable, actions)
    SectionSave(R.string.mission_admin_save_schedule, MissionSection.SCHEDULE, form, writable, actions.onSave)
}

/**
 * One planned time as the drawn date/time pair.
 *
 * @param labelRes what the pair means.
 * @param date the date half.
 * @param time the time half.
 * @param writable whether a write may run right now.
 * @param onDate the date changed.
 * @param onTime the time changed.
 */
@Composable
private fun ScheduleTime(
    labelRes: Int,
    date: String,
    time: String,
    writable: Boolean,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
) {
    KrtDateTimeField(
        label = stringResource(labelRes),
        date = date,
        time = time,
        onDate = onDate,
        onTime = onTime,
        enabled = writable,
    )
}

/**
 * „Tatsächlicher Start" — a state line and an action, never a text field.
 *
 * Design ch. 06 artboard 8 names the reason: a free field for the start timestamp is what made the
 * one action the whole screen exists for look like bookkeeping. Before the start, the line says
 * check-in is locked for everyone and the filled CTA offers to start; after it, the line says since
 * when, and a ghost offers to correct it.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun ActualStart(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    Text(
        text =
            if (form.started) {
                stringResource(R.string.mission_admin_running_since, form.actualStart.toKrtStartedAt())
            } else {
                stringResource(R.string.mission_admin_not_started)
            },
        style = MaterialTheme.typography.bodySmall,
        color = if (form.started) KrtPalette.Gray1 else KrtPalette.TextMuted,
    )
    when {
        form.started && form.correctingStart -> {
            KrtDateTimeField(
                label = stringResource(R.string.mission_admin_correct_start),
                date = form.correctStartDate,
                time = form.correctStartClock,
                onDate = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(correctStartDate = v) } },
                onTime = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(correctStartClock = v) } },
                enabled = writable,
                // A corrected actual start records something that already happened, so „liegt in
                // der Vergangenheit" would fire on every legitimate correction.
                warnPast = false,
            )
            KrtGhostButton(
                text = stringResource(R.string.mission_timeline_cancel),
                onClick = actions.onCancelCorrectStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = writable,
            )
        }

        form.started -> {
            KrtGhostButton(
                text = stringResource(R.string.mission_admin_correct_start),
                onClick = actions.onCorrectStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = writable,
            )
        }

        else -> {
            // The one filled action of this tab. It asks first (artboard 9): the step frees
            // check-in for everyone, which is consequential — but correctable, so it is a standard
            // modal rather than a danger one.
            KrtCtaButton(
                text = stringResource(R.string.mission_admin_start_now),
                onClick = actions.onAskStart,
                iconRes = DesignR.drawable.ic_krt_check,
                modifier = Modifier.fillMaxWidth().testTag(MISSION_ADMIN_START_TAG),
                enabled = writable && form.saving == null,
            )
        }
    }
}

/**
 * Sichtbarkeit: one checkbox, naming both of its sides.
 *
 * A yes/no is **not** one-of-N, so it is a square checkbox — the round radio is the design system's
 * only circular element and stays reserved for a real choice, such as the payout preference
 * (ch. 06 artboard 10).
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun FlagsFields(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    KrtCheckboxRow(
        checked = form.internal,
        onCheckedChange = { v -> actions.onChange(MissionSection.FLAGS) { it.copy(internal = v) } },
        label = stringResource(R.string.mission_admin_internal),
        enabled = writable,
    )
    // A flag whose off-state is not named gets guessed at.
    Hint(text = stringResource(R.string.mission_admin_internal_hint))
    SectionSave(R.string.mission_admin_save_flags, MissionSection.FLAGS, form, writable, actions.onSave)
}

/**
 * One section's save, inside the section it saves.
 *
 * @param labelRes what it says.
 * @param section which section it writes.
 * @param form the form, for the in-flight state.
 * @param writable whether a write may run right now.
 * @param onSave raises the write.
 */
@Composable
private fun SectionSave(
    labelRes: Int,
    section: MissionSection,
    form: MissionAdminForm,
    writable: Boolean,
    onSave: (MissionSection) -> Unit,
) {
    KrtGhostButton(
        text = stringResource(labelRes),
        onClick = { onSave(section) },
        iconRes = DesignR.drawable.ic_krt_check,
        modifier = Modifier.fillMaxWidth(),
        // Every save is disabled while ANY of them is in flight: they share one form, and a second
        // write launched against a counter the first is about to bump is a 409 the member caused by
        // being quick. That is a WAITING lock, not a permission one — hence no lock glyph, which
        // the design system reserves for a missing right (artboard 10).
        enabled = writable && form.saving == null,
    )
}

/**
 * What a section is called on its head.
 *
 * @return the string resource.
 */
private fun MissionSection.titleRes(): Int =
    when (this) {
        MissionSection.CORE -> R.string.mission_admin_core
        MissionSection.SCHEDULE -> R.string.mission_admin_schedule
        MissionSection.FLAGS -> R.string.mission_admin_flags
        MissionSection.PEOPLE -> R.string.mission_admin_people
    }

/**
 * A muted line of explanation between the controls it is about.
 *
 * `TextMuted`, never `Gray2`: #646464 is the hairline value and fails AA as small text on the
 * section ground — the same helper in `MissionStructureUi` had it right, and this one did not
 * (design README correction 16).
 *
 * @param text what it says.
 */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}
