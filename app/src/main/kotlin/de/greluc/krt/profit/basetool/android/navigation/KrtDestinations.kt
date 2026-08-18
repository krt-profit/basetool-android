/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import de.greluc.krt.profit.basetool.android.core.designsystem.R

/** URI scheme the app registers for its own deep links. */
const val KRT_DEEP_LINK_SCHEME = "basetool"

/**
 * A destination of the app.
 *
 * Routes double as deep-link paths so a notification, a web link and an in-app navigation all end
 * up at the same entry — there is exactly one address per screen.
 *
 * @property route navigation route, without a leading slash.
 * @property title German screen title shown in the top bar.
 * @property iconRes glyph used in the bottom bar, the rail and the "Mehr" list.
 */
@Immutable
enum class KrtDestination(
    val route: String,
    val title: String,
    @param:DrawableRes val iconRes: Int,
) {
    /** The dashboard — the app's home and the target of every "back from a root". */
    Home("home", "Übersicht", R.drawable.ic_krt_dashboard),

    /** Einsätze — never called "Missionen" in user-visible copy. */
    Missions("missions", "Einsätze", R.drawable.ic_krt_target),

    /** Operationen — the umbrella records above single Einsätze. */
    Operations("operations", "Operationen", R.drawable.ic_krt_clipboard_check),

    /** Aufträge — the job-order queue. */
    Orders("orders", "Aufträge", R.drawable.ic_krt_clipboard_list),

    /** Lager — the stock tree. */
    Inventory("inventory", "Lager", R.drawable.ic_krt_crate),

    /** The overflow list of secondary destinations on phones. */
    More("more", "Mehr", R.drawable.ic_krt_more_h),

    /** Hangar — the member's ships. */
    Hangar("hangar", "Hangar", R.drawable.ic_krt_ship),

    /** Materialbörse — offers and requests between members. */
    Exchange("exchange", "Materialbörse", R.drawable.ic_krt_swap),

    /** Raffinerie — refinery runs and their yields. */
    Refinery("refinery", "Raffinerie", R.drawable.ic_krt_refinery),

    /** Mein Inventar & Blueprints — the member's personal stock. */
    PersonalInventory("personal-inventory", "Mein Inventar", R.drawable.ic_krt_blueprint),

    /** Bank — org-unit accounts and booking requests. */
    Bank("bank", "Bank", R.drawable.ic_krt_bank),

    /** Beförderung — evaluations and eligibility. */
    Promotion("promotion", "Beförderung", R.drawable.ic_krt_rank),

    /** Benachrichtigungen — the inbox behind the bell. */
    Notifications("notifications", "Benachrichtigungen", R.drawable.ic_krt_bell),

    /** Einstellungen — language, app lock, legal texts. */
    Settings("settings", "Einstellungen", R.drawable.ic_krt_gear),
    ;

    /** The deep link that opens this destination, e.g. `basetool://missions`. */
    val deepLink: String get() = "$KRT_DEEP_LINK_SCHEME://$route"
}

/**
 * The five phone destinations, in bar order.
 *
 * Five is the ceiling the design spec sets for a bottom bar; everything else lives behind
 * [KrtDestination.More].
 */
val PHONE_DESTINATIONS =
    listOf(
        KrtDestination.Home,
        KrtDestination.Missions,
        KrtDestination.Orders,
        KrtDestination.Inventory,
        KrtDestination.More,
    )

/**
 * The eight rail destinations for tablets.
 *
 * A rail has room for the three areas that a phone hides behind "Mehr", so Hangar, Raffinerie and
 * Materialbörse move up one level rather than being buried on the larger screen.
 */
val TABLET_DESTINATIONS =
    listOf(
        KrtDestination.Home,
        KrtDestination.Missions,
        KrtDestination.Orders,
        KrtDestination.Inventory,
        KrtDestination.Hangar,
        KrtDestination.Refinery,
        KrtDestination.Exchange,
        KrtDestination.More,
    )

/**
 * Everything reachable through the "Mehr" list.
 *
 * The list is identical on both form factors so a user who learned where something lives on a phone
 * finds it in the same place on a tablet; the rail merely offers a shortcut to three of them.
 */
val MORE_DESTINATIONS =
    listOf(
        KrtDestination.Operations,
        KrtDestination.Hangar,
        KrtDestination.Exchange,
        KrtDestination.Refinery,
        KrtDestination.PersonalInventory,
        KrtDestination.Bank,
        KrtDestination.Promotion,
        KrtDestination.Settings,
    )

/**
 * Resolves a route back to its destination.
 *
 * @param route the route to look up, or `null` while the graph is still settling.
 * @return the destination, or `null` for an unknown route — which the caller renders as the
 *   in-fiction "Signal Lost" screen rather than silently falling back to the dashboard.
 */
fun destinationOf(route: String?): KrtDestination? = KrtDestination.entries.firstOrNull { it.route == route }
