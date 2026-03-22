package uk.co.developmentanddinosaurs.diplodokode.generator

import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

internal class SpecValidator {

  fun validate(schemas: Map<String, Schema>): List<GenerationDiagnostic> {
    val diagnostics = mutableListOf<GenerationDiagnostic>()
    schemas.forEach { (name, schema) ->
      checkRefs(name, schema, schemas, diagnostics)
      checkInlineVariants(name, schema, diagnostics)
      checkArraysWithoutItems(name, schema, diagnostics)
      checkDiscriminatorCoverage(name, schema, schemas, diagnostics)
    }
    return diagnostics
  }

  private fun checkRefs(
      schemaName: String,
      schema: Schema,
      allSchemas: Map<String, Schema>,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    collectRefs(schema).forEach { (location, refName) ->
      if (refName !in allSchemas) {
        diagnostics.add(
            GenerationDiagnostic(
                schemaName = schemaName,
                location = location,
                message = "References undefined schema '$refName'.",
                severity = DiagnosticSeverity.ERROR,
            )
        )
      }
    }
  }

  private fun collectRefs(schema: Schema, prefix: String = ""): List<Pair<String, String>> {
    val refs = mutableListOf<Pair<String, String>>()
    schema.ref?.let { refs.add("${prefix}\$ref" to it.substringAfterLast("/")) }
    schema.allOf?.forEachIndexed { i, s -> refs.addAll(collectRefs(s, "${prefix}allOf[$i].")) }
    schema.oneOf?.forEachIndexed { i, s -> refs.addAll(collectRefs(s, "${prefix}oneOf[$i].")) }
    schema.anyOf?.forEachIndexed { i, s -> refs.addAll(collectRefs(s, "${prefix}anyOf[$i].")) }
    schema.items?.let { refs.addAll(collectRefs(it, "${prefix}items.")) }
    schema.properties?.forEach { (key, propSchema) ->
      refs.addAll(collectRefs(propSchema, "${prefix}properties.$key."))
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

  private fun checkDiscriminatorCoverage(
      schemaName: String,
      schema: Schema,
      allSchemas: Map<String, Schema>,
      diagnostics: MutableList<GenerationDiagnostic>,
  ) {
    val discriminator = schema.discriminator ?: return
    val variants = schema.oneOf ?: schema.anyOf ?: return
    val refVariants = variants.mapNotNull { it.ref?.substringAfterLast("/") }

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
