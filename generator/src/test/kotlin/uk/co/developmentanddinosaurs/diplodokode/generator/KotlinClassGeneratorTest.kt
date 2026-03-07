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
      val fileSpec = generator.generateFromSchema("Dinosaur", schema)
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
      val code = generator.generateFromSchema("Types", schema).toString()

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
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it should emit descriptions as KDoc") {
        code shouldContain "/**"
        code shouldContain "The name of the dinosaur"
      }
    }
  }

  Given("a schema with no properties") {
    val schema = Schema(type = "object")

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Empty", schema).toString()

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
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

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
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

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
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

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
      val code = generator.generateFromSchema("Diet", schema).toString()

      Then("it should generate an enum class") {
        code shouldContain "enum class Diet"
      }

      Then("it should uppercase the enum values") {
        code shouldContain "CARNIVORE"
        code shouldContain "HERBIVORE"
      }
    }
  }

  Given("a schema with a typed array property") {
    val schema = Schema(
      type = "object",
      required = listOf("tags", "aliases"),
      properties = mapOf(
        "tags" to Schema(type = "array", items = Schema(type = "string")),
        "aliases" to Schema(type = "array", items = Schema(type = "string")),
        "friendIds" to Schema(type = "array", items = Schema(type = "integer")),
        "friends" to Schema(type = "array", items = Schema(ref = "#/components/schemas/Dinosaur")),
        "rawList" to Schema(type = "array"),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("required string array should be non-nullable List<String>") {
        code shouldContain "val tags: List<String>"
        code shouldNotContain "val tags: List<String>?"
      }

      Then("required array should be non-nullable") {
        code shouldContain "val aliases: List<String>"
      }

      Then("optional integer array should be nullable List<Int>") {
        code shouldContain "val friendIds: List<Int>?"
      }

      Then("array with ref items should resolve to the referenced type") {
        code shouldContain "val friends: List<Dinosaur>?"
      }

      Then("array without items should fall back to List<Any>") {
        code shouldContain "val rawList: List<Any>?"
      }
    }
  }

  Given("a schema with a lowercase class name") {
    val schema = Schema(type = "object")

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("dinosaur", schema).toString()

      Then("it should capitalise the class name") {
        code shouldContain "data class Dinosaur"
      }
    }
  }

  Given("a schema with format-mapped properties") {
    val schema = Schema(
      type = "object",
      required = listOf("id", "createdAt", "birthDate", "startTime", "timeout",
        "avatar", "attachment", "endpoint", "externalId", "score"),
      properties = mapOf(
        "id" to Schema(type = "string", format = "uuid"),
        "createdAt" to Schema(type = "string", format = "date-time"),
        "birthDate" to Schema(type = "string", format = "date"),
        "startTime" to Schema(type = "string", format = "time"),
        "timeout" to Schema(type = "string", format = "duration"),
        "avatar" to Schema(type = "string", format = "byte"),
        "attachment" to Schema(type = "string", format = "binary"),
        "endpoint" to Schema(type = "string", format = "uri"),
        "externalId" to Schema(type = "integer", format = "int64"),
        "score" to Schema(type = "number", format = "float"),
        "name" to Schema(type = "string"),
        "count" to Schema(type = "integer"),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("uuid format maps to UUID") {
        code shouldContain "val id: UUID"
      }

      Then("date-time format maps to Instant") {
        code shouldContain "val createdAt: Instant"
      }

      Then("date format maps to LocalDate") {
        code shouldContain "val birthDate: LocalDate"
      }

      Then("time format maps to LocalTime") {
        code shouldContain "val startTime: LocalTime"
      }

      Then("duration format maps to Duration") {
        code shouldContain "val timeout: Duration"
      }

      Then("byte format maps to ByteArray") {
        code shouldContain "val avatar: ByteArray"
      }

      Then("binary format maps to ByteArray") {
        code shouldContain "val attachment: ByteArray"
      }

      Then("uri format maps to URI") {
        code shouldContain "val endpoint: URI"
      }

      Then("int64 format maps to Long") {
        code shouldContain "val externalId: Long"
      }

      Then("float format maps to Float") {
        code shouldContain "val score: Float"
      }

      Then("plain string without format stays String") {
        code shouldContain "val name: String?"
      }

      Then("plain integer without format stays Int") {
        code shouldContain "val count: Int?"
      }
    }
  }
})
