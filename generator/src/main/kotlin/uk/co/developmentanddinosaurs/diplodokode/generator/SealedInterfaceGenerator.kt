package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

internal class SealedInterfaceGenerator(
    private val config: GeneratorConfig,
    private val typeResolver: TypeResolver,
) {

  fun generate(
      name: String,
      schema: Schema,
      variants: List<Schema>,
      keyword: String,
      discriminatorEnum: DiscriminatorEnum?,
  ): FileSpec {
    val interfaceName = config.namingStrategy.className(name)
    val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName).addModifiers(KModifier.SEALED)

    config.serialisationStrategy?.let { interfaceBuilder.addAnnotation(it.classAnnotation) }
    schema.description?.let { interfaceBuilder.addKdoc("$it\n") }
    val kdoc = if (keyword == "anyOf") "One or more of the following variants may be used.\n"
               else "Exactly one of the following variants must be used.\n"
    interfaceBuilder.addKdoc(kdoc)

    val useSerialisedDiscriminator = config.serialisationStrategy != null && discriminatorEnum != null
    if (discriminatorEnum != null && !useSerialisedDiscriminator) {
      val enumType = ClassName(config.packageName, interfaceName, "Type")
      val enumBuilder = TypeSpec.enumBuilder("Type")
      discriminatorEnum.constants.zip(discriminatorEnum.rawValues).forEach { (kotlinName, _) ->
        enumBuilder.addEnumConstant(kotlinName)
      }
      interfaceBuilder.addType(enumBuilder.build())
      interfaceBuilder.addProperty(
          PropertySpec.builder(discriminatorEnum.propertyName, enumType)
              .addModifiers(KModifier.ABSTRACT)
              .build()
      )
    } else if (discriminatorEnum != null) {
      config.serialisationStrategy?.discriminatorAnnotation(discriminatorEnum.propertyName)
          ?.let { interfaceBuilder.addAnnotation(it) }
    } else {
      schema.discriminator?.let { disc ->
        interfaceBuilder.addKdoc(
            "Warning: discriminator property '${disc.propertyName}' is declared but not all variants carry it; falling back to `abstract val ${disc.propertyName}: String`.\n"
        )
        interfaceBuilder.addProperty(
            PropertySpec.builder(disc.propertyName, String::class)
                .addModifiers(KModifier.ABSTRACT)
                .build()
        )
      }
    }

    val discriminatorPropName = discriminatorEnum?.propertyName ?: schema.discriminator?.propertyName
    schema.properties
        ?.filter { (propName, _) -> propName != discriminatorPropName }
        ?.forEach { (propName, propSchema) ->
          val propertyName = config.namingStrategy.propertyName(propName)
          val isNullable = config.nullabilityStrategy.isNullable(propName, propSchema, schema.required?.toSet() ?: emptySet())
          val kotlinType = typeResolver.resolveType(propName, propSchema, isNullable, emptyMap())
          val propBuilder = PropertySpec.builder(propertyName, kotlinType).addModifiers(KModifier.ABSTRACT)
          if (!propSchema.enum.isNullOrEmpty()) {
            val values = propSchema.enum.joinToString(", ")
            propBuilder.addKdoc("NOTE: this property has an inline enum constraint [$values] — define the enum as a \$ref schema for a typed abstract property.\n")
          }
          propBuilder.applySerialName(propName, propertyName)
          interfaceBuilder.addProperty(propBuilder.build())
        }

    variants.filter { it.ref == null }.takeIf { it.isNotEmpty() }?.let {
      interfaceBuilder.addKdoc("NOTE: Inline $keyword variants are not supported. Define variants as \$ref schemas.\n")
    }

    val fileBuilder = FileSpec.builder(config.packageName, interfaceName)
    if (useSerialisedDiscriminator) {
      config.serialisationStrategy?.discriminatorFileAnnotation()?.let { fileBuilder.addAnnotation(it) }
    }
    return fileBuilder.addType(interfaceBuilder.build()).build()
  }

  private fun PropertySpec.Builder.applySerialName(specName: String, kotlinName: String): PropertySpec.Builder {
    if (kotlinName != specName) {
      config.serialisationStrategy?.propertyAnnotation(specName)?.let { addAnnotation(it) }
    }
    return this
  }
}
