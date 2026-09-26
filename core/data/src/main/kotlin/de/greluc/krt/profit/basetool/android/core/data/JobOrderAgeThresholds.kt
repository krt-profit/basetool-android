/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.data

import java.time.Duration
import java.time.Instant

/**
 * The ages in days at which an unfinished order is coloured as old, taken from the operator's
 * `job_order.age_yellow_days` and `job_order.age_red_days` system settings.
 *
 * The defaults match the values the migration seeds and the web app falls back to.
 *
 * @property yellowDays age from which an order is drawn in the warning colour.
 * @property redDays age from which it is drawn in the danger colour.
 */
data class JobOrderAgeThresholds(
    val yellowDays: Long = DEFAULT_YELLOW_DAYS,
    val redDays: Long = DEFAULT_RED_DAYS,
) {
    /**
     * Which band an order created at [createdAt] falls into.
     *
     * @param createdAt when the order was raised.
     * @param now the moment to measure against; overridden by tests.
     * @return the band; [JobOrderAgeBand.Fresh] for anything below the yellow threshold.
     */
    fun bandFor(
        createdAt: Instant,
        now: Instant = Instant.now(),
    ): JobOrderAgeBand {
        val days = Duration.between(createdAt, now).toDays()
        return when {
            days >= redDays -> JobOrderAgeBand.Old
            days >= yellowDays -> JobOrderAgeBand.Ageing
            else -> JobOrderAgeBand.Fresh
        }
    }

    companion object {
        /** `job_order.age_yellow_days` as the schema seeds it. */
        const val DEFAULT_YELLOW_DAYS: Long = 30

        /** `job_order.age_red_days` as the schema seeds it. */
        const val DEFAULT_RED_DAYS: Long = 90

        /** Settings key for [yellowDays]. */
        const val KEY_YELLOW_DAYS: String = "job_order.age_yellow_days"

        /** Settings key for [redDays]. */
        const val KEY_RED_DAYS: String = "job_order.age_red_days"
    }
}

/**
 * How old an order is, in the three bands the queue colours by.
 *
 * A band rather than a colour: `core:data` states the fact and the design system owns what it
 * looks like, which is the same split every other status in this app follows.
 */
enum class JobOrderAgeBand {
    /** Below the yellow threshold. */
    Fresh,

    /** At or past the yellow threshold. */
    Ageing,

    /** At or past the red threshold. */
    Old,
}
