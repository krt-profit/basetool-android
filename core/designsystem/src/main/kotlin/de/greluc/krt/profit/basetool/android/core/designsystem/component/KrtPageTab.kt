/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.component

/**
 * One tab of a page-level tab row.
 *
 * @property label the tab's name; rendered uppercase.
 * @property locked whether the caller may not open it; a locked tab is drawn at 45 % with a lock
 *   glyph and stays tappable to raise the refusal, never hidden.
 * @property count how many rows its content holds, or `null` when it holds no list or it is not read
 *   yet; `0` means none.
 */
data class KrtPageTab(
    val label: String,
    val count: Int? = null,
    val locked: Boolean = false,
)
