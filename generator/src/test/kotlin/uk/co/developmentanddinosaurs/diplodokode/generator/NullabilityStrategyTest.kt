package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class NullabilityStrategyTest : BehaviorSpec({

  val required = setOf("name", "age")
  val plainSchema = Schema(type = "string")
  val nullableSchema = Schema(type = "string", nullable = true)

  Given("the SpecDrivenNullabilityStrategy") {
    val strategy = SpecDrivenNullabilityStrategy()

    When("checking a required property") {
      Then("it is non-nullable") {
        strategy.isNullable("name", plainSchema, required) shouldBe false
      }
    }

    When("checking an optional property") {
      Then("it is nullable") {
        strategy.isNullable("weight", plainSchema, required) shouldBe true
      }
    }

    When("checking a required property with nullable: true") {
      Then("nullable: true overrides the required constraint") {
        strategy.isNullable("name", nullableSchema, required) shouldBe true
      }
    }

    When("checking an optional property with nullable: true") {
      Then("it is nullable") {
        strategy.isNullable("weight", nullableSchema, required) shouldBe true
      }
    }

    When("the required set is empty") {
      Then("all properties are nullable") {
        strategy.isNullable("name", plainSchema, emptySet()) shouldBe true
      }
    }
  }

  Given("the AllNullableStrategy") {
    val strategy = AllNullableStrategy()

    When("checking a required property") {
      Then("it is nullable regardless") {
        strategy.isNullable("name", plainSchema, required) shouldBe true
      }
    }

    When("checking an optional property") {
      Then("it is nullable") {
        strategy.isNullable("weight", plainSchema, required) shouldBe true
      }
    }

    When("the required set is empty") {
      Then("it is still nullable") {
        strategy.isNullable("name", plainSchema, emptySet()) shouldBe true
      }
    }
  }

  Given("the AllNonNullableStrategy") {
    val strategy = AllNonNullableStrategy()

    When("checking a required property") {
      Then("it is non-nullable") {
        strategy.isNullable("name", plainSchema, required) shouldBe false
      }
    }

    When("checking an optional property") {
      Then("it is non-nullable regardless") {
        strategy.isNullable("weight", plainSchema, required) shouldBe false
      }
    }

    When("checking a property with nullable: true") {
      Then("nullable: true is ignored") {
        strategy.isNullable("name", nullableSchema, required) shouldBe false
      }
    }

    When("the required set is empty") {
      Then("it is still non-nullable") {
        strategy.isNullable("name", plainSchema, emptySet()) shouldBe false
      }
    }
  }
})
