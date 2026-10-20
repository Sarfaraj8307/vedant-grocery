package com.vedant.admin.core.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes a PostgREST money value into [Money] (integer paise).
 *
 * PostgREST is inconsistent about how it serialises a `numeric(12,2)`: the same
 * column can arrive as `152`, `152.0` or `"152.00"` depending on the driver and
 * the exact value. Reading the raw literal via [JsonPrimitive.content] means all
 * three decode identically — no `Double` ever touches the value.
 *
 * On encode it writes a plain decimal rupee string ([Money.toPlainString]), the
 * exact counterpart of [Money.parse], so a value round-trips and a `numeric(12,2)`
 * column reads it correctly. (The app does not send money in Phase 1+ anyway.)
 */
object MoneySerializer : KSerializer<Money> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vedant.admin.core.money.Money", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Money {
        val literal = if (decoder is JsonDecoder) {
            decoder.decodeJsonElement().jsonPrimitive.content
        } else {
            decoder.decodeString()
        }
        return Money.parse(literal)
    }

    override fun serialize(encoder: Encoder, value: Money) {
        val literal = value.toPlainString()
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(literal))
        } else {
            encoder.encodeString(literal)
        }
    }
}
