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
 * @property locked whether the caller may not open it. A locked tab is **drawn** — 45 % alpha plus
 *   a lock glyph — and stays tappable; the tap raises the refusal instead of switching. Hiding it
 *   is forbidden: this organisation grants roles by hand, and a function nobody sees is never
 *   requested (design ch. 06 artboard 6, ch. 09 artboards 11–14).
 * @property count how many rows its content holds, or `null` when it holds no list — or holds one
 *   that has not been read yet. A `0` is a statement ("none"), `null` is silence, and the two are
 *   not the same thing to a member deciding whether to open the tab.
 */
data class KrtPageTab(
    val label: String,
    val count: Int? = null,
    val locked: Boolean = false,
)
