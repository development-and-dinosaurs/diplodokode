package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.DefaultValue
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

  Given("a variant that belongs to two discriminated sealed hierarchies") {
    val schema = Schema(
      type = "object",
      required = listOf("dinosaurType", "predatorType", "name"),
      properties = mapOf(
        "dinosaurType" to Schema(type = "string", enum = listOf("tyrannosaur")),
        "predatorType" to Schema(type = "string", enum = listOf("biped")),
        "name" to Schema(type = "string"),
      ),
    )
    val overrides = listOf(
      DiscriminatorOverride("Dinosaur", "dinosaurType", "TYRANNOSAUR", "tyrannosaur"),
      DiscriminatorOverride("Predator", "predatorType", "BIPED", "biped"),
    )

    When("the generator produces the variant data class") {
      val code = generator().generate("Tyrannosaur", schema, listOf("Dinosaur", "Predator"), discriminatorOverrides = overrides).toString()

      Then("the first discriminator property is an override with its default") {
        code shouldContain "override val dinosaurType: Dinosaur.Type"
        code shouldContain "Dinosaur.Type.TYRANNOSAUR"
      }

      Then("the second discriminator property is also an override with its default") {
        code shouldContain "override val predatorType: Predator.Type"
        code shouldContain "Predator.Type.BIPED"
      }

      Then("the regular property is still generated") {
        code shouldContain "val name: String"
      }

      Then("no inline enum classes are generated for the discriminator properties") {
        code shouldNotContain "enum class DinosaurType"
        code shouldNotContain "enum class PredatorType"
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
      val code = generator().generate("Tyrannosaur", schema, listOf("Dinosaur"), discriminatorOverrides = listOf(override)).toString()

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
      val code = generator(config).generate("Tyrannosaur", schema, listOf("Dinosaur"), discriminatorOverrides = listOf(override)).toString()

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

  Given("a schema where a date-time property has a default value") {
    val schema = Schema(
      type = "object",
      properties = mapOf(
        "createdAt" to Schema(type = "string", format = "date-time", default = DefaultValue.Str("2024-01-01T00:00:00Z")),
      ),
    )

    When("the generator produces a data class") {
      val code = generator().generate("Tyrannosaur", schema).toString()

      Then("no raw string literal default is emitted") {
        code shouldNotContain "= \"2024-01-01T00:00:00Z\""
      }

      Then("a parse call is emitted as the default") {
        code shouldContain "Instant.parse(\"2024-01-01T00:00:00Z\")"
      }
    }
  }

  Given("a schema where a byte-format property has a default value") {
    val schema = Schema(
      type = "object",
      properties = mapOf(
        "data" to Schema(type = "string", format = "byte", default = DefaultValue.Str("aGVsbG8=")),
      ),
    )

    When("the generator produces a data class") {
      val code = generator().generate("Tyrannosaur", schema).toString()

      Then("a toByteArray() call is emitted as the default") {
        code shouldContain "\"aGVsbG8=\".toByteArray()"
      }
    }
  }

  Given("a schema with a custom numeric type and a numeric default") {
    val customConfig = GeneratorConfig(
      typeMappingStrategy = CustomTypeMappingStrategy(
        baseMappings = mapOf("number" to com.squareup.kotlinpoet.ClassName("java.math", "BigDecimal")),
      ),
    )
    val schema = Schema(
      type = "object",
      properties = mapOf("weight" to Schema(type = "number", default = DefaultValue.Num(42.5))),
    )

    When("the generator produces a data class") {
      val code = generator(customConfig).generate("Dinosaur", schema).toString()

      Then("a numeric default is emitted as a double literal fallback") {
        code shouldContain "= 42.5"
      }
    }
  }

  Given("a schema with a uri-format string property") {
    val schema = Schema(
      type = "object",
      properties = mapOf("homepage" to Schema(type = "string", format = "uri")),
    )

    When("the generator produces a data class") {
      val code = generator().generate("Dinosaur", schema).toString()

      Then("the property type is String") {
        code shouldContain "val homepage: String"
      }

      Then("a KDoc note explains the uri-to-String mapping") {
        code shouldContain "format is 'uri'; represented as String"
      }
    }
  }

  Given("a schema with an array property that has no items schema") {
    val schema = Schema(
      type = "object",
      properties = mapOf("bones" to Schema(type = "array")),
    )

    When("the generator produces a data class") {
      val code = generator().generate("Tyrannosaur", schema).toString()

      Then("the property type is List<Any>") {
        code shouldContain "val bones: List<Any>"
      }

      Then("a KDoc note warns about missing items schema") {
        code shouldContain "no 'items' schema defined"
      }
    }
  }

  Given("a schema with additionalProperties: false at the schema level") {
    val schema = Schema(
      type = "object",
      additionalProperties = AdditionalProperties.Forbidden,
      properties = mapOf("name" to Schema(type = "string")),
    )

    When("the generator produces a data class") {
      val code = generator().generate("Tyrannosaur", schema).toString()

      Then("the named property is still emitted") {
        code shouldContain "val name: String"
      }

      Then("no Map property is generated") {
        code shouldNotContain "Map<"
      }

      Then("a KDoc note states additional properties are forbidden") {
        code shouldContain "additional properties are forbidden by the OpenAPI spec"
      }
    }
  }

  Given("a schema where a property has additionalProperties: false") {
    val schema = Schema(
      type = "object",
      properties = mapOf(
        "name" to Schema(type = "string"),
        "metadata" to Schema(additionalProperties = AdditionalProperties.Forbidden),
      ),
    )

    When("the generator produces a data class") {
      val code = generator().generate("Tyrannosaur", schema).toString()

      Then("the forbidden-additional-properties property is not emitted") {
        code shouldNotContain "metadata"
        code shouldNotContain "Map<"
      }

      Then("a KDoc note states additional properties are forbidden") {
        code shouldContain "additional properties are forbidden by the OpenAPI spec"
      }

      Then("the regular property is still emitted") {
        code shouldContain "val name: String"
      }
    }
  }
})
