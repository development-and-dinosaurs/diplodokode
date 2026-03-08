package uk.co.developmentanddinosaurs.diplodokode.plugin

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class DiplodokodePluginFunctionalTest : BehaviorSpec({

  val testProjectDir = File("build/tmp/testKit")

  beforeSpec {
    testProjectDir.deleteRecursively()
    testProjectDir.mkdirs()
  }

  Given("a project with the Diplodokode plugin applied") {
    copyTestResourceFile("test-build.gradle.kts", testProjectDir.resolve("build.gradle.kts"))
    copyTestResourceFile("test-settings.gradle.kts", testProjectDir.resolve("settings.gradle.kts"))
    copyTestResourceFile("dinosaur-api.yaml", testProjectDir.resolve("src/main/resources/dinosaur-api.yaml"))

    When("the generateDiplodokode task runs") {
      val result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments("generateDiplodokode", "--stacktrace")
        .withPluginClasspath()
        .build()

      Then("the task should succeed") {
        result.task(":generateDiplodokode")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      Then("it should write the generated file to the output directory") {
        val generatedFile = File(testProjectDir, "build/generated/kotlin/uk/co/developmentanddinosaurs/diplodokode/generated/Dinosaur.kt")
        generatedFile.shouldExist()
      }

      Then("the generated file should contain the expected data class") {
        val generatedContent = File(testProjectDir, "build/generated/kotlin/uk/co/developmentanddinosaurs/diplodokode/generated/Dinosaur.kt").readText()
        generatedContent shouldContain "data class Dinosaur"
        generatedContent shouldContain "val name: String"
        generatedContent shouldContain "val species: String"
        generatedContent shouldContain "val age: Int"
        generatedContent shouldContain "val weight: Double?"
        generatedContent shouldContain "val isCarnivore: Boolean?"
      }
    }
  }

  Given("a project with custom configuration") {
    val configuredProjectDir = File("build/tmp/testKit-configured")
    configuredProjectDir.deleteRecursively()
    configuredProjectDir.mkdirs()

    copyTestResourceFile("test-build-configured.gradle.kts", configuredProjectDir.resolve("build.gradle.kts"))
    copyTestResourceFile("test-settings.gradle.kts", configuredProjectDir.resolve("settings.gradle.kts"))
    copyTestResourceFile("dinosaur-api.yaml", configuredProjectDir.resolve("src/main/resources/dinosaur-api.yaml"))

    When("the generateDiplodokode task runs") {
      GradleRunner.create()
        .withProjectDir(configuredProjectDir)
        .withArguments("generateDiplodokode", "--stacktrace")
        .withPluginClasspath()
        .build()

      Then("the generated file uses the configured package name") {
        val generatedFile = File(configuredProjectDir, "build/generated/kotlin/com/example/dinosaurs/models/Dinosaur.kt")
        generatedFile.shouldExist()
        generatedFile.readText() shouldContain "package com.example.dinosaurs.models"
      }

      Then("the generated file uses Java types for format mappings") {
        val content = File(configuredProjectDir, "build/generated/kotlin/com/example/dinosaurs/models/Dinosaur.kt").readText()
        content shouldContain "java.time.Instant"
        content shouldNotContain "kotlinx.datetime"
      }

      Then("the generated file makes all properties non-nullable") {
        val content = File(configuredProjectDir, "build/generated/kotlin/com/example/dinosaurs/models/Dinosaur.kt").readText()
        content shouldNotContain "?"
      }

      Then("the generated file preserves property names from the spec") {
        val content = File(configuredProjectDir, "build/generated/kotlin/com/example/dinosaurs/models/Dinosaur.kt").readText()
        content shouldContain "val isCarnivore"
        content shouldContain "val discoveredAt"
      }
    }
  }
})

/**
 * Copies a resource file to the target location, creating parent directories as needed
 */
private fun copyTestResourceFile(sourcePath: String, targetFile: File) {
  val sourceFile = File("src/test/resources/$sourcePath")
  targetFile.parentFile?.mkdirs()
  sourceFile.copyTo(targetFile, overwrite = true)
}
