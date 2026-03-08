package uk.co.developmentanddinosaurs.diplodokode.plugin

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import java.io.File

class GenerateDiplodokodeTaskTest : BehaviorSpec({

  Given("a task configured with a valid OpenAPI spec") {
    val project = ProjectBuilder.builder().build()
    val specFile = File("src/test/resources/dinosaur-api.yaml").absoluteFile
    val outputDir = project.layout.buildDirectory.dir("generated/kotlin").get().asFile

    val task = project.tasks.register("generateDiplodokode", GenerateDiplodokodeTask::class.java) { task ->
      task.inputFile.set(specFile)
      task.outputDir.set(outputDir)

      task.namingMode.set("default")
      task.nullabilityMode.set("spec-driven")
      task.packageName.set("uk.co.developmentanddinosaurs.diplodokode.generated")
      task.typeMappingPreset.set("kmp")
      task.typeMappingFormatOverrides.set(emptyMap())
      task.typeMappingBaseOverrides.set(emptyMap())
    }.get()

    When("the task action runs") {
      task.generate()

      Then("it should write the generated file to the output directory") {
        val generatedFile = outputDir.resolve("uk/co/developmentanddinosaurs/diplodokode/generated/Dinosaur.kt")
        generatedFile.shouldExist()
      }

      Then("the generated file should contain the expected data class") {
        val generatedFile = outputDir.resolve("uk/co/developmentanddinosaurs/diplodokode/generated/Dinosaur.kt")
        val content = generatedFile.readText()
        content shouldContain "data class Dinosaur"
        content shouldContain "val name: String"
        content shouldContain "val weight: Double?"
      }
    }
  }
})
