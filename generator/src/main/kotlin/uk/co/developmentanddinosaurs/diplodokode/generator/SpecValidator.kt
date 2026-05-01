package uk.co.developmentanddinosaurs.diplodokode.generator

import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

internal class SpecValidator {

  fun validate(schemas: Map<String, Schema>, knownExternalNames: Set<String> = emptySet()): List<GenerationDiagnostic> {
    val allKnownNames = schemas.keys + knownExternalNames
    val diagnostics = mutableListOf<GenerationDiagnostic>()
    val interfacesByVariant = buildInterfacesByVariant(schemas)
    schemas.forEach { (name, schema) ->
      checkRefs(name, schema, allKnownNames, diagnostics)
      checkInlineVariants(name, schema, diagnostics)
      checkArraysWithoutItems(name, schema, diagnostics)
      checkDiscriminatorCoverage(name, schema, schemas, diagnostics)
      checkAmbiguousCommonInterface(name, schema, interfacesByVariant, diagnostics)
      checkInlinePrimitiveUnionInItems(name, schema, diagnostics)
      checkPolymorphicOverride(name, schema, knownExternalNames, diagnostics)
    }
    return diagnostics
  }

  private fun buildInterfacesByVariant(schemas: Map<String, Schema>): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()
    schemas.forEach { (interfaceName, schema) ->
      val variants = schema.oneOf ?: schema.anyOf ?: return@forEach
      variants.mapNotNull { it.ref?.let(RefUtil::schemaNameFromRef) }.forEach { variantName ->
        result.getOrPut(variantName) { mutableSetOf() }.add(interfaceName)
      }
    }
    return result
  }

  private fun checkRefs(
      schemaName: String,
      schema: Schema,
      allKnownNames: Set<String>,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    collectRefs(schema).forEach { (location, refName, rawRef) ->
      if (refName !in allKnownNames) {
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = location,
                message = "References undefined schema '$refName'.",
                severity = DiagnosticSeverity.ERROR,
            )
        )
        return@forEach
      }
      if (RefUtil.isExternalRef(rawRef)) {
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = location,
                message = "Ref '$rawRef' points to an external document. Cross-document refs are not supported; resolving by local schema name '$refName' may collide.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      } else if (!RefUtil.isLocalComponentsRef(rawRef)) {
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = location,
                message = "Ref '$rawRef' is not a canonical local reference (#/components/schemas/<Name>); resolving by path-tail '$refName' which may collide with other schemas.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      }
    }
  }

  private fun collectRefs(schema: Schema, prefix: String = ""): List<Triple<String, String, String>> {
    val refs = mutableListOf<Triple<String, String, String>>()
    schema.ref?.let { refs.add(Triple("${prefix}\$ref", RefUtil.schemaNameFromRef(it), it)) }
    schema.allOf?.forEachIndexed { i, s -> refs.addAll(collectRefs(s, "${prefix}allOf[$i].")) }
    schema.oneOf?.forEachIndexed { i, s -> refs.addAll(collectRefs(s, "${prefix}oneOf[$i].")) }
    schema.anyOf?.forEachIndexed { i, s -> refs.addAll(collectRefs(s, "${prefix}anyOf[$i].")) }
    schema.items?.let { refs.addAll(collectRefs(it, "${prefix}items.")) }
    schema.properties?.forEach { (key, propSchema) ->
      refs.addAll(collectRefs(propSchema, "${prefix}properties.$key."))
    }
    (schema.additionalProperties as? AdditionalProperties.Typed)?.let {
      refs.addAll(collectRefs(it.schema, "${prefix}additionalProperties."))
    }
    schema.discriminator?.mapping?.forEach { (key, refPath) ->
      refs.add(Triple("${prefix}discriminator.mapping.$key", RefUtil.schemaNameFromRef(refPath), refPath))
    }
    return refs
  }

  private fun checkInlineVariants(
      schemaName: String,
      schema: Schema,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    val (keyword, variants) = when {
      schema.oneOf != null -> "oneOf" to schema.oneOf
      schema.anyOf != null -> "anyOf" to schema.anyOf
      else -> return
    }
    variants.forEachIndexed { i, variant ->
      if (variant.ref == null) {
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = "$keyword[$i]",
                message = "Inline $keyword variant has no \$ref — no named Kotlin type can be generated. Refactor to a \$ref schema.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      }
    }
  }

  private fun checkArraysWithoutItems(
      schemaName: String,
      schema: Schema,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    if (schema.type == "array" && schema.items == null) {
      diagnostics.add(
          GenerationDiagnostic(
              schemaName = schemaName,
              location = "items",
              message = "Array schema has no 'items' — will generate List<Any>.",
              severity = DiagnosticSeverity.WARNING,
          )
      )
    }
    schema.properties?.forEach { (propName, propSchema) ->
      if (propSchema.type == "array" && propSchema.items == null) {
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = "properties.$propName.items",
                message = "Array property '$propName' has no 'items' schema — will generate List<Any>.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      }
    }
  }

  private fun checkAmbiguousCommonInterface(
      schemaName: String,
      schema: Schema,
      interfacesByVariant: Map<String, Set<String>>,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    schema.properties?.forEach { (propName, propSchema) ->
      val variants = propSchema.oneOf ?: propSchema.anyOf ?: return@forEach
      val refNames = variants.mapNotNull { it.ref?.let(RefUtil::schemaNameFromRef) }
      if (refNames.size < 2 || refNames.size != variants.size) return@forEach
      val common = refNames
          .map { interfacesByVariant[it] ?: emptySet() }
          .reduce { a, b -> a intersect b }
      if (common.size > 1) {
        val sorted = common.sorted()
        val picked = sorted.first()
        val alternatives = sorted.drop(1).joinToString(", ")
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = "properties.$propName",
                message = "Variants ${refNames.joinToString(", ")} share multiple common interfaces ($picked, $alternatives); resolving to '$picked' (alphabetically first). Pick one parent explicitly to remove the ambiguity.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      }
    }
  }

  private fun checkInlinePrimitiveUnionInItems(
      schemaName: String,
      schema: Schema,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    schema.properties?.forEach { (propName, propSchema) ->
      collectInlinePrimitiveUnionsInItems(propSchema, "properties.$propName").forEach { (location, oneOf) ->
        val typeList = oneOf.mapNotNull { it.type }.joinToString(", ")
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = location,
                message = "Inline primitive oneOf [$typeList] used as array items — generating a synthetic top-level wrapper schema. Define it as a top-level oneOf schema and \$ref it to give the wrapper a meaningful name.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      }
    }
  }

  private fun collectInlinePrimitiveUnionsInItems(schema: Schema, prefix: String): List<Pair<String, List<Schema>>> {
    val items = schema.items ?: return emptyList()
    val result = mutableListOf<Pair<String, List<Schema>>>()
    items.oneOf?.takeIf { isPrimitiveUnion(it) }?.let { result.add("$prefix.items.oneOf" to it) }
    result.addAll(collectInlinePrimitiveUnionsInItems(items, "$prefix.items"))
    return result
  }

  private fun checkPolymorphicOverride(
      schemaName: String,
      schema: Schema,
      knownExternalNames: Set<String>,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    if (schemaName !in knownExternalNames) return
    val keyword = when {
      schema.oneOf != null -> "oneOf"
      schema.anyOf != null -> "anyOf"
      else -> return
    }
    val hasDiscriminator = schema.discriminator != null
    val typeNote = if (hasDiscriminator) " It must also expose a nested 'Type' enum with constants matching the discriminator values, since variant data classes will reference '$schemaName.Type.<CONSTANT>'." else ""
    diagnostics.add(
        GenerationDiagnostic(
            schemaName = schemaName,
            location = keyword,
            message = "Schema '$schemaName' is overridden via schemaOverrides but is declared as a polymorphic ($keyword) sealed-interface schema. The override class must be a sealed/open interface that variant data classes can implement.$typeNote",
            severity = DiagnosticSeverity.WARNING,
        )
    )
  }

  private fun checkDiscriminatorCoverage(
      schemaName: String,
      schema: Schema,
      allSchemas: Map<String, Schema>,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    val discriminator = schema.discriminator ?: return
    val variants = schema.oneOf ?: schema.anyOf ?: return
    val refVariants = variants.mapNotNull { it.ref?.let(RefUtil::schemaNameFromRef) }

    val missingVariants = refVariants.filter { variantName ->
      val variantSchema = allSchemas[variantName] ?: return@filter false
      !variantSchema.properties.orEmpty().containsKey(discriminator.propertyName)
    }

    // Only warn when SOME variants have the property but not all — that's the bug.
    // If no variants have it, the discriminator may be intentional for serialisation only.
    if (missingVariants.isNotEmpty() && missingVariants.size < refVariants.size) {
      missingVariants.forEach { variantName ->
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = "discriminator.propertyName",
                message = "Discriminator property '${discriminator.propertyName}' is absent from variant '$variantName' — falling back to `abstract val ${discriminator.propertyName}: String`.",
                severity = DiagnosticSeverity.WARNING,
            )
        )
      }
    }
  }
}
