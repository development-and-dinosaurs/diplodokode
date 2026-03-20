package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class KotlinClassGenerator(config: GeneratorConfig = GeneratorConfig()) {

  private val typeResolver = TypeResolver(config)
  private val enumClassGenerator = EnumClassGenerator(config)
  private val dataClassGenerator = DataClassGenerator(config, typeResolver, enumClassGenerator)
  private val sealedInterfaceGenerator = SealedInterfaceGenerator(config, typeResolver)
  private val primitiveUnionGenerator = PrimitiveUnionGenerator(config)

  fun generateFromSchema(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorEnum: DiscriminatorEnum? = null,
      discriminatorOverrides: List<DiscriminatorOverride> = emptyList(),
      interfacePropertyNames: Set<String> = emptySet(),
  ): FileSpec =
      when {
        !schema.enum.isNullOrEmpty() -> enumClassGenerator.generateTopLevelEnum(name, schema)
        !schema.oneOf.isNullOrEmpty() && isPrimitiveUnion(schema.oneOf) -> primitiveUnionGenerator.generate(name, schema)
        !schema.oneOf.isNullOrEmpty() -> sealedInterfaceGenerator.generate(name, schema, schema.oneOf, "oneOf", discriminatorEnum, implementedInterfaces)
        !schema.anyOf.isNullOrEmpty() -> sealedInterfaceGenerator.generate(name, schema, schema.anyOf, "anyOf", discriminatorEnum, implementedInterfaces)
        else -> dataClassGenerator.generate(name, schema, implementedInterfaces, discriminatorOverrides, interfacePropertyNames)
      }
}
