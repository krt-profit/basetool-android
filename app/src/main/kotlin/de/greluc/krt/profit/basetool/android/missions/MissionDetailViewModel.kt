/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.data.Identity
import de.greluc.krt.profit.basetool.android.core.data.IdentitySource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSections
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncSource
import de.greluc.krt.profit.basetool.android.core.data.LiveSyncTopic
import de.greluc.krt.profit.basetool.android.core.data.MissionAdminSource
import de.greluc.krt.profit.basetool.android.core.data.MissionDetail
import de.greluc.krt.profit.basetool.android.core.data.MissionFinanceEntry
import de.greluc.krt.profit.basetool.android.core.data.MissionFinances
import de.greluc.krt.profit.basetool.android.core.data.MissionJobType
import de.greluc.krt.profit.basetool.android.core.data.MissionParticipant
import de.greluc.krt.profit.basetool.android.core.data.MissionPeopleSource
import de.greluc.krt.profit.basetool.android.core.data.MissionSource
import de.greluc.krt.profit.basetool.android.core.data.MissionStatus
import de.greluc.krt.profit.basetool.android.core.data.MissionStructureSource
import de.greluc.krt.profit.basetool.android.core.data.MissionTimelineSource
import de.greluc.krt.profit.basetool.android.core.network.ApiError
import de.greluc.krt.profit.basetool.android.core.network.ApiResult
import de.greluc.krt.profit.basetool.android.core.network.Connectivity
import de.greluc.krt.profit.basetool.android.ui.FirstLoadRetry
import de.greluc.krt.profit.basetool.android.ui.observeLiveSync
import de.greluc.krt.profit.basetool.android.ui.publishLiveSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The tabs of the Einsatz detail, in the order the design puts them.
 *
 * An enum rather than indices so a reordering is a compile-time move and the deep link's `?tab=`
 * has something stable to name.
 */
enum class MissionTab {
    /** Facts, briefing and description. */
    OVERVIEW,

    /** The roster. */
    PARTICIPANTS,

    /** The Einheiten and their crews. */
    UNITS,

    /** The Ablauf checklist. */
    STEPS,

    /** The Ziele. */
    OBJECTIVES,

    /** The radio plan. */
    FREQUENCIES,

    /** Income, expense and the entries behind them. */
    FINANCES,

    /** Verwaltung: editing the Einsatz itself, usable only by a caller the server says may manage it. */
    ADMIN,
}

/** How far the Einsatz itself has got. */
sealed interface MissionDetailPhase {
    /** The read is in flight. */
    data object Loading : MissionDetailPhase

    /** It arrived. */
    data object Ready : MissionDetailPhase

    /**
     * It did not.
     *
     * @property error what went wrong. `Forbidden` and `NotFound` are ordinary answers here, not
     *   outages: the backend refuses an outsider an internal or terminal Einsatz, and a stale link
     *   is a 404. The screen words them differently for that reason.
     */
    data class Failed(
        val error: ApiError,
    ) : MissionDetailPhase
}

/**
 * How far the Finanzen tab has got.
 *
 * Separate from [MissionDetailPhase] because the finances are a second, differently guarded read
 * that may be refused while the Einsatz itself is visible.
 */
sealed interface MissionFinancesPhase {
    /** Not asked for yet — the tab has never been opened. */
    data object Idle : MissionFinancesPhase

    /** In flight. */
    data object Loading : MissionFinancesPhase

    /**
     * Loaded.
     *
     * @property finances the totals band and the entries beneath it.
     */
    data class Ready(
        val finances: MissionFinances,
    ) : MissionFinancesPhase

    /**
     * Refused or unavailable.
     *
     * @property error what went wrong; `Forbidden` is the ordinary "not your Einsatz's books".
     */
    data class Failed(
        val error: ApiError,
    ) : MissionFinancesPhase
}

/**
 * The open sign-up sheet (design ch. 06, artboard 3).
 *
 * @property jobTypes the Funktionen catalogue, read when the sheet opens; empty until it arrives.
 * @property desired the function they picked, or `null`; optional.
 * @property donate whether the share goes to the org treasury.
 * @property saving whether the write is running.
 * @property error the last refusal, kept in the sheet so the answers are not lost.
 */
data class JoinSheet(
    val jobTypes: List<MissionJobType> = emptyList(),
    val desired: MissionJobType? = null,
    val donate: Boolean = false,
    val saving: Boolean = false,
    val error: ApiError? = null,
)

/**
 * The three seams the Einsatz detail depends on, bundled as one constructor argument.
 *
 * @property read the list, the detail, the books and the roster.
 * @property admin the Einsatz's own record: the three sections, the party lead, the managers.
 * @property structure what the Einsatz is made of: Einheiten, crew, frequencies.
 */
data class MissionSeams(
    val read: MissionSource,
    val admin: MissionAdminSource,
    val structure: MissionStructureSource,
    val timeline: MissionTimelineSource,
    val people: MissionPeopleSource,
)

/**
 * Everything the detail screen draws.
 *
 * @property missionId which Einsatz this is about, known before anything has loaded
 * @property detail the Einsatz once it arrives
 * @property phase how far that read has got
 * @property tab which tab is showing
 * @property finances how far the money has got, on its own timeline
 * @property refreshing whether a pull-to-refresh is running over content already on screen
 * @property retryIn seconds until the automatic retry, or `null` when nothing is counting
 * @property rosterJobTypes the MISSION Funktionen the roster offers a manager; empty until the
 *   Teilnehmer tab is opened by someone who may assign one
 * @property adminForm the open Verwaltung form, or `null` when the tab is not on screen
 * @property lifecycleAsk the status the member is being asked to confirm, or `null`; kept outside
 *   [adminForm]
 * @property structure what a manager is composing on the Einheiten or Frequenzen tab
 * @property timeline what a manager is composing on the Ablauf or Ziele tab
 * @property memberPicker the one member lookup behind the party lead, the managers and
 *   „Teilnehmer hinzufügen"
 * @property crewJobTypes the CREW Funktionen a crew slot can hold, a separate catalogue from
 *   [rosterJobTypes]; empty until the Einheiten tab is opened by someone who may assign one
 */
data class MissionDetailState(
    val missionId: String,
    val detail: MissionDetail? = null,
    val phase: MissionDetailPhase = MissionDetailPhase.Loading,
    val tab: MissionTab = MissionTab.OVERVIEW,
    val finances: MissionFinancesPhase = MissionFinancesPhase.Idle,
    val refreshing: Boolean = false,
    val retryIn: Int? = null,
    val me: Identity? = null,
    val saving: Boolean = false,
    val online: Boolean = true,
    val entryDraft: FinanceEntryDraft? = null,
    val joinSheet: JoinSheet? = null,
    val rosterJobTypes: List<MissionJobType> = emptyList(),
    val adminForm: MissionAdminForm? = null,
    val lifecycleAsk: MissionStatus? = null,
    val structure: MissionStructureDraft = MissionStructureDraft(),
    val timeline: MissionTimelineDraft = MissionTimelineDraft(),
    val memberPicker: MissionMemberPickerState = MissionMemberPickerState(),
    val crewJobTypes: List<MissionJobType> = emptyList(),
    val unitShips: List<Pair<String, String>> = emptyList(),
    val unitMembers: List<Pair<String, String>> = emptyList(),
    val error: ApiError? = null,
) {
    /**
     * What the status badge may advance the Einsatz to, or `null` when it is at rest.
     *
     * Read by the badge band; the same mapping the confirmation and the write use, so the three
     * cannot disagree about which step is on offer.
     */
    val lifecycleNext: MissionStatus?
        get() =
            when (detail?.status) {
                MissionStatus.PLANNED -> MissionStatus.ACTIVE
                MissionStatus.ACTIVE -> MissionStatus.COMPLETED
                else -> null
            }

    /** The caller's own sign-up, or `null` when they are not on this Einsatz. */
    val mySignUp: MissionParticipant?
        get() = me?.let { identity -> detail?.participants?.firstOrNull { it.userId == identity.userId } }

    /**
     * Whether a write may be offered at all.
     *
     * Not knowing who the caller is disables every one of them: each write addresses a
     * participant row, and there is no way to tell which row is theirs.
     */
    val writable: Boolean
        get() = online && !saving && me != null

    /**
     * Whether checking in is possible at all yet.
     *
     * The server refuses a check-in before the Einsatz has actually started — "Cannot check in
     * before mission actual start time is set", found on a device — and `actualStartTime` is the
     * same fact the refusal is about. Offering the control before then is offering a 400.
     */
    val checkInPossible: Boolean
        get() = detail?.actualStartTime != null

    /**
     * Whether a booking can be made at all.
     *
     * The create names a participant, and the only one the app may name is the caller's own. A
     * member who has not signed up therefore has nothing to book against — which is the server's
     * rule, not a shortcut.
     */
    val bookingPossible: Boolean
        get() = writable && mySignUp != null

    /**
     * Whether the caller may act on another member's row — the server's own `canEdit`, never derived
     * from role strings.
     *
     * Independent of [writable]: an offline manager still holds the right.
     */
    val canManage: Boolean
        get() = detail?.canManage == true

    /**
     * The row a manager action may address, or `null` when it may not run.
     *
     * The single gate for all manager actions: refuses when not writable, not permitted, or the
     * participant id is not in the roster last read.
     *
     * @param participantId the row the caller tapped.
     * @return the row as last read, or `null`.
     */
    fun rowToManage(participantId: String): MissionParticipant? {
        if (!writable || !canManage) {
            return null
        }
        return detail?.participants?.firstOrNull { it.id == participantId }
    }
}

/**
 * The booking being written.
 *
 * @property entryId the entry being changed, or `null` for a new one
 * @property income whether it is money in rather than money out
 * @property amount the magnitude as typed; the sign lives in [income]
 * @property note what it was for, as typed
 * @property version the entry's version when changing one, echoed back on save
 */
data class FinanceEntryDraft(
    val entryId: String? = null,
    val income: Boolean = true,
    val amount: String = "",
    val note: String = "",
    val version: Long? = null,
) {
    /** Whether the form holds something the server will accept. */
    val submittable: Boolean
        get() = amount.toDoubleOrNull()?.let { it > 0 } == true
}

/**
 * Drives one Einsatz's detail; the finances are fetched lazily when their tab is first opened.
 *
 * @property source where the Einsatz comes from
 * @property identity who the caller is — which decides which sign-up on the roster is theirs
 * @property connectivity whether the device has a network
 * @property missionId which Einsatz to load
 * @property liveSync the live-sync bridge, or `null` in a test or a preview; the screen listens to
 *   its own room and announces its own writes into it
 */
class MissionDetailViewModel(
    private val seams: MissionSeams,
    private val identity: IdentitySource,
    connectivity: Connectivity,
    private val missionId: String,
    private val liveSync: LiveSyncSource? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MissionDetailState(missionId = missionId))

    /** What the screen draws. */
    val state: StateFlow<MissionDetailState> = mutableState.asStateFlow()

    /**
     * Editing the Einsatz itself — the three independently locked sections.
     *
     * Public like [roster], and for the same reason: the screen calls it directly rather than
     * through wrappers that would only widen this class.
     */
    val admin =
        MissionAdmin(
            missionId = missionId,
            source = seams.admin,
            scope = viewModelScope,
            read = {
                val current = mutableState.value
                MissionAdminContext(current.adminForm, current.detail, current.canManage)
            },
            write = { form -> mutableState.update { it.copy(adminForm = form) } },
            onSaved = { saved ->
                mutableState.update { it.copy(detail = saved) }
                announce(LiveSyncSections.MISSION_OVERVIEW)
            },
        )

    /**
     * The Einsatz's structure — Einheiten, crew, frequencies, leadership.
     *
     * Public like [admin] and [roster]; the screen calls it directly.
     */
    val structure =
        MissionStructure(
            missionId = missionId,
            structure = seams.structure,
            admin = seams.admin,
            scope = viewModelScope,
            read = { mutableState.value.let { it.structure to it.detail } },
            write = { draft, saved ->
                val current = mutableState.value
                mutableState.value =
                    current.copy(structure = draft, detail = saved ?: current.detail)
                if (saved != null) {
                    announce(LiveSyncSections.MISSION_CREW)
                }
            },
        )

    /**
     * The Ablauf and the Ziele, as a manager writes them.
     *
     * A holder of its own rather than more methods on [structure]: these two sections carry their
     * own version counters and their endpoints answer with a list rather than the Einsatz, so
     * nothing about them shares a code path with the Einheiten.
     */
    val timeline =
        MissionTimeline(
            missionId = missionId,
            source = seams.timeline,
            scope = viewModelScope,
            read = { mutableState.value.let { it.timeline to it.detail } },
            write = { draft, saved ->
                val current = mutableState.value
                mutableState.value = current.copy(timeline = draft, detail = saved ?: current.detail)
                if (saved != null) {
                    announce(LiveSyncSections.MISSION_OVERVIEW)
                }
            },
        )

    /**
     * The one member picker behind the three member-shaped writes.
     *
     * It only names somebody; the write that follows is [structure]'s, which is why the pick lands
     * back here and is dispatched by target rather than the picker holding three callbacks.
     */
    val memberPicker =
        MissionMemberPicker(
            source = seams.people,
            scope = viewModelScope,
            read = { mutableState.value.memberPicker },
            write = { picker -> mutableState.update { it.copy(memberPicker = picker) } },
            onPicked = { target, option ->
                when (target) {
                    MissionMemberTarget.PARTY_LEAD -> {
                        structure.setPartyLead(
                            option.id,
                            mutableState.value.detail?.partyLeadVersion ?: 0L,
                        )
                    }

                    MissionMemberTarget.MANAGER -> {
                        structure.addManager(option.id)
                    }

                    MissionMemberTarget.PARTICIPANT -> {
                        structure.addParticipant(option.id)
                    }
                }
            },
        )

    /**
     * The manager's half of the Teilnehmer tab, called by the screen directly.
     *
     * It reads the roster through this view model rather than holding its own copy.
     */
    val roster =
        MissionRoster(
            missionId = missionId,
            source = seams.read,
            scope = viewModelScope,
            rowToManage = { mutableState.value.rowToManage(it) },
            write = ::writeRow,
        )

    /**
     * The chapter-14 retry ladder for this screen's first load (REQ-APP-UI-003).
     *
     * Shared rather than re-derived: the conditions under which a countdown is right are the same
     * on every screen.
     */
    private val retry =
        FirstLoadRetry(
            scope = viewModelScope,
            onCountdown = { left -> mutableState.update { it.copy(retryIn = left) } },
            onRetry = { reload(keepContent = false) },
        )

    /**
     * Reads the CREW catalogue once, for a manager on the Einheiten tab.
     *
     * Guarded on both counts the roster's own loader is: a caller who may not assign gets no
     * catalogue at all, and a catalogue already in hand is not fetched twice.
     */
    private fun loadCrewJobTypes() {
        val current = mutableState.value
        if (!current.canManage || current.crewJobTypes.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            when (val result = seams.people.crewJobTypes()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(crewJobTypes = result.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the CREW catalogue could not be read: ${result.error}" }
                }
            }
        }
    }

    /**
     * The status badge's lifecycle: ask, confirm, dismiss.
     *
     * Its own holder rather than three more methods on the view model — the badge is one surface
     * with one concern (design ch. 06, F2), and the screen binds it as one object instead of
     * threading three unrelated-looking callbacks through the tree.
     */
    inner class Lifecycle {
        /**
         * Asks before advancing the lifecycle.
         *
         * Starting is not destructive, so this is a standard modal rather than a danger one — but it
         * cannot be taken back, which is what the confirmation says.
         */
        fun ask() {
            val current = mutableState.value
            val next = current.lifecycleNext ?: return
            mutableState.value = current.copy(lifecycleAsk = next)
        }

        /** Closes the confirmation without moving anything. */
        fun dismiss() {
            mutableState.update { it.copy(lifecycleAsk = null) }
        }

        /**
         * Advances the lifecycle in one Kern-section PATCH, echoing every other Kern field as it stands.
         *
         * Setting `ACTIVE` also stamps `actualStartTime` server-side, which opens check-in.
         */
        fun confirm() {
            val current = mutableState.value
            val detail = current.detail
            val next = current.lifecycleAsk
            val mayWrite = current.writable && current.canManage
            if (detail == null || next == null || !mayWrite) {
                return
            }
            mutableState.value = current.copy(saving = true, error = null, lifecycleAsk = null)
            viewModelScope.launch {
                val result =
                    seams.admin.patchCore(
                        missionId,
                        name = detail.name,
                        description = detail.description,
                        meetingPoint = detail.meetingPoint,
                        calendarLink = detail.calendarLink,
                        status = next.name,
                        operationId = detail.operationId,
                        version = detail.coreVersion,
                    )
                settle(result)
                if (result is ApiResult.Success) {
                    announce(LiveSyncSections.MISSION_OVERVIEW)
                }
            }
        }
    }

    /** The status badge's lifecycle actions. */
    val lifecycle: Lifecycle = Lifecycle()

    /** The member asked again. Cancels the countdown and starts the ladder over. */
    fun onRetry() {
        retry.onManualRetry()
    }

    init {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                mutableState.update { it.copy(online = online) }
            }
        }
        observeLiveSync(liveSync, setOf(LiveSyncTopic.mission(missionId))) { sections ->
            if (sections.any { it in ROSTER_SECTIONS }) {
                reload(keepContent = true)
            }
            if (LiveSyncSections.MISSION_FINANCE in sections &&
                mutableState.value.finances !is MissionFinancesPhase.Idle
            ) {
                loadFinances()
            }
        }
    }

    /** Loads the Einsatz. Safe to call more than once. */
    fun load() {
        readIdentity()
        reload(keepContent = false)
    }

    /**
     * Reads who the caller is, once.
     *
     * A failure costs the writes, not the screen: the Einsatz still reads, and what is lost is
     * knowing which row on the roster is the caller's.
     */
    private fun readIdentity() {
        if (mutableState.value.me != null) {
            return
        }
        viewModelScope.launch {
            when (val result = identity.me()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(me = result.value) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the caller could not be identified: ${result.error}" }
                }
            }
        }
    }

    /** Signs the caller up, or withdraws them. */
    fun onToggleSignUp() {
        val current = mutableState.value
        if (!current.writable) {
            return
        }
        val mine = current.mySignUp
        if (mine == null) {
            onJoinSheetOpened()
            return
        }
        mutableState.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val result =
                run {
                    when (val left = seams.read.leave(missionId, mine.id)) {
                        is ApiResult.Failure -> left
                        is ApiResult.Success -> seams.read.detail(missionId)
                    }
                }
            settle(result)
        }
    }

    /**
     * Opens the sign-up sheet and reads the Funktionen it offers.
     *
     * The catalogue is read here rather than with the mission: a member who never signs up should
     * not pay for a list they will not see.
     */
    fun onJoinSheetOpened() {
        mutableState.update { it.copy(joinSheet = JoinSheet(), error = null) }
        viewModelScope.launch {
            when (val result = seams.read.jobTypes()) {
                is ApiResult.Success -> {
                    val open = mutableState.value.joinSheet ?: return@launch
                    mutableState.update { it.copy(joinSheet = open.copy(jobTypes = result.value)) }
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the Funktionen catalogue could not be read: ${result.error}" }
                }
            }
        }
    }

    /** Closes the sheet without signing up. */
    fun onJoinSheetDismissed() {
        mutableState.update { it.copy(joinSheet = null) }
    }

    /**
     * Picks — or unpicks — the function the member would like.
     *
     * @param jobType the function; the same one again clears it, because „Wunsch" is optional and
     *   a chip row with no way back would make it compulsory in practice.
     */
    fun onDesiredFunction(jobType: MissionJobType) {
        val open = mutableState.value.joinSheet ?: return
        val next = if (open.desired?.id == jobType.id) null else jobType
        mutableState.update { it.copy(joinSheet = open.copy(desired = next)) }
    }

    /**
     * Sets where the member's share goes.
     *
     * @param donate `true` for the org treasury, `false` for their own account.
     */
    fun onJoinPayout(donate: Boolean) {
        val open = mutableState.value.joinSheet ?: return
        mutableState.update { it.copy(joinSheet = open.copy(donate = donate)) }
    }

    /** Sends the sign-up with what the sheet collected. */
    fun onJoinConfirmed() {
        val current = mutableState.value
        val open = current.joinSheet ?: return
        if (open.saving || !current.writable) {
            return
        }
        mutableState.value = current.copy(joinSheet = open.copy(saving = true, error = null))
        viewModelScope.launch {
            when (
                val result =
                    seams.read.join(
                        missionId = missionId,
                        desiredJobTypeId = open.desired?.id,
                        donate = open.donate,
                    )
            ) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(joinSheet = null) }
                    settle(result)
                }

                is ApiResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            joinSheet = open.copy(saving = false, error = result.error),
                        )
                    }
                }
            }
        }
    }

    /** Stamps the caller's own check-in, or takes it back. */
    fun onToggleCheckIn() {
        val current = mutableState.value
        val mine = current.mySignUp
        if (mine == null || !current.writable || !current.checkInPossible) {
            return
        }
        writeRow { seams.read.setCheckedIn(missionId, mine.id, checkedIn = !mine.checkedIn) }
    }

    /** Switches the caller's own share between paid out and donated. */
    fun onTogglePayoutPreference() {
        val current = mutableState.value
        val mine = current.mySignUp
        if (mine == null || !current.writable) {
            return
        }
        writeRow { seams.read.setDonating(missionId, mine.id, donating = mine.donating != true) }
    }

    /** Opens the editor on a new booking. */
    fun onAddEntry() {
        val current = mutableState.value
        if (current.bookingPossible) {
            mutableState.value = current.copy(entryDraft = FinanceEntryDraft(), error = null)
        }
    }

    /**
     * Opens the editor on a booking that exists.
     *
     * @param entry the booking.
     */
    fun onEditEntry(entry: MissionFinanceEntry) {
        val current = mutableState.value
        if (!current.writable) {
            return
        }
        mutableState.value =
            current.copy(
                entryDraft =
                    FinanceEntryDraft(
                        entryId = entry.id,
                        income = entry.income,
                        amount = entry.amount.substringBefore('.').filter(Char::isDigit),
                        note = entry.note.orEmpty(),
                        version = entry.version,
                    ),
                error = null,
            )
    }

    /**
     * Changes what the editor holds.
     *
     * @param transform how it changes.
     */
    private fun updateDraft(transform: (FinanceEntryDraft) -> FinanceEntryDraft) {
        val current = mutableState.value
        current.entryDraft?.let {
            mutableState.value = current.copy(entryDraft = transform(it), error = null)
        }
    }

    /**
     * Sets whether the booking is money in.
     *
     * @param income `true` for an income.
     */
    fun onEntryIncomeChanged(income: Boolean) = updateDraft { it.copy(income = income) }

    /**
     * Sets the amount.
     *
     * @param value what the member typed, unparsed.
     */
    fun onEntryAmountChanged(value: String) =
        updateDraft { it.copy(amount = value.filter(Char::isDigit)) }

    /**
     * Sets the note.
     *
     * @param value what the member typed.
     */
    fun onEntryNoteChanged(value: String) = updateDraft { it.copy(note = value.take(NOTE_LENGTH)) }

    /** Closes the editor, discarding what was typed. */
    fun onDismissEntry() {
        mutableState.update { it.copy(entryDraft = null, error = null) }
    }

    /** Sends the booking. */
    fun onSaveEntry() {
        val current = mutableState.value
        val draft = current.entryDraft
        val mine = current.mySignUp
        val ready = draft != null && mine != null && draft.submittable
        if (!ready || !current.writable) {
            return
        }
        requireNotNull(draft)
        requireNotNull(mine)
        val note = draft.note.trim().takeIf { it.isNotEmpty() }
        bookkeeping {
            if (draft.entryId == null) {
                seams.read.addFinanceEntry(missionId, mine.id, draft.income, draft.amount, note)
            } else {
                seams.read.updateFinanceEntry(
                    draft.entryId,
                    draft.income,
                    draft.amount,
                    note,
                    draft.version,
                )
            }
        }
    }

    /**
     * Removes one booking.
     *
     * @param entry the booking.
     */
    fun onDeleteEntry(entry: MissionFinanceEntry) {
        if (!mutableState.value.writable) {
            return
        }
        bookkeeping { seams.read.deleteFinanceEntry(entry.id) }
    }

    /**
     * Runs a money write and re-reads the tab.
     *
     * Always a re-read: the three totals above the list move with every booking, and patching one
     * row would leave a sum that disagrees with the rows under it.
     *
     * @param request the call.
     */
    private fun bookkeeping(request: suspend () -> ApiResult<Unit>) {
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(entryDraft = null, saving = false, error = null) }
                    loadFinances()
                    announce(LiveSyncSections.MISSION_FINANCE)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the booking could not be written: ${result.error}" }
                    mutableState.update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Runs a write that answers with one participant row and patches that row in place without
     * re-reading the roster.
     *
     * @param request the call.
     */
    private fun writeRow(request: suspend () -> ApiResult<MissionParticipant>) {
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    val current = mutableState.value
                    val detail = current.detail
                    mutableState.value =
                        current.copy(
                            detail = detail?.withRow(result.value),
                            saving = false,
                            error = null,
                        )
                    announce(LiveSyncSections.MISSION_CREW)
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "the sign-up could not be changed: ${result.error}" }
                    mutableState.update { it.copy(saving = false, error = result.error) }
                }
            }
        }
    }

    /**
     * Tells the other viewers of this Einsatz that one of its regions moved.
     *
     * Only the member's own writes reach here. A change that arrived through the room is applied
     * and never re-announced, or two clients would bounce one check-in off each other forever.
     *
     * @param section the region that changed.
     */
    private fun announce(section: String) {
        publishLiveSync(liveSync, LiveSyncTopic.mission(missionId), section)
    }

    /**
     * Files what a whole-Einsatz write returned.
     *
     * @param result the answer.
     */
    private fun settle(result: ApiResult<MissionDetail>) {
        when (result) {
            is ApiResult.Success -> {
                mutableState.update {
                    it.copy(
                        detail = result.value,
                        phase = MissionDetailPhase.Ready,
                        saving = false,
                        error = null,
                    )
                }
                announce(LiveSyncSections.MISSION_CREW)
            }

            is ApiResult.Failure -> {
                KrtLog.w(LOG_TAG) { "the sign-up could not be changed: ${result.error}" }
                mutableState.update { it.copy(saving = false, error = result.error) }
            }
        }
    }

    /**
     * Re-reads the Einsatz, and the money too when its tab has already been opened.
     *
     * The content stays on screen while it runs — the member is looking at something they expect
     * to remain.
     */
    fun onRefresh() {
        mutableState.update { it.copy(refreshing = true) }
        reload(keepContent = true)
        if (mutableState.value.finances !is MissionFinancesPhase.Idle) {
            loadFinances()
        }
    }

    /**
     * Switches tab, fetching the finances the first time their tab is chosen.
     *
     * Arriving on Verwaltung fills the form from the Einsatz as last read; leaving clears it, so its
     * section counters are never stale.
     *
     * @param tab the tab the member picked.
     */
    fun onTabSelected(tab: MissionTab) {
        val leaving = mutableState.value.tab
        mutableState.update { it.copy(tab = tab) }
        when {
            tab == MissionTab.ADMIN -> admin.open()
            leaving == MissionTab.ADMIN -> admin.dismiss()
        }
        if (tab == MissionTab.FINANCES && mutableState.value.finances is MissionFinancesPhase.Idle) {
            loadFinances()
        }
        if (tab == MissionTab.UNITS) {
            loadCrewJobTypes()
            val current = mutableState.value
            if (current.canManage && current.unitShips.isEmpty()) {
                viewModelScope.launch {
                    val ships = seams.admin.unitShipOptions(missionId)
                    val members = (seams.people.members("") as? ApiResult.Success)?.value?.rows.orEmpty()
                    mutableState.update { state ->
                        state.copy(
                            unitShips = ships,
                            unitMembers = members.map { it.id to it.name },
                        )
                    }
                }
            }
        }
        if (tab == MissionTab.PARTICIPANTS) {
            val current = mutableState.value
            roster.loadJobTypes(current.canManage, current.rosterJobTypes) { types ->
                mutableState.update { it.copy(rosterJobTypes = types) }
            }
        }
    }

    /** Retries the money after a failure, without reloading the Einsatz around it. */
    fun onRetryFinances() {
        loadFinances()
    }

    /**
     * Reads the Einsatz.
     *
     * @param keepContent whether what is on screen survives until the answer arrives.
     */
    private fun reload(keepContent: Boolean) {
        if (!keepContent) {
            mutableState.update { it.copy(phase = MissionDetailPhase.Loading) }
        }
        viewModelScope.launch {
            when (val result = seams.read.detail(missionId)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(
                            detail = result.value,
                            phase = MissionDetailPhase.Ready,
                            refreshing = false,
                        )
                    }
                    retry.onSuccess()
                }

                is ApiResult.Failure -> {
                    KrtLog.w(LOG_TAG) { "Einsatz could not be read: ${result.error}" }
                    mutableState.update {
                        it.copy(
                            phase = MissionDetailPhase.Failed(result.error),
                            refreshing = false,
                        )
                    }
                    retry.onFailure(result.error, hasContent = false)
                }
            }
        }
    }

    /** Reads the money. */
    private fun loadFinances() {
        mutableState.update { it.copy(finances = MissionFinancesPhase.Loading) }
        viewModelScope.launch {
            mutableState.value =
                when (val result = seams.read.finances(missionId)) {
                    is ApiResult.Success -> {
                        mutableState.value.copy(finances = MissionFinancesPhase.Ready(result.value))
                    }

                    is ApiResult.Failure -> {
                        KrtLog.w(LOG_TAG) { "Einsatz finances could not be read: ${result.error}" }
                        mutableState.value.copy(finances = MissionFinancesPhase.Failed(result.error))
                    }
                }
        }
    }

    private companion object {
        /**
         * Sections whose change means the Einsatz itself has to be re-read.
         *
         * The roster and the core fields ride the same read, so they fold into one refresh; the
         * money does not, and has its own branch.
         */
        val ROSTER_SECTIONS =
            setOf(LiveSyncSections.MISSION_CREW, LiveSyncSections.MISSION_OVERVIEW)

        /** What the server's note column takes. */
        const val NOTE_LENGTH = 2000

        /** Log subsystem. No member name or amount is ever logged. */
        const val LOG_TAG = "missions"
    }
}

/**
 * The Einsatz with one participant row replaced by a newer copy, with the roster counts recomputed
 * from the rows.
 *
 * @param row the row as the server now has it.
 * @return the Einsatz, or itself unchanged when the row is not on this one.
 */
private fun MissionDetail.withRow(row: MissionParticipant): MissionDetail {
    if (participants.none { it.id == row.id }) {
        return this
    }
    val updated = participants.map { if (it.id == row.id) row else it }
    return copy(participants = updated, checkedInParticipants = updated.count { it.checkedIn })
}
