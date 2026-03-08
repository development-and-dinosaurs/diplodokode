package uk.co.developmentanddinosaurs.diplodokode.generator

import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

data class ResolvedSpec(
  val schemas: Map<String, Schema>,
  val implementedInterfaces: Map<String, List<String>>,
)

class SchemaResolver {

  fun resolve(schemas: Map<String, Schema>): ResolvedSpec {
    val flatSchemas = schemas.mapValues { (_, schema) -> flattenAllOf(schema, schemas, visited = mutableSetOf()) }
    val implementedInterfaces = buildInterfaceMap(schemas)
    return ResolvedSpec(flatSchemas, implementedInterfaces)
  }

  private fun flattenAllOf(schema: Schema, allSchemas: Map<String, Schema>, visited: MutableSet<String>): Schema {
    if (schema.allOf.isNullOrEmpty()) return schema

    val mergedProperties = mutableMapOf<String, Schema>()
    val mergedRequired = mutableListOf<String>()

    schema.allOf.forEach { subSchema ->
      val resolved =
          if (subSchema.ref != null) {
            val refName = subSchema.ref.substringAfterLast("/")
            if (refName in visited) return@forEach
            visited.add(refName)
            allSchemas[refName]
          } else {
            subSchema
          }
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

  private fun buildInterfaceMap(schemas: Map<String, Schema>): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    schemas.forEach { (name, schema) ->
      (schema.oneOf ?: schema.anyOf)?.forEach { variant ->
        variant.ref?.substringAfterLast("/")?.let { variantName ->
          result.getOrPut(variantName) { mutableListOf() }.add(name)
        }
      }
    }
    return result
  }
}
