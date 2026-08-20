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
 * The one JSON reader configured for this backend's wire format.
 *
 * It is here, in the module that owns the generated models, because the two are one decision: a
 * reader without the contextual registration below **compiles against every model and throws when
 * one is decoded**, and it throws only for the models that happen to carry a decimal. Each
 * repository owning its own `Json { ignoreUnknownKeys = true }` was fine while every DTO was
 * hand-written; the moment the models are generated, the reader has to know what the generator
 * emitted.
 *
 * Three settings, each load-bearing:
 *
 * - **`ignoreUnknownKeys`** — the server may add a field this build has never heard of, and by
 *   `REQ-API-009` additive change is explicitly free. Refusing it would turn every server
 *   improvement into a crash on phones nobody can redeploy.
 * - **`coerceInputValues`** — the same rule applied to enums, and the setting this module would be
 *   dangerous without. Generated enums are **strict**: kotlinx.serialization throws on a constant
 *   it does not know. Adding a value to a server-side enum is additive change, which the contract
 *   says is free — and the app reads one of those enums on the *login* path, so without coercion a
 *   fourth approval status would crash every installed build at the one moment a member cannot
 *   work around it. Coerced, an unknown constant arrives as `null`, and each repository decides
 *   what absent means (`ApprovalStatus.fromWire` maps it to "not cleared", so the gate stays).
 *   This is exactly the behaviour the hand-written DTOs had by parsing the field as a plain
 *   `String`; the reader has to keep it now that the models are generated.
 * - **contextual `KrtDecimal`** — the generator marks decimal properties `@Contextual`, which
 *   *overrides* a type's own serializer and demands one from the module at runtime. Registering it
 *   here is what makes `KrtDecimal`'s serializer reachable from a generated model.
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
