package uk.co.developmentanddinosaurs.diplodokode.generator

import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

data class DiscriminatorEnum(
  val propertyName: String,
  val constants: List<String>,
)

data class DiscriminatorOverride(
  val interfaceName: String,
  val propertyName: String,
  val constant: String,
)

data class ResolvedSpec(
  val schemas: Map<String, Schema>,
  val implementedInterfaces: Map<String, List<String>>,
  val discriminatorEnums: Map<String, DiscriminatorEnum>,
  val discriminatorOverrides: Map<String, DiscriminatorOverride>,
)

class SchemaResolver {

  fun resolve(schemas: Map<String, Schema>): ResolvedSpec {
    val flatSchemas = schemas.mapValues { (_, schema) -> flattenAllOf(schema, schemas, mutableSetOf()) }
    val (interfaceMap, enumMap, overrideMap) = buildDiscriminatorMaps(schemas)
    return ResolvedSpec(flatSchemas, interfaceMap, enumMap, overrideMap)
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

  private fun buildDiscriminatorMaps(
      schemas: Map<String, Schema>
  ): Triple<Map<String, List<String>>, Map<String, DiscriminatorEnum>, Map<String, DiscriminatorOverride>> {
    val interfaceMap = mutableMapOf<String, MutableList<String>>()
    val enumMap = mutableMapOf<String, DiscriminatorEnum>()
    val overrideMap = mutableMapOf<String, DiscriminatorOverride>()

    schemas.forEach { (interfaceName, schema) ->
      val variants = schema.oneOf ?: schema.anyOf ?: return@forEach
      val refVariants = variants.mapNotNull { it.ref?.substringAfterLast("/") }

      refVariants.forEach { variantName ->
        interfaceMap.getOrPut(variantName) { mutableListOf() }.add(interfaceName)
      }

      val discriminator = schema.discriminator ?: return@forEach

      val constants = mutableListOf<String>()
      refVariants.forEach { variantName ->
        val variantSchema = schemas[variantName] ?: return@forEach
        if (!variantSchema.properties.isNullOrEmpty() && variantSchema.properties.containsKey(discriminator.propertyName)) {
          val constant = discriminatorValueFor(variantName, discriminator, variantSchema).uppercase()
          constants.add(constant)
          overrideMap[variantName] = DiscriminatorOverride(interfaceName, discriminator.propertyName, constant)
        }
      }

      if (constants.isNotEmpty()) {
        enumMap[interfaceName] = DiscriminatorEnum(discriminator.propertyName, constants)
      }
    }

    return Triple(interfaceMap, enumMap, overrideMap)
  }

  private fun discriminatorValueFor(variantName: String, discriminator: Discriminator, variantSchema: Schema): String {
    discriminator.mapping?.entries?.find { (_, ref) -> ref.substringAfterLast("/") == variantName }
        ?.key?.let { return it }
    variantSchema.properties?.get(discriminator.propertyName)?.enum?.firstOrNull()?.let { return it }
    return variantName.lowercase()
  }
}
