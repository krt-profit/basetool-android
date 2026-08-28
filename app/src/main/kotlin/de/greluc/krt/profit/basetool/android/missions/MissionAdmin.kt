/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.MissionAdminSource
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Log tag for the Verwaltung's own lines. */
private const val LOG_TAG = "MissionAdmin"

/** Which of the three independently locked sections a save addresses. */
enum class MissionSection {
    /** Title, briefing, meeting point. */
    CORE,

    /** The four times, including the one that opens the Einsatz for check-in. */
    SCHEDULE,

    /** Internal or open. */
    FLAGS,
}

/**
 * The Verwaltung form, as typed.
 *
 * Times are held as **text** rather than parsed instants. They are ISO-8601 on the wire and this
 * build has no drawn date-time picker to offer instead (round 10 asks for one), so the field is
 * what the member typed and the server is what validates it — which keeps this from inventing a
 * date format the design never chose.
 *
 * @property name the title; the server requires one.
 * @property description the briefing, blank for none.
 * @property meetingPoint where to gather, blank for none.
 * @property meetingTime Teamspeak time, blank for none.
 * @property plannedStart the scheduled server join, blank for none.
 * @property plannedEnd the scheduled end, blank for none.
 * @property actualStart when it actually began, blank while it has not.
 * @property internal whether only the owning unit sees it.
 * @property saving which section is being written, or `null`.
 * @property error the last refusal.
 */
data class MissionAdminForm(
    val name: String = "",
    val description: String = "",
    val meetingPoint: String = "",
    val meetingTime: String = "",
    val plannedStart: String = "",
    val plannedEnd: String = "",
    val actualStart: String = "",
    val internal: Boolean = false,
    val saving: MissionSection? = null,
    val error: ApiError? = null,
) {
    /** Whether the Einsatz has been started, which is what the server needs before any check-in. */
    val started: Boolean
        get() = actualStart.isNotBlank()
}

/**
 * What the Verwaltung needs to read out of the screen's state.
 *
 * A record rather than three accessors on [MissionAdmin]'s constructor: the holder already sits at
 * the parameter limit, and these three are always read together anyway.
 *
 * @property form the open sheet, or `null`.
 * @property detail the Einsatz as last read, which carries the three section counters.
 * @property canManage whether the caller may edit at all.
 */
data class MissionAdminContext(
    val form: MissionAdminForm?,
    val detail: MissionDetail?,
    val canManage: Boolean,
)

/**
 * Editing the Einsatz itself.
 *
 * > **This surface has no artboard.** Chapter 06 draws seven reading tabs and the list's
 * > „Einsatz erstellen" FAB, and nothing else of the Verwaltung half. It is built from the design
 * > system's own drawn parts — a `KrtBottomSheet` of `KrtTextField` rows, the same shape as the
 * > booking and sign-up sheets — and its composition is **unratified**. Round 10
 * > (`MISSING_ARTBOARD_PROMPTS_10.md`) asks for the drawing; when it lands, this is what it
 * > corrects.
 *
 * @property missionId the Einsatz.
 * @property source where the three section writes go.
 * @property scope the view model's scope.
 * @property onSaved hands back the Einsatz each write answers with.
 */
class MissionAdmin(
    private val missionId: String,
    private val source: MissionAdminSource,
    private val scope: CoroutineScope,
    private val read: () -> MissionAdminContext,
    private val write: (MissionAdminForm?) -> Unit,
    private val onSaved: (MissionDetail) -> Unit,
) {
    /** Opens the sheet, filled from the Einsatz as read. Refused for a caller who may not manage. */
    fun open() {
        val context = read()
        val detail = context.detail ?: return
        if (!context.canManage) {
            return
        }
        write(formFor(detail))
    }

    /** Closes it, discarding what was typed. */
    fun dismiss() {
        write(null)
    }

    /**
     * Records a change in the open form.
     *
     * @param change what the field did to it.
     */
    fun change(change: (MissionAdminForm) -> MissionAdminForm) {
        val open = read().form ?: return
        write(change(open))
    }

    /**
     * Saves one section of the open form.
     *
     * @param section which one; the three are locked independently and are saved that way.
     */
    fun save(section: MissionSection) {
        val context = read()
        val form = context.form ?: return
        val detail = context.detail ?: return
        save(section, form, detail, write)
    }

    /** Stamps the Einsatz as running now, which is what opens it for check-in. */
    fun startNow() {
        val context = read()
        val form = context.form ?: return
        val detail = context.detail ?: return
        save(MissionSection.SCHEDULE, form.copy(actualStart = Instant.now().toString()), detail, write)
    }

    /**
     * Fills the form from the Einsatz as read.
     *
     * @param detail the Einsatz.
     * @return the form to open the sheet with.
     */
    fun formFor(detail: MissionDetail): MissionAdminForm =
        MissionAdminForm(
            name = detail.name,
            description = detail.description.orEmpty(),
            meetingPoint = detail.meetingPoint.orEmpty(),
            meetingTime = detail.meetingTime?.toString().orEmpty(),
            plannedStart = detail.plannedStartTime?.toString().orEmpty(),
            plannedEnd = detail.plannedEndTime?.toString().orEmpty(),
            actualStart = detail.actualStartTime?.toString().orEmpty(),
            internal = detail.isInternal,
        )

    /**
     * Saves one section, and only that one.
     *
     * The three carry **independent** version counters on the server, so saving all of them
     * together would throw that away and turn any concurrent edit into a 409 the member cannot make
     * sense of. Each save therefore sends its own section's counter and nothing else's.
     *
     * @param section which one.
     * @param form what is typed.
     * @param detail the Einsatz as last read, for the counters.
     * @param onState reports the form back as the write progresses.
     */
    private fun save(
        section: MissionSection,
        form: MissionAdminForm,
        detail: MissionDetail,
        onState: (MissionAdminForm) -> Unit,
    ) {
        onState(form.copy(saving = section, error = null))
        scope.launch {
            val result =
                when (section) {
                    MissionSection.CORE -> {
                        source.patchCore(
                            missionId,
                            name = form.name.trim(),
                            description = form.description.blankToNull(),
                            meetingPoint = form.meetingPoint.blankToNull(),
                            version = detail.coreVersion,
                        )
                    }

                    MissionSection.SCHEDULE -> {
                        source.patchSchedule(
                            missionId,
                            meetingTime = form.meetingTime.blankToNull(),
                            plannedStartTime = form.plannedStart.blankToNull(),
                            plannedEndTime = form.plannedEnd.blankToNull(),
                            actualStartTime = form.actualStart.blankToNull(),
                            version = detail.scheduleVersion,
                        )
                    }

                    MissionSection.FLAGS -> {
                        source.patchFlags(missionId, internal = form.internal, version = detail.flagsVersion)
                    }
                }
            when (result) {
                is ApiResult.Success -> {
                    onSaved(result.value)
                    // Re-fill from the answer rather than keeping what was typed: the write returns
                    // the whole Einsatz, so the other two sections' counters arrive fresh and a
                    // manager can make a second edit without a 409 from a version they never saw.
                    onState(formFor(result.value))
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the $section section could not be saved: ${result.error}" }
                    onState(form.copy(saving = null, error = result.error))
                }
            }
        }
    }
}

/**
 * Blank means "not given", which for these fields is what the server reads as a clear.
 *
 * @return the trimmed text, or `null` when there is none.
 */
private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }
