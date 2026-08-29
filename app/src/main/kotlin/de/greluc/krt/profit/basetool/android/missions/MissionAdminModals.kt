/*
 * Basetool Android — native companion app of the Profit Basetool.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtModal
import de.greluc.krt.profit.basetool.android.core.designsystem.component.KrtSectionTitle
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtPalette
import de.greluc.krt.profit.basetool.android.core.designsystem.theme.KrtSpacing

/** Test handle for „Einsatz jetzt starten?". */
const val MISSION_START_CONFIRM_TAG: String = "mission-start-confirm"

/** Test handle for the per-section conflict modal. */
const val MISSION_CONFLICT_TAG: String = "mission-section-conflict"

/**
 * „Einsatz jetzt starten?" — the confirmation the drawn start action requires.
 *
 * A **standard** modal, not a danger one (design ch. 06 artboard 9): the step is consequential but
 * correctable, because the start time stays editable afterwards. The text names the consequence in
 * numbers — how many signed-up members can check in — rather than asking „Bist du sicher?".
 *
 * @param registered how many are signed up, which is what the consequence is measured in.
 * @param onConfirm start it.
 * @param onDismiss leave it unstarted.
 */
@Composable
fun MissionStartConfirm(
    registered: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.mission_start_confirm_title),
        confirmText = stringResource(R.string.mission_start_confirm_cta),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(MISSION_START_CONFIRM_TAG),
    ) {
        Text(
            text = pluralStringResource(R.plurals.mission_start_confirm_body, registered, registered),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        Text(
            text = stringResource(R.string.mission_start_confirm_reversible),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * A refused save, named by the section it belongs to.
 *
 * Design ch. 06 artboard 11. The Einsatz carries four independent counters, so a `409` must say
 * **which** section somebody else changed — and say plainly that the others are untouched. A shared
 * error slot at the foot of the form could do neither. Both versions are shown, server first, in
 * the same shape as the Auftrag's note conflict (ch. 10).
 *
 * @param conflict what collided.
 * @param onKeepMine write the member's own version against the fresh counter.
 * @param onReload discard it and take the server's.
 */
@Composable
fun MissionSectionConflictModal(
    conflict: MissionSectionConflict,
    onKeepMine: () -> Unit,
    onReload: () -> Unit,
) {
    KrtModal(
        title = stringResource(R.string.mission_conflict_title, stringResource(conflict.section.nameRes())),
        confirmText = stringResource(R.string.mission_conflict_keep_mine),
        onConfirm = onKeepMine,
        onDismiss = onReload,
        cancelText = stringResource(R.string.mission_conflict_reload),
        modifier = Modifier.testTag(MISSION_CONFLICT_TAG),
    ) {
        Text(
            text = stringResource(R.string.mission_conflict_body, stringResource(conflict.section.nameRes())),
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.Gray1,
        )
        conflict.theirs?.let { theirs ->
            Version(titleRes = R.string.mission_conflict_theirs, value = theirs)
        }
        Version(titleRes = R.string.mission_conflict_mine, value = conflict.mine)
        // The line that makes the per-section lock worth having: the other three are fine, and a
        // member who does not read that will reload work they did not have to lose.
        Text(
            text = stringResource(R.string.mission_conflict_others_unaffected),
            style = MaterialTheme.typography.bodySmall,
            color = KrtPalette.TextMuted,
        )
    }
}

/**
 * One of the two versions the conflict shows.
 *
 * @param titleRes whose it is.
 * @param value what it says.
 */
@Composable
private fun Version(
    titleRes: Int,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KrtSpacing.xs),
    ) {
        KrtSectionTitle(text = stringResource(titleRes))
        Text(
            text = value.ifBlank { stringResource(R.string.krt_empty_value) },
            style = MaterialTheme.typography.bodyMedium,
            color = KrtPalette.White,
        )
    }
}

/**
 * What a section is called inside a sentence.
 *
 * Its own resource rather than the panel title: the head is UPPERCASE by the design system's own
 * rule, and a shouted word inside prose reads as an error the sentence did not intend.
 *
 * @return the string resource.
 */
private fun MissionSection.nameRes(): Int =
    when (this) {
        MissionSection.CORE -> R.string.mission_section_core
        MissionSection.SCHEDULE -> R.string.mission_section_schedule
        MissionSection.FLAGS -> R.string.mission_section_flags
        MissionSection.PEOPLE -> R.string.mission_section_people
    }
