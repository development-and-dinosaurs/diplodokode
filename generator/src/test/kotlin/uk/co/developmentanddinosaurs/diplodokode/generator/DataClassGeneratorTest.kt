package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class DataClassGeneratorTest : BehaviorSpec({

  fun generator(config: GeneratorConfig = GeneratorConfig()): DataClassGenerator {
    val typeResolver = TypeResolver(config)
    val enumClassGenerator = EnumClassGenerator(config)
    return DataClassGenerator(config, typeResolver, enumClassGenerator)
  }

  Given("a data class that implements a sealed interface") {
    val schema = Schema(
      type = "object",
      required = listOf("name"),
      properties = mapOf("name" to Schema(type = "string")),
    )

    When("the generator produces a data class with implemented interfaces") {
      val code = generator().generate("Tyrannosaur", schema, implementedInterfaces = listOf("Dinosaur")).toString()

      Then("the class implements the interface") {
        code shouldContain ") : Dinosaur"
      }
    }

    When("the generator produces a data class with multiple interfaces") {
      val code = generator().generate("Tyrannosaur", schema, implementedInterfaces = listOf("Dinosaur", "PackHunter")).toString()

      Then("all interfaces are listed") {
        code shouldContain "Dinosaur"
        code shouldContain "PackHunter"
      }
    }
  }

  Given("a data class with a discriminator override, without serialisation") {
    val schema = Schema(
      type = "object",
      required = listOf("type", "armLength"),
      properties = mapOf(
        "type" to Schema(type = "string", enum = listOf("tyrannosaur")),
        "armLength" to Schema(type = "number"),
      ),
    )
    val override = DiscriminatorOverride("Dinosaur", "type", "TYRANNOSAUR", "tyrannosaur")

    When("the generator produces the variant data class") {
      val code = generator().generate("Tyrannosaur", schema, listOf("Dinosaur"), discriminatorOverride = override).toString()

      Then("the discriminator property is an override with a default value") {
        code shouldContain "override val type: Dinosaur.Type"
        code shouldContain "Dinosaur.Type.TYRANNOSAUR"
      }

      Then("no inline enum class is generated for the discriminator property") {
        code shouldNotContain "enum class Type"
      }

      Then("other properties are still generated") {
        code shouldContain "val armLength: Double"
      }
    }
  }

  Given("a data class with a discriminator override and serialisation enabled") {
    val schema = Schema(
      type = "object",
      required = listOf("type", "armLength"),
      properties = mapOf(
        "type" to Schema(type = "string", enum = listOf("tyrannosaur")),
        "armLength" to Schema(type = "number"),
      ),
    )
    val override = DiscriminatorOverride("Dinosaur", "type", "TYRANNOSAUR", "tyrannosaur")
    val config = GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy)

    When("the generator produces the variant data class") {
      val code = generator(config).generate("Tyrannosaur", schema, listOf("Dinosaur"), discriminatorOverride = override).toString()

      Then("the class is annotated with @SerialName for the discriminator value") {
        code shouldContain """@SerialName("tyrannosaur")"""
      }

      Then("the discriminator property is absent from the class") {
        code shouldNotContain "val type:"
        code shouldNotContain "override val type"
      }

      Then("other properties are still generated") {
        code shouldContain "val armLength: Double"
      }
    }
  }

  Given("a data class with interface property names") {
    val schema = Schema(
      type = "object",
      required = listOf("name", "era"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "era" to Schema(type = "string"),
      ),
    )

    When("the generator produces the data class with interface property names") {
      val code = generator().generate(
        "Tyrannosaur", schema, listOf("Dinosaur"),
        interfacePropertyNames = setOf("name"),
      ).toString()

      Then("the shared interface property is an override") {
        code shouldContain "override val name: String"
      }

      Then("non-shared properties are plain vals") {
        code shouldContain "val era: String"
        code shouldNotContain "override val era"
      }
    }
  }

  Given("a data class with a uuid-format property") {
    val schema = Schema(
      type = "object",
      required = listOf("id"),
      properties = mapOf("id" to Schema(type = "string", format = "uuid")),
    )

    When("the generator produces the data class") {
      val code = generator().generate("FossilRecord", schema).toString()

      Then("a file-level OptIn annotation is present for ExperimentalUuidApi") {
        code shouldContain "ExperimentalUuidApi"
        code shouldContain "@file:OptIn"
      }

      Then("the property uses Uuid") {
        code shouldContain "val id: Uuid"
      }
    }
  }

  Given("a data class with an inline enum property") {
    val schema = Schema(
      type = "object",
      required = listOf("diet"),
      properties = mapOf(
        "diet" to Schema(type = "string", enum = listOf("carnivore", "herbivore")),
      ),
    )

    When("the generator produces the data class") {
      val code = generator().generate("Dinosaur", schema).toString()

      Then("a companion enum class is generated in the same file") {
        code shouldContain "enum class Diet"
        code shouldContain "CARNIVORE"
        code shouldContain "HERBIVORE"
      }

      Then("the property uses the generated enum type") {
        code shouldContain "val diet: Diet"
      }
    }
  }

  Given("a data class with snake_case property names and serialisation enabled") {
    val schema = Schema(
      type = "object",
      required = listOf("arm_length"),
      properties = mapOf(
        "arm_length" to Schema(type = "number"),
      ),
    )
    val config = GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy)

    When("the generator produces the data class") {
      val code = generator(config).generate("Tyrannosaur", schema).toString()

      Then("the Kotlin property name is camelCase") {
        code shouldContain "val armLength: Double"
      }

      Then("a @SerialName annotation maps back to the spec name") {
        code shouldContain """@SerialName("arm_length")"""
      }
    }
  }
})
