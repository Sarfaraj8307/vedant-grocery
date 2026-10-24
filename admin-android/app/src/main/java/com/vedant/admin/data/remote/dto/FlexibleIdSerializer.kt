package com.vedant.admin.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes an id column that may arrive as a JSON **number** or a JSON **string**.
 *
 * WHY THIS EXISTS
 * `store_settings` is a singleton whose primary key is the integer `1`
 * (`id=1 CHECK id=1`), not a UUID — the customer PWA and this app both target it
 * with `eq("id", 1)`. PostgREST therefore returns `{"id": 1}`.
 *
 * A plain `val id: String?` cannot decode a JSON number: kotlinx.serialization
 * throws `Expected string literal but was '1'`, which propagated out of the
 * settings read as a failure and left the Store-settings screen unable to load
 * at all. Reading the primitive's literal text means `1` and `"1"` both decode
 * to `"1"`, so the id works as a filter value regardless of the column type —
 * and a future UUID column keeps working unchanged.
 *
 * Serialisation always writes a string, which PostgREST casts to the column type.
 */
object FlexibleIdSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vedant.admin.data.remote.dto.FlexibleId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content
        else decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String) {
        if (encoder is kotlinx.serialization.json.JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(value))
        } else {
            encoder.encodeString(value)
        }
    }
}
