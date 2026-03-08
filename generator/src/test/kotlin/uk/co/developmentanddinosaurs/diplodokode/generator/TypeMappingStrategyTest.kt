package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TypeMappingStrategyTest : BehaviorSpec({

  Given("the KotlinMultiplatformTypeMappingStrategy") {
    val strategy = KotlinMultiplatformTypeMappingStrategy()

    When("resolving base types without a format") {
      Then("string maps to String") {
        strategy.resolve("string", null) shouldBe String::class.asTypeName()
      }
      Then("integer maps to Int") {
        strategy.resolve("integer", null) shouldBe Int::class.asTypeName()
      }
      Then("number maps to Double") {
        strategy.resolve("number", null) shouldBe Double::class.asTypeName()
      }
      Then("boolean maps to Boolean") {
        strategy.resolve("boolean", null) shouldBe Boolean::class.asTypeName()
      }
      Then("array maps to List") {
        strategy.resolve("array", null) shouldBe List::class.asTypeName()
      }
      Then("object maps to Any") {
        strategy.resolve("object", null) shouldBe Any::class.asTypeName()
      }
      Then("an unknown type returns null") {
        strategy.resolve("exotic", null) shouldBe null
      }
    }

    When("resolving string formats") {
      Then("date-time maps to kotlinx.datetime.Instant") {
        strategy.resolve("string", "date-time") shouldBe ClassName("kotlinx.datetime", "Instant")
      }
      Then("date maps to kotlinx.datetime.LocalDate") {
        strategy.resolve("string", "date") shouldBe ClassName("kotlinx.datetime", "LocalDate")
      }
      Then("time maps to kotlinx.datetime.LocalTime") {
        strategy.resolve("string", "time") shouldBe ClassName("kotlinx.datetime", "LocalTime")
      }
      Then("duration maps to kotlin.time.Duration") {
        strategy.resolve("string", "duration") shouldBe ClassName("kotlin.time", "Duration")
      }
      Then("uuid maps to kotlin.uuid.Uuid") {
        strategy.resolve("string", "uuid") shouldBe ClassName("kotlin.uuid", "Uuid")
      }
      Then("uri maps to String") {
        strategy.resolve("string", "uri") shouldBe String::class.asTypeName()
      }
      Then("byte maps to ByteArray") {
        strategy.resolve("string", "byte") shouldBe ByteArray::class.asTypeName()
      }
      Then("binary maps to ByteArray") {
        strategy.resolve("string", "binary") shouldBe ByteArray::class.asTypeName()
      }
      Then("an unknown string format falls back to the base string mapping") {
        strategy.resolve("string", "exotic-format") shouldBe String::class.asTypeName()
      }
    }

    When("resolving integer and number formats") {
      Then("integer int64 maps to Long") {
        strategy.resolve("integer", "int64") shouldBe Long::class.asTypeName()
      }
      Then("number float maps to Float") {
        strategy.resolve("number", "float") shouldBe Float::class.asTypeName()
      }
    }
  }

  Given("the JavaTypeMappingStrategy") {
    val strategy = JavaTypeMappingStrategy()

    When("resolving base types without a format") {
      Then("string maps to String") {
        strategy.resolve("string", null) shouldBe String::class.asTypeName()
      }
      Then("integer maps to Int") {
        strategy.resolve("integer", null) shouldBe Int::class.asTypeName()
      }
      Then("number maps to Double") {
        strategy.resolve("number", null) shouldBe Double::class.asTypeName()
      }
      Then("boolean maps to Boolean") {
        strategy.resolve("boolean", null) shouldBe Boolean::class.asTypeName()
      }
      Then("an unknown type returns null") {
        strategy.resolve("exotic", null) shouldBe null
      }
    }

    When("resolving string formats") {
      Then("date-time maps to java.time.Instant") {
        strategy.resolve("string", "date-time") shouldBe ClassName("java.time", "Instant")
      }
      Then("date maps to java.time.LocalDate") {
        strategy.resolve("string", "date") shouldBe ClassName("java.time", "LocalDate")
      }
      Then("time maps to java.time.LocalTime") {
        strategy.resolve("string", "time") shouldBe ClassName("java.time", "LocalTime")
      }
      Then("duration maps to java.time.Duration") {
        strategy.resolve("string", "duration") shouldBe ClassName("java.time", "Duration")
      }
      Then("uuid maps to java.util.UUID") {
        strategy.resolve("string", "uuid") shouldBe ClassName("java.util", "UUID")
      }
      Then("uri maps to java.net.URI") {
        strategy.resolve("string", "uri") shouldBe ClassName("java.net", "URI")
      }
      Then("byte maps to ByteArray") {
        strategy.resolve("string", "byte") shouldBe ByteArray::class.asTypeName()
      }
      Then("binary maps to ByteArray") {
        strategy.resolve("string", "binary") shouldBe ByteArray::class.asTypeName()
      }
    }

    When("resolving integer and number formats") {
      Then("integer int64 maps to Long") {
        strategy.resolve("integer", "int64") shouldBe Long::class.asTypeName()
      }
      Then("number float maps to Float") {
        strategy.resolve("number", "float") shouldBe Float::class.asTypeName()
      }
    }
  }

  Given("a CustomTypeMappingStrategy") {
    When("constructed with custom format and base mappings") {
      val customType = ClassName("com.example", "DinoDate")
      val strategy = CustomTypeMappingStrategy(
          formatMappings = mapOf("string" to mapOf("dino-time" to customType)),
          baseMappings = mapOf("dino" to ClassName("com.example", "Dino")),
      )

      Then("it resolves custom format mappings") {
        strategy.resolve("string", "dino-time") shouldBe customType
      }
      Then("it resolves custom base mappings") {
        strategy.resolve("dino", null) shouldBe ClassName("com.example", "Dino")
      }
      Then("it returns null for unknown types") {
        strategy.resolve("string", null) shouldBe null
      }
    }

    When("constructed with empty maps") {
      val strategy = CustomTypeMappingStrategy()

      Then("it returns null for any type") {
        strategy.resolve("string", null) shouldBe null
        strategy.resolve("integer", "int64") shouldBe null
      }
    }
  }

  Given("a strategy with format overrides applied via withOverrides") {
    val base = KotlinMultiplatformTypeMappingStrategy()
    val override = ClassName("java.time", "Instant")
    val strategy = base.withOverrides(formatOverrides = mapOf("date-time" to override))

    When("resolving a format that has an override") {
      Then("the override wins over the base strategy") {
        strategy.resolve("string", "date-time") shouldBe override
      }
    }

    When("resolving a format with no override") {
      Then("the base strategy result is used") {
        strategy.resolve("string", "uuid") shouldBe ClassName("kotlin.uuid", "Uuid")
      }
    }

    When("resolving a base type with no override") {
      Then("the base strategy result is used") {
        strategy.resolve("integer", null) shouldBe Int::class.asTypeName()
      }
    }
  }

  Given("a strategy with base type overrides applied via withOverrides") {
    val base = KotlinMultiplatformTypeMappingStrategy()
    val override = Long::class.asTypeName()
    val strategy = base.withOverrides(baseOverrides = mapOf("integer" to override))

    When("resolving the overridden base type without a format") {
      Then("the override wins") {
        strategy.resolve("integer", null) shouldBe override
      }
    }

    When("resolving the overridden base type with a known format") {
      Then("the format mapping wins over the base override") {
        strategy.resolve("integer", "int64") shouldBe Long::class.asTypeName()
      }
    }
  }

  Given("a strategy with both format and base overrides applied") {
    val base = JavaTypeMappingStrategy()
    val formatOverride = ClassName("com.example", "DinoInstant")
    val baseOverride = ClassName("com.example", "DinoInt")
    val strategy = base.withOverrides(
        formatOverrides = mapOf("date-time" to formatOverride),
        baseOverrides = mapOf("integer" to baseOverride),
    )

    When("resolving the overridden format") {
      Then("the format override wins") {
        strategy.resolve("string", "date-time") shouldBe formatOverride
      }
    }

    When("resolving the overridden base type") {
      Then("the base override wins") {
        strategy.resolve("integer", null) shouldBe baseOverride
      }
    }

    When("resolving something untouched by overrides") {
      Then("the base strategy is used") {
        strategy.resolve("string", "uuid") shouldBe ClassName("java.util", "UUID")
      }
    }
  }

  Given("withOverrides applied to another withOverrides result") {
    val base = KotlinMultiplatformTypeMappingStrategy()
    val firstOverride = ClassName("com.example", "DinoInstant")
    val secondOverride = ClassName("com.example", "BetterDinoInstant")
    val strategy = base
        .withOverrides(formatOverrides = mapOf("date-time" to firstOverride))
        .withOverrides(formatOverrides = mapOf("date-time" to secondOverride))

    When("resolving the overridden format") {
      Then("the outermost override wins") {
        strategy.resolve("string", "date-time") shouldBe secondOverride
      }
    }
  }

  Given("two strategies resolving the same type") {
    val kmp = KotlinMultiplatformTypeMappingStrategy()
    val java = JavaTypeMappingStrategy()

    When("comparing their date-time resolution") {
      Then("they produce different types") {
        kmp.resolve("string", "date-time") shouldNotBe java.resolve("string", "date-time")
      }
    }

    When("comparing their uuid resolution") {
      Then("they produce different types") {
        kmp.resolve("string", "uuid") shouldNotBe java.resolve("string", "uuid")
      }
    }

    When("comparing their uri resolution") {
      Then("they produce different types") {
        kmp.resolve("string", "uri") shouldNotBe java.resolve("string", "uri")
      }
    }
  }
})
