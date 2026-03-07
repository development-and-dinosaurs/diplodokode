package uk.co.developmentanddinosaurs.diplodokode.generator

import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class SchemaResolver {

  fun resolve(schemas: Map<String, Schema>): Map<String, Schema> =
      schemas.mapValues { (_, schema) -> resolveSchema(schema, schemas) }

  private fun resolveSchema(schema: Schema, allSchemas: Map<String, Schema>): Schema {
    if (schema.allOf.isNullOrEmpty()) return schema

    val mergedProperties = mutableMapOf<String, Schema>()
    val mergedRequired = mutableListOf<String>()

    schema.allOf.forEach { subSchema ->
      val resolved =
          if (subSchema.ref != null) allSchemas[subSchema.ref.substringAfterLast("/")]
          else subSchema
      resolved?.properties?.let { mergedProperties.putAll(it) }
      resolved?.required?.let { mergedRequired.addAll(it) }
    }

    return Schema(
        type = "object",
        description = schema.description,
        properties = mergedProperties.takeIf { it.isNotEmpty() },
        required = mergedRequired.distinct().takeIf { it.isNotEmpty() },
    )
  }
}
