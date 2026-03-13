package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class SealedInterfaceGeneratorTest : BehaviorSpec({

  fun generator(config: GeneratorConfig = GeneratorConfig()) =
    SealedInterfaceGenerator(config, TypeResolver(config))

  Given("a oneOf schema with no discriminator") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
        Schema(ref = "#/components/schemas/Triceratops"),
      ),
    )

    When("the generator produces a sealed interface") {
      val code = generator().generate("Dinosaur", schema, schema.oneOf!!, "oneOf", null).toString()

      Then("it produces a sealed interface") {
        code shouldContain "sealed interface Dinosaur"
      }

      Then("it includes the oneOf KDoc") {
        code shouldContain "Exactly one of the following variants must be used."
      }

      Then("no nested Type enum is present") {
        code shouldNotContain "enum class Type"
      }

      Then("no abstract discriminator property is present") {
        code shouldNotContain "abstract val"
      }
    }
  }

  Given("an anyOf schema") {
    val schema = Schema(
      anyOf = listOf(Schema(ref = "#/components/schemas/Tyrannosaur")),
    )

    When("the generator produces a sealed interface") {
      val code = generator().generate("Dinosaur", schema, schema.anyOf!!, "anyOf", null).toString()

      Then("the KDoc says 'one or more'") {
        code shouldContain "One or more of the following variants may be used."
      }
    }
  }

  Given("a schema with a description") {
    val schema = Schema(
      description = "A long-necked herbivorous dinosaur",
      oneOf = listOf(Schema(ref = "#/components/schemas/Diplodocus")),
    )

    When("the generator produces a sealed interface") {
      val code = generator().generate("Sauropod", schema, schema.oneOf!!, "oneOf", null).toString()

      Then("the description appears in the KDoc") {
        code shouldContain "A long-necked herbivorous dinosaur"
      }
    }
  }

  Given("a schema with a discriminator enum, without serialisation") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
        Schema(ref = "#/components/schemas/Triceratops"),
      ),
      discriminator = Discriminator("type"),
    )
    val discriminatorEnum = DiscriminatorEnum(
      "type",
      listOf("TYRANNOSAUR", "TRICERATOPS"),
      listOf("tyrannosaur", "triceratops"),
    )

    When("the generator produces a sealed interface") {
      val code = generator().generate("Dinosaur", schema, schema.oneOf!!, "oneOf", discriminatorEnum).toString()

      Then("a nested Type enum is generated") {
        code shouldContain "enum class Type"
        code shouldContain "TYRANNOSAUR"
        code shouldContain "TRICERATOPS"
      }

      Then("an abstract discriminator property is generated") {
        code shouldContain "val type: Type"
      }

      Then("no @JsonClassDiscriminator is present") {
        code shouldNotContain "JsonClassDiscriminator"
      }
    }
  }

  Given("a schema with a discriminator enum and serialisation enabled") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
        Schema(ref = "#/components/schemas/Triceratops"),
      ),
      discriminator = Discriminator("type"),
    )
    val discriminatorEnum = DiscriminatorEnum(
      "type",
      listOf("TYRANNOSAUR", "TRICERATOPS"),
      listOf("tyrannosaur", "triceratops"),
    )
    val config = GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy)

    When("the generator produces a sealed interface") {
      val code = generator(config).generate("Dinosaur", schema, schema.oneOf!!, "oneOf", discriminatorEnum).toString()

      Then("the sealed interface is annotated with @Serializable") {
        code shouldContain "@Serializable"
      }

      Then("it is annotated with @JsonClassDiscriminator") {
        code shouldContain """@JsonClassDiscriminator("type")"""
      }

      Then("no nested Type enum is generated") {
        code shouldNotContain "enum class Type"
      }

      Then("no discriminator property is generated") {
        code shouldNotContain "val type:"
      }

      Then("the file opts in to ExperimentalSerializationApi") {
        code shouldContain "ExperimentalSerializationApi"
      }
    }
  }

  Given("a schema with a discriminator but not all variants carry it (partial discriminator)") {
    val schema = Schema(
      oneOf = listOf(Schema(ref = "#/components/schemas/Tyrannosaur")),
      discriminator = Discriminator("type"),
    )

    When("the generator produces a sealed interface with no discriminator enum") {
      val code = generator().generate("Dinosaur", schema, schema.oneOf!!, "oneOf", null).toString()

      Then("it falls back to an abstract String property") {
        code shouldContain "abstract val type: String"
      }

      Then("a warning appears in the KDoc") {
        code shouldContain "Warning: discriminator property 'type'"
      }
    }
  }

  Given("a schema with shared abstract properties") {
    val schema = Schema(
      oneOf = listOf(Schema(ref = "#/components/schemas/Tyrannosaur")),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "era" to Schema(type = "string"),
      ),
    )

    When("the generator produces a sealed interface") {
      val code = generator().generate("Dinosaur", schema, schema.oneOf!!, "oneOf", null).toString()

      Then("abstract properties from the parent schema are declared") {
        code shouldContain "val name: String?"
        code shouldContain "val era: String?"
      }
    }
  }

  Given("a sealed interface that is itself a variant of another sealed interface") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Diplodocus"),
        Schema(ref = "#/components/schemas/Brachiosaurus"),
      ),
    )

    When("the generator produces a sealed interface with implemented interfaces") {
      val code = generator().generate("Sauropod", schema, schema.oneOf!!, "oneOf", null, implementedInterfaces = listOf("Dinosaur")).toString()

      Then("the sealed interface extends the parent interface") {
        code shouldContain "sealed interface Sauropod : Dinosaur"
      }
    }
  }

  Given("a schema with inline (non-ref) variants") {
    val schema = Schema(
      oneOf = listOf(
        Schema(type = "object", properties = mapOf("name" to Schema(type = "string"))),
      ),
    )

    When("the generator produces a sealed interface") {
      val code = generator().generate("Dinosaur", schema, schema.oneOf!!, "oneOf", null).toString()

      Then("a warning note appears in the KDoc") {
        code shouldContain "NOTE: Inline oneOf variants are not supported"
      }
    }
  }
})
