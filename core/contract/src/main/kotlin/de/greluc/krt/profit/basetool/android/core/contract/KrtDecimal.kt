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
 * Every decimal the API sends — money, quantities, yields.
 *
 * The backend's decimals are `BigDecimal`, and the two obvious client-side types are both wrong.
 * `Double` loses cents: the bank is a double-entry ledger where a rounding error is not a display
 * bug but a wrong balance. `String` reads without loss but cannot be compared or summed without
 * every call site parsing it again, and one of them will forget.
 *
 * `java.math.BigDecimal` itself is not usable either, and the reason is worth stating because it
 * is what this class exists to work around: kotlinx.serialization ships no serializer for it, so
 * the generator marks such fields `@Contextual` — and `@Contextual` on a property describes the
 * property's own type, not a type argument. A `Map<String, BigDecimal>` therefore fails to compile
 * with "Serializer for element of type java.math.BigDecimal has not been found", which is where
 * this was found. A type that carries its own serializer works everywhere a type can appear.
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
 * Reads and writes [KrtDecimal] as a **JSON number**, keeping every digit the server sent.
 *
 * The precision lives in the transport, not only in the type: reading through `Double` would round
 * before the value ever reached `BigDecimal`, and writing through one would round on the way out.
 * So the number's own text is taken from the JSON tree and handed to `BigDecimal`, and on the way
 * back an unquoted literal is emitted — a quoted string would change the wire shape, and the
 * server's `BigDecimal` fields are declared `type: number`.
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
