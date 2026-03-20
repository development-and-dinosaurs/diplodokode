package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.DefaultValue
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private const val JAVA_TIME = "java.time"
private const val KOTLIN_UUID = "kotlin.uuid"
private const val KOTLINX_DATETIME = "kotlinx.datetime"

internal class DataClassGenerator(
    private val config: GeneratorConfig,
    private val typeResolver: TypeResolver,
    private val enumClassGenerator: EnumClassGenerator,
) {

  fun generate(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorOverrides: List<DiscriminatorOverride> = emptyList(),
      interfacePropertyNames: Set<String> = emptySet(),
  ): FileSpec {
    val className = config.namingStrategy.className(name)
    val fileBuilder = FileSpec.builder(config.packageName, className)

    val serialiseDiscriminator = discriminatorOverrides.isNotEmpty() && when (config.polymorphismStrategy) {
      PolymorphismStrategy.ANNOTATION -> discriminatorOverrides.any {
        config.serialisationStrategy?.discriminatorAnnotation(it.propertyName) != null
      }
      PolymorphismStrategy.MODULE -> config.serialisationStrategy != null
    }
    val required = schema.required?.toSet() ?: emptySet()

    val enumClassNames = buildInlineEnumClasses(schema, discriminatorOverrides, interfacePropertyNames, fileBuilder)

    val constructorParams = schema.properties?.entries
        ?.filter { (propName, propValue) ->
          !(serialiseDiscriminator && discriminatorOverrides.any { it.propertyName == propName }) &&
              propValue.additionalProperties !is AdditionalProperties.Forbidden
        }
        ?.map { (propName, propValue) ->
          buildConstructorParam(propName, propValue, required, discriminatorOverrides, enumClassNames)
        } ?: emptyList()

    val properties = schema.properties?.entries
        ?.filter { (propName, propValue) ->
          !(serialiseDiscriminator && discriminatorOverrides.any { it.propertyName == propName }) &&
              propValue.additionalProperties !is AdditionalProperties.Forbidden
        }
        ?.map { (propName, propValue) ->
          buildProperty(propName, propValue, required, discriminatorOverrides, interfacePropertyNames, enumClassNames)
        } ?: emptyList()

    if (constructorParams.isEmpty()) {
      return generateDataObject(className, fileBuilder, implementedInterfaces, serialiseDiscriminator, discriminatorOverrides)
    }

    val allTypes = constructorParams.map { it.type } + properties.map { it.type }
    if (allTypes.any { typeResolver.containsKotlinUuid(it) }) {
      fileBuilder.addAnnotation(
          AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
              .addMember("%T::class", ClassName(KOTLIN_UUID, "ExperimentalUuidApi"))
              .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
              .build()
      )
    }

    val hasForbiddenAdditionalProperties = schema.additionalProperties is AdditionalProperties.Forbidden ||
        schema.properties?.values?.any { it.additionalProperties is AdditionalProperties.Forbidden } == true

    val dataClassBuilder = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
        .also { builder ->
          schema.description?.let { builder.addKdoc("$it\n") }
          if (hasForbiddenAdditionalProperties) {
            builder.addKdoc("NOTE: additional properties are forbidden by the OpenAPI spec.\n")
          }
          config.serialisationStrategy?.let { strategy ->
            builder.addAnnotation(strategy.classAnnotation)
            if (serialiseDiscriminator) {
              discriminatorOverrides.firstOrNull()?.let { override ->
                strategy.variantAnnotation(override.rawValue)?.let { builder.addAnnotation(it) }
              }
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

  private fun generateDataObject(
      className: String,
      fileBuilder: FileSpec.Builder,
      implementedInterfaces: List<String>,
      serialiseDiscriminator: Boolean,
      discriminatorOverrides: List<DiscriminatorOverride>,
  ): FileSpec {
    val objectBuilder = TypeSpec.objectBuilder(className)
        .addModifiers(KModifier.DATA)
        .also { builder ->
          config.serialisationStrategy?.let { strategy ->
            builder.addAnnotation(strategy.classAnnotation)
            if (serialiseDiscriminator) {
              discriminatorOverrides.firstOrNull()?.let { override ->
                strategy.variantAnnotation(override.rawValue)?.let { builder.addAnnotation(it) }
              }
            }
          }
        }
    implementedInterfaces.forEach { iface ->
      objectBuilder.addSuperinterface(ClassName(config.packageName, config.namingStrategy.className(iface)))
    }
    return fileBuilder.addType(objectBuilder.build()).build()
  }

  private fun buildInlineEnumClasses(
      schema: Schema,
      discriminatorOverrides: List<DiscriminatorOverride>,
      interfacePropertyNames: Set<String>,
      fileBuilder: FileSpec.Builder,
  ): Map<String, ClassName> =
      schema.properties?.entries
          ?.filter { (propName, propValue) ->
            !propValue.enum.isNullOrEmpty() &&
                discriminatorOverrides.none { it.propertyName == propName } &&
                propName !in interfacePropertyNames
          }
          ?.associate { (propName, propValue) ->
            val enumName = config.namingStrategy.className(propName)
            fileBuilder.addType(enumClassGenerator.generateEnumClass(enumName, propValue.enum!!))
            propName to ClassName(config.packageName, enumName)
          } ?: emptyMap()

  private fun buildConstructorParam(
      propName: String,
      propValue: Schema,
      required: Set<String>,
      discriminatorOverrides: List<DiscriminatorOverride>,
      enumClassNames: Map<String, ClassName>,
  ): ParameterSpec {
    val propertyName = config.namingStrategy.propertyName(propName)
    val matchingOverride = discriminatorOverrides.find { it.propertyName == propName }
    if (matchingOverride != null) {
      val enumType = ClassName(config.packageName, config.namingStrategy.className(matchingOverride.interfaceName), "Type")
      return ParameterSpec.builder(propertyName, enumType)
          .defaultValue("%T.%L", enumType, matchingOverride.constant)
          .build()
    }
    val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, required)
    val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames)
    val paramBuilder = ParameterSpec.builder(propertyName, kotlinType)
    propValue.default?.let { formatDefault(it, kotlinType, enumClassNames[propName]) }
        ?.let { paramBuilder.defaultValue(it) }
    return paramBuilder.build()
  }

  private fun buildProperty(
      propName: String,
      propValue: Schema,
      required: Set<String>,
      discriminatorOverrides: List<DiscriminatorOverride>,
      interfacePropertyNames: Set<String>,
      enumClassNames: Map<String, ClassName>,
  ): PropertySpec {
    val propertyName = config.namingStrategy.propertyName(propName)
    val matchingOverride = discriminatorOverrides.find { it.propertyName == propName }
    return when {
      matchingOverride != null -> buildDiscriminatorProperty(propertyName, propName, matchingOverride)
      propName in interfacePropertyNames -> buildOverrideProperty(propName, propValue, propertyName, required, enumClassNames)
      else -> buildPlainProperty(propName, propValue, propertyName, required, enumClassNames)
    }
  }

  private fun buildDiscriminatorProperty(
      propertyName: String,
      propName: String,
      discriminatorOverride: DiscriminatorOverride,
  ): PropertySpec {
    val enumType = ClassName(config.packageName, config.namingStrategy.className(discriminatorOverride.interfaceName), "Type")
    return PropertySpec.builder(propertyName, enumType)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(propertyName)
        .applySerialName(propName, propertyName)
        .build()
  }

  private fun buildOverrideProperty(
      propName: String,
      propValue: Schema,
      propertyName: String,
      required: Set<String>,
      enumClassNames: Map<String, ClassName>,
  ): PropertySpec {
    val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, required)
    val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames)
    val builder = PropertySpec.builder(propertyName, kotlinType)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(propertyName)
    if (typeResolver.containsAny(kotlinType)) {
      config.serialisationStrategy?.anyPropertyAnnotation()?.let { builder.addAnnotation(it) }
    }
    return builder.applySerialName(propName, propertyName).build()
  }

  private fun buildPlainProperty(
      propName: String,
      propValue: Schema,
      propertyName: String,
      required: Set<String>,
      enumClassNames: Map<String, ClassName>,
  ): PropertySpec {
    val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, required)
    val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames)
    val builder = PropertySpec.builder(propertyName, kotlinType)
        .addModifiers(KModifier.PUBLIC)
        .initializer(propertyName)
    propValue.description?.let { builder.addKdoc("$it\n") }
    val baseKotlinType = kotlinType.copy(nullable = false)
    val strDefault = propValue.default as? DefaultValue.Str
    if (strDefault != null && enumClassNames[propName] == null &&
        baseKotlinType != String::class.asTypeName() && parseableDefaults[baseKotlinType] == null) {
      val typeName = baseKotlinType.toString().substringAfterLast(".")
      builder.addKdoc("NOTE: default value '${strDefault.value}' cannot be represented as a Kotlin literal for type $typeName; no default emitted.\n")
    }
    if (propValue.format == "uri") {
      builder.addKdoc("NOTE: format is 'uri'; represented as String (no KMP-safe URI type). See README for alternatives.\n")
    }
    if (propValue.type == "array" && propValue.items == null) {
      builder.addKdoc("NOTE: no 'items' schema defined — type is List<Any>. Add an 'items' schema for a typed list.\n")
    }
    if (propValue.type == "array" && !propValue.items?.enum.isNullOrEmpty()) {
      val values = propValue.items.enum.joinToString(", ")
      builder.addKdoc("NOTE: items have an enum constraint [$values] — define as a \$ref schema for a typed List.\n")
    }
    if (typeResolver.containsAny(kotlinType)) {
      config.serialisationStrategy?.anyPropertyAnnotation()?.let { builder.addAnnotation(it) }
    }
    return builder.applySerialName(propName, propertyName).build()
  }

  private fun formatDefault(default: DefaultValue, kotlinType: TypeName, enumClassName: ClassName?): CodeBlock? {
    val baseType = kotlinType.copy(nullable = false)
    return when (default) {
      is DefaultValue.Null -> if (kotlinType.isNullable) CodeBlock.of("null") else null
      is DefaultValue.Bool -> CodeBlock.of("%L", default.value)
      is DefaultValue.Str -> when {
        enumClassName != null -> CodeBlock.of(
            "%T.%L", enumClassName, config.namingStrategy.enumConstant(default.value)
        )
        baseType == String::class.asTypeName() -> CodeBlock.of("%S", default.value)
        else -> parseableDefaults[baseType]?.invoke(default.value)
      }
      is DefaultValue.Num -> when (baseType) {
        Long::class.asTypeName() -> CodeBlock.of("%LL", default.value.toLong())
        Float::class.asTypeName() -> CodeBlock.of("%Lf", default.value.toFloat())
        Int::class.asTypeName() -> CodeBlock.of("%L", default.value.toInt())
        Double::class.asTypeName() -> CodeBlock.of("%L", default.value.toDouble())
        else -> CodeBlock.of("%L", default.value.toDouble())
      }
    }
  }

  companion object {
    private fun parseCall(type: ClassName) = { v: String -> CodeBlock.of("%T.parse(%S)", type, v) }

    val parseableDefaults: Map<TypeName, (String) -> CodeBlock> = mapOf(
        ClassName(KOTLINX_DATETIME, "Instant")  to parseCall(ClassName(KOTLINX_DATETIME, "Instant")),
        ClassName(KOTLINX_DATETIME, "LocalDate") to parseCall(ClassName(KOTLINX_DATETIME, "LocalDate")),
        ClassName(KOTLINX_DATETIME, "LocalTime") to parseCall(ClassName(KOTLINX_DATETIME, "LocalTime")),
        ClassName("kotlin.time", "Duration")       to parseCall(ClassName("kotlin.time", "Duration")),
        ClassName(KOTLIN_UUID, "Uuid")           to parseCall(ClassName(KOTLIN_UUID, "Uuid")),
        ClassName(JAVA_TIME, "Instant")          to parseCall(ClassName(JAVA_TIME, "Instant")),
        ClassName(JAVA_TIME, "LocalDate")        to parseCall(ClassName(JAVA_TIME, "LocalDate")),
        ClassName(JAVA_TIME, "LocalTime")        to parseCall(ClassName(JAVA_TIME, "LocalTime")),
        ClassName(JAVA_TIME, "Duration")         to parseCall(ClassName(JAVA_TIME, "Duration")),
        ClassName("java.util", "UUID")             to { v -> CodeBlock.of("%T.fromString(%S)", ClassName("java.util", "UUID"), v) },
        ClassName("java.net", "URI")               to { v -> CodeBlock.of("%T(%S)", ClassName("java.net", "URI"), v) },
        ByteArray::class.asTypeName()              to { v -> CodeBlock.of("%S.toByteArray()", v) },
    )
  }

  private fun PropertySpec.Builder.applySerialName(specName: String, kotlinName: String): PropertySpec.Builder {
    if (kotlinName != specName) {
      config.serialisationStrategy?.propertyAnnotation(specName)?.let { addAnnotation(it) }
    }
    return this
  }
}
