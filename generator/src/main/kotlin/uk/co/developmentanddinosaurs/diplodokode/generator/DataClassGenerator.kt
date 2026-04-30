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
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.ExampleValue
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private const val JAVA_TIME = "java.time"
private const val KOTLIN_TIME = "kotlin.time"
private const val KOTLIN_UUID = "kotlin.uuid"
private const val KOTLINX_DATETIME = "kotlinx.datetime"

private const val DEPRECATED_IN_THE_OPEN_API_SPEC_ = "Deprecated in the OpenAPI spec."

private const val LEVEL_T_WARNING = "level = %T.WARNING"

private fun String.sanitizeKdoc(): String = replace("*/", "* /")

private fun backtickFence(content: String): String {
  val maxRun = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
  return "`".repeat(maxOf(3, maxRun + 1))
}

private fun ExampleValue.toKdoc(): String = when (this) {
  is ExampleValue.Str -> "Example: \"${value.sanitizeKdoc()}\"\n"
  is ExampleValue.Num -> "Example: $value\n"
  is ExampleValue.Bool -> "Example: $value\n"
  is ExampleValue.Null -> "Example: null\n"
  is ExampleValue.Raw -> {
    val sanitized = yaml.sanitizeKdoc()
    val fence = backtickFence(sanitized)
    "Example:\n$fence\n$sanitized\n$fence\n"
  }
}

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
      allImplementedInterfaces: Map<String, List<String>> = emptyMap(),
  ): FileSpec {
    val className = config.namingStrategy.className(name)
    val fileBuilder = FileSpec.builder(config.packageName, className)

    val serialisedDiscriminatorProperties = discriminatorOverrides
        .filter { isDiscriminatorSerialised(it) }
        .map { it.propertyName }
        .toSet()
    val serialiseDiscriminator = serialisedDiscriminatorProperties.isNotEmpty()
    val required = schema.required?.toSet() ?: emptySet()

    val (enumClassNames, nestedEnumTypes) = buildInlineEnumClasses(className, schema, discriminatorOverrides, interfacePropertyNames)

    val constructorParams = schema.properties?.entries
        ?.filter { (propName, _) -> propName !in serialisedDiscriminatorProperties }
        ?.map { (propName, propValue) ->
          buildConstructorParam(propName, propValue, required, discriminatorOverrides, enumClassNames, allImplementedInterfaces)
        } ?: emptyList()

    val properties = schema.properties?.entries
        ?.filter { (propName, _) -> propName !in serialisedDiscriminatorProperties }
        ?.map { (propName, propValue) ->
          buildProperty(propName, propValue, required, discriminatorOverrides, interfacePropertyNames, enumClassNames, allImplementedInterfaces)
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

    val hasForbiddenAdditionalProperties = schema.additionalProperties is AdditionalProperties.Forbidden

    val dataClassBuilder = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
        .also { builder ->
          if (schema.deprecated == true) {
            builder.addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember("%S", DEPRECATED_IN_THE_OPEN_API_SPEC_)
                    .addMember(LEVEL_T_WARNING, DeprecationLevel::class)
                    .build()
            )
          }
          listOfNotNull(schema.title, schema.description).joinToString("\n\n")
              .takeIf { it.isNotEmpty() }?.let { builder.addKdoc("$it\n") }
          schema.example?.let { builder.addKdoc(it.toKdoc()) }
          if (hasForbiddenAdditionalProperties) {
            builder.addKdoc("NOTE: additional properties are forbidden by the OpenAPI spec.\n")
          }
          config.serialisationStrategy?.let { strategy ->
            builder.addAnnotation(strategy.classAnnotation)
            if (serialiseDiscriminator) {
              discriminatorOverrides.firstOrNull { isDiscriminatorSerialised(it) }?.let { override ->
                strategy.variantAnnotation(override.rawValue)?.let { builder.addAnnotation(it) }
              }
            }
          }
        }
        .primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParams).build())
        .addProperties(properties)
        .also { builder -> nestedEnumTypes.forEach { builder.addType(it) } }

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
              discriminatorOverrides.firstOrNull { isDiscriminatorSerialised(it) }?.let { override ->
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

  private fun isDiscriminatorSerialised(override: DiscriminatorOverride): Boolean =
      when (config.polymorphismStrategy) {
        PolymorphismStrategy.ANNOTATION -> config.serialisationStrategy?.discriminatorAnnotation(override.propertyName) != null
        PolymorphismStrategy.MODULE -> config.serialisationStrategy != null
      }

  private fun buildInlineEnumClasses(
      parentClassName: String,
      schema: Schema,
      discriminatorOverrides: List<DiscriminatorOverride>,
      interfacePropertyNames: Set<String>,
  ): Pair<Map<String, ClassName>, List<TypeSpec>> {
    val classNames = mutableMapOf<String, ClassName>()
    val types = mutableListOf<TypeSpec>()
    schema.properties?.entries
        ?.filter { (propName, propValue) ->
          !propValue.enum.isNullOrEmpty() &&
              discriminatorOverrides.none { it.propertyName == propName } &&
              propName !in interfacePropertyNames
        }
        ?.forEach { (propName, propValue) ->
          val enumName = config.namingStrategy.className(propName)
          types.add(enumClassGenerator.generateEnumClass(enumName, propValue.enum!!, deprecated = propValue.deprecated))
          classNames[propName] = ClassName(config.packageName, parentClassName, enumName)
        }
    return classNames to types
  }

  private fun buildConstructorParam(
      propName: String,
      propValue: Schema,
      required: Set<String>,
      discriminatorOverrides: List<DiscriminatorOverride>,
      enumClassNames: Map<String, ClassName>,
      allImplementedInterfaces: Map<String, List<String>>,
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
    val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames, allImplementedInterfaces)
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
      allImplementedInterfaces: Map<String, List<String>>,
  ): PropertySpec {
    val propertyName = config.namingStrategy.propertyName(propName)
    val matchingOverride = discriminatorOverrides.find { it.propertyName == propName }
    return when {
      matchingOverride != null -> buildDiscriminatorProperty(propertyName, propName, matchingOverride, propValue.deprecated)
      propName in interfacePropertyNames -> buildOverrideProperty(propName, propValue, propertyName, required, enumClassNames, allImplementedInterfaces)
      else -> buildPlainProperty(propName, propValue, propertyName, required, enumClassNames, allImplementedInterfaces)
    }
  }

  private fun buildDiscriminatorProperty(
      propertyName: String,
      propName: String,
      discriminatorOverride: DiscriminatorOverride,
      deprecated: Boolean? = null,
  ): PropertySpec {
    val enumType = ClassName(config.packageName, config.namingStrategy.className(discriminatorOverride.interfaceName), "Type")
    val builder = PropertySpec.builder(propertyName, enumType)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(propertyName)
    if (deprecated == true) {
      builder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", DEPRECATED_IN_THE_OPEN_API_SPEC_)
              .addMember(LEVEL_T_WARNING, DeprecationLevel::class)
              .build()
      )
    }
    return builder.applySerialName(propName, propertyName).build()
  }

  private fun buildOverrideProperty(
      propName: String,
      propValue: Schema,
      propertyName: String,
      required: Set<String>,
      enumClassNames: Map<String, ClassName>,
      allImplementedInterfaces: Map<String, List<String>>,
  ): PropertySpec {
    val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, required)
    val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames, allImplementedInterfaces)
    val builder = PropertySpec.builder(propertyName, kotlinType)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(propertyName)
    if (propValue.readOnly == true) {
      builder.addKdoc("NOTE: This property is read-only in the OpenAPI spec; do not include it in request bodies.\n")
    }
    if (propValue.writeOnly == true) {
      builder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", "This property is write-only in the OpenAPI spec and will not appear in responses.")
              .addMember(LEVEL_T_WARNING, DeprecationLevel::class)
              .build()
      )
      builder.addKdoc("NOTE: This property is write-only in the OpenAPI spec and will not appear in responses.\n")
    }
    if (propValue.deprecated == true) {
      builder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", DEPRECATED_IN_THE_OPEN_API_SPEC_)
              .addMember(LEVEL_T_WARNING, DeprecationLevel::class)
              .build()
      )
    }
    propValue.example?.let { builder.addKdoc(it.toKdoc()) }
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
      allImplementedInterfaces: Map<String, List<String>>,
  ): PropertySpec {
    val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, required)
    val kotlinType = typeResolver.resolveType(propName, propValue, isNullable, enumClassNames, allImplementedInterfaces)
    val builder = PropertySpec.builder(propertyName, kotlinType)
        .addModifiers(KModifier.PUBLIC)
        .initializer(propertyName)
    listOfNotNull(propValue.title, propValue.description).joinToString("\n\n")
        .takeIf { it.isNotEmpty() }?.let { builder.addKdoc("$it\n") }
    propValue.example?.let { builder.addKdoc(it.toKdoc()) }
    val baseKotlinType = kotlinType.copy(nullable = false)
    val strDefault = propValue.default as? DefaultValue.Str
    if (strDefault != null && enumClassNames[propName] == null &&
        baseKotlinType != String::class.asTypeName() && parseableDefaults[baseKotlinType] == null) {
      val typeName = baseKotlinType.toString().substringAfterLast(".")
      builder.addKdoc("NOTE: default value '${strDefault.value}' cannot be represented as a Kotlin literal for type $typeName; no default emitted.\n")
    }
    val numDefault = propValue.default as? DefaultValue.Num
    if (numDefault != null && baseKotlinType !in knownNumericTypes) {
      val typeName = baseKotlinType.toString().substringAfterLast(".")
      builder.addKdoc("NOTE: default value '${numDefault.value}' cannot be represented as a Kotlin literal for type $typeName; no default emitted.\n")
    }
    if (propValue.format == "uri" && baseKotlinType == String::class.asTypeName()) {
      builder.addKdoc("NOTE: format is 'uri'; represented as String (no KMP-safe URI type). See README for alternatives.\n")
    }
    if (propValue.type == "array" && propValue.items == null) {
      builder.addKdoc("NOTE: no 'items' schema defined — type is List<Any>. Add an 'items' schema for a typed list.\n")
    }
    val baseKotlinBase = kotlinType.copy(nullable = false)
    if (!propValue.allOf.isNullOrEmpty() && propValue.allOf.singleOrNull()?.ref == null) {
      builder.addKdoc("NOTE: property-level 'allOf' with multiple items or without a \$ref fell back to Any. Extract to a named schema for a typed property.\n")
    }
    if (baseKotlinBase == Any::class.asTypeName() && !propValue.oneOf.isNullOrEmpty() && !isPrimitiveUnion(propValue.oneOf)) {
      builder.addKdoc("NOTE: property-level 'oneOf' has no common sealed-interface parent; fell back to Any. Extract to a named schema or ensure all variants share a top-level oneOf parent.\n")
    }
    if (baseKotlinBase == Any::class.asTypeName() && !propValue.anyOf.isNullOrEmpty()) {
      builder.addKdoc("NOTE: property-level 'anyOf' has no common sealed-interface parent; fell back to Any. Extract to a named schema or ensure all variants share a top-level anyOf parent.\n")
    }
    if (propValue.type == "array" && !propValue.items?.enum.isNullOrEmpty()) {
      val values = propValue.items.enum.joinToString(", ")
      builder.addKdoc("NOTE: items have an enum constraint [$values] — define as a \$ref schema for a typed List.\n")
    }
    if (propValue.readOnly == true) {
      builder.addKdoc("NOTE: This property is read-only in the OpenAPI spec; do not include it in request bodies.\n")
    }
    if (propValue.writeOnly == true) {
      builder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", "This property is write-only in the OpenAPI spec and will not appear in responses.")
              .addMember(LEVEL_T_WARNING, DeprecationLevel::class)
              .build()
      )
      builder.addKdoc("NOTE: This property is write-only in the OpenAPI spec and will not appear in responses.\n")
    }
    if (propValue.deprecated == true) {
      builder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", DEPRECATED_IN_THE_OPEN_API_SPEC_)
              .addMember(LEVEL_T_WARNING, DeprecationLevel::class)
              .build()
      )
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
        else -> null
      }
    }
  }

  companion object {
    val knownNumericTypes: Set<TypeName> = setOf(
        Long::class.asTypeName(),
        Float::class.asTypeName(),
        Int::class.asTypeName(),
        Double::class.asTypeName(),
    )

    private fun parseCall(type: ClassName) = { v: String -> CodeBlock.of("%T.parse(%S)", type, v) }

    val parseableDefaults: Map<TypeName, (String) -> CodeBlock> = mapOf(
        ClassName(KOTLIN_TIME, "Instant")        to parseCall(ClassName(KOTLIN_TIME, "Instant")),
        ClassName(KOTLINX_DATETIME, "LocalDate") to parseCall(ClassName(KOTLINX_DATETIME, "LocalDate")),
        ClassName(KOTLINX_DATETIME, "LocalTime") to parseCall(ClassName(KOTLINX_DATETIME, "LocalTime")),
        ClassName(KOTLIN_TIME, "Duration")       to parseCall(ClassName("kotlin.time", "Duration")),
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
