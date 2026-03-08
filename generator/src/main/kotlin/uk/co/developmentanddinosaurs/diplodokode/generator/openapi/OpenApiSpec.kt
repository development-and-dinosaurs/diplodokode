package uk.co.developmentanddinosaurs.diplodokode.generator.openapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


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
  val allOf: List<Schema>? = null,
  val anyOf: List<Schema>? = null,
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

@Serializable
data class Discriminator(
  val propertyName: String,
  val mapping: Map<String, String>? = null,
)
