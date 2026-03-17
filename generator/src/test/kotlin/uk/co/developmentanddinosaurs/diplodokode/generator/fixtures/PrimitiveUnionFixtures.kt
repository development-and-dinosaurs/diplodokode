package uk.co.developmentanddinosaurs.diplodokode.generator.fixtures

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Fixtures that mirror the exact output the generator produces from primitive-union-property-api.yaml
 * when [uk.co.developmentanddinosaurs.diplodokode.generator.KotlinxSerialisationStrategy] is configured.
 *
 * These are used by [uk.co.developmentanddinosaurs.diplodokode.generator.SerialisationIntegrationTest]
 * to exercise real kotlinx.serialization primitive union encode/decode without needing to compile
 * generated code at test runtime.
 */

interface Union2<A, B> {
    fun <R> fold(onFirst: (A) -> R, onSecond: (B) -> R): R

    fun firstOrNull(): A? = fold({ it }, { null })
    fun secondOrNull(): B? = fold({ null }, { it })

    fun first(): A = firstOrNull() ?: error("Expected First variant of Union2")
    fun second(): B = secondOrNull() ?: error("Expected Second variant of Union2")
}

interface Union3<A, B, C> {
    fun <R> fold(onFirst: (A) -> R, onSecond: (B) -> R, onThird: (C) -> R): R

    fun firstOrNull(): A? = fold({ it }, { null }, { null })
    fun secondOrNull(): B? = fold({ null }, { it }, { null })
    fun thirdOrNull(): C? = fold({ null }, { null }, { it })

    fun first(): A = firstOrNull() ?: error("Expected First variant of Union3")
    fun second(): B = secondOrNull() ?: error("Expected Second variant of Union3")
    fun third(): C = thirdOrNull() ?: error("Expected Third variant of Union3")
}

@Serializable(with = StringOrDoubleSerializer::class)
sealed interface StringOrDouble : Union2<String, Double> {
    @JvmInline
    value class StringValue(val value: String) : StringOrDouble

    @JvmInline
    value class DoubleValue(val value: Double) : StringOrDouble

    override fun <R> fold(onFirst: (String) -> R, onSecond: (Double) -> R): R = when (this) {
        is StringValue -> onFirst(value)
        is DoubleValue -> onSecond(value)
    }

    companion object {
        operator fun invoke(value: String): StringOrDouble = StringValue(value)
        operator fun invoke(value: Double): StringOrDouble = DoubleValue(value)
    }
}

object StringOrDoubleSerializer : KSerializer<StringOrDouble> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrDouble", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StringOrDouble) {
        when (value) {
            is StringOrDouble.StringValue -> encoder.encodeString(value.value)
            is StringOrDouble.DoubleValue -> encoder.encodeDouble(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): StringOrDouble {
        if (decoder !is JsonDecoder) throw SerializationException("Primitive union types only support JSON deserialization")
        val element = decoder.decodeJsonElement()
        if (element !is JsonPrimitive) throw SerializationException("Expected a primitive value for StringOrDouble")
        return when {
            element.isString -> StringOrDouble.StringValue(element.content)
            !element.isString && element.content.toDoubleOrNull() != null -> StringOrDouble.DoubleValue(element.content.toDouble())
            else -> throw SerializationException("Unexpected value for StringOrDouble: " + element.content)
        }
    }
}

@Serializable
data class Stegosaurus(
    val name: String,
    val score: StringOrDouble? = null,
)

@Serializable(with = TagValueSerializer::class)
sealed interface TagValue : Union3<String, Boolean, Int> {
    @JvmInline
    value class StringValue(val value: String) : TagValue

    @JvmInline
    value class BooleanValue(val value: Boolean) : TagValue

    @JvmInline
    value class IntValue(val value: Int) : TagValue

    override fun <R> fold(onFirst: (String) -> R, onSecond: (Boolean) -> R, onThird: (Int) -> R): R = when (this) {
        is StringValue -> onFirst(value)
        is BooleanValue -> onSecond(value)
        is IntValue -> onThird(value)
    }

    companion object {
        operator fun invoke(value: String): TagValue = StringValue(value)
        operator fun invoke(value: Boolean): TagValue = BooleanValue(value)
        operator fun invoke(value: Int): TagValue = IntValue(value)
    }
}

object TagValueSerializer : KSerializer<TagValue> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TagValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TagValue) {
        when (value) {
            is TagValue.StringValue -> encoder.encodeString(value.value)
            is TagValue.BooleanValue -> encoder.encodeBoolean(value.value)
            is TagValue.IntValue -> encoder.encodeInt(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): TagValue {
        if (decoder !is JsonDecoder) throw SerializationException("Primitive union types only support JSON deserialization")
        val element = decoder.decodeJsonElement()
        if (element !is JsonPrimitive) throw SerializationException("Expected a primitive value for TagValue")
        return when {
            element.isString -> TagValue.StringValue(element.content)
            !element.isString && element.content.toBooleanStrictOrNull() != null -> TagValue.BooleanValue(element.content.toBooleanStrict())
            !element.isString && element.content.toIntOrNull() != null -> TagValue.IntValue(element.content.toInt())
            else -> throw SerializationException("Cannot deserialize TagValue: unexpected value ${element.content}")
        }
    }
}

@Serializable
data class Pterodactyl(
    val name: String,
    val tag: TagValue? = null,
)
