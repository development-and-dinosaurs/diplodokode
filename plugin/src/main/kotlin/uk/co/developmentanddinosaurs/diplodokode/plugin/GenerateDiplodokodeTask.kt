package uk.co.developmentanddinosaurs.diplodokode.plugin

import com.squareup.kotlinpoet.ClassName
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException
import uk.co.developmentanddinosaurs.diplodokode.generator.AllNonNullableStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.AllNullableStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.DefaultNamingStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.DiplodokodeGenerator
import uk.co.developmentanddinosaurs.diplodokode.generator.GenerationResult
import uk.co.developmentanddinosaurs.diplodokode.generator.GeneratorConfig
import uk.co.developmentanddinosaurs.diplodokode.generator.JavaTypeMappingStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.KotlinMultiplatformTypeMappingStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.KotlinxSerialisationStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.NamingStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.NullabilityStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.PreserveNamingStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.SerializationStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.SpecDrivenNullabilityStrategy
import uk.co.developmentanddinosaurs.diplodokode.generator.TypeMappingStrategy

@CacheableTask
abstract class GenerateDiplodokodeTask : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputFile: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  abstract val packageName: Property<String>

  @get:Input
  abstract val typeMappingPreset: Property<String>

  @get:Input
  abstract val typeMappingFormatOverrides: MapProperty<String, String>

  @get:Input
  abstract val typeMappingBaseOverrides: MapProperty<String, String>

  @get:Input
  abstract val namingMode: Property<String>

  @get:Input
  abstract val nullabilityMode: Property<String>

  @get:Input
  abstract val serialisationLibrary: Property<String>

  @get:Input
  @get:Optional
  abstract val modulePackage: Property<String>

  @get:Input
  abstract val moduleName: Property<String>

  @get:Input
  abstract val schemaOverrides: MapProperty<String, String>

  @TaskAction
  fun generate() {
    val config = GeneratorConfig(
        namingStrategy = buildNamingStrategy(),
        nullabilityStrategy = buildNullabilityStrategy(),
        packageName = packageName.get(),
        typeMappingStrategy = buildTypeMappingStrategy(),
        serialisationStrategy = buildSerialisationStrategy(),
        modulePackage = if (modulePackage.isPresent) modulePackage.get() else null,
        moduleName = moduleName.get(),
        schemaOverrides = schemaOverrides.get().mapValues { (_, fqcn) -> ClassName.bestGuess(fqcn) },
    )
    val generator = DiplodokodeGenerator(config)
    val specFile = inputFile.get().asFile
    val outputDirectory = outputDir.get().asFile

    when (val result = generator.generateFromSpecWithResult(specFile)) {
      is GenerationResult.Success -> writeFiles(result.files, outputDirectory)
      is GenerationResult.PartialSuccess -> {
        result.warnings.forEach { diag ->
          logger.warn("[${diag.schemaName}] ${diag.location}: ${diag.message}")
        }
        writeFiles(result.files, outputDirectory)
      }
      is GenerationResult.Failure -> {
        result.errors.forEach { diag ->
          logger.error("[${diag.schemaName}] ${diag.location}: ${diag.message}")
        }
        throw GradleException(
            "Diplodokode generation failed with ${result.errors.size} error(s). See above for details."
        )
      }
    }
  }

  private fun writeFiles(files: List<com.squareup.kotlinpoet.FileSpec>, outputDirectory: java.io.File) {
    outputDirectory.mkdirs()
    files.forEach { fileSpec ->
      fileSpec.writeTo(outputDirectory)
      logger.lifecycle("Generated: ${fileSpec.name}")
    }
    logger.lifecycle("Successfully generated ${files.size} files in ${outputDirectory.absolutePath}")
  }

  private fun buildTypeMappingStrategy(): TypeMappingStrategy {
    val base: TypeMappingStrategy = when (typeMappingPreset.get()) {
      "java" -> JavaTypeMappingStrategy()
      else   -> KotlinMultiplatformTypeMappingStrategy()
    }

    val formatOverrides = typeMappingFormatOverrides.get().mapValues { (_, fqcn) -> ClassName.bestGuess(fqcn) }
    val baseOverrides = typeMappingBaseOverrides.get().mapValues { (_, fqcn) -> ClassName.bestGuess(fqcn) }

    return if (formatOverrides.isEmpty() && baseOverrides.isEmpty()) base
    else base.withOverrides(formatOverrides, baseOverrides)
  }

  private fun buildNamingStrategy(): NamingStrategy = when (namingMode.get()) {
    "preserve" -> PreserveNamingStrategy()
    else       -> DefaultNamingStrategy()
  }

  private fun buildNullabilityStrategy(): NullabilityStrategy = when (nullabilityMode.get()) {
    "all-nullable"     -> AllNullableStrategy()
    "all-non-nullable" -> AllNonNullableStrategy()
    else               -> SpecDrivenNullabilityStrategy()
  }

  private fun buildSerialisationStrategy(): SerializationStrategy? = when (serialisationLibrary.get()) {
    "kotlinx" -> KotlinxSerialisationStrategy
    else      -> null
  }

}
