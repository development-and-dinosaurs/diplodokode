package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private val KOTLIN_UUID = ClassName("kotlin.uuid", "Uuid")

internal class TypeResolver(private val config: GeneratorConfig) {

  fun resolveType(
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
          propValue.additionalProperties != null && propValue.additionalProperties !is AdditionalProperties.Forbidden -> resolveMapType(propValue.additionalProperties)
          propValue.oneOf != null && isPrimitiveUnion(propValue.oneOf) ->
              ClassName(config.packageName, config.namingStrategy.className(primitiveUnionName(propValue.oneOf, config.typeMappingStrategy)))
          else -> enumClassNames[propName] ?: mapTypeToKotlin(propValue.type, propValue.format)
        }
    return if (isNullable) baseType.copy(nullable = true) else baseType
  }

  private fun resolveMapType(additionalProperties: AdditionalProperties): TypeName {
    val valueType = when (additionalProperties) {
      is AdditionalProperties.Allowed -> Any::class.asTypeName()
      is AdditionalProperties.Forbidden -> Any::class.asTypeName()
      is AdditionalProperties.Typed -> resolveItemType(additionalProperties.schema)
    }
    return Map::class.asTypeName().parameterizedBy(String::class.asTypeName(), valueType)
  }

  fun resolveItemType(items: Schema): TypeName =
      when {
        items.ref != null -> ClassName(config.packageName, config.namingStrategy.className(items.ref.substringAfterLast("/")))
        items.type == "array" -> {
          val elementType = items.items?.let { resolveItemType(it) } ?: Any::class.asTypeName()
          List::class.asTypeName().parameterizedBy(elementType)
        }
        else -> mapTypeToKotlin(items.type, items.format)
      }

  fun mapTypeToKotlin(openApiType: String?, format: String? = null): TypeName =
      openApiType?.let { config.typeMappingStrategy.resolve(it, format) } ?: String::class.asTypeName()

  fun containsKotlinUuid(type: TypeName): Boolean =
      when {
        type.copy(nullable = false) == KOTLIN_UUID -> true
        type is ParameterizedTypeName -> type.typeArguments.any { containsKotlinUuid(it) }
        else -> false
      }

  fun containsAny(type: TypeName): Boolean =
      when {
        type.copy(nullable = false) == Any::class.asTypeName() -> true
        type is ParameterizedTypeName -> type.typeArguments.any { containsAny(it) }
        else -> false
      }
}
