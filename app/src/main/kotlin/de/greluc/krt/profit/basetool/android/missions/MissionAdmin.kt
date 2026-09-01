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

/** Which of the four independently locked sections a save addresses. */
enum class MissionSection {
    /** Title, briefing, meeting point. */
    CORE,

    /** The four times, including the one that opens the Einsatz for check-in. */
    SCHEDULE,

    /** Internal or open. */
    FLAGS,

    /**
     * Party lead, managers, extra participants.
     *
     * Not a saved section — its three writes fire on the pick — but it is a panel like the others
     * and carries a state chip, so it belongs in the enum the tab folds over.
     */
    PEOPLE,
}

/**
 * What one section's head says about itself while it is folded.
 *
 * A fold must hide nothing needed for a decision (design ch. 02 §10, ch. 06 artboard 7), so the
 * head carries this even when the body does not exist on screen.
 */
enum class MissionSectionState {
    /** Nothing has happened to it in this sitting. */
    IDLE,

    /** Edited and not yet written — the tab must not be left without that being visible. */
    DIRTY,

    /** A write is in flight. */
    SAVING,

    /** The last write landed; the receipt sits at the head, not in a toast. */
    SAVED,

    /** Somebody else saved this section while it was being typed. */
    CONFLICT,
}

/**
 * A refused save, named by the section it belongs to.
 *
 * The Einsatz carries four independent counters, so a `409` must say **which** section was changed
 * underneath — a shared error slot at the foot of the form cannot (design ch. 06 artboard 11).
 *
 * @property section which section collided.
 * @property mine what the member had typed, for the modal's lower half.
 * @property theirs what the server holds, for the upper half — `null` when the answer carried no
 *   readable value, in which case the modal shows only the member's own.
 */
data class MissionSectionConflict(
    val section: MissionSection,
    val mine: String,
    val theirs: String?,
)

/**
 * The Verwaltung form, as typed.
 *
 * Times are held as **date and time halves**, never as ISO text. Design ch. 06 artboard 8 draws
 * every timestamp as a pair (the web's own `.datetime-split-inputs`), and the wire value is built
 * from the two — an earlier build made all four free `KrtTextField`s, which is what made the
 * schedule read as paperwork.
 *
 * @property name the title; the server requires one.
 * @property description the briefing, blank for none.
 * @property meetingPoint where to gather, blank for none.
 * @property meetingDate Teamspeak date, `TT.MM.JJJJ`.
 * @property meetingClock Teamspeak time, `HH:MM`.
 * @property plannedStartDate the scheduled server join's date.
 * @property plannedStartClock its time.
 * @property plannedEndDate the scheduled end's date.
 * @property plannedEndClock its time.
 * @property actualStart when it actually began, as the wire string, blank while it has not. Not a
 *   field — a state line plus an action (artboard 8).
 * @property actualEnd when it actually ended, as the server sent it; blank while it runs.
 * @property endingNow whether the „Einsatz beenden" pair is open.
 * @property endDate that pair's date half.
 * @property endClock that pair's time half.
 * @property correctingStart whether the „Startzeit korrigieren" pair is open.
 * @property correctStartDate that pair's date half.
 * @property correctStartClock that pair's time half.
 * @property internal whether only the owning unit sees it.
 * @property operationId which Operation the Einsatz belongs to, or `null` for none. The Kern
 *   section is the **only** place this can be set: the Operation's own form has no such field
 *   because the wire has none, and the app used to offer it in neither.
 * @property operations what that picker may offer, read once with the tab.
 * @property partyLeadSet whether an Einsatzleitung is named — the first figure of the Personen
 *   head's „Leitung · Manager · Teilnehmer" count.
 * @property managerCount how many managers there are.
 * @property participantCount how many have signed up.
 * @property expanded which sections are open; Kern starts open and the rest closed.
 * @property states each section's own head state.
 * @property savedAt the clock time a section's receipt shows, per section.
 * @property saving which section is being written, or `null`.
 * @property conflict the refused save, or `null`.
 * @property error a refusal that is not a conflict.
 * @property errorSection which section [error] belongs to, or `null` when it belongs to the tab
 *   rather than to one section. The same reasoning as [MissionSectionConflict]: four sections
 *   save independently, so a refusal at the foot of all four says nothing about which one was
 *   refused — and on a scrolled tab it is not even on screen.
 */
data class MissionAdminForm(
    val name: String = "",
    val description: String = "",
    val meetingPoint: String = "",
    val meetingDate: String = "",
    val meetingClock: String = "",
    val plannedStartDate: String = "",
    val plannedStartClock: String = "",
    val plannedEndDate: String = "",
    val plannedEndClock: String = "",
    val actualStart: String = "",
    val actualEnd: String = "",
    val endingNow: Boolean = false,
    val endDate: String = "",
    val endClock: String = "",
    val correctingStart: Boolean = false,
    val correctStartDate: String = "",
    val correctStartClock: String = "",
    val internal: Boolean = false,
    val operationId: String? = null,
    val operations: List<Pair<String, String>> = emptyList(),
    val partyLeadSet: Boolean = false,
    val managerCount: Int = 0,
    val participantCount: Int = 0,
    val expanded: Set<MissionSection> = setOf(MissionSection.CORE),
    val states: Map<MissionSection, MissionSectionState> = emptyMap(),
    val savedAt: Map<MissionSection, String> = emptyMap(),
    val saving: MissionSection? = null,
    val conflict: MissionSectionConflict? = null,
    val error: ApiError? = null,
    val errorSection: MissionSection? = null,
) {
    /** Whether the Einsatz has been started, which is what the server needs before any check-in. */
    val started: Boolean
        get() = actualStart.isNotBlank()

    /**
     * Whether it has been ended.
     *
     * No status says so: activation stamps the start server-side and nothing stamps the end, so
     * `actualEndTime` is the only fact there is — and setting it closes every participant's open
     * end-time with it.
     */
    val ended: Boolean
        get() = actualEnd.isNotBlank()

    /**
     * Whether **this** section's fields may be edited right now.
     *
     * A write locks only the section that is writing. Locking the whole tab froze the Ziele while
     * the Zeitplan saved, which is exactly what design ch. 18 §3 (E4) rules out: the sections carry
     * independent version counters and are saved independently, so they have to be editable
     * independently too.
     *
     * @param writable whether a write may run at all — online, and the screen not otherwise busy.
     * @param section the section being drawn.
     * @return whether its fields accept input.
     */
    fun writes(
        writable: Boolean,
        section: MissionSection,
    ): Boolean = writable && (saving == null || saving == section)

    /**
     * That section's head state.
     *
     * @param section which one.
     * @return what its chip says.
     */
    fun stateOf(section: MissionSection): MissionSectionState =
        when {
            saving == section -> MissionSectionState.SAVING
            conflict?.section == section -> MissionSectionState.CONFLICT
            else -> states[section] ?: MissionSectionState.IDLE
        }
}

/**
 * What the Verwaltung needs to read out of the screen's state.
 *
 * @property form the open form, or `null`.
 * @property detail the Einsatz as last read, which carries the section counters.
 * @property canManage whether the caller may edit at all.
 */
data class MissionAdminContext(
    val form: MissionAdminForm?,
    val detail: MissionDetail?,
    val canManage: Boolean,
)

/**
 * Editing the Einsatz itself — four folded sections, each saved on its own.
 *
 * Composition ratified by the designer on 2026-08-29 (ch. 06 artboards 7–12): four panel headers,
 * Kern open and the rest closed, each head carrying its own state chip so the fold hides nothing;
 * the save button **inside** its section rather than at the form's foot; the schedule's timestamps
 * as date/time pairs; the start as a confirmed action rather than a typed field; and a `409` that
 * names the section it belongs to.
 *
 * @property missionId the Einsatz.
 * @property source where the section writes go.
 * @property scope the view model's scope.
 * @property read the form, the Einsatz and the caller's right.
 * @property write reports the form back.
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
    /** Opens the tab's form, filled from the Einsatz as read. Refused for a caller who may not manage. */
    fun open() {
        val context = read()
        val detail = context.detail ?: return
        if (!context.canManage) {
            return
        }
        write(formFor(detail))
        // After the form, not before: the tab opens on what is already known, and the Operation
        // picker fills in when its list arrives. A read the member waits for would make attaching
        // an Einsatz feel like the reason the tab is slow.
        scope.launch {
            val options = source.operationOptions()
            write(read().form?.copy(operations = options))
        }
    }

    /** Closes it, discarding what was typed. */
    fun dismiss() {
        write(null)
    }

    /**
     * Folds one section open or shut.
     *
     * Several may be open at once — no accordion, per the artboard — and the fold state lives only
     * for the sitting.
     *
     * @param section which one.
     */
    fun toggle(section: MissionSection) {
        val open = read().form ?: return
        val next = if (open.expanded.contains(section)) open.expanded - section else open.expanded + section
        write(open.copy(expanded = next))
    }

    /**
     * Records a change in the open form, and marks its section as edited.
     *
     * @param section the section the field belongs to, so its head can say „Geändert".
     * @param change what the field did to it.
     */
    fun change(
        section: MissionSection,
        change: (MissionAdminForm) -> MissionAdminForm,
    ) {
        val open = read().form ?: return
        write(change(open).copy(states = open.states + (section to MissionSectionState.DIRTY)))
    }

    /**
     * Saves one section of the open form.
     *
     * @param section which one; the three writable ones are locked independently and are saved that
     *   way. [MissionSection.PEOPLE] has no save and is ignored.
     */
    fun save(section: MissionSection) {
        val context = read()
        val form = context.form
        val detail = context.detail
        // Personen has no save: its three writes fire on the pick. The branch is here rather than
        // absent so a new section cannot be added without a decision about what it writes.
        if (form == null || detail == null || section == MissionSection.PEOPLE) {
            return
        }
        save(section, form, detail)
    }

    /** Opens the „Startzeit korrigieren" pair, filled from the start as it stands. */
    fun correctStart() {
        val open = read().form ?: return
        val (date, clock) = open.actualStart.toKrtDateTime()
        write(open.copy(correctingStart = true, correctStartDate = date, correctStartClock = clock))
    }

    /** Abandons the correction, leaving the start as it was. */
    fun cancelCorrectStart() {
        val open = read().form ?: return
        write(open.copy(correctingStart = false))
    }

    /**
     * Opens the „Einsatz beenden" pair, filled with **now**.
     *
     * Now rather than blank because that is what ending an Einsatz means in practice, and because
     * the alternative is a member typing today's date into a field they opened by pressing „end".
     * It stays editable: a run written up the next morning ended when it ended.
     */
    fun endMission() {
        val open = read().form ?: return
        val (date, clock) = Instant.now().toString().toKrtDateTime()
        write(open.copy(endingNow = true, endDate = date, endClock = clock))
    }

    /** Abandons that, leaving the Einsatz as it was. */
    fun cancelEndMission() {
        val open = read().form ?: return
        write(open.copy(endingNow = false))
    }

    /** Takes the member's own version after a conflict, writing it against the fresh counter. */
    fun keepMine() {
        val context = read()
        val form = context.form
        val detail = context.detail
        val section = form?.conflict?.section
        if (form == null || detail == null || section == null) {
            return
        }
        save(section, form.copy(conflict = null), detail)
    }

    /** Drops the conflict and re-fills the form from the Einsatz as last read. */
    fun reloadAfterConflict() {
        val detail = read().detail ?: return
        write(formFor(detail))
    }

    /**
     * Fills the form from the Einsatz as read.
     *
     * @param detail the Einsatz.
     * @return the form to open the tab with.
     */
    fun formFor(detail: MissionDetail): MissionAdminForm {
        val (meetDate, meetClock) = detail.meetingTime?.toString().orEmpty().toKrtDateTime()
        val (startDate, startClock) = detail.plannedStartTime?.toString().orEmpty().toKrtDateTime()
        val (endDate, endClock) = detail.plannedEndTime?.toString().orEmpty().toKrtDateTime()
        return MissionAdminForm(
            name = detail.name,
            description = detail.description.orEmpty(),
            meetingPoint = detail.meetingPoint.orEmpty(),
            meetingDate = meetDate,
            meetingClock = meetClock,
            plannedStartDate = startDate,
            plannedStartClock = startClock,
            plannedEndDate = endDate,
            plannedEndClock = endClock,
            actualStart = detail.actualStartTime?.toString().orEmpty(),
            actualEnd = detail.actualEndTime?.toString().orEmpty(),
            internal = detail.isInternal,
            operationId = detail.operationId,
            partyLeadSet = !detail.partyLeadName.isNullOrBlank(),
            managerCount = detail.managers.size,
            participantCount = detail.registeredParticipants,
        )
    }

    /**
     * Saves one section, and only that one.
     *
     * The sections carry **independent** version counters on the server, so saving them together
     * would throw that away and turn any concurrent edit into a 409 the member cannot make sense
     * of. Each save therefore sends its own section's counter and nothing else's.
     *
     * @param section which one.
     * @param form what is typed.
     * @param detail the Einsatz as last read, for the counters.
     */
    private fun save(
        section: MissionSection,
        form: MissionAdminForm,
        detail: MissionDetail,
    ) {
        write(form.copy(saving = section, error = null, errorSection = null, conflict = null))
        scope.launch {
            val result = request(section, form, detail)
            when (result) {
                is ApiResult.Success -> {
                    onSaved(result.value)
                    // Re-fill from the answer rather than keeping what was typed: the write returns
                    // the whole Einsatz, so the other sections' counters arrive fresh and a manager
                    // can make a second edit without a 409 from a version they never saw. The fold
                    // state and the receipt survive the re-fill — they belong to the sitting.
                    val current = read().form ?: form
                    write(
                        formFor(result.value).copy(
                            expanded = current.expanded,
                            states = current.states + (section to MissionSectionState.SAVED),
                            savedAt = current.savedAt + (section to krtClockNow()),
                        ),
                    )
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the $section section could not be saved: ${result.error}" }
                    write(refusal(section, form, detail, result.error))
                }
            }
        }
    }

    /**
     * Turns a refusal into the form state that explains it.
     *
     * A `409` becomes a conflict **named by its section**, with both versions to show; anything
     * else stays a plain error on the section.
     *
     * @param section which section was being saved.
     * @param form what was typed.
     * @param detail the Einsatz as last read, for the server's own value.
     * @param error what came back.
     * @return the form to draw.
     */
    private fun refusal(
        section: MissionSection,
        form: MissionAdminForm,
        detail: MissionDetail,
        error: ApiError,
    ): MissionAdminForm =
        if (error is ApiError.OptimisticLock) {
            form.copy(
                saving = null,
                conflict =
                    MissionSectionConflict(
                        section = section,
                        mine = form.summaryOf(section),
                        theirs = detail.summaryOf(section),
                    ),
            )
        } else {
            form.copy(saving = null, error = error, errorSection = section)
        }

    /**
     * Runs the one write the section needs.
     *
     * @param section which one.
     * @param form what is typed.
     * @param detail the Einsatz as last read, for the counters.
     * @return what the server answered.
     */
    private suspend fun request(
        section: MissionSection,
        form: MissionAdminForm,
        detail: MissionDetail,
    ): ApiResult<MissionDetail> =
        when (section) {
            MissionSection.CORE -> {
                source.patchCore(
                    missionId,
                    name = form.name.trim(),
                    description = form.description.blankToNull(),
                    meetingPoint = form.meetingPoint.blankToNull(),
                    // Echoed, not edited. The Kern PATCH replaces the section, so leaving the link
                    // out cleared it on every rename - the app does not show the field at all.
                    calendarLink = detail.calendarLink,
                    // The status is the badge's business (F2), never this form's.
                    status = null,
                    // The only place an Einsatz joins an Operation: the Operation's own form has
                    // no such field, because the wire has none. Blank means „keiner".
                    operationId = form.operationId,
                    version = detail.coreVersion,
                )
            }

            MissionSection.SCHEDULE -> {
                source.patchSchedule(
                    missionId,
                    meetingTime = krtWireInstant(form.meetingDate, form.meetingClock),
                    plannedStartTime = krtWireInstant(form.plannedStartDate, form.plannedStartClock),
                    plannedEndTime = krtWireInstant(form.plannedEndDate, form.plannedEndClock),
                    actualStartTime =
                        if (form.correctingStart) {
                            krtWireInstant(form.correctStartDate, form.correctStartClock)
                        } else {
                            form.actualStart.blankToNull()
                        },
                    // Echoed when it is not being edited, for the same reason as the start: this
                    // PATCH replaces the section, and an omitted end would reopen an Einsatz that
                    // had been closed — together with every participant's end-time it closed.
                    actualEndTime =
                        if (form.endingNow) {
                            krtWireInstant(form.endDate, form.endClock)
                        } else {
                            form.actualEnd.blankToNull()
                        },
                    version = detail.scheduleVersion,
                )
            }

            MissionSection.FLAGS -> {
                source.patchFlags(missionId, internal = form.internal, version = detail.flagsVersion)
            }

            // Guarded by `save`; the branch exists so a new section cannot be added without a
            // decision about what it writes.
            MissionSection.PEOPLE -> {
                error("the Personen section has no save")
            }
        }
}

/**
 * The one value a conflict modal shows for this section, from what was typed.
 *
 * @param section which section collided.
 * @return the member's own version, in words.
 */
private fun MissionAdminForm.summaryOf(section: MissionSection): String =
    when (section) {
        MissionSection.CORE -> name
        MissionSection.SCHEDULE -> "$plannedStartDate $plannedStartClock".trim()
        MissionSection.FLAGS -> internal.toString()
        MissionSection.PEOPLE -> ""
    }

/**
 * The same value as the server last answered with.
 *
 * @param section which section collided.
 * @return the server's version, or `null` when this section has none worth showing.
 */
private fun MissionDetail.summaryOf(section: MissionSection): String? =
    when (section) {
        MissionSection.CORE -> name
        MissionSection.SCHEDULE -> plannedStartTime?.toString()
        MissionSection.FLAGS -> isInternal.toString()
        MissionSection.PEOPLE -> null
    }

/**
 * Blank means "not given", which for these fields is what the server reads as a clear.
 *
 * @return the trimmed text, or `null` when there is none.
 */
private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }
