/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCard
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCardVariant
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCheckboxRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChip
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtChipTone
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtDateTimeField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtIcon
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtOption
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtPanelHeader
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSelectField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtToLocalDate
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtToLocalTime
import de.greluc.krt.profit.basetool.android.core.designsystem.component.krtUppercase
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.ui.FieldLimits
import java.time.Duration
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
 * @property onAskLifecycle the badge's lifecycle action was pressed — opens the confirmation.
 * @property onConfirmLifecycle the confirmation was accepted.
 * @property onDismissLifecycle the confirmation was declined.
 * @property onCorrectStart the started time is to be corrected.
 * @property onCancelCorrectStart that correction was abandoned.
 * @property onEndMission the Einsatz is to be ended, which opens the time pair.
 * @property onCancelEndMission that was abandoned.
 * @property onKeepMine a conflict is resolved by re-writing the member's own version.
 * @property onReload a conflict is resolved by taking the server's.
 */
data class MissionAdminActions(
    val onChange: (MissionSection, (MissionAdminForm) -> MissionAdminForm) -> Unit,
    val onToggle: (MissionSection) -> Unit,
    val onSave: (MissionSection) -> Unit,
    val onAskLifecycle: () -> Unit,
    val onConfirmLifecycle: () -> Unit,
    val onDismissLifecycle: () -> Unit,
    val onCorrectStart: () -> Unit,
    val onCancelCorrectStart: () -> Unit,
    val onEndMission: () -> Unit = {},
    val onCancelEndMission: () -> Unit = {},
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KrtSpacing.s16)
                    .testTag(MISSION_ADMIN_SHEET_TAG),
            // 10 dp between the cards and a 16 dp screen margin — design ch. 18 §3 (E4).
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.s10),
        ) {
            Hint(text = stringResource(R.string.mission_admin_section_hint))
            AdminSection(MissionSection.CORE, form, actions) { CoreFields(form, form.writes(writable, it), actions) }
            AdminSection(MissionSection.SCHEDULE, form, actions) {
                ScheduleFields(form, form.writes(writable, it), actions)
            }
            AdminSection(MissionSection.FLAGS, form, actions) { FlagsFields(form, form.writes(writable, it), actions) }
            AdminSection(MissionSection.PEOPLE, form, actions) {
                Hint(text = stringResource(R.string.mission_member_hint))
                MemberSection(members = members)
            }
            // Only a refusal that belongs to no section lands here; a section's own is drawn in
            // that section, where the member who caused it is looking.
            form.error?.takeIf { form.errorSection == null }?.let { SignUpError(error = it) }
        }
    }
}

/**
 * One folded section: a **card** with a panel header, its state chip, and its body when open.
 *
 * A card rather than a HUD box or a bare stack (design ch. 18 §3, E4). The HUD box stays reserved
 * for emphasis blocks — four of them side by side emphasise nothing.
 *
 * @param section which one.
 * @param form the form, for the fold and the state.
 * @param actions what the tab can do.
 * @param body what the section holds; it is handed **this** section, so it can lock its own fields
 *   without waiting on a write somewhere else in the tab.
 */
@Composable
private fun AdminSection(
    section: MissionSection,
    form: MissionAdminForm,
    actions: MissionAdminActions,
    body: @Composable (MissionSection) -> Unit,
) {
    val open = form.expanded.contains(section)
    KrtCard(modifier = Modifier.fillMaxWidth(), variant = KrtCardVariant.Flush) {
        KrtPanelHeader(
            title = stringResource(section.titleRes()),
            expanded = open,
            onToggle = { actions.onToggle(section) },
            stateChip = { SectionStateChip(section, form) },
            busy = form.saving == section,
        )
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KrtSpacing.s16),
                verticalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
            ) {
                body(section)
                form.error?.takeIf { form.errorSection == section }?.let { SignUpError(error = it) }
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
    // Three of the four have a standing value; Kern has none worth a chip. Written as guards
    // rather than an exhaustive `when` with an empty branch, which is an unused expression the
    // compiler rejects under -Werror.
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
    if (section == MissionSection.PEOPLE) {
        // „Ein Kopf, der nur ein Wort zeigt, macht das Aufklappen zur Pflicht — dann ist die
        // Faltung falsch" (ch. 02 §10). Artboard 7 draws Leitung · Manager · Teilnehmer as one
        // count; this head was the only one folding over nothing.
        KrtChip(
            text =
                stringResource(
                    R.string.mission_admin_people_counts,
                    if (form.partyLeadSet) 1 else 0,
                    form.managerCount,
                    form.participantCount,
                ),
            tone = KrtChipTone.Data,
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
        onValueChange = { v ->
            actions.onChange(MissionSection.CORE) { it.copy(name = v.take(FieldLimits.NAME)) }
        },
        label = stringResource(R.string.mission_admin_name),
        enabled = writable,
    )
    KrtTextField(
        value = form.description,
        onValueChange = { v ->
            actions.onChange(MissionSection.CORE) {
                it.copy(description = v.take(FieldLimits.DESCRIPTION))
            }
        },
        modifier = Modifier.heightIn(min = DESCRIPTION_MIN),
        label = stringResource(R.string.mission_admin_description),
        enabled = writable,
        minLines = MIN_BRIEFING_LINES,
    )
    KrtTextField(
        value = form.meetingPoint,
        onValueChange = { v ->
            actions.onChange(MissionSection.CORE) {
                it.copy(meetingPoint = v.take(FieldLimits.MEETING_POINT))
            }
        },
        label = stringResource(R.string.mission_admin_meeting_point),
        enabled = writable,
    )
    OperationField(form = form, writable = writable, actions = actions)
    SectionSave(R.string.mission_admin_save_core, MissionSection.CORE, form, writable, actions.onSave)
}

/**
 * Which Operation the Einsatz belongs to.
 *
 * **The only place this can be set.** An Einsatz joins an Operation through its own Kern section
 * (`PATCH /missions/{id}/core` with `operationId`); the Operation's own form has no such field
 * because the wire has none. The app used to offer it in neither, so its Operation form told the
 * member to do it „from the Einsatz" and the Einsatz had no control — a dead end that read like a
 * missing permission.
 *
 * „Keiner" is a real choice and stands first: an Einsatz standing alone is the ordinary case.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun OperationField(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    val none = stringResource(R.string.mission_admin_operation_none)
    var open by remember { mutableStateOf(false) }
    KrtSelectField(
        value = form.operations.firstOrNull { it.first == form.operationId }?.second ?: none,
        options =
            listOf(KrtOption(value = "", label = none)) +
                form.operations.map { KrtOption(value = it.first, label = it.second) },
        onSelect = { option ->
            open = false
            actions.onChange(MissionSection.CORE) { it.copy(operationId = option.value.ifBlank { null }) }
        },
        expanded = open,
        onExpandedChange = { open = it },
        label = stringResource(R.string.mission_admin_operation),
        selectedValue = form.operationId.orEmpty(),
        enabled = writable,
    )
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
    ScheduleDuration(form)
    SectionSave(R.string.mission_admin_save_schedule, MissionSection.SCHEDULE, form, writable, actions.onSave)
}

/**
 * „Dauer 4 Std. — berechnet aus Start und Ende, wie im Briefing."
 *
 * The same span the Briefing card carries, said again where it is being edited: a manager typing an
 * end time has no other way to see what they have just made the Einsatz last, and the artboard puts
 * the sentence directly under the three pairs for that reason.
 *
 * Silent when either end is missing or the span is not positive — a duration invented from one
 * timestamp would be a guess printed as a plan.
 *
 * @param form what is typed.
 */
@Composable
private fun ScheduleDuration(form: MissionAdminForm) {
    // Parsed through the picker's own readers, because the fields hold what the member SEES
    // („29.08.2026", „21:00") and not an ISO instant.
    val start = form.plannedStartDate.krtToLocalDate()?.atTime(form.plannedStartClock.krtToLocalTime())
    val end = form.plannedEndDate.krtToLocalDate()?.atTime(form.plannedEndClock.krtToLocalTime())
    val minutes =
        if (start == null || end == null) {
            null
        } else {
            Duration.between(start, end).toMinutes().takeIf { it > 0 }
        } ?: return
    Text(
        text =
            stringResource(
                R.string.mission_admin_schedule_duration,
                minutes / MINUTES_PER_HOUR,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.TextMuted,
    )
}

/** Minutes in an hour, for the Zeitplan's computed duration. */
private const val MINUTES_PER_HOUR = 60L

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
    // A framed readout, not a loose line: artboard 06-8 boxes it under its own uppercase label so
    // it reads as the fourth **value** of the section rather than as a footnote to the third.
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(KrtSpacing.hairline, KrtPalette.Gray3)
                .padding(horizontal = KrtSpacing.s12, vertical = KrtSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        Text(
            text = stringResource(R.string.mission_admin_actual_start).krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
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
    }
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

        // No branch for the unstarted Einsatz: starting it is not this form's action any more.
        // Design ch. 06 (F2) puts the lifecycle on the status badge — „kein Formular, kein
        // Overflow-Eintrag, keine zweite Stelle". The line above still says check-in is locked,
        // which is the fact this section is responsible for.
    }
    EndState(form = form, writable = writable, actions = actions)
}

/**
 * When the Einsatz actually ended — a state line and, while it runs, the action that ends it.
 *
 * **Ending it is not a status.** Activation auto-stamps `actualStartTime` server-side and nothing
 * does the same for the end: `actualEndTime` on the schedule PATCH is the only thing that sets it,
 * and setting it also closes every participant's open end-time — which is what the payout figures
 * rest on. The app sent it never, so an Einsatz begun on a phone stayed open for everyone on it.
 *
 * Shaped like the start above: the fact first, then the action, never a bare field.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun EndState(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.s4),
    ) {
        Text(
            text = stringResource(R.string.mission_admin_actual_end).krtUppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = KrtPalette.TextMuted,
        )
        Text(
            text =
                if (form.ended) {
                    stringResource(R.string.mission_admin_ended_at, form.actualEnd.toKrtStartedAt())
                } else {
                    stringResource(R.string.mission_admin_not_ended)
                },
            style = MaterialTheme.typography.bodySmall,
            color = if (form.ended) KrtPalette.Gray1 else KrtPalette.TextMuted,
        )
    }
    when {
        form.endingNow -> {
            KrtDateTimeField(
                label = stringResource(R.string.mission_admin_end_mission),
                date = form.endDate,
                time = form.endClock,
                onDate = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(endDate = v) } },
                onTime = { v -> actions.onChange(MissionSection.SCHEDULE) { it.copy(endClock = v) } },
                enabled = writable,
                // An end records something that has happened, so „liegt in der Vergangenheit"
                // would fire on every legitimate entry.
                warnPast = false,
            )
            KrtGhostButton(
                text = stringResource(R.string.mission_timeline_cancel),
                onClick = actions.onCancelEndMission,
                modifier = Modifier.fillMaxWidth(),
                enabled = writable,
            )
        }

        // Only a running Einsatz can be ended: ending one that never began would stamp a close
        // over an open start, and the server would take it.
        form.started && !form.ended -> {
            KrtGhostButton(
                text = stringResource(R.string.mission_admin_end_mission),
                onClick = actions.onEndMission,
                modifier = Modifier.fillMaxWidth(),
                enabled = writable,
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
    // With the info glyph the artboards draw beside it: a paragraph of muted text at the top of a
    // form otherwise reads as part of the form rather than as a note about it.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KrtSpacing.s8),
    ) {
        KrtIcon(
            id = DesignR.drawable.ic_krt_info,
            contentDescription = null,
            size = HINT_GLYPH,
            tint = KrtPalette.TextMuted,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/** The info glyph beside a hint — 16 px in artboard 06-7. */
private val HINT_GLYPH = 16.dp
