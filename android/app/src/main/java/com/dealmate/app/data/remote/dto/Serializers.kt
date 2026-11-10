package com.dealmate.app.data.remote.dto

import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.ShippingStatus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The one Json configuration used for the API, the Room cache and tests. */
val DealMateJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

/** Maps lowercase wire strings to enum constants; anything unknown becomes [fallback] instead of crashing. */
abstract class LenientEnumSerializer<E : Enum<E>>(
    name: String,
    private val values: List<E>,
    private val fallback: E,
    private val wire: (E) -> String = { it.name.lowercase() },
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(wire(value))
    override fun deserialize(decoder: Decoder): E {
        val raw = decoder.decodeString().trim().lowercase()
        return values.firstOrNull { wire(it) == raw } ?: fallback
    }
}

object MarketplaceSerializer :
    LenientEnumSerializer<Marketplace>("Marketplace", Marketplace.entries, Marketplace.UNKNOWN, { it.wire })

object MatchTypeSerializer : LenientEnumSerializer<MatchType>("MatchType", MatchType.entries, MatchType.UNKNOWN)

object PriceConfidenceSerializer :
    LenientEnumSerializer<PriceConfidence>("PriceConfidence", PriceConfidence.entries, PriceConfidence.UNKNOWN)

object ShippingStatusSerializer :
    LenientEnumSerializer<ShippingStatus>("ShippingStatus", ShippingStatus.entries, ShippingStatus.UNKNOWN)

/**
 * ISO-8601 datetimes as produced by Pydantic ("2026-01-05T08:30:00Z", "...+00:00", with or without
 * fractional seconds; a naive datetime is read as UTC). Unparseable values become null.
 */
@OptIn(ExperimentalSerializationApi::class)
object LenientInstantSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInstant", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: Instant?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant? {
        if (!decoder.decodeNotNullMark()) return decoder.decodeNull()
        return parse(decoder.decodeString())
    }

    fun parse(text: String): Instant? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(t).toInstant() }
            .recoverCatching { LocalDateTime.parse(t.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }
            .getOrNull()
    }
}
