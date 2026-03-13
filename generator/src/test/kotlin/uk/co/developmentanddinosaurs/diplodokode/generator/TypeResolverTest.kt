package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import com.squareup.kotlinpoet.ParameterizedTypeName
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class TypeResolverTest : BehaviorSpec({

  val resolver = TypeResolver(GeneratorConfig())

  Given("a primitive OpenAPI type") {
    Then("string maps to String") {
      resolver.mapTypeToKotlin("string").toString() shouldBe "kotlin.String"
    }
    Then("integer maps to Int") {
      resolver.mapTypeToKotlin("integer").toString() shouldBe "kotlin.Int"
    }
    Then("number maps to Double") {
      resolver.mapTypeToKotlin("number").toString() shouldBe "kotlin.Double"
    }
    Then("boolean maps to Boolean") {
      resolver.mapTypeToKotlin("boolean").toString() shouldBe "kotlin.Boolean"
    }
    Then("object maps to Any") {
      resolver.mapTypeToKotlin("object").toString() shouldBe "kotlin.Any"
    }
    Then("an unknown type falls back to String") {
      resolver.mapTypeToKotlin("exotic").toString() shouldBe "kotlin.String"
    }
    Then("a null type falls back to String") {
      resolver.mapTypeToKotlin(null).toString() shouldBe "kotlin.String"
    }
  }

  Given("a string type with a format") {
    Then("date-time maps to kotlinx Instant") {
      resolver.mapTypeToKotlin("string", "date-time").toString() shouldBe "kotlinx.datetime.Instant"
    }
    Then("date maps to kotlinx LocalDate") {
      resolver.mapTypeToKotlin("string", "date").toString() shouldBe "kotlinx.datetime.LocalDate"
    }
    Then("time maps to kotlinx LocalTime") {
      resolver.mapTypeToKotlin("string", "time").toString() shouldBe "kotlinx.datetime.LocalTime"
    }
    Then("uuid maps to kotlin.uuid.Uuid") {
      resolver.mapTypeToKotlin("string", "uuid").toString() shouldBe "kotlin.uuid.Uuid"
    }
    Then("integer with int64 format maps to Long") {
      resolver.mapTypeToKotlin("integer", "int64").toString() shouldBe "kotlin.Long"
    }
    Then("number with float format maps to Float") {
      resolver.mapTypeToKotlin("number", "float").toString() shouldBe "kotlin.Float"
    }
  }

  Given("a property with a \$ref") {
    val schema = Schema(ref = "#/components/schemas/Tyrannosaur")

    When("resolveType is called") {
      val type = resolver.resolveType("dinosaur", schema, isNullable = false, enumClassNames = emptyMap())

      Then("it resolves to the referenced class name in the configured package") {
        type.toString() shouldBe "uk.co.developmentanddinosaurs.diplodokode.generated.Tyrannosaur"
      }
    }

    When("resolveType is called with nullable = true") {
      val type = resolver.resolveType("dinosaur", schema, isNullable = true, enumClassNames = emptyMap())

      Then("the type is nullable") {
        type.isNullable shouldBe true
      }
    }
  }

  Given("a property with an array type") {
    When("items is a primitive type") {
      val schema = Schema(type = "array", items = Schema(type = "string"))
      val type = resolver.resolveType("names", schema, isNullable = false, enumClassNames = emptyMap())

      Then("it resolves to List<String>") {
        type.toString() shouldBe "kotlin.collections.List<kotlin.String>"
      }
    }

    When("items is a \$ref") {
      val schema = Schema(type = "array", items = Schema(ref = "#/components/schemas/Triceratops"))
      val type = resolver.resolveType("herd", schema, isNullable = false, enumClassNames = emptyMap())

      Then("it resolves to List<RefType>") {
        type.toString() shouldBe "kotlin.collections.List<uk.co.developmentanddinosaurs.diplodokode.generated.Triceratops>"
      }
    }

    When("items is absent") {
      val schema = Schema(type = "array")
      val type = resolver.resolveType("items", schema, isNullable = false, enumClassNames = emptyMap())

      Then("it falls back to List<Any>") {
        type.toString() shouldBe "kotlin.collections.List<kotlin.Any>"
      }
    }
  }

  Given("a property matching an inline enum class") {
    val schema = Schema(type = "string")
    val enumClassName = com.squareup.kotlinpoet.ClassName("uk.co.developmentanddinosaurs.diplodokode.generated", "Diet")
    val enumClassNames = mapOf("diet" to enumClassName)

    When("resolveType is called") {
      val type = resolver.resolveType("diet", schema, isNullable = false, enumClassNames = enumClassNames)

      Then("it uses the pre-resolved enum ClassName") {
        type shouldBe enumClassName
      }
    }
  }

  Given("resolveItemType with a nested array") {
    val nestedItems = Schema(type = "string")
    val outerItems = Schema(type = "array", items = nestedItems)

    When("called") {
      val type = resolver.resolveItemType(outerItems)

      Then("it produces a parameterized List type") {
        type.shouldBeInstanceOf<ParameterizedTypeName>()
        type.toString() shouldBe "kotlin.collections.List<kotlin.String>"
      }
    }
  }

  Given("containsKotlinUuid") {
    Then("returns true for a direct Uuid type") {
      val uuidType = resolver.mapTypeToKotlin("string", "uuid")
      resolver.containsKotlinUuid(uuidType) shouldBe true
    }

    Then("returns false for non-uuid types") {
      resolver.containsKotlinUuid(resolver.mapTypeToKotlin("string")) shouldBe false
      resolver.containsKotlinUuid(resolver.mapTypeToKotlin("integer")) shouldBe false
    }

    Then("returns true for List<Uuid>") {
      val schema = Schema(type = "array", items = Schema(type = "string", format = "uuid"))
      val listType = resolver.resolveItemType(Schema(type = "array", items = Schema(type = "string", format = "uuid")))
      val parameterized = resolver.resolveType("ids", schema, isNullable = false, enumClassNames = emptyMap())
      resolver.containsKotlinUuid(parameterized) shouldBe true
    }

    Then("returns false for a nullable non-uuid type") {
      val type = resolver.mapTypeToKotlin("string").copy(nullable = true)
      resolver.containsKotlinUuid(type) shouldBe false
    }
  }
})
