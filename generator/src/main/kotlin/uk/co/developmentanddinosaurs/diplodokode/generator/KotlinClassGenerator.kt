package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class KotlinClassGenerator(config: GeneratorConfig = GeneratorConfig()) {

  private val typeResolver = TypeResolver(config)
  private val enumClassGenerator = EnumClassGenerator(config)
  private val dataClassGenerator = DataClassGenerator(config, typeResolver, enumClassGenerator)
  private val sealedInterfaceGenerator = SealedInterfaceGenerator(config, typeResolver)
  private val primitiveUnionGenerator = PrimitiveUnionGenerator(config)
  private val mapTypealiasGenerator = MapTypealiasGenerator(config, typeResolver)

  fun generateFromSchema(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorEnum: DiscriminatorEnum? = null,
      discriminatorOverrides: List<DiscriminatorOverride> = emptyList(),
      interfacePropertyNames: Set<String> = emptySet(),
      allImplementedInterfaces: Map<String, List<String>> = emptyMap(),
  ): FileSpec =
      when {
        !schema.enum.isNullOrEmpty() -> enumClassGenerator.generateTopLevelEnum(name, schema)
        !schema.oneOf.isNullOrEmpty() && isPrimitiveUnion(schema.oneOf) -> primitiveUnionGenerator.generate(name, schema)
        !schema.oneOf.isNullOrEmpty() -> sealedInterfaceGenerator.generate(name, schema, schema.oneOf, "oneOf", discriminatorEnum, implementedInterfaces, allImplementedInterfaces)
        !schema.anyOf.isNullOrEmpty() -> sealedInterfaceGenerator.generate(name, schema, schema.anyOf, "anyOf", discriminatorEnum, implementedInterfaces, allImplementedInterfaces)
        isMapOnlySchema(schema) -> mapTypealiasGenerator.generate(name, schema)
        else -> dataClassGenerator.generate(name, schema, implementedInterfaces, discriminatorOverrides, interfacePropertyNames, allImplementedInterfaces)
      }

  private fun isMapOnlySchema(schema: Schema): Boolean =
      schema.properties.isNullOrEmpty() &&
          schema.additionalProperties != null &&
          schema.additionalProperties !is AdditionalProperties.Forbidden
}
