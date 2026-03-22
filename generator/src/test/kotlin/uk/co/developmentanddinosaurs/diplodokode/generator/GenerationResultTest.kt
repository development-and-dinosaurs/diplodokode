package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class GenerationResultTest : BehaviorSpec({

  val generator = DiplodokodeGenerator(GeneratorConfig())

  Given("a valid OpenAPI spec file") {
    val specFile = File("src/test/resources/dinosaur-api.yaml")

    When("generateFromSpecWithResult is called") {
      val result = generator.generateFromSpecWithResult(specFile)

      Then("it returns Success") {
        result.shouldBeInstanceOf<GenerationResult.Success>()
      }

      Then("the files list is non-empty") {
        (result as GenerationResult.Success).files.isNotEmpty() shouldBe true
      }
    }

    When("generateFromSpec is called") {
      val files = generator.generateFromSpec(specFile)

      Then("it returns the same files as the Success result") {
        val resultFiles = (generator.generateFromSpecWithResult(specFile) as GenerationResult.Success).files
        files.map { "${it.packageName}.${it.name}" } shouldBe
            resultFiles.map { "${it.packageName}.${it.name}" }
      }
    }
  }

  Given("a GenerationDiagnostic") {
    val diagnostic = GenerationDiagnostic(
      schemaName = "Tyrannosaur",
      location = "properties.armLength",
      message = "No items schema defined for array property.",
    )

    Then("it holds the schema name, location, and message") {
      diagnostic.schemaName shouldBe "Tyrannosaur"
      diagnostic.location shouldBe "properties.armLength"
      diagnostic.message shouldBe "No items schema defined for array property."
    }
  }

  Given("a PartialSuccess result") {
    val files = emptyList<com.squareup.kotlinpoet.FileSpec>()
    val warnings = listOf(
      GenerationDiagnostic("Triceratops", "properties.horns", "Array property has no items schema."),
    )
    val result = GenerationResult.PartialSuccess(files, warnings)

    Then("it holds both files and warnings") {
      result.files shouldBe files
      result.warnings shouldBe warnings
    }
  }

  Given("a Failure result") {
    val errors = listOf(
      GenerationDiagnostic("Diplodocus", "\$ref", "References undefined schema 'Sauropod'."),
    )
    val result = GenerationResult.Failure(errors)

    Then("it holds the errors") {
      result.errors shouldBe errors
    }
  }
})
