package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class KotlinClassGenerator {

  fun generateDataClass(name: String, schema: Schema): FileSpec {
    val className = name.replaceFirstChar { it.uppercase() }

    val constructorParams =
        schema.properties?.entries?.map { (propName, propValue) ->
          val isRequired = schema.required?.contains(propName) ?: false
          val kotlinType = mapTypeToKotlin(propValue.type, isRequired)
          val propertyName = propName.replaceFirstChar { it.lowercase() }

          ParameterSpec.builder(propertyName, kotlinType).build()
        } ?: emptyList()

    val properties = schema.properties?.entries?.map { (propName, propValue) ->
      val isRequired = schema.required?.contains(propName) ?: false
      val kotlinType = mapTypeToKotlin(propValue.type, isRequired)
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
        .primaryConstructor(
          FunSpec.constructorBuilder().addParameters(constructorParams).build()
        )
        .addProperties(properties)
        .build()

    return FileSpec.builder("uk.co.developmentanddinosaurs.diplodokode.generated", className)
      .addType(dataClass)
      .build()
  }

  private fun mapTypeToKotlin(openApiType: String?, isRequired: Boolean): TypeName {
    val baseType =
        when (openApiType) {
          "string" -> String::class.asTypeName()
          "integer" -> Int::class.asTypeName()
          "number" -> Double::class.asTypeName()
          "boolean" -> Boolean::class.asTypeName()
          "array" -> List::class.asTypeName()
          "object" -> Any::class.asTypeName()
          else -> String::class.asTypeName()
        }

    return if (isRequired) baseType else baseType.copy(nullable = true)
  }
}
