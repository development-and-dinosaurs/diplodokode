package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName

interface TypeMappingStrategy {
  fun resolve(type: String, format: String?): TypeName?

  fun withOverrides(
      formatOverrides: Map<String, TypeName> = emptyMap(),
      baseOverrides: Map<String, TypeName> = emptyMap(),
  ): TypeMappingStrategy = OverridingTypeMappingStrategy(this, formatOverrides, baseOverrides)
}

abstract class MapBasedTypeMappingStrategy : TypeMappingStrategy {
  protected abstract val formatMappings: Map<String, Map<String, TypeName>>

  private val baseMappings: Map<String, TypeName> = mapOf(
      "string"  to String::class.asTypeName(),
      "integer" to Int::class.asTypeName(),
      "number"  to Double::class.asTypeName(),
      "boolean" to Boolean::class.asTypeName(),
      "array"   to List::class.asTypeName(),
      "object"  to Any::class.asTypeName(),
  )

  override fun resolve(type: String, format: String?): TypeName? =
      (if (format != null) formatMappings[type]?.get(format) else null)
          ?: baseMappings[type]
}

private const val KOTLINX_DATETIME = "kotlinx.datetime"
private const val KOTLIN_TIME = "kotlin.time"
private const val KOTLIN_UUID = "kotlin.uuid"

class KotlinMultiplatformTypeMappingStrategy : MapBasedTypeMappingStrategy() {
  override val formatMappings: Map<String, Map<String, TypeName>> = mapOf(
      "string" to mapOf(
          "date-time" to ClassName(KOTLINX_DATETIME, "Instant"),
          "date"      to ClassName(KOTLINX_DATETIME, "LocalDate"),
          "time"      to ClassName(KOTLINX_DATETIME, "LocalTime"),
          "duration"  to ClassName(KOTLIN_TIME, "Duration"),
          "uuid"      to ClassName(KOTLIN_UUID, "Uuid"),
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
}

private const val JAVA_TIME = "java.time"
private const val JAVA_UTIL = "java.util"
private const val JAVA_NET = "java.net"

class JavaTypeMappingStrategy : MapBasedTypeMappingStrategy() {
  override val formatMappings: Map<String, Map<String, TypeName>> = mapOf(
      "string" to mapOf(
          "date-time" to ClassName(JAVA_TIME, "Instant"),
          "date"      to ClassName(JAVA_TIME, "LocalDate"),
          "time"      to ClassName(JAVA_TIME, "LocalTime"),
          "duration"  to ClassName(JAVA_TIME, "Duration"),
          "uuid"      to ClassName(JAVA_UTIL, "UUID"),
          "uri"       to ClassName(JAVA_NET, "URI"),
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
}

class CustomTypeMappingStrategy(
    private val formatMappings: Map<String, Map<String, TypeName>> = emptyMap(),
    private val baseMappings: Map<String, TypeName> = emptyMap(),
) : TypeMappingStrategy {
  override fun resolve(type: String, format: String?): TypeName? =
      (if (format != null) formatMappings[type]?.get(format) else null)
          ?: baseMappings[type]
}

internal class OverridingTypeMappingStrategy(
    private val base: TypeMappingStrategy,
    private val formatOverrides: Map<String, TypeName>,
    private val baseOverrides: Map<String, TypeName>,
) : TypeMappingStrategy {
  override fun resolve(type: String, format: String?): TypeName? =
      (if (format != null) formatOverrides[format] else null)
          ?: baseOverrides[type]
          ?: base.resolve(type, format)
}
