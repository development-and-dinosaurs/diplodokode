package uk.co.developmentanddinosaurs.diplodokode.generator.openapi

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
  val type: String? = null,
  val required: List<String>? = null,
  val description: String? = null,
  val properties: Map<String, Schema>? = null,
)
