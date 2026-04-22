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
      interfacesByVariant: Map<String, List<String>> = emptyMap(),
  ): TypeName {
    val baseType =
        when {
          propValue.ref != null -> resolveRef(propValue.ref)
          propValue.type == "array" -> {
            val elementType = propValue.items?.let { resolveItemType(it, interfacesByVariant) } ?: Any::class.asTypeName()
            List::class.asTypeName().parameterizedBy(elementType)
          }
          propValue.additionalProperties != null && propValue.additionalProperties !is AdditionalProperties.Forbidden -> resolveMapType(propValue.additionalProperties)
          propValue.oneOf != null && isPrimitiveUnion(propValue.oneOf) ->
              ClassName(config.packageName, config.namingStrategy.className(primitiveUnionName(propValue.oneOf, config.typeMappingStrategy)))
          !propValue.allOf.isNullOrEmpty() -> resolveAllOfType(propValue.allOf)
          !propValue.oneOf.isNullOrEmpty() -> resolveUnionType(propValue.oneOf, interfacesByVariant)
          !propValue.anyOf.isNullOrEmpty() -> resolveUnionType(propValue.anyOf, interfacesByVariant)
          else -> enumClassNames[propName] ?: mapTypeToKotlin(propValue.type, propValue.format)
        }
    return if (isNullable) baseType.copy(nullable = true) else baseType
  }

  private fun resolveAllOfType(allOf: List<Schema>): TypeName {
    val singleRef = allOf.singleOrNull()?.ref
    return if (singleRef != null) resolveRef(singleRef) else Any::class.asTypeName()
  }

  private fun resolveUnionType(variants: List<Schema>, interfacesByVariant: Map<String, List<String>>): TypeName {
    val refs = variants.map { it.ref?.substringAfterLast("/") }
    if (refs.any { it == null }) return Any::class.asTypeName()
    val refNames = refs.filterNotNull()
    if (refNames.isEmpty()) return Any::class.asTypeName()
    val commonInterfaces = refNames
        .map { interfacesByVariant[it]?.toSet() ?: emptySet() }
        .reduce { a, b -> a intersect b }
    val commonInterface = commonInterfaces.firstOrNull() ?: return Any::class.asTypeName()
    return ClassName(config.packageName, config.namingStrategy.className(commonInterface))
  }

  private fun resolveMapType(additionalProperties: AdditionalProperties): TypeName {
    val valueType = when (additionalProperties) {
      is AdditionalProperties.Allowed -> Any::class.asTypeName()
      is AdditionalProperties.Forbidden -> Any::class.asTypeName()
      is AdditionalProperties.Typed -> resolveItemType(additionalProperties.schema)
    }
    return Map::class.asTypeName().parameterizedBy(String::class.asTypeName(), valueType)
  }

  fun resolveItemType(items: Schema, interfacesByVariant: Map<String, List<String>> = emptyMap()): TypeName =
      when {
        items.ref != null -> resolveRef(items.ref)
        items.type == "array" -> {
          val elementType = items.items?.let { resolveItemType(it, interfacesByVariant) } ?: Any::class.asTypeName()
          List::class.asTypeName().parameterizedBy(elementType)
        }
        !items.allOf.isNullOrEmpty() -> resolveAllOfType(items.allOf)
        !items.oneOf.isNullOrEmpty() && !isPrimitiveUnion(items.oneOf) -> resolveUnionType(items.oneOf, interfacesByVariant)
        !items.anyOf.isNullOrEmpty() -> resolveUnionType(items.anyOf, interfacesByVariant)
        else -> mapTypeToKotlin(items.type, items.format)
      }

  private fun resolveRef(ref: String): ClassName {
    val schemaName = ref.substringAfterLast("/")
    return config.schemaOverrides[schemaName]
        ?: ClassName(config.packageName, config.namingStrategy.className(schemaName))
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
