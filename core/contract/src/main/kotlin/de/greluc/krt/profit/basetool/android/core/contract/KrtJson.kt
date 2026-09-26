/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * The one JSON reader configured for this backend's wire format and the generated models.
 *
 * - `ignoreUnknownKeys`: fields the server adds are ignored (REQ-API-009).
 * - `coerceInputValues`: an unknown enum constant decodes as `null` instead of throwing; each
 *   repository decides what absent means.
 * - contextual `KrtDecimal`: registers the serializer that generated decimal properties marked
 *   `@Contextual` demand at runtime.
 */
val KrtJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        serializersModule =
            SerializersModule {
                contextual(KrtDecimalSerializer)
            }
    }
