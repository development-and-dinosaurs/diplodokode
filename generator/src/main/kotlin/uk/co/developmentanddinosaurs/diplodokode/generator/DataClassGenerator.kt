package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

internal class DataClassGenerator(
    private val config: GeneratorConfig,
    private val typeResolver: TypeResolver,
    private val enumClassGenerator: EnumClassGenerator,
) {

  fun generate(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorOverride: DiscriminatorOverride? = null,
      interfacePropertyNames: Set<String> = emptySet(),
  ): FileSpec {
    val className = config.namingStrategy.className(name)
    val fileBuilder = FileSpec.builder(config.packageName, className)

    val serialiseDiscriminator = config.serialisationStrategy != null && discriminatorOverride != null

    val enumClassNames =
        schema.properties
            ?.entries
            ?.filter { (propName, propValue) ->
              !propValue.enum.isNullOrEmpty() && propName != discriminatorOverride?.propertyName &&
                  propName !in interfacePropertyNames
            }
            ?.associate { (propName, propValue) ->
              val enumName = config.namingStrategy.className(propName)
              fileBuilder.addType(enumClassGenerator.generateEnumClass(enumName, propValue.enum!!))
              propName to ClassName(config.packageName, enumName)
            } ?: emptyMap()

    val constructorParams =
        schema.properties?.entries
            ?.filter { (propName, _) -> !(serialiseDiscriminator && propName == discriminatorOverride.propertyName) }
            ?.map { (propName, propValue) ->
              val propertyName = config.namingStrategy.propertyName(propName)
              if (propName == discriminatorOverride?.propertyName) {
                val enumType = ClassName(config.packageName, config.namingStrategy.className(discriminatorOverride.interfaceName), "Type")
                ParameterSpec.builder(propertyName, enumType)
                    .defaultValue("%T.%L", enumType, discriminatorOverride.constant)
                    .build()
              } else {
                val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, schema.required?.toSet() ?: emptySet())
                val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames)
                ParameterSpec.builder(propertyName, kotlinType).build()
              }
            } ?: emptyList()

    val properties =
        schema.properties?.entries
            ?.filter { (propName, _) -> !(serialiseDiscriminator && propName == discriminatorOverride.propertyName) }
            ?.map { (propName, propValue) ->
              val propertyName = config.namingStrategy.propertyName(propName)
              when (propName) {
                discriminatorOverride?.propertyName -> {
                  val enumType = ClassName(config.packageName, config.namingStrategy.className(discriminatorOverride.interfaceName), "Type")
                  PropertySpec.builder(propertyName, enumType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(propertyName)
                    .applySerialName(propName, propertyName)
                    .build()
                }
                in interfacePropertyNames -> {
                  val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, schema.required?.toSet() ?: emptySet())
                  val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames)
                  PropertySpec.builder(propertyName, kotlinType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(propertyName)
                    .applySerialName(propName, propertyName)
                    .build()
                }
                else -> {
                  val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, schema.required?.toSet() ?: emptySet())
                  val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames)
                  val propertyBuilder = PropertySpec.builder(propertyName, kotlinType)
                    .addModifiers(KModifier.PUBLIC)
                    .initializer(propertyName)
                  propValue.description?.let { propertyBuilder.addKdoc("$it\n") }
                  if (propValue.type == "array" && !propValue.items?.enum.isNullOrEmpty()) {
                    val values = propValue.items.enum.joinToString(", ")
                    propertyBuilder.addKdoc("NOTE: items have an enum constraint [$values] — define as a \$ref schema for a typed List.\n")
                  }
                  propertyBuilder.applySerialName(propName, propertyName).build()
                }
              }
            } ?: emptyList()

    val allTypes = constructorParams.map { it.type } + properties.map { it.type }
    if (allTypes.any { typeResolver.containsKotlinUuid(it) }) {
      fileBuilder.addAnnotation(
          AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
              .addMember("%T::class", ClassName("kotlin.uuid", "ExperimentalUuidApi"))
              .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
              .build()
      )
    }

    val dataClassBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .also { builder ->
              config.serialisationStrategy?.let { strategy ->
                builder.addAnnotation(strategy.classAnnotation)
                if (serialiseDiscriminator) {
                  strategy.variantAnnotation(discriminatorOverride.rawValue)?.let { builder.addAnnotation(it) }
                }
              }
            }
            .primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParams).build())
            .addProperties(properties)

    implementedInterfaces.forEach { iface ->
      dataClassBuilder.addSuperinterface(ClassName(config.packageName, config.namingStrategy.className(iface)))
    }

    return fileBuilder.addType(dataClassBuilder.build()).build()
  }

  private fun PropertySpec.Builder.applySerialName(specName: String, kotlinName: String): PropertySpec.Builder {
    if (kotlinName != specName) {
      config.serialisationStrategy?.propertyAnnotation(specName)?.let { addAnnotation(it) }
    }
    return this
  }
}
