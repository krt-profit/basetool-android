/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.core.contract

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * Every decimal the API sends — money, quantities, yields — held exactly as a `BigDecimal`.
 *
 * Carries its own serializer, because kotlinx.serialization has none for `BigDecimal` and
 * `@Contextual` does not reach type arguments such as `Map<String, BigDecimal>`.
 *
 * @property value the exact decimal, as the server sent it.
 */
@JvmInline
@Serializable(with = KrtDecimalSerializer::class)
value class KrtDecimal(
    val value: BigDecimal,
) {
    /**
     * The plain decimal form, never scientific notation.
     *
     * @return e.g. `1234.50`, suitable for formatting but not itself formatted for a locale.
     */
    override fun toString(): String = value.toPlainString()
}

/**
 * Reads and writes [KrtDecimal] as a JSON number through its literal text, never through `Double`, and writes an
 * unquoted literal back.
 */
object KrtDecimalSerializer : KSerializer<KrtDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("KrtDecimal", PrimitiveKind.STRING)

    /**
     * Reads the number's literal text.
     *
     * @param decoder the JSON decoder; this type is JSON-only by design.
     * @return the decoded value.
     * @throws IllegalArgumentException when the decoder is not a JSON one, or the literal is not a
     *   decimal — both are contract breaks rather than recoverable states.
     */
    override fun deserialize(decoder: Decoder): KrtDecimal {
        val json = requireNotNull(decoder as? JsonDecoder) { "KrtDecimal reads JSON only" }
        return KrtDecimal(BigDecimal(json.decodeJsonElement().jsonPrimitive.content))
    }

    /**
     * Writes the value as an unquoted JSON number.
     *
     * @param encoder the JSON encoder; this type is JSON-only by design.
     * @param value the value to write.
     * @throws IllegalArgumentException when the encoder is not a JSON one.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: KrtDecimal,
    ) {
        val json = requireNotNull(encoder as? JsonEncoder) { "KrtDecimal writes JSON only" }
        json.encodeJsonElement(JsonUnquotedLiteral(value.value.toPlainString()))
    }
}
