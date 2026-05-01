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

  Given("a schema with an external ref") {
    val schemas = mapOf(
        "Tyrannosaur" to Schema(
            type = "object",
            properties = mapOf("prey" to Schema(ref = "other.yaml#/components/schemas/Triceratops")),
        ),
        "Triceratops" to Schema(type = "object"),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning is produced noting cross-document refs are unsupported") {
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        warnings shouldHaveSize 1
        warnings[0].message shouldContain "external"
        warnings[0].message shouldContain "other.yaml"
      }

      Then("no error is produced because the local tail matches a known schema") {
        diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.shouldBeEmpty()
      }
    }
  }

  Given("a schema with a non-canonical local ref (e.g. #/definitions/Foo)") {
    val schemas = mapOf(
        "Tyrannosaur" to Schema(
            type = "object",
            properties = mapOf("prey" to Schema(ref = "#/definitions/Triceratops")),
        ),
        "Triceratops" to Schema(type = "object"),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning is produced noting the non-canonical form") {
        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        warnings shouldHaveSize 1
        warnings[0].message shouldContain "canonical"
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

  Given("a property whose oneOf variants share more than one common interface") {
    val schemas = mapOf(
      "LandDweller" to Schema(oneOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
          Schema(ref = "#/components/schemas/Allosaurus"),
      )),
      "Carnivore" to Schema(oneOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
          Schema(ref = "#/components/schemas/Allosaurus"),
      )),
      "Tyrannosaur" to Schema(type = "object"),
      "Allosaurus" to Schema(type = "object"),
      "Encounter" to Schema(
          type = "object",
          properties = mapOf(
              "threat" to Schema(oneOf = listOf(
                  Schema(ref = "#/components/schemas/Tyrannosaur"),
                  Schema(ref = "#/components/schemas/Allosaurus"),
              )),
          ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning is emitted naming the picked interface and the alternative") {
        val warning = diagnostics.single { it.schemaName == "Encounter" && it.location == "properties.threat" }
        warning.severity shouldBe DiagnosticSeverity.WARNING
        warning.message shouldContain "Carnivore"
        warning.message shouldContain "LandDweller"
        warning.message shouldContain "alphabetically first"
      }
    }
  }

  Given("a property with an inline primitive oneOf used as array items") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
          type = "object",
          properties = mapOf(
              "measurements" to Schema(
                  type = "array",
                  items = Schema(oneOf = listOf(
                      Schema(type = "string"),
                      Schema(type = "number"),
                  )),
              ),
          ),
      ),
    )

    When("the validator runs") {
      val diagnostics = validator.validate(schemas)

      Then("a warning is emitted suggesting the union be promoted to a top-level schema") {
        val warning = diagnostics.single { it.location == "properties.measurements.items.oneOf" }
        warning.severity shouldBe DiagnosticSeverity.WARNING
        warning.message shouldContain "string"
        warning.message shouldContain "number"
        warning.message shouldContain "top-level"
      }
    }
  }

  Given("a polymorphic sealed-interface schema that the user has overridden via schemaOverrides") {
    val schemas = mapOf(
      "Dinosaur" to Schema(
          oneOf = listOf(
              Schema(ref = "#/components/schemas/Tyrannosaur"),
              Schema(ref = "#/components/schemas/Triceratops"),
          ),
          discriminator = Discriminator(propertyName = "type"),
      ),
      "Tyrannosaur" to Schema(type = "object", properties = mapOf("type" to Schema(type = "string", enum = listOf("tyrannosaur")))),
      "Triceratops" to Schema(type = "object", properties = mapOf("type" to Schema(type = "string", enum = listOf("triceratops")))),
    )

    When("the validator runs with Dinosaur in knownExternalNames") {
      val diagnostics = validator.validate(schemas, knownExternalNames = setOf("Dinosaur"))

      Then("a warning is emitted explaining the override must be a sealed interface and provide a nested Type enum") {
        val warning = diagnostics.single { it.schemaName == "Dinosaur" && it.location == "oneOf" }
        warning.severity shouldBe DiagnosticSeverity.WARNING
        warning.message shouldContain "sealed"
        warning.message shouldContain "Type"
      }
    }
  }

  Given("a non-polymorphic schema that the user has overridden") {
    val schemas = mapOf(
      "DinosaurDna" to Schema(
          type = "object",
          properties = mapOf("sequence" to Schema(type = "string")),
      ),
    )

    When("the validator runs with DinosaurDna in knownExternalNames") {
      val diagnostics = validator.validate(schemas, knownExternalNames = setOf("DinosaurDna"))

      Then("no polymorphic-override warning is emitted because there is no sealed-interface contract to satisfy") {
        diagnostics.filter { it.message.contains("sealed") }.shouldBeEmpty()
      }
    }
  }
})
