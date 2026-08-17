/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.designsystem.modifier

/**
 * Which corners carry a HUD bracket.
 *
 * The design system draws brackets diagonally opposed, never on all four corners: the HUD box and
 * the toast use top-left plus bottom-right, which reads as a targeting reticle rather than a frame.
 */
enum class KrtBracketCorners {
    /** Top-left and bottom-right — the HUD box, toast and modal spelling. */
    TopLeftBottomRight,

    /** Top-right and bottom-left, for mirrored layouts. */
    TopRightBottomLeft,
}
