package uk.co.developmentanddinosaurs.diplodokode.generator.openapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlDynamicSerializer


@Serializable
data class OpenApiSpec(
  val components: Components? = null,
)

@Serializable
data class Components(
  val schemas: Map<String, Schema>? = null,
)

@Serializable
data class Schema(
  @SerialName($$"$ref") val ref: String? = null,
  val additionalProperties: AdditionalProperties? = null,
  val allOf: List<Schema>? = null,
  val anyOf: List<Schema>? = null,
  val default: DefaultValue? = null,
  val description: String? = null,
  val discriminator: Discriminator? = null,
  val enum: List<String>? = null,
  val format: String? = null,
  val items: Schema? = null,
  val nullable: Boolean? = null,
  val oneOf: List<Schema>? = null,
  val properties: Map<String, Schema>? = null,
  val required: List<String>? = null,
  val type: String? = null,
)

/**
 * Represents the OpenAPI `additionalProperties` field, which can be:
 * - `true` — any additional properties are allowed ([Allowed])
 * - `false` — no additional properties are allowed ([Forbidden])
 * - a schema object — additional properties must conform to the given schema ([Typed])
 */
@Serializable(with = AdditionalPropertiesSerializer::class)
sealed class AdditionalProperties {
  data object Allowed : AdditionalProperties()
  data object Forbidden : AdditionalProperties()
  data class Typed(val schema: Schema) : AdditionalProperties()
}

internal object AdditionalPropertiesSerializer : KSerializer<AdditionalProperties> {
  override val descriptor: SerialDescriptor = YamlDynamicSerializer.descriptor

  override fun serialize(encoder: Encoder, value: AdditionalProperties) {
    when (value) {
      is AdditionalProperties.Allowed -> encoder.encodeBoolean(true)
      is AdditionalProperties.Forbidden -> encoder.encodeBoolean(false)
      is AdditionalProperties.Typed -> encoder.encodeSerializableValue(Schema.serializer(), value.schema)
    }
  }

  override fun deserialize(decoder: Decoder): AdditionalProperties {
    return when (val raw = decoder.decodeSerializableValue(YamlDynamicSerializer)) {
      true -> AdditionalProperties.Allowed
      false -> AdditionalProperties.Forbidden
      is Map<*, *> -> {
        val yamlString = Yaml.encodeToString(YamlDynamicSerializer, raw)
        AdditionalProperties.Typed(Yaml.decodeFromString(Schema.serializer(), yamlString))
      }
      else -> AdditionalProperties.Allowed
    }
  }
}

/**
 * Represents the OpenAPI `default` field on a schema property, which can be a string, number,
 * boolean, or null scalar value.
 */
@Serializable(with = DefaultValueSerializer::class)
sealed class DefaultValue {
  data class Str(val value: String) : DefaultValue()
  data class Num(val value: Number) : DefaultValue()
  data class Bool(val value: Boolean) : DefaultValue()
  data object Null : DefaultValue()
}

internal object DefaultValueSerializer : KSerializer<DefaultValue> {
  override val descriptor: SerialDescriptor = YamlDynamicSerializer.descriptor

  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  override fun serialize(encoder: Encoder, value: DefaultValue) {
    when (value) {
      is DefaultValue.Str -> encoder.encodeString(value.value)
      is DefaultValue.Num -> encoder.encodeDouble(value.value.toDouble())
      is DefaultValue.Bool -> encoder.encodeBoolean(value.value)
      is DefaultValue.Null -> encoder.encodeNull()
    }
  }

  override fun deserialize(decoder: Decoder): DefaultValue {
    val raw = decoder.decodeSerializableValue(YamlDynamicSerializer)
    return when (raw) {
      is Boolean -> DefaultValue.Bool(raw)
      is Number -> DefaultValue.Num(raw)
      is String -> DefaultValue.Str(raw)
      else -> DefaultValue.Null
    }
  }
}

@Serializable
data class Discriminator(
  val propertyName: String,
  val mapping: Map<String, String>? = null,
)
