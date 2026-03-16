package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private val PRIMITIVE_UNION_TYPES = setOf("string", "integer", "number", "boolean")

/** Canonical disambiguation order — used for both naming and serializer dispatch. */
internal val PRIMITIVE_DECODE_PRIORITY = mapOf("string" to 0, "boolean" to 1, "integer" to 2, "number" to 3)

internal fun isPrimitiveUnion(oneOf: List<Schema>): Boolean =
    oneOf.isNotEmpty() && oneOf.all { it.ref == null && it.format == null && it.type in PRIMITIVE_UNION_TYPES }

internal fun primitiveUnionName(oneOf: List<Schema>, typeMappingStrategy: TypeMappingStrategy): String =
  oneOf
    .sortedBy { PRIMITIVE_DECODE_PRIORITY[it.type] ?: Int.MAX_VALUE }
    .mapNotNull { it.type }
    .joinToString("Or") { type ->
      (typeMappingStrategy.resolve(type, null) as? ClassName)?.simpleName
        ?: type.replaceFirstChar { it.uppercase() }
    }
