/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtCtaButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtGhostButton
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtRadioRow
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtTextField
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** Test handle for the Verwaltung tab's content. */
const val MISSION_ADMIN_SHEET_TAG: String = "mission-admin-sheet"

/**
 * What the Verwaltung tab can do.
 *
 * Opening and closing are absent on purpose: the form's lifecycle belongs to the tab, so
 * `MissionDetailViewModel.onTabSelected` fills it on arrival and clears it on departure. A screen
 * that could also open it would give the form two owners, and one of them a stale set of section
 * counters.
 *
 * @property onChange a field changed.
 * @property onSave one section is to be saved.
 * @property onStartNow stamp the Einsatz as running now.
 */
data class MissionAdminActions(
    val onChange: ((MissionAdminForm) -> MissionAdminForm) -> Unit,
    val onSave: (MissionSection) -> Unit,
    val onStartNow: () -> Unit,
)

/**
 * Verwaltung: editing the Einsatz itself — Kern, Zeitplan, Sichtbarkeit, each saved on its own.
 *
 * **The eighth tab, and the repository owner's answer to round 10's question 10a** (2026-08-29):
 * the Verwaltung is a tab of the Einsatz rather than a sheet over it. That is the better half of
 * the choice the prompt offered — editing an Einsatz is a place rather than a modal errand, the
 * back gesture keeps meaning „leave the Einsatz" instead of „close a sheet", and each section can
 * be saved and re-read without the surface disappearing underneath it. It also keeps every manager
 * affordance in one place instead of scattering a pencil across a pinned head.
 *
 * It is drawn only for a caller the server says may manage this Einsatz; `MissionTabRow` holds
 * that gate, and every write behind it is refused by the backend regardless.
 *
 * The three sections are three saves, not one. Each carries its own version counter on the server,
 * deliberately, so that two managers editing different sections both commit. A single save for all
 * three would throw that away and turn every concurrent edit into a 409 nobody can explain.
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
        // One item rather than one per field: the sections are a form, and a form that recycles its
        // rows loses the focus and the soft keyboard the moment a field scrolls out of the viewport.
        Column(
            modifier = Modifier.fillMaxWidth().testTag(MISSION_ADMIN_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(KrtSpacing.sm),
        ) {
            Hint(text = stringResource(R.string.mission_admin_section_hint))
            AdminCoreSection(form = form, writable = writable, actions = actions)
            AdminScheduleSection(form = form, writable = writable, actions = actions)
            AdminFlagsSection(form = form, writable = writable, actions = actions)
            AdminPeopleSection(members = members)
            form.error?.let { SignUpError(error = it) }
        }
    }
}

/**
 * Personen: who leads the Einsatz, who may manage it, and who is on it.
 *
 * The fourth section, and the only one that is not a form: all three writes name a member, so all
 * three go through one picker rather than three fields. Not version-locked as a section — the party
 * lead carries its own counter and the other two are membership rows, so there is nothing here to
 * „save".
 *
 * @param members the actions and the picker's state.
 */
@Composable
private fun AdminPeopleSection(members: MissionMemberActions) {
    KrtSectionTitle(text = stringResource(R.string.mission_admin_people))
    Hint(text = stringResource(R.string.mission_member_hint))
    MemberSection(members = members)
}

/**
 * Kern: what the Einsatz is called, what it is about, and where everyone gathers.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun AdminCoreSection(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    KrtSectionTitle(text = stringResource(R.string.mission_admin_core))
    KrtTextField(
        value = form.name,
        onValueChange = { v -> actions.onChange { it.copy(name = v) } },
        label = stringResource(R.string.mission_admin_name),
    )
    KrtTextField(
        value = form.description,
        onValueChange = { v -> actions.onChange { it.copy(description = v) } },
        label = stringResource(R.string.mission_admin_description),
    )
    KrtTextField(
        value = form.meetingPoint,
        onValueChange = { v -> actions.onChange { it.copy(meetingPoint = v) } },
        label = stringResource(R.string.mission_admin_meeting_point),
    )
    SectionSave(
        label = stringResource(R.string.mission_admin_save_core),
        section = MissionSection.CORE,
        form = form,
        writable = writable,
        onSave = actions.onSave,
    )
}

/**
 * Zeitplan: the four times, one of which decides whether anybody can check in at all.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun AdminScheduleSection(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    KrtSectionTitle(text = stringResource(R.string.mission_admin_schedule))
    Hint(text = stringResource(R.string.mission_admin_start_hint))
    KrtTextField(
        value = form.meetingTime,
        onValueChange = { v -> actions.onChange { it.copy(meetingTime = v) } },
        label = stringResource(R.string.mission_admin_meeting_time),
    )
    KrtTextField(
        value = form.plannedStart,
        onValueChange = { v -> actions.onChange { it.copy(plannedStart = v) } },
        label = stringResource(R.string.mission_admin_planned_start),
    )
    KrtTextField(
        value = form.plannedEnd,
        onValueChange = { v -> actions.onChange { it.copy(plannedEnd = v) } },
        label = stringResource(R.string.mission_admin_planned_end),
    )
    KrtTextField(
        value = form.actualStart,
        onValueChange = { v -> actions.onChange { it.copy(actualStart = v) } },
        label = stringResource(R.string.mission_admin_actual_start),
    )
    // „Der Einsatz läuft jetzt" is a verb, and typing an ISO timestamp to express it is paperwork.
    // It is this section's primary action — nothing else on the screen unlocks the check-in the
    // whole Einsatz exists for — so it is drawn as one, filled and full width, rather than as a
    // third ghost among the saves.
    if (!form.started) {
        KrtCtaButton(
            text = stringResource(R.string.mission_admin_start_now),
            onClick = actions.onStartNow,
            iconRes = DesignR.drawable.ic_krt_check,
            modifier = Modifier.fillMaxWidth(),
            enabled = writable && form.saving == null,
        )
    }
    SectionSave(
        label = stringResource(R.string.mission_admin_save_schedule),
        section = MissionSection.SCHEDULE,
        form = form,
        writable = writable,
        onSave = actions.onSave,
    )
}

/**
 * Sichtbarkeit: whether the Einsatz is the owning unit's business or the whole organisation's.
 *
 * @param form what is typed.
 * @param writable whether a write may run right now.
 * @param actions what the tab can do.
 */
@Composable
private fun AdminFlagsSection(
    form: MissionAdminForm,
    writable: Boolean,
    actions: MissionAdminActions,
) {
    KrtSectionTitle(text = stringResource(R.string.mission_admin_flags))
    KrtRadioRow(
        selected = form.internal,
        onSelect = { actions.onChange { it.copy(internal = !it.internal) } },
        label = stringResource(R.string.mission_admin_internal),
        enabled = writable,
    )
    SectionSave(
        label = stringResource(R.string.mission_admin_save_flags),
        section = MissionSection.FLAGS,
        form = form,
        writable = writable,
        onSave = actions.onSave,
    )
}

/**
 * One section's save.
 *
 * @param label what it says.
 * @param section which section it writes.
 * @param form the form, for the in-flight state.
 * @param writable whether a write may run right now.
 * @param onSave raises the write.
 */
@Composable
private fun SectionSave(
    label: String,
    section: MissionSection,
    form: MissionAdminForm,
    writable: Boolean,
    onSave: (MissionSection) -> Unit,
) {
    KrtGhostButton(
        text = label,
        onClick = { onSave(section) },
        iconRes = DesignR.drawable.ic_krt_check,
        modifier = Modifier.fillMaxWidth(),
        // Every save is disabled while ANY of them is in flight: the three share one form, and a
        // second write launched against a counter the first is about to bump is a 409 the member
        // caused by being quick.
        enabled = writable && form.saving == null,
    )
}

/**
 * A muted line of explanation between the controls it is about.
 *
 * @param text what it says.
 */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KrtPalette.Gray2,
    )
}
