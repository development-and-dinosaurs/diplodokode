package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

private val OPT_IN = ClassName("kotlin", "OptIn")
private val EXPERIMENTAL_UUID_API = ClassName("kotlin.uuid", "ExperimentalUuidApi")
private val EXPERIMENTAL_TIME = ClassName("kotlin.time", "ExperimentalTime")

/**
 * Builds a single `@file:OptIn(...)` annotation covering every experimental marker
 * required by [types], or `null` if no opt-ins are needed. Combining markers into a
 * single annotation matches Kotlin's idiomatic style for multiple opt-ins.
 */
internal fun fileOptInAnnotation(typeResolver: TypeResolver, types: Iterable<TypeName>): AnnotationSpec? {
  val markers = buildList {
    if (types.any { typeResolver.containsKotlinUuid(it) }) add(EXPERIMENTAL_UUID_API)
    if (types.any { typeResolver.containsKotlinTimeInstant(it) }) add(EXPERIMENTAL_TIME)
  }
  if (markers.isEmpty()) return null
  return AnnotationSpec.builder(OPT_IN)
      .apply { markers.forEach { addMember("%T::class", it) } }
      .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
      .build()
}
