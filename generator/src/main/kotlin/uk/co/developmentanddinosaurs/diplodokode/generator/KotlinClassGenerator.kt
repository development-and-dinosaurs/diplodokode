package uk.co.developmentanddinosaurs.diplodokode.generator

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

  fun generateFromSchema(name: String, schema: Schema): FileSpec =
      if (!schema.enum.isNullOrEmpty()) generateTopLevelEnum(name, schema)
      else generateDataClass(name, schema)

  private fun generateDataClass(name: String, schema: Schema): FileSpec {
    val className = name.replaceFirstChar { it.uppercase() }
    val fileBuilder = FileSpec.builder(PACKAGE, className)

    val enumClassNames =
        schema.properties
            ?.entries
            ?.filter { (_, propValue) -> !propValue.enum.isNullOrEmpty() }
            ?.associate { (propName, propValue) ->
              val enumName = propName.replaceFirstChar { it.uppercase() }
              fileBuilder.addType(generateEnumClass(enumName, propValue.enum!!))
              propName to ClassName(PACKAGE, enumName)
            } ?: emptyMap()

    val constructorParams =
        schema.properties?.entries?.map { (propName, propValue) ->
          val isNullable =
              !(schema.required?.contains(propName) ?: false) || propValue.nullable == true
          val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
          val propertyName = propName.replaceFirstChar { it.lowercase() }
          ParameterSpec.builder(propertyName, kotlinType).build()
        } ?: emptyList()

    val properties =
        schema.properties?.entries?.map { (propName, propValue) ->
          val isNullable =
              !(schema.required?.contains(propName) ?: false) || propValue.nullable == true
          val kotlinType = resolveType(propName, propValue, isNullable, enumClassNames)
          val propertyName = propName.replaceFirstChar { it.lowercase() }

          val propertyBuilder =
              PropertySpec.builder(propertyName, kotlinType)
                  .addModifiers(KModifier.PUBLIC)
                  .initializer(propertyName)

          propValue.description?.let { propertyBuilder.addKdoc("$it\n") }

          propertyBuilder.build()
        } ?: emptyList()

    val dataClass =
        TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParams).build())
            .addProperties(properties)
            .build()

    return fileBuilder.addType(dataClass).build()
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
          else -> enumClassNames[propName] ?: mapTypeToKotlin(propValue.type)
        }
    return if (isNullable) baseType.copy(nullable = true) else baseType
  }

  private fun resolveItemType(items: Schema): TypeName =
      when {
        items.ref != null -> ClassName(PACKAGE, items.ref.substringAfterLast("/"))
        else -> mapTypeToKotlin(items.type)
      }

  private fun mapTypeToKotlin(openApiType: String?): TypeName =
      when (openApiType) {
        "string" -> String::class.asTypeName()
        "integer" -> Int::class.asTypeName()
        "number" -> Double::class.asTypeName()
        "boolean" -> Boolean::class.asTypeName()
        "array" -> List::class.asTypeName()
        "object" -> Any::class.asTypeName()
        else -> String::class.asTypeName()
      }
}
