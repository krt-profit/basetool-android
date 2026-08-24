/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

/**
 * One live-sync room the app can listen to (REQ-APP-SYNC-001, server ADR-0143).
 *
 * The wire form is the string the server canonicalises to — a prefix, optionally a colon and a
 * lower-case UUID. It is built here rather than assembled at each call site because the string is
 * the room key on both ends: a topic that differs from the server's canonical form by so much as
 * the case of its id opens a second, empty room, and nothing anywhere reports it.
 *
 * The factories are the only way to make one, so a screen cannot invent a room the server does not
 * serve. A topic the server refuses is dropped from the stream's set and named in [LiveSyncEvent.
 * Subscribed], which is the app's cue to treat that screen as poll-only.
 *
 * @property wire the canonical topic string.
 * @property global whether this is a tool-wide room rather than one resource's.
 */
class LiveSyncTopic private constructor(
    val wire: String,
    val global: Boolean,
) {
    /**
     * Two topics are the same room when their wire form is.
     *
     * @param other the other value.
     * @return whether both name the same room.
     */
    override fun equals(other: Any?): Boolean = other is LiveSyncTopic && other.wire == wire

    /**
     * Hashes on the wire form, so a topic keys a map by the room it names.
     *
     * @return the hash.
     */
    override fun hashCode(): Int = wire.hashCode()

    /**
     * Renders the room's wire form, which is also what a log line should carry.
     *
     * @return the canonical topic string.
     */
    override fun toString(): String = wire

    companion object {
        /** The Einsatz list. */
        val MISSIONS: LiveSyncTopic = global("missions")

        /** The Auftrags-queue. Refused unless the member may see the queue at all. */
        val ORDERS: LiveSyncTopic = global("orders")

        /** The shared Lager. */
        val INVENTORY: LiveSyncTopic = global("inventory")

        /** The Materialbörse board and its Gesuche. */
        val MATERIALBOARD: LiveSyncTopic = global("materialboard")

        /** The member's Raffinerie orders. */
        val REFINERY: LiveSyncTopic = global("refinery")

        /** The org-unit bank overview. */
        val ORGUNIT_BANK: LiveSyncTopic = global("orgunit-bank")

        /**
         * One Einsatz.
         *
         * @param missionId the Einsatz.
         * @return its room.
         */
        fun mission(missionId: String): LiveSyncTopic = resource("mission", missionId)

        /**
         * One Operation.
         *
         * @param operationId the Operation.
         * @return its room.
         */
        fun operation(operationId: String): LiveSyncTopic = resource("operation", operationId)

        /**
         * One Auftrag.
         *
         * @param orderId the Auftrag.
         * @return its room.
         */
        fun order(orderId: String): LiveSyncTopic = resource("order", orderId)

        /**
         * One Raffinerie-Order.
         *
         * @param orderId the Raffinerie-Order.
         * @return its room.
         */
        fun refineryOrder(orderId: String): LiveSyncTopic = resource("refinery-order", orderId)

        /**
         * One bank account.
         *
         * @param accountId the account.
         * @return its room.
         */
        fun bankAccount(accountId: String): LiveSyncTopic = resource("bank", accountId)

        /**
         * Parses a topic the server named back at us.
         *
         * Only the server's own strings reach this — the `subscribed` list and the `topic` of a
         * `changed` frame — so it recognises a room by whether it carries an id, exactly as the
         * server's parser does, and answers `null` for anything else rather than inventing a room.
         *
         * @param wire the topic as it arrived.
         * @return the topic, or `null` if it is not one this build knows.
         */
        fun parse(wire: String): LiveSyncTopic? {
            val trimmed = wire.trim()
            val separator = trimmed.indexOf(':')
            return when {
                trimmed.isEmpty() -> null
                separator < 0 -> GLOBAL_TOPICS[trimmed]
                else -> resourceOrNull(trimmed.substring(0, separator), trimmed.substring(separator + 1))
            }
        }

        /**
         * Builds a per-resource topic when both halves are ones this build serves.
         *
         * @param prefix the wire prefix.
         * @param id the resource id, still as it arrived.
         * @return the topic, or `null` if the prefix is unknown or the id empty.
         */
        private fun resourceOrNull(
            prefix: String,
            id: String,
        ): LiveSyncTopic? =
            if (prefix in RESOURCE_PREFIXES && id.isNotEmpty()) {
                LiveSyncTopic("$prefix:${id.lowercase()}", global = false)
            } else {
                null
            }

        private fun global(prefix: String) = LiveSyncTopic(prefix, global = true)

        private fun resource(
            prefix: String,
            id: String,
        ) = LiveSyncTopic("$prefix:${id.lowercase()}", global = false)

        private val GLOBAL_TOPICS: Map<String, LiveSyncTopic> by lazy {
            listOf(MISSIONS, ORDERS, INVENTORY, MATERIALBOARD, REFINERY, ORGUNIT_BANK)
                .associateBy { it.wire }
        }

        private val RESOURCE_PREFIXES =
            setOf("mission", "operation", "order", "refinery-order", "bank")
    }
}

/** Section keys a room can name. Grouped by room so a screen cannot ask for a key of another. */
object LiveSyncSections {
    /** An Einsatz's participant list. */
    const val MISSION_CREW: String = "crew"

    /** An Einsatz's Finanzen tab. */
    const val MISSION_FINANCE: String = "finance"

    /** An Einsatz's core fields. */
    const val MISSION_OVERVIEW: String = "overview"

    /** The Einsatz list. */
    const val MISSIONS_LIST: String = "list"

    /** An Operation's payout state. */
    const val OPERATION_PAYOUT: String = "payout"

    /** An Operation's roll-up. */
    const val OPERATION_FINANCE: String = "finance"

    /** An Auftrag's assignees. */
    const val ORDER_ASSIGNEES: String = "assignees"

    /** An Auftrag's header, which carries its status. */
    const val ORDER_HEADER: String = "header"

    /** The Auftrags-queue. */
    const val ORDERS_QUEUE: String = "queue"

    /** The shared Lager's stock. */
    const val INVENTORY_STOCK: String = "stock"

    /** The Materialbörse's Angebote. */
    const val BOARD_OFFERS: String = "board"

    /** The Materialbörse's Gesuche. */
    const val BOARD_REQUESTS: String = "requests"

    /** The Raffinerie queue. */
    const val REFINERY_QUEUE: String = "queue"

    /** A Raffinerie-Order's own fields. */
    const val REFINERY_ORDER: String = "order"

    /** A Raffinerie-Order's Einlagern dialog — the section a booking announces. */
    const val REFINERY_STORE: String = "store"

    /** A bank account's settings region. */
    const val ORGUNIT_BANK_SETTINGS: String = "orgUnitBankSettings"

    /** A bank account's balance and bookings. */
    const val BANK_ACCOUNT: String = "account"
}
