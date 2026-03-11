package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private val KOTLIN_UUID = ClassName("kotlin.uuid", "Uuid")

class KotlinClassGenerator(private val config: GeneratorConfig = GeneratorConfig()) {

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
    val interfaceName = config.namingStrategy.className(name)
    val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName).addModifiers(KModifier.SEALED)

    schema.description?.let { interfaceBuilder.addKdoc("$it\n") }
    val kdoc = if (keyword == "anyOf") "One or more of the following variants may be used.\n"
               else "Exactly one of the following variants must be used.\n"
    interfaceBuilder.addKdoc(kdoc)

    if (discriminatorEnum != null) {
      val enumType = ClassName(config.packageName, interfaceName, "Type")
      val enumBuilder = TypeSpec.enumBuilder("Type")
      config.serialisationStrategy?.let { enumBuilder.addAnnotation(it.classAnnotation) }
      discriminatorEnum.constants.zip(discriminatorEnum.rawValues).forEach { (kotlinName, rawValue) ->
        val constantAnnotation = config.serialisationStrategy?.enumConstantAnnotation(rawValue)
        if (constantAnnotation != null) {
          enumBuilder.addEnumConstant(kotlinName, TypeSpec.anonymousClassBuilder().addAnnotation(constantAnnotation).build())
        } else {
          enumBuilder.addEnumConstant(kotlinName)
        }
      }
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
          val propertyName = config.namingStrategy.propertyName(propName)
          val isNullable = config.nullabilityStrategy.isNullable(propName, propSchema, schema.required?.toSet() ?: emptySet())
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

    return FileSpec.builder(config.packageName, interfaceName).addType(interfaceBuilder.build()).build()
  }

  private fun generateDataClass(
      name: String,
      schema: Schema,
      implementedInterfaces: List<String> = emptyList(),
      discriminatorOverride: DiscriminatorOverride? = null,
      interfacePropertyNames: Set<String> = emptySet(),
  ): FileSpec {
    val className = config.namingStrategy.className(name)
    val fileBuilder = FileSpec.builder(config.packageName, className)

    val enumClassNames =
        schema.properties
            ?.entries
            ?.filter { (propName, propValue) ->
              !propValue.enum.isNullOrEmpty() && propName != discriminatorOverride?.propertyName &&
                  propName !in interfacePropertyNames
            }
            ?.associate { (propName, propValue) ->
              val enumName = config.namingStrategy.className(propName)
              fileBuilder.addType(generateEnumClass(enumName, propValue.enum!!))
              propName to ClassName(config.packageName, enumName)
            } ?: emptyMap()

    val constructorParams =
        schema.properties?.entries?.map { (propName, propValue) ->
          val propertyName = config.namingStrategy.propertyName(propName)
          if (propName == discriminatorOverride?.propertyName) {
            val enumType = ClassName(config.packageName, config.namingStrategy.className(discriminatorOverride.interfaceName), "Type")
            ParameterSpec.builder(propertyName, enumType)
                .defaultValue("%T.%L", enumType, discriminatorOverride.constant)
                .build()
          } else {
            val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, schema.required?.toSet() ?: emptySet())
            val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
            ParameterSpec.builder(propertyName, kotlinType).build()
          }
        } ?: emptyList()

    val properties =
        schema.properties?.entries?.map { (propName, propValue) ->
          val propertyName = config.namingStrategy.propertyName(propName)
          when {
            propName == discriminatorOverride?.propertyName -> {
              val enumType = ClassName(config.packageName, config.namingStrategy.className(discriminatorOverride.interfaceName), "Type")
              PropertySpec.builder(propertyName, enumType)
                  .addModifiers(KModifier.OVERRIDE)
                  .initializer(propertyName)
                  .build()
            }
            propName in interfacePropertyNames -> {
              val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, schema.required?.toSet() ?: emptySet())
              val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
              PropertySpec.builder(propertyName, kotlinType)
                  .addModifiers(KModifier.OVERRIDE)
                  .initializer(propertyName)
                  .build()
            }
            else -> {
              val isNullable = config.nullabilityStrategy.isNullable(propName, propValue, schema.required?.toSet() ?: emptySet())
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

    val allTypes = constructorParams.map { it.type } + properties.map { it.type }
    if (allTypes.any { containsKotlinUuid(it) }) {
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
            .also { builder -> config.serialisationStrategy?.let { builder.addAnnotation(it.classAnnotation) } }
            .primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParams).build())
            .addProperties(properties)

    implementedInterfaces.forEach { iface ->
      dataClassBuilder.addSuperinterface(ClassName(config.packageName, config.namingStrategy.className(iface)))
    }

    return fileBuilder.addType(dataClassBuilder.build()).build()
  }

  private fun generateTopLevelEnum(name: String, schema: Schema): FileSpec {
    val enumName = config.namingStrategy.className(name)
    return FileSpec.builder(config.packageName, enumName)
        .addType(generateEnumClass(enumName, schema.enum ?: emptyList()))
        .build()
  }

  private fun generateEnumClass(name: String, values: List<String>): TypeSpec {
    val enumBuilder = TypeSpec.enumBuilder(name)
    config.serialisationStrategy?.let { enumBuilder.addAnnotation(it.classAnnotation) }
    values.forEach { rawValue ->
      val kotlinName = config.namingStrategy.enumConstant(rawValue)
      val constantAnnotation = config.serialisationStrategy?.enumConstantAnnotation(rawValue)
      if (constantAnnotation != null) {
        enumBuilder.addEnumConstant(kotlinName, TypeSpec.anonymousClassBuilder().addAnnotation(constantAnnotation).build())
      } else {
        enumBuilder.addEnumConstant(kotlinName)
      }
    }
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
          propValue.ref != null -> ClassName(config.packageName, config.namingStrategy.className(propValue.ref.substringAfterLast("/")))
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
        items.ref != null -> ClassName(config.packageName, config.namingStrategy.className(items.ref.substringAfterLast("/")))
        items.type == "array" -> {
          val elementType = items.items?.let { resolveItemType(it) } ?: Any::class.asTypeName()
          List::class.asTypeName().parameterizedBy(elementType)
        }
        else -> mapTypeToKotlin(items.type, items.format)
      }

  private fun mapTypeToKotlin(openApiType: String?, format: String? = null): TypeName =
      openApiType?.let { config.typeMappingStrategy.resolve(it, format) } ?: String::class.asTypeName()

  private fun containsKotlinUuid(type: TypeName): Boolean =
      when {
        type.copy(nullable = false) == KOTLIN_UUID -> true
        type is ParameterizedTypeName -> type.typeArguments.any { containsKotlinUuid(it) }
        else -> false
      }
}
