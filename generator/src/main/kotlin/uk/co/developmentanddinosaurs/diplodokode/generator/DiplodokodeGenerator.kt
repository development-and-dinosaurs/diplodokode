package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.OpenApiSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.OpenApiSpecParser
import java.io.File

class DiplodokodeGenerator(private val config: GeneratorConfig = GeneratorConfig()) {
  private val parser = OpenApiSpecParser()
  private val resolver = SchemaResolver(config)
  private val classGenerator = KotlinClassGenerator(config)
  private val moduleGenerator = SerializersModuleGenerator(config)
  private val unionInterfaceGenerator = UnionInterfaceGenerator(config)
  private val validator = SpecValidator()

  /**
   * Generates Kotlin files from the given OpenAPI spec file.
   *
   * Throws [IllegalStateException] if generation fails. For structured error handling,
   * use [generateFromSpecWithResult] instead.
   */
  fun generateFromSpec(specFile: File): List<FileSpec> {
    return when (val result = generateFromSpecWithResult(specFile)) {
      is GenerationResult.Success -> result.files
      is GenerationResult.PartialSuccess -> result.files
      is GenerationResult.Failure -> throw IllegalStateException(
          result.errors.joinToString("\n") { "[${it.schemaName}] ${it.message}" }
      )
    }
  }

  /**
   * Generates Kotlin files from the given OpenAPI spec file, returning a structured result.
   *
   * Returns [GenerationResult.Success] when generation completes without issues,
   * [GenerationResult.PartialSuccess] when generation completes with warnings, or
   * [GenerationResult.Failure] when generation cannot proceed.
   */
  fun generateFromSpecWithResult(specFile: File): GenerationResult {
    val openApiSpec = parser.parse(specFile)
    val schemas = openApiSpec.components?.schemas ?: return GenerationResult.Success(emptyList())

    val diagnostics = validator.validate(schemas)
    val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
    val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }

    if (errors.isNotEmpty()) return GenerationResult.Failure(errors)

    val files = generateFromParsed(openApiSpec)
    return if (warnings.isNotEmpty()) GenerationResult.PartialSuccess(files, warnings)
    else GenerationResult.Success(files)
  }

  private fun generateFromParsed(openApiSpec: OpenApiSpec): List<FileSpec> {
    val schemas = openApiSpec.components?.schemas ?: return emptyList()
    val (resolvedSchemas, implementedInterfaces, discriminatorEnums, discriminatorOverrides, interfacePropertyNames) = resolver.resolve(schemas)

    val classFiles = resolvedSchemas.map { (name, schema) ->
      classGenerator.generateFromSchema(
          name,
          schema,
          implementedInterfaces[name] ?: emptyList(),
          discriminatorEnums[name],
          discriminatorOverrides[name] ?: emptyList(),
          interfacePropertyNames[name] ?: emptySet(),
      )
    }

    val moduleFile = if (config.serialisationStrategy != null && config.polymorphismStrategy == PolymorphismStrategy.MODULE) {
      val interfaceVariants = mutableMapOf<String, MutableList<String>>()
      implementedInterfaces.forEach { (variantName, interfaces) ->
        interfaces.forEach { interfaceName ->
          interfaceVariants.getOrPut(interfaceName) { mutableListOf() }.add(variantName)
        }
      }
      moduleGenerator.generate(interfaceVariants)
    } else null

    val unionArities = resolvedSchemas.values
      .mapNotNull { it.oneOf }
      .filter { it.isNotEmpty() && isPrimitiveUnion(it) }
      .map { it.size }
      .toSet()
    val unionInterfaceFiles = unionArities.map { unionInterfaceGenerator.generate(it) }

    val allFiles = if (moduleFile != null) classFiles + moduleFile else classFiles
    return (allFiles + unionInterfaceFiles).distinctBy { "${it.packageName}.${it.name}" }
  }
}
