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
    @param:StringRes private val navTitleRes: Int? = null,
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
    Exchange(
        "exchange",
        R.string.nav_exchange,
        DesignR.drawable.ic_krt_swap,
        // „BÖRSE" on the rail, „Materialbörse" everywhere else. Both the navigation map (ch. 03)
        // and the tablet dashboard (ch. 05) label the rail entry with the short form, while the
        // „Mehr" list spells it out — a rail column is 88 dp wide and the compound crowds it.
        navTitleRes = R.string.nav_exchange_short,
    ),

    /** Raffinerie — refinery runs and their yields. */
    Refinery("refinery", R.string.nav_refinery, DesignR.drawable.ic_krt_refinery),

    /** Mein Inventar & Blueprints — the member's personal stock. */
    PersonalInventory("personal-inventory", R.string.nav_personal_inventory, DesignR.drawable.ic_krt_blueprint),

    /** Bank — org-unit accounts and booking requests. */
    Bank("bank", R.string.nav_bank, DesignR.drawable.ic_krt_bank),

    /**
     * Handel — the material catalogue and what the universe pays for it.
     *
     * One entry, not three: design chapter 16 puts the Preis-Übersicht and the Profitberechnung in
     * this screen's own overflow rather than in the „Mehr" list, because all three answer the same
     * question at different resolutions.
     */
    Materials("materials", R.string.nav_materials, DesignR.drawable.ic_krt_list),

    /** Benachrichtigungen — the inbox behind the bell. */
    Notifications("notifications", R.string.nav_notifications, DesignR.drawable.ic_krt_bell),

    /** Einstellungen — language, app lock, legal texts. */
    Settings("settings", R.string.nav_settings, DesignR.drawable.ic_krt_gear),

    /** The open-source notice, pushed from Einstellungen. */
    Licenses("licenses", R.string.licenses_title, DesignR.drawable.ic_krt_list),

    /**
     * Where a link this build does not know ends up — design ch. 03, „Unbekannte Route → 404
     * in-fiction", drawn in ch. 14.
     *
     * Reached only through the catch-all deep link the graph registers for it, never by tapping
     * anything. It carries the in-fiction wording rather than the placeholder's, because a link
     * that goes nowhere *is* a failure, whereas an area with no screen yet is not.
     */
    NotFound("not-found", R.string.route_not_found_title, DesignR.drawable.ic_krt_warning),

    /** The Fleetview import, pushed from the Hangar's overflow. */
    FleetImport("hangar-import", R.string.fleet_import_title, DesignR.drawable.ic_krt_upload),

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

    /** The Material × Terminal price matrix, pushed from Handel's overflow. */
    MaterialMatrix("material-matrix", R.string.materials_matrix_title, DesignR.drawable.ic_krt_list),

    /** The profit calculation for one ship's full load, pushed from Handel's overflow. */
    MaterialProfit("material-profit", R.string.materials_profit_title, DesignR.drawable.ic_krt_ship),

    /**
     * One material's prices across every terminal, pushed from the Handel list.
     *
     * Parameterised for the reason the Einsatz detail is: the page is about one material, and the
     * route has to say which.
     */
    MaterialDetail(
        "material/{materialId}",
        R.string.materials_detail_title,
        DesignR.drawable.ic_krt_list,
    ),

    /** One bank account with its ledger, pushed from the Konten list. */
    BankAccount("bank-account/{accountId}", R.string.bank_title, DesignR.drawable.ic_krt_bank),

    /** One holder's custody, pushed from the holder register in the Konten tab. */
    BankHolder("bank-holder/{holderId}", R.string.bank_title, DesignR.drawable.ic_krt_users),

    /** The form for a new refinery order, pushed from the Raffinerie list. */
    RefineryCreate(
        "refinery-create",
        R.string.refinery_create_title,
        DesignR.drawable.ic_krt_refinery,
    ),

    /**
     * The form that rewrites one Auftrag, pushed from its detail's overflow.
     *
     * The same screen as [OrderCreate], pre-filled — design ch. 10 artboard 10 is explicit that
     * there is no second layout — so it carries the order's id and nothing else.
     */
    OrderEdit("order-edit/{orderId}", R.string.order_edit_title, DesignR.drawable.ic_krt_edit),

    /**
     * The stock rows linked to one Auftrag, pushed from its detail's overflow.
     *
     * It belongs to the **Auftrag** and not to the material reference — `material.collection.back`
     * reads „Zurück zum Auftrag", and design chapter 16 corrected itself about that.
     */
    OrderCollection(
        "order-collection/{orderId}",
        R.string.order_collection_title,
        DesignR.drawable.ic_krt_crate,
    ),

    /** The form for a new material order, pushed from the Aufträge queue. */
    OrderCreate(
        "order-create",
        R.string.order_create_title,
        DesignR.drawable.ic_krt_clipboard_list,
    ),

    /** One job order in full, pushed from the queue. */
    OrderDetail(
        "order/{orderId}",
        R.string.order_detail_title,
        DesignR.drawable.ic_krt_clipboard_list,
    ),

    /** One Raffinerie order with its yield table, pushed from „Meine Orders". */
    RefineryOrder(
        "refinery-order/{refineryOrderId}",
        R.string.refinery_order_title,
        DesignR.drawable.ic_krt_refinery,
    ),
    ;

    /**
     * The label a bar or rail entry carries, which is not always the destination's name.
     *
     * Falls back to [titleRes], so only a destination whose navigation label the design shortens
     * needs to say so.
     */
    @get:StringRes
    val navLabelRes: Int get() = navTitleRes ?: titleRes

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
        KrtDestination.Materials,
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
        // Pushed from the Hangar's overflow, so the bar keeps saying Hangar while it is open.
        KrtDestination.FleetImport to KrtDestination.Hangar,
        // Without this the bar would light up "Übersicht" — the fallback for an unknown
        // destination — while the member is looking at an Einsatz they opened from "Einsätze".
        KrtDestination.MissionDetail to KrtDestination.Missions,
        // Without this the bar would light up „Übersicht" while a member reads a material they
        // opened from „Handel".
        KrtDestination.MaterialDetail to KrtDestination.Materials,
        KrtDestination.OrderEdit to KrtDestination.Orders,
        KrtDestination.OrderCollection to KrtDestination.Orders,
        KrtDestination.MaterialMatrix to KrtDestination.Materials,
        KrtDestination.MaterialProfit to KrtDestination.Materials,
        // Same reason, one list over: an Operation is opened from "Operationen", which itself sits
        // behind "Mehr", and the bar has to keep saying so.
        KrtDestination.OperationDetail to KrtDestination.Operations,
        KrtDestination.BankAccount to KrtDestination.Bank,
        KrtDestination.BankHolder to KrtDestination.Bank,
        KrtDestination.RefineryCreate to KrtDestination.Refinery,
        KrtDestination.OrderCreate to KrtDestination.Orders,
        KrtDestination.OrderDetail to KrtDestination.Orders,
        KrtDestination.RefineryOrder to KrtDestination.Refinery,
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
 * The route that opens one material's prices.
 *
 * @param materialId the material to open.
 * @return the concrete route, with the id substituted into [KrtDestination.MaterialDetail]'s
 *   pattern.
 */
fun materialDetailRoute(materialId: String): String = "material/" + materialId

/** The name of the id argument in [KrtDestination.MaterialDetail]'s route. */
const val MATERIAL_ID_ARG: String = "materialId"

/**
 * The route that opens the edit form for one Auftrag.
 *
 * @param orderId the Auftrag to rewrite.
 * @return the concrete route.
 */
fun orderEditRoute(orderId: String): String = "order-edit/" + orderId

/**
 * The route that opens one Auftrag's linked stock rows.
 *
 * @param orderId the Auftrag.
 * @return the concrete route.
 */
fun orderCollectionRoute(orderId: String): String = "order-collection/" + orderId

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

/**
 * The route of one holder's custody detail — design chapter 12, artboard 8.
 *
 * @param holderId whose custody to show.
 * @return the route.
 */
fun bankHolderRoute(holderId: String): String = "bank-holder/" + holderId

/** The name of the id argument in [KrtDestination.BankAccount]'s route. */
const val ACCOUNT_ID_ARG: String = "accountId"

/** The name of the id argument in [KrtDestination.BankHolder]'s route. */
const val HOLDER_ID_ARG: String = "holderId"

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
 * The route that opens one Raffinerie order.
 *
 * @param orderId the order to open.
 * @return the concrete route, with the id substituted into [KrtDestination.RefineryOrder]'s
 *   pattern.
 */
fun refineryOrderRoute(orderId: String): String = "refinery-order/" + orderId

/** The name of the id argument in [KrtDestination.RefineryOrder]'s route. */
const val REFINERY_ORDER_ID_ARG: String = "refineryOrderId"

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
 * @return the destination, or `null` while the graph is still settling — the caller falls back to
 *   Übersicht for the **top bar's** identity, which is a question about chrome and not about
 *   routing. An unknown *link* is a different question and is answered before the graph is asked
 *   at all: `UnknownLinkGuard` sends it to [NotFound]. This KDoc used to claim the fallback was the
 *   404, next to a call site reading `?: KrtDestination.Home`, and that mismatch is why the rule
 *   went unimplemented for as long as it did.
 */
fun destinationOf(route: String?): KrtDestination? = KrtDestination.entries.firstOrNull { it.route == route }
