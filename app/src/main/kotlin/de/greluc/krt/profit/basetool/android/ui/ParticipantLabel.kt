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
 * The name to draw for a participant, or the deleted-account label (REQ-DATA-008).
 *
 * The server sends an empty name for a hard-deleted account's retained rows. Resolved when drawing,
 * not in the mapper, because the name is also used to match crew slots to roster rows.
 *
 * @param name the name as read, empty exactly when the account behind the row is gone.
 * @return the name, or the deleted-account label.
 */
@Composable
internal fun participantLabel(name: String): String =
    name.ifBlank { stringResource(R.string.participant_deleted) }
