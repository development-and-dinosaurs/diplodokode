package uk.co.developmentanddinosaurs.diplodokode.generator.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

class OpenApiSpecParserTest : BehaviorSpec({

  val parser = OpenApiSpecParser()

  Given("an OpenAPI spec file with schemas") {
    val specFile = File("src/test/resources/dinosaur-api.yaml")

    When("the parser reads the file") {
      val spec = parser.parse(specFile)

      Then("it should populate the components") {
        spec.components.shouldNotBeNull()
      }

      Then("it should parse the correct number of schemas") {
        spec.components!!.schemas!!.size shouldBe 1
      }

      Then("it should parse the schema by name") {
        spec.components!!.schemas!!.keys shouldContain "Dinosaur"
      }

      Then("it should parse the required fields") {
        val schema = spec.components!!.schemas!!["Dinosaur"]!!
        schema.required!! shouldBe listOf("name", "species", "age")
      }

      Then("it should parse properties with their types") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["name"]!!.type shouldBe "string"
        properties["age"]!!.type shouldBe "integer"
        properties["weight"]!!.type shouldBe "number"
        properties["isCarnivore"]!!.type shouldBe "boolean"
      }

      Then("it should parse property descriptions") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["name"]!!.description shouldBe "The name of the dinosaur"
        properties["weight"]!!.description shouldBe "The weight of the dinosaur in kilograms"
      }
    }
  }

  Given("an OpenAPI spec file with an empty schema") {
    val specFile = File("src/test/resources/empty-class-api.yaml")

    When("the parser reads the file") {
      val spec = parser.parse(specFile)

      Then("it should parse the schema with no properties") {
        val schema = spec.components!!.schemas!!["EmptyDinosaur"]!!
        schema.properties!!.size shouldBe 0
      }

      Then("it should parse the schema with no required fields") {
        val schema = spec.components!!.schemas!!["EmptyDinosaur"]!!
        schema.required!!.size shouldBe 0
      }
    }
  }

  Given("an OpenAPI spec file with a nullable property") {
    val specFile = File("src/test/resources/nullable-api.yaml")

    When("the parser reads the file") {
      val spec = parser.parse(specFile)

      Then("it should parse nullable as true for the nullable property") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["tag"]!!.nullable shouldBe true
      }

      Then("it should parse nullable as null for a non-nullable property") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["name"]!!.nullable shouldBe null
      }
    }
  }

  Given("an OpenAPI spec file with enum properties") {
    val specFile = File("src/test/resources/enum-api.yaml")

    When("the parser reads the file") {
      val spec = parser.parse(specFile)

      Then("it should parse enum values for an enum property") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["diet"]!!.enum shouldBe listOf("carnivore", "herbivore")
      }

      Then("it should parse null enum for a non-enum property") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["name"]!!.enum shouldBe null
      }
    }
  }

  Given("an OpenAPI spec file with a \$ref property") {
    val specFile = File("src/test/resources/ref-api.yaml")

    When("the parser reads the file") {
      val spec = parser.parse(specFile)

      Then("it should parse the ref for the ref property") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["diet"]!!.ref shouldBe "#/components/schemas/Diet"
      }

      Then("it should parse null ref for a non-ref property") {
        val properties = spec.components!!.schemas!!["Dinosaur"]!!.properties!!
        properties["name"]!!.ref shouldBe null
      }
    }
  }

  Given("an OpenAPI spec file with no components") {
    val specFile = File("src/test/resources/no-components-api.yaml")

    When("the parser reads the file") {
      val spec = parser.parse(specFile)

      Then("components should be null") {
        spec.components.shouldBeNull()
      }
    }
  }
})
