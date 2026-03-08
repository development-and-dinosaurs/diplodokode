package uk.co.developmentanddinosaurs.diplodokode.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import uk.co.developmentanddinosaurs.diplodokode.generator.DiplodokodeGenerator
import uk.co.developmentanddinosaurs.diplodokode.generator.GeneratorConfig

@CacheableTask
abstract class GenerateDiplodokodeTask : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputFile: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  abstract val packageName: Property<String>

  @TaskAction
  fun generate() {
    val config = GeneratorConfig(packageName = packageName.get())
    val generator = DiplodokodeGenerator(config)
    val specFile = inputFile.get().asFile
    val outputDirectory = outputDir.get().asFile

    try {
      val generatedFiles = generator.generateFromSpec(specFile)

      outputDirectory.mkdirs()

      generatedFiles.forEach { fileSpec ->
        fileSpec.writeTo(outputDirectory)
        println("Generated: ${fileSpec.name}")
      }

      println("✅ Successfully generated ${generatedFiles.size} files in ${outputDirectory.absolutePath}")

    } catch (e: Exception) {
      println("❌ Failed to run generation: ${e.message}")
      throw e
    }
  }
}
