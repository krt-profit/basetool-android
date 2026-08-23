/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import de.greluc.krt.profit.basetool.android.R
import de.greluc.krt.profit.basetool.android.core.designsystem.R as DesignR

/** URI scheme the app registers for its own deep links. */
const val KRT_DEEP_LINK_SCHEME = "basetool"

/**
 * A destination of the app.
 *
 * Routes double as deep-link paths so a notification, a web link and an in-app navigation all end
 * up at the same entry — there is exactly one address per screen.
 *
 * Titles are **string resources, not literals**. They are the app's most visible copy — the bottom
 * bar, the rail, the top bar and the "Mehr" list all render them — so a literal here would leave the
 * whole navigation in German for a member who switched the app to English, which is the one place
 * the gap is impossible to miss and the easiest to overlook while writing the enum.
 *
 * @property route navigation route, without a leading slash.
 * @property titleRes screen title shown in the top bar and beside the glyph.
 * @property iconRes glyph used in the bottom bar, the rail and the "Mehr" list.
 */
@Immutable
enum class KrtDestination(
    val route: String,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    /** The dashboard — the app's home and the target of every "back from a root". */
    Home("home", R.string.nav_home, DesignR.drawable.ic_krt_dashboard),

    /** Einsätze — never called "Missionen" in user-visible copy. */
    Missions("missions", R.string.nav_missions, DesignR.drawable.ic_krt_target),

    /** Operationen — the umbrella records above single Einsätze. */
    Operations("operations", R.string.nav_operations, DesignR.drawable.ic_krt_clipboard_check),

    /** Aufträge — the job-order queue. */
    Orders("orders", R.string.nav_orders, DesignR.drawable.ic_krt_clipboard_list),

    /** Lager — the stock tree. */
    Inventory("inventory", R.string.nav_inventory, DesignR.drawable.ic_krt_crate),

    /** The overflow list of secondary destinations on phones. */
    More("more", R.string.nav_more, DesignR.drawable.ic_krt_more_h),

    /** Hangar — the member's ships. */
    Hangar("hangar", R.string.nav_hangar, DesignR.drawable.ic_krt_ship),

    /** Materialbörse — offers and requests between members. */
    Exchange("exchange", R.string.nav_exchange, DesignR.drawable.ic_krt_swap),

    /** Raffinerie — refinery runs and their yields. */
    Refinery("refinery", R.string.nav_refinery, DesignR.drawable.ic_krt_refinery),

    /** Mein Inventar & Blueprints — the member's personal stock. */
    PersonalInventory("personal-inventory", R.string.nav_personal_inventory, DesignR.drawable.ic_krt_blueprint),

    /** Bank — org-unit accounts and booking requests. */
    Bank("bank", R.string.nav_bank, DesignR.drawable.ic_krt_bank),

    /** Beförderung — evaluations and eligibility. */
    Promotion("promotion", R.string.nav_promotion, DesignR.drawable.ic_krt_rank),

    /** Benachrichtigungen — the inbox behind the bell. */
    Notifications("notifications", R.string.nav_notifications, DesignR.drawable.ic_krt_bell),

    /** Einstellungen — language, app lock, legal texts. */
    Settings("settings", R.string.nav_settings, DesignR.drawable.ic_krt_gear),

    /** The open-source notice, pushed from Einstellungen. */
    Licenses("licenses", R.string.licenses_title, DesignR.drawable.ic_krt_list),

    /**
     * One Einsatz in full, pushed from the Einsatz list.
     *
     * The only parameterised route in the graph. Its deep link therefore carries the id too
     * (`basetool://mission/<id>`), which is what lets a notification about one Einsatz open that
     * Einsatz rather than the list it happens to be in.
     */
    MissionDetail("mission/{missionId}", R.string.mission_detail_title, DesignR.drawable.ic_krt_target),

    /**
     * One Operation in full, pushed from the Operationen list.
     *
     * Parameterised like the Einsatz detail and for the same reason: a link about one Operation has
     * to open that Operation, not the list it sits in.
     */
    OperationDetail(
        "operation/{operationId}",
        R.string.operation_detail_title,
        DesignR.drawable.ic_krt_clipboard_check,
    ),

    /** One bank account with its ledger, pushed from the Konten list. */
    BankAccount("bank-account/{accountId}", R.string.bank_title, DesignR.drawable.ic_krt_bank),

    /** One job order in full, pushed from the queue. */
    OrderDetail(
        "order/{orderId}",
        R.string.order_detail_title,
        DesignR.drawable.ic_krt_clipboard_list,
    ),
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
 * Destinations that are **pushed from another screen** rather than being reachable on their own,
 * mapped to the destination they belong to.
 *
 * Two things read this. The navigation bar highlights the parent's root while a sub-page is open,
 * so the bar never claims the member is somewhere they are not; and the top bar shows a back arrow.
 * Without the mapping the open-source notice would light up "Übersicht" — the fallback for an
 * unknown destination — while showing a page reached from "Mehr".
 */
val SUB_DESTINATIONS: Map<KrtDestination, KrtDestination> =
    mapOf(
        KrtDestination.Licenses to KrtDestination.Settings,
        // Without this the bar would light up "Übersicht" — the fallback for an unknown
        // destination — while the member is looking at an Einsatz they opened from "Einsätze".
        KrtDestination.MissionDetail to KrtDestination.Missions,
        // Same reason, one list over: an Operation is opened from "Operationen", which itself sits
        // behind "Mehr", and the bar has to keep saying so.
        KrtDestination.OperationDetail to KrtDestination.Operations,
        KrtDestination.BankAccount to KrtDestination.Bank,
        KrtDestination.OrderDetail to KrtDestination.Orders,
    )

/**
 * The route that opens one Einsatz.
 *
 * @param missionId the Einsatz to open.
 * @return the concrete route, with the id substituted into [KrtDestination.MissionDetail]'s
 *   pattern. Built here rather than at the call site so the pattern and its filling cannot drift.
 */
fun missionDetailRoute(missionId: String): String = "mission/" + missionId

/** The name of the id argument in [KrtDestination.MissionDetail]'s route. */
const val MISSION_ID_ARG: String = "missionId"

/**
 * The route that opens one Operation.
 *
 * @param operationId the Operation to open.
 * @return the concrete route, with the id substituted into [KrtDestination.OperationDetail]'s
 *   pattern. Built here so the pattern and its filling cannot drift.
 */
fun operationDetailRoute(operationId: String): String = "operation/" + operationId

/** The name of the id argument in [KrtDestination.OperationDetail]'s route. */
const val OPERATION_ID_ARG: String = "operationId"

/**
 * The route that opens one bank account.
 *
 * @param accountId the account to open.
 * @return the concrete route, with the id substituted into [KrtDestination.BankAccount]'s pattern.
 */
fun bankAccountRoute(accountId: String): String = "bank-account/" + accountId

/** The name of the id argument in [KrtDestination.BankAccount]'s route. */
const val ACCOUNT_ID_ARG: String = "accountId"

/**
 * The route that opens one job order.
 *
 * @param orderId the order to open.
 * @return the concrete route, with the id substituted into [KrtDestination.OrderDetail]'s pattern.
 */
fun orderDetailRoute(orderId: String): String = "order/" + orderId

/** The name of the id argument in [KrtDestination.OrderDetail]'s route. */
const val ORDER_ID_ARG: String = "orderId"

/**
 * Resolves a destination to the navigation root it belongs to.
 *
 * @param destination any destination.
 * @return its parent for a pushed sub-page, otherwise the destination itself.
 */
fun rootOf(destination: KrtDestination): KrtDestination = SUB_DESTINATIONS[destination] ?: destination

/**
 * Resolves a route back to its destination.
 *
 * @param route the route to look up, or `null` while the graph is still settling.
 * @return the destination, or `null` for an unknown route — which the caller renders as the
 *   in-fiction "Signal Lost" screen rather than silently falling back to the dashboard.
 */
fun destinationOf(route: String?): KrtDestination? = KrtDestination.entries.firstOrNull { it.route == route }
