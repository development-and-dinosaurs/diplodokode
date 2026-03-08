package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private const val PACKAGE = "uk.co.developmentanddinosaurs.diplodokode.generated"

class KotlinClassGenerator {

  fun generateFromSchema(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorEnum: DiscriminatorEnum? = null,
      discriminatorOverride: DiscriminatorOverride? = null,
      interfacePropertyNames: Set<String> = emptySet(),
  ): FileSpec =
      when {
        !schema.enum.isNullOrEmpty() -> generateTopLevelEnum(name, schema)
        !schema.oneOf.isNullOrEmpty() -> generateSealedInterface(name, schema, schema.oneOf, "oneOf", discriminatorEnum)
        !schema.anyOf.isNullOrEmpty() -> generateSealedInterface(name, schema, schema.anyOf, "anyOf", discriminatorEnum)
        else -> generateDataClass(name, schema, implementedInterfaces, discriminatorOverride, interfacePropertyNames)
      }

  private fun generateSealedInterface(
      name: String,
      schema: Schema,
      variants: List<Schema>,
      keyword: String,
      discriminatorEnum: DiscriminatorEnum?,
  ): FileSpec {
    val interfaceName = name.replaceFirstChar { it.uppercase() }
    val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName).addModifiers(KModifier.SEALED)

    schema.description?.let { interfaceBuilder.addKdoc("$it\n") }
    val kdoc = if (keyword == "anyOf") "One or more of the following variants may be used.\n"
               else "Exactly one of the following variants must be used.\n"
    interfaceBuilder.addKdoc(kdoc)

    if (discriminatorEnum != null) {
      val enumType = ClassName(PACKAGE, interfaceName, "Type")
      val enumBuilder = TypeSpec.enumBuilder("Type")
      discriminatorEnum.constants.forEach { enumBuilder.addEnumConstant(it) }
      interfaceBuilder.addType(enumBuilder.build())
      interfaceBuilder.addProperty(
          PropertySpec.builder(discriminatorEnum.propertyName, enumType)
              .addModifiers(KModifier.ABSTRACT)
              .build()
      )
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
          val propertyName = propName.replaceFirstChar { it.lowercase() }
          val isNullable = !(schema.required?.contains(propName) ?: false) || propSchema.nullable == true
          val kotlinType = resolveType(propName, propSchema, isNullable, emptyMap())
          val propBuilder = PropertySpec.builder(propertyName, kotlinType).addModifiers(KModifier.ABSTRACT)
          if (!propSchema.enum.isNullOrEmpty()) {
            val values = propSchema.enum.joinToString(", ")
            propBuilder.addKdoc("NOTE: this property has an inline enum constraint [$values] — define the enum as a \$ref schema for a typed abstract property.\n")
          }
          interfaceBuilder.addProperty(propBuilder.build())
        }

    variants.filter { it.ref == null }.takeIf { it.isNotEmpty() }?.let {
      interfaceBuilder.addKdoc("NOTE: Inline $keyword variants are not supported. Define variants as \$ref schemas.\n")
    }

    return FileSpec.builder(PACKAGE, interfaceName).addType(interfaceBuilder.build()).build()
  }

  private fun generateDataClass(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorOverride: DiscriminatorOverride? = null,
      interfacePropertyNames: Set<String> = emptySet(),
  ): FileSpec {
    val className = name.replaceFirstChar { it.uppercase() }
    val fileBuilder = FileSpec.builder(PACKAGE, className)

    if (hasUuidFormat(schema)) {
      fileBuilder.addAnnotation(
          AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
              .addMember("%T::class", ClassName("kotlin.uuid", "ExperimentalUuidApi"))
              .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
              .build()
      )
    }

    val enumClassNames =
        schema.properties
            ?.entries
            ?.filter { (propName, propValue) ->
              !propValue.enum.isNullOrEmpty() && propName != discriminatorOverride?.propertyName &&
                  propName !in interfacePropertyNames
            }
            ?.associate { (propName, propValue) ->
              val enumName = propName.replaceFirstChar { it.uppercase() }
              fileBuilder.addType(generateEnumClass(enumName, propValue.enum!!))
              propName to ClassName(PACKAGE, enumName)
            } ?: emptyMap()

    val constructorParams =
        schema.properties?.entries?.map { (propName, propValue) ->
          val propertyName = propName.replaceFirstChar { it.lowercase() }
          if (propName == discriminatorOverride?.propertyName) {
            val enumType = ClassName(PACKAGE, discriminatorOverride.interfaceName, "Type")
            ParameterSpec.builder(propertyName, enumType)
                .defaultValue("%T.%L", enumType, discriminatorOverride.constant)
                .build()
          } else {
            val isNullable = !(schema.required?.contains(propName) ?: false) || propValue.nullable == true
            val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
            ParameterSpec.builder(propertyName, kotlinType).build()
          }
        } ?: emptyList()

    val properties =
        schema.properties?.entries?.map { (propName, propValue) ->
          val propertyName = propName.replaceFirstChar { it.lowercase() }
          when {
            propName == discriminatorOverride?.propertyName -> {
              val enumType = ClassName(PACKAGE, discriminatorOverride.interfaceName, "Type")
              PropertySpec.builder(propertyName, enumType)
                  .addModifiers(KModifier.OVERRIDE)
                  .initializer(propertyName)
                  .build()
            }
            propName in interfacePropertyNames -> {
              val isNullable = !(schema.required?.contains(propName) ?: false) || propValue.nullable == true
              val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
              PropertySpec.builder(propertyName, kotlinType)
                  .addModifiers(KModifier.OVERRIDE)
                  .initializer(propertyName)
                  .build()
            }
            else -> {
              val isNullable = !(schema.required?.contains(propName) ?: false) || propValue.nullable == true
              val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
              val propertyBuilder = PropertySpec.builder(propertyName, kotlinType)
                  .addModifiers(KModifier.PUBLIC)
                  .initializer(propertyName)
              propValue.description?.let { propertyBuilder.addKdoc("$it\n") }
              if (propValue.type == "array" && !propValue.items?.enum.isNullOrEmpty()) {
                val values = propValue.items.enum.joinToString(", ")
                propertyBuilder.addKdoc("NOTE: items have an enum constraint [$values] — define as a \$ref schema for a typed List.\n")
              }
              propertyBuilder.build()
            }
          }
        } ?: emptyList()

    val dataClassBuilder =
        TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParams).build())
            .addProperties(properties)

    implementedInterfaces.forEach { iface ->
      dataClassBuilder.addSuperinterface(ClassName(PACKAGE, iface))
    }

    return fileBuilder.addType(dataClassBuilder.build()).build()
  }

  private fun generateTopLevelEnum(name: String, schema: Schema): FileSpec {
    val enumName = name.replaceFirstChar { it.uppercase() }
    return FileSpec.builder(PACKAGE, enumName)
        .addType(generateEnumClass(enumName, schema.enum ?: emptyList()))
        .build()
  }

  private fun generateEnumClass(name: String, values: List<String>): TypeSpec {
    val enumBuilder = TypeSpec.enumBuilder(name)
    values.forEach { enumBuilder.addEnumConstant(it.uppercase()) }
    return enumBuilder.build()
  }

  private fun resolveType(
      propName: String,
      propValue: Schema,
      isNullable: Boolean,
      enumClassNames: Map<String, ClassName>,
  ): TypeName {
    val baseType =
        when {
          propValue.ref != null -> ClassName(PACKAGE, propValue.ref.substringAfterLast("/"))
          propValue.type == "array" -> {
            val elementType = propValue.items?.let { resolveItemType(it) } ?: Any::class.asTypeName()
            List::class.asTypeName().parameterizedBy(elementType)
          }
          else -> enumClassNames[propName] ?: mapTypeToKotlin(propValue.type, propValue.format)
        }
    return if (isNullable) baseType.copy(nullable = true) else baseType
  }

  private fun resolveItemType(items: Schema): TypeName =
      when {
        items.ref != null -> ClassName(PACKAGE, items.ref.substringAfterLast("/"))
        items.type == "array" -> {
          val elementType = items.items?.let { resolveItemType(it) } ?: Any::class.asTypeName()
          List::class.asTypeName().parameterizedBy(elementType)
        }
        else -> mapTypeToKotlin(items.type, items.format)
      }

  private fun mapTypeToKotlin(openApiType: String?, format: String? = null): TypeName =
      formatMappings[openApiType]?.get(format)
          ?: baseMappings[openApiType]
          ?: String::class.asTypeName()

  private fun hasUuidFormat(schema: Schema): Boolean =
      schema.properties?.values?.any { usesUuid(it) } == true

  private fun usesUuid(prop: Schema): Boolean =
      (prop.type == "string" && prop.format == "uuid") ||
          (prop.type == "array" && prop.items != null && usesUuid(prop.items))

  companion object {
    private val formatMappings: Map<String, Map<String, TypeName>> = mapOf(
        "string" to mapOf(
            "date-time" to ClassName("kotlinx.datetime", "Instant"),
            "date"      to ClassName("kotlinx.datetime", "LocalDate"),
            "time"      to ClassName("kotlinx.datetime", "LocalTime"),
            "duration"  to ClassName("kotlin.time", "Duration"),
            "uuid"      to ClassName("kotlin.uuid", "Uuid"),
            "uri"       to String::class.asTypeName(),
            "byte"      to ByteArray::class.asTypeName(),
            "binary"    to ByteArray::class.asTypeName(),
        ),
        "integer" to mapOf(
            "int64" to Long::class.asTypeName(),
        ),
        "number" to mapOf(
            "float" to Float::class.asTypeName(),
        ),
    )

    private val baseMappings: Map<String, TypeName> = mapOf(
        "string"  to String::class.asTypeName(),
        "integer" to Int::class.asTypeName(),
        "number"  to Double::class.asTypeName(),
        "boolean" to Boolean::class.asTypeName(),
        "array"   to List::class.asTypeName(),
        "object"  to Any::class.asTypeName(),
    )
  }
}
