package uk.co.developmentanddinosaurs.diplodokode.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import uk.co.developmentanddinosaurs.diplodokode.generator.DiplodokodeGenerator

@CacheableTask
abstract class GenerateDiplodokodeTask : DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val inputFile: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun generate() {
    val generator = DiplodokodeGenerator()
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
