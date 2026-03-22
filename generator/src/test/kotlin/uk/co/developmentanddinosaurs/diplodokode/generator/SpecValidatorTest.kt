package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class SpecValidatorTest : BehaviorSpec({

  val validator = SpecValidator()

  Given("a valid spec with no issues") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf("name" to Schema(type = "string")),
      ),
      "Triceratops" to Schema(
        type = "object",
        properties = mapOf("hornCount" to Schema(type = "integer")),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("no diagnostics are produced") {
        diagnostics.shouldBeEmpty()
      }
    }
  }

  Given("a schema with a \$ref to an undefined schema") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "favouritePrey" to Schema(ref = "#/components/schemas/Triceratops"),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced") {
        diagnostics shouldHaveSize 1
        diagnostics[0].severity shouldBe DiagnosticSeverity.ERROR
      }

      Then("the diagnostic identifies the schema and undefined name") {
        diagnostics[0].schemaName shouldBe "Tyrannosaur"
        diagnostics[0].message shouldContain "Triceratops"
      }
    }
  }

  Given("a schema with a \$ref to a defined schema") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "favouritePrey" to Schema(ref = "#/components/schemas/Triceratops"),
        ),
      ),
      "Triceratops" to Schema(type = "object"),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("no error diagnostics are produced") {
        diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.shouldBeEmpty()
      }
    }
  }

  Given("a schema with a \$ref in an allOf entry pointing to an undefined schema") {
    val schemas = mapOf(
      "ExtendedDinosaur" to Schema(
        allOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
          Schema(type = "object", properties = mapOf("hornCount" to Schema(type = "integer"))),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced") {
        diagnostics.filter { it.severity == DiagnosticSeverity.ERROR } shouldHaveSize 1
      }

      Then("the diagnostic identifies the undefined schema in allOf") {
        val error = diagnostics.first { it.severity == DiagnosticSeverity.ERROR }
        error.schemaName shouldBe "ExtendedDinosaur"
        error.message shouldContain "Tyrannosaur"
      }
    }
  }

  Given("a schema with a \$ref in a oneOf entry pointing to an undefined schema") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
        oneOf = listOf(Schema(ref = "#/components/schemas/Tyrannosaur")),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced for the undefined oneOf ref") {
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        errors shouldHaveSize 1
        errors[0].message shouldContain "Tyrannosaur"
      }
    }
  }

  Given("a schema with an array property whose items \$ref is undefined") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "prey" to Schema(type = "array", items = Schema(ref = "#/components/schemas/Triceratops")),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced for the undefined items ref") {
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        errors shouldHaveSize 1
        errors[0].schemaName shouldBe "Tyrannosaur"
        errors[0].message shouldContain "Triceratops"
      }
    }
  }

  Given("a schema with multiple undefined \$refs") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "prey" to Schema(ref = "#/components/schemas/Triceratops"),
          "habitat" to Schema(ref = "#/components/schemas/Forest"),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced for each undefined ref") {
        diagnostics.filter { it.severity == DiagnosticSeverity.ERROR } shouldHaveSize 2
      }
    }
  }

  Given("a oneOf schema with an inline variant (no \$ref)") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
        oneOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
          Schema(type = "object", properties = mapOf("name" to Schema(type = "string"))),
        ),
      ),
      "Tyrannosaur" to Schema(type = "object"),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning diagnostic is produced for the inline variant") {
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        warnings shouldHaveSize 1
        warnings[0].schemaName shouldBe "Dinosaur"
        warnings[0].location shouldBe "oneOf[1]"
        warnings[0].message shouldContain "no \$ref"
      }
    }
  }

  Given("an anyOf schema with an inline variant") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
        anyOf = listOf(
          Schema(type = "object"),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning diagnostic is produced") {
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        warnings shouldHaveSize 1
        warnings[0].message shouldContain "anyOf"
      }
    }
  }

  Given("a schema with an array property that has no items") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "name" to Schema(type = "string"),
          "bones" to Schema(type = "array"),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning is produced for the array property") {
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        warnings shouldHaveSize 1
        warnings[0].schemaName shouldBe "Tyrannosaur"
        warnings[0].location shouldBe "properties.bones.items"
        warnings[0].message shouldContain "'bones'"
      }
    }
  }

  Given("a oneOf schema with a discriminator where only some variants have the discriminator property") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
        oneOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
          Schema(ref = "#/components/schemas/Triceratops"),
        ),
        discriminator = Discriminator("type"),
      ),
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf("type" to Schema(type = "string", enum = listOf("tyrannosaur"))),
      ),
      "Triceratops" to Schema(
        type = "object",
        properties = mapOf("hornCount" to Schema(type = "integer")),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning is produced for the variant missing the discriminator property") {
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        warnings shouldHaveSize 1
        warnings[0].schemaName shouldBe "Dinosaur"
        warnings[0].message shouldContain "Triceratops"
        warnings[0].message shouldContain "'type'"
      }
    }
  }

  Given("a spec with multiple issues across schemas") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "prey" to Schema(ref = "#/components/schemas/Triceratops"),
          "bones" to Schema(type = "array"),
        ),
      ),
      "Dinosaur" to Schema(
        oneOf = listOf(
          Schema(type = "object"),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("all issues are reported") {
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        errors shouldHaveSize 1
        warnings shouldHaveSize 2
      }
    }
  }

  Given("a schema with a typed additionalProperties whose \$ref is undefined") {
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        additionalProperties = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties.Typed(
          Schema(ref = "#/components/schemas/Diet"),
        ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced for the undefined additionalProperties ref") {
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        errors shouldHaveSize 1
        errors[0].schemaName shouldBe "Tyrannosaur"
        errors[0].message shouldContain "Diet"
      }
    }
  }

  Given("a schema with a discriminator mapping that references an undefined schema") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
        oneOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
        ),
        discriminator = Discriminator(
          propertyName = "type",
          mapping = mapOf("tyrannosaur" to "#/components/schemas/Tyrannosaur", "unknown" to "#/components/schemas/Undefinosaur"),
        ),
      ),
      "Tyrannosaur" to Schema(type = "object"),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("an error diagnostic is produced for the undefined mapping target") {
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        errors shouldHaveSize 1
        errors[0].schemaName shouldBe "Dinosaur"
        errors[0].message shouldContain "Undefinosaur"
      }
    }
  }
})
