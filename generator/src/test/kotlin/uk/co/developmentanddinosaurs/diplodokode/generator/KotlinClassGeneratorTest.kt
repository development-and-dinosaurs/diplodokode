package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class KotlinClassGeneratorTest : BehaviorSpec({

  val generator = KotlinClassGenerator()

  Given("a schema with required and optional properties") {
    val schema = Schema(
      type = "object",
      required = listOf("name", "age"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "age" to Schema(type = "integer"),
        "weight" to Schema(type = "number"),
        "active" to Schema(type = "boolean"),
      )
    )

    When("the generator produces a data class") {
      val fileSpec = generator.generateDataClass("Dinosaur", schema)
      val code = fileSpec.toString()

      Then("it should generate a data class with the correct name") {
        code shouldContain "data class Dinosaur"
      }

      Then("required fields should be non-nullable") {
        code shouldContain "val name: String"
        code shouldContain "val age: Int"
      }

      Then("optional fields should be nullable") {
        code shouldContain "val weight: Double?"
        code shouldContain "val active: Boolean?"
      }
    }
  }

  Given("a schema with all supported OpenAPI types") {
    val schema = Schema(
      type = "object",
      required = listOf("str", "int", "num", "bool", "arr", "obj", "unknown"),
      properties = mapOf(
        "str" to Schema(type = "string"),
        "int" to Schema(type = "integer"),
        "num" to Schema(type = "number"),
        "bool" to Schema(type = "boolean"),
        "arr" to Schema(type = "array"),
        "obj" to Schema(type = "object"),
        "unknown" to Schema(type = "exotic"),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateDataClass("Types", schema).toString()

      Then("it should map each type to the correct Kotlin type") {
        code shouldContain "val str: String"
        code shouldContain "val int: Int"
        code shouldContain "val num: Double"
        code shouldContain "val bool: Boolean"
        code shouldContain "val arr: List"
        code shouldContain "val obj: Any"
        code shouldContain "val unknown: String"
      }
    }
  }

  Given("a schema with property descriptions") {
    val schema = Schema(
      type = "object",
      properties = mapOf(
        "name" to Schema(type = "string", description = "The name of the dinosaur"),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateDataClass("Dinosaur", schema).toString()

      Then("it should emit descriptions as KDoc") {
        code shouldContain "/**"
        code shouldContain "The name of the dinosaur"
      }
    }
  }

  Given("a schema with no properties") {
    val schema = Schema(type = "object")

    When("the generator produces a data class") {
      val code = generator.generateDataClass("Empty", schema).toString()

      Then("it should generate an empty data class") {
        code shouldContain "data class Empty"
        code shouldNotContain "val "
        code shouldNotContain "constructor"
      }
    }
  }

  Given("a schema with a required nullable property") {
    val schema = Schema(
      type = "object",
      required = listOf("name", "tag"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "tag" to Schema(type = "string", nullable = true),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateDataClass("Dinosaur", schema).toString()

      Then("the required non-nullable field should be non-nullable") {
        code shouldContain "val name: String"
      }

      Then("the required nullable field should be nullable") {
        code shouldContain "val tag: String?"
      }
    }
  }

  Given("a schema with an enum property") {
    val schema = Schema(
      type = "object",
      required = listOf("name", "diet"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "diet" to Schema(type = "string", enum = listOf("carnivore", "herbivore")),
        "favouritePrey" to Schema(type = "string", enum = listOf("triceratops", "brachiosaurus")),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateDataClass("Dinosaur", schema).toString()

      Then("it should generate an enum class for the required enum property") {
        code shouldContain "enum class Diet"
        code shouldContain "CARNIVORE"
        code shouldContain "HERBIVORE"
      }

      Then("it should generate an enum class for the optional enum property") {
        code shouldContain "enum class FavouritePrey"
        code shouldContain "TRICERATOPS"
        code shouldContain "BRACHIOSAURUS"
      }

      Then("the required enum property should be non-nullable") {
        code shouldContain "val diet: Diet"
        code shouldNotContain "val diet: Diet?"
      }

      Then("the optional enum property should be nullable") {
        code shouldContain "val favouritePrey: FavouritePrey?"
      }
    }
  }

  Given($$"a schema with a $ref property") {
    val schema = Schema(
      type = "object",
      required = listOf("name", "diet"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "diet" to Schema(ref = "#/components/schemas/Diet"),
        "favouritePrey" to Schema(ref = "#/components/schemas/Dinosaur"),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateDataClass("Dinosaur", schema).toString()

      Then("the required ref property should use the referenced type") {
        code shouldContain "val diet: Diet"
        code shouldNotContain "val diet: Diet?"
      }

      Then("the optional ref property should be nullable") {
        code shouldContain "val favouritePrey: Dinosaur?"
      }
    }
  }

  Given("a top-level enum schema") {
    val schema = Schema(
      type = "string",
      enum = listOf("carnivore", "herbivore"),
    )

    When("the generator produces a top-level enum") {
      val code = generator.generateTopLevelEnum("Diet", schema).toString()

      Then("it should generate an enum class") {
        code shouldContain "enum class Diet"
      }

      Then("it should uppercase the enum values") {
        code shouldContain "CARNIVORE"
        code shouldContain "HERBIVORE"
      }
    }
  }

  Given("a schema with a lowercase class name") {
    val schema = Schema(type = "object")

    When("the generator produces a data class") {
      val code = generator.generateDataClass("dinosaur", schema).toString()

      Then("it should capitalise the class name") {
        code shouldContain "data class Dinosaur"
      }
    }
  }
})
