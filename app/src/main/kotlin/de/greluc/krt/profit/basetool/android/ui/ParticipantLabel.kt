/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.greluc.krt.profit.basetool.android.R

/**
 * The name to draw for a participant, with the deleted-account case spelled out.
 *
 * A hard-deleted account **keeps its rows**: an operation that has already been settled must not
 * silently redistribute the shares and the expenses that member advanced (backend `REQ-DATA-008`).
 * The server sends **no name** for such a row, and that emptiness is the contract's own signal for
 * this case rather than an accident — `OperationPayoutDto` says so in as many words, and the web
 * has resolved it to „Gelöschter Nutzer" since the key existed.
 *
 * The app drew an empty cell instead, which reads as a rendering fault rather than as a fact about
 * the row. This is the same wording as the web's `mission.participant.deleted`, per the copy rule
 * to reuse the web's keys where they exist.
 *
 * **Resolved when drawing, never in the mapper.** `MissionParticipant.name` is also what the
 * Einheiten tab matches a crew slot against its roster row by (`MissionCrewMember.name` carries no
 * participant id), so substituting a label upstream would make two deleted accounts match each
 * other and borrow one another's check-in mark.
 *
 * @param name the name as read, empty exactly when the account behind the row is gone.
 * @return the name, or the deleted-account label.
 */
@Composable
internal fun participantLabel(name: String): String =
    name.ifBlank { stringResource(R.string.participant_deleted) }
