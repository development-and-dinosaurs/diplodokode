package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
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

  Given("a spec file with an inline oneOf variant (warning, no error)") {
    val specFile = File("src/test/resources/inline-variant-api.yaml")

    When("generateFromSpecWithResult is called") {
      val result = generator.generateFromSpecWithResult(specFile)

      Then("it returns PartialSuccess") {
        result.shouldBeInstanceOf<GenerationResult.PartialSuccess>()
      }

      Then("the warnings list is non-empty") {
        (result as GenerationResult.PartialSuccess).warnings.isNotEmpty() shouldBe true
      }

      Then("files are still produced despite the warning") {
        (result as GenerationResult.PartialSuccess).files.isNotEmpty() shouldBe true
      }

      Then("the warning severity is WARNING") {
        (result as GenerationResult.PartialSuccess).warnings[0].severity shouldBe DiagnosticSeverity.WARNING
      }
    }

    When("generateFromSpec is called") {
      Then("it still returns files (warnings do not throw)") {
        val files = generator.generateFromSpec(specFile)
        files.isNotEmpty() shouldBe true
      }
    }
  }

  Given("a spec file with an undefined \$ref") {
    val specFile = File("src/test/resources/undefined-ref-api.yaml")

    When("generateFromSpecWithResult is called") {
      val result = generator.generateFromSpecWithResult(specFile)

      Then("it returns Failure") {
        result.shouldBeInstanceOf<GenerationResult.Failure>()
      }

      Then("the error identifies the undefined schema") {
        val failure = result as GenerationResult.Failure
        failure.errors shouldHaveSize 1
        failure.errors[0].schemaName shouldBe "Tyrannosaur"
        failure.errors[0].message shouldBe "References undefined schema 'Triceratops'."
        failure.errors[0].severity shouldBe DiagnosticSeverity.ERROR
      }
    }

    When("generateFromSpec is called") {
      Then("it throws IllegalStateException") {
        io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
          generator.generateFromSpec(specFile)
        }
      }
    }
  }

  Given("a GenerationDiagnostic") {
    val diagnostic = GenerationDiagnostic(
      schemaName = "Tyrannosaur",
      location = "properties.armLength",
      message = "No items schema defined for array property.",
      severity = DiagnosticSeverity.WARNING,
    )

    Then("it holds the schema name, location, message, and severity") {
      diagnostic.schemaName shouldBe "Tyrannosaur"
      diagnostic.location shouldBe "properties.armLength"
      diagnostic.message shouldBe "No items schema defined for array property."
      diagnostic.severity shouldBe DiagnosticSeverity.WARNING
    }
  }

  Given("a PartialSuccess result") {
    val files = emptyList<com.squareup.kotlinpoet.FileSpec>()
    val warnings = listOf(
      GenerationDiagnostic("Triceratops", "properties.horns", "Array property has no items schema.", DiagnosticSeverity.WARNING),
    )
    val result = GenerationResult.PartialSuccess(files, warnings)

    Then("it holds both files and warnings") {
      result.files shouldBe files
      result.warnings shouldBe warnings
    }
  }

  Given("a Failure result") {
    val errors = listOf(
      GenerationDiagnostic("Diplodocus", "\$ref", "References undefined schema 'Sauropod'.", DiagnosticSeverity.ERROR),
    )
    val result = GenerationResult.Failure(errors)

    Then("it holds the errors") {
      result.errors shouldBe errors
    }
  }
})
