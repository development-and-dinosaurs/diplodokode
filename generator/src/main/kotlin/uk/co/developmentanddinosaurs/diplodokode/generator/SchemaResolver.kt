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
  val interfacePropertyNames: Map<String, Set<String>>,
)

class SchemaResolver(private val config: GeneratorConfig = GeneratorConfig()) {

  fun resolve(schemas: Map<String, Schema>): ResolvedSpec {
    val flatSchemas = schemas.mapValues { (_, schema) -> flattenAllOf(schema, schemas, mutableSetOf()) }
    val (interfaceMap, enumMap, overrideMap) = buildDiscriminatorMaps(schemas, flatSchemas)
    val interfacePropertyNames = buildInterfacePropertyNames(schemas, interfaceMap)
    return ResolvedSpec(flatSchemas, interfaceMap, enumMap, overrideMap, interfacePropertyNames)
  }

  private fun flattenAllOf(schema: Schema, allSchemas: Map<String, Schema>, visited: MutableSet<String>): Schema {
    if (schema.allOf.isNullOrEmpty()) return schema

    val mergedProperties = mutableMapOf<String, Schema>()
    val mergedRequired = mutableListOf<String>()

    schema.properties?.let { mergedProperties.putAll(it) }
    schema.required?.let { mergedRequired.addAll(it) }

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
      rawSchemas: Map<String, Schema>,
      flatSchemas: Map<String, Schema>,
  ): Triple<Map<String, List<String>>, Map<String, DiscriminatorEnum>, Map<String, DiscriminatorOverride>> {
    val interfaceMap = mutableMapOf<String, MutableList<String>>()
    val enumMap = mutableMapOf<String, DiscriminatorEnum>()
    val overrideMap = mutableMapOf<String, DiscriminatorOverride>()

    rawSchemas.forEach { (interfaceName, schema) ->
      val variants = schema.oneOf ?: schema.anyOf ?: return@forEach
      val refVariants = variants.mapNotNull { it.ref?.substringAfterLast("/") }

      refVariants.forEach { variantName ->
        interfaceMap.getOrPut(variantName) { mutableListOf() }.add(interfaceName)
      }

      val discriminator = schema.discriminator ?: return@forEach

      val stagedOverrides = mutableMapOf<String, DiscriminatorOverride>()
      val constants = mutableListOf<String>()
      refVariants.forEach { variantName ->
        val variantSchema = flatSchemas[variantName] ?: return@forEach
        if (!variantSchema.properties.isNullOrEmpty() && variantSchema.properties.containsKey(discriminator.propertyName)) {
          val constant = sanitiseEnumConstant(discriminatorValueFor(variantName, discriminator, variantSchema))
          constants.add(constant)
          stagedOverrides[variantName] = DiscriminatorOverride(interfaceName, discriminator.propertyName, constant)
        }
      }

      // Only use the typed discriminator enum if every ref variant has the discriminator
      // property. If any variant is missing it, the abstract override on the sealed interface
      // cannot be satisfied by all implementors, producing uncompilable Kotlin. Fall back to
      // abstract val prop: String in that case.
      if (constants.size == refVariants.size && constants.isNotEmpty()) {
        enumMap[interfaceName] = DiscriminatorEnum(discriminator.propertyName, constants)
        overrideMap.putAll(stagedOverrides)
      }
    }

    return Triple(interfaceMap, enumMap, overrideMap)
  }

  private fun buildInterfacePropertyNames(
      rawSchemas: Map<String, Schema>,
      interfaceMap: Map<String, List<String>>,
  ): Map<String, Set<String>> =
      interfaceMap.mapValues { (_, interfaces) ->
        interfaces.flatMap { ifaceName ->
          val ifaceSchema = rawSchemas[ifaceName] ?: return@flatMap emptyList()
          val discriminatorPropName = ifaceSchema.discriminator?.propertyName
          ifaceSchema.properties?.keys?.filter { it != discriminatorPropName } ?: emptyList()
        }.toSet()
      }.filterValues { it.isNotEmpty() }

  private fun sanitiseEnumConstant(value: String): String {
    val sanitised = value.replace(Regex("[^A-Za-z0-9_]"), "_").uppercase()
    return if (sanitised.first().isDigit()) "_$sanitised" else sanitised
  }

  private fun discriminatorValueFor(variantName: String, discriminator: Discriminator, variantSchema: Schema): String {
    discriminator.mapping?.entries?.find { (_, ref) -> ref.substringAfterLast("/") == variantName }
        ?.key?.let { return it }
    variantSchema.properties?.get(discriminator.propertyName)?.enum?.firstOrNull()?.let { return it }
    return variantName.lowercase()
  }
}
