/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.navigation.KrtDestination
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/**
 * The four shortcuts on the dashboard, in the order design chapter 05 draws them.
 *
 * Each opens the **surface** its action lives on, not the action itself. There is no global "check
 * in" — a check-in belongs to one Einsatz — so the tile opens the Einsatz list the member picks
 * from. A tile that guessed which Einsatz they meant would be wrong on exactly the days it matters.
 *
 * The order is the chapter's and is deliberately fixed: shortcuts that move with the data are
 * shortcuts nobody can build muscle memory on.
 *
 * @property labelRes the tile's caption.
 * @property iconRes the glyph above it, from the in-house stroke set.
 * @property destination where a tap goes.
 */
enum class QuickAction(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val destination: KrtDestination,
) {
    /** Check in on an Einsatz — via the list, since check-in is per Einsatz. */
    CheckIn(R.string.dashboard_quick_check_in, DesignR.drawable.ic_krt_target, KrtDestination.Missions),

    /** Book material into the Lager. */
    BookIn(R.string.dashboard_quick_book_in, DesignR.drawable.ic_krt_crate, KrtDestination.Inventory),

    /** Raise or work a job order. */
    Order(R.string.dashboard_quick_order, DesignR.drawable.ic_krt_clipboard_list, KrtDestination.Orders),

    /** Offer material on the Materialbörse. */
    Offer(R.string.dashboard_quick_offer, DesignR.drawable.ic_krt_swap, KrtDestination.Exchange),
}
