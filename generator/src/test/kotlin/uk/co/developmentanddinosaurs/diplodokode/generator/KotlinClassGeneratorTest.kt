package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class KotlinClassGeneratorTest : BehaviorSpec({

  val generator = KotlinClassGenerator(GeneratorConfig())

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

  Given("a schema with an array of arrays") {
    val schema = Schema(
      type = "object",
      required = listOf("matrix", "table"),
      properties = mapOf(
        "matrix" to Schema(
          type = "array",
          items = Schema(type = "array", items = Schema(type = "integer")),
        ),
        "table" to Schema(
          type = "array",
          items = Schema(type = "array", items = Schema(type = "string")),
        ),
        "deeplyNested" to Schema(
          type = "array",
          items = Schema(type = "array", items = Schema(type = "array", items = Schema(type = "boolean"))),
        ),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Grid", schema).toString()

      Then("array of array of integers should be List<List<Int>>") {
        code shouldContain "val matrix: List<List<Int>>"
      }

      Then("array of array of strings should be List<List<String>>") {
        code shouldContain "val table: List<List<String>>"
      }

      Then("three levels of nesting should be List<List<List<Boolean>>>") {
        code shouldContain "val deeplyNested: List<List<List<Boolean>>>?"
      }
    }
  }

  Given("a schema with an array of inline enum items") {
    val schema = Schema(
      type = "object",
      properties = mapOf(
        "diets" to Schema(type = "array", items = Schema(type = "string", enum = listOf("carnivore", "herbivore"))),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it should fall back to List of the base type") {
        code shouldContain "val diets: List<String>?"
      }

      Then("it should emit a KDoc note with the enum values") {
        code shouldContain "NOTE: items have an enum constraint [carnivore, herbivore]"
        code shouldContain "\$ref"
      }
    }
  }

  Given("a schema with an array of enum refs") {
    val schema = Schema(
      type = "object",
      required = listOf("diets"),
      properties = mapOf(
        "diets" to Schema(type = "array", items = Schema(ref = "#/components/schemas/Diet")),
        "preyTypes" to Schema(type = "array", items = Schema(ref = "#/components/schemas/PreyType")),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("required array of enum refs should be List<Diet>") {
        code shouldContain "val diets: List<Diet>"
        code shouldNotContain "val diets: List<Diet>?"
      }

      Then("optional array of enum refs should be nullable List<PreyType>") {
        code shouldContain "val preyTypes: List<PreyType>?"
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

      Then("uuid format maps to Uuid") {
        code shouldContain "val id: Uuid"
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

      Then("uri format maps to String") {
        code shouldContain "val endpoint: String"
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

      Then("uuid format adds OptIn annotation to the file") {
        code shouldContain "@file:OptIn(ExperimentalUuidApi::class)"
      }
    }
  }

  Given("a schema with oneOf variants") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
        Schema(ref = "#/components/schemas/Triceratops"),
      )
    )

    When("the generator produces a sealed interface") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it should generate a sealed interface") {
        code shouldContain "sealed interface Dinosaur"
      }

      Then("it should not generate a data class") {
        code shouldNotContain "data class"
      }
    }
  }

  Given("a schema with oneOf and a discriminator, with a pre-computed discriminator enum") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
        Schema(ref = "#/components/schemas/Triceratops"),
      ),
      discriminator = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator("type"),
    )
    val discriminatorEnum = DiscriminatorEnum("type", listOf("TYRANNOSAUR", "TRICERATOPS"))

    When("the generator produces a sealed interface") {
      val code = generator.generateFromSchema("Dinosaur", schema, discriminatorEnum = discriminatorEnum).toString()

      Then("it should add a nested Type enum with all variant constants") {
        code shouldContain "enum class Type"
        code shouldContain "TYRANNOSAUR"
        code shouldContain "TRICERATOPS"
      }

      Then("it should add a typed discriminator property") {
        code shouldContain "val type: Type"
      }
    }
  }

  Given("a schema with oneOf and a discriminator but no pre-computed enum") {
    val schema = Schema(
      oneOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
      ),
      discriminator = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator("type"),
    )

    When("the generator produces a sealed interface without discriminator enum info") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it falls back to a String discriminator property") {
        code shouldContain "val type: String"
      }
    }
  }

  Given("a schema with oneOf inline variants") {
    val schema = Schema(
      oneOf = listOf(
        Schema(type = "object", properties = mapOf("armLength" to Schema(type = "number"))),
      )
    )

    When("the generator produces a sealed interface") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it should emit a KDoc note about unsupported inline variants") {
        code shouldContain "NOTE: Inline oneOf variants are not supported"
      }
    }
  }

  Given("a schema with anyOf variants") {
    val schema = Schema(
      anyOf = listOf(
        Schema(ref = "#/components/schemas/Tyrannosaur"),
        Schema(ref = "#/components/schemas/Triceratops"),
      )
    )

    When("the generator produces a sealed interface") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it should generate a sealed interface") {
        code shouldContain "sealed interface Dinosaur"
      }

      Then("it should note that one or more variants may be used") {
        code shouldContain "One or more of the following variants may be used"
      }

      Then("it should not generate a data class") {
        code shouldNotContain "data class"
      }
    }
  }

  Given("a data class schema that implements a sealed interface") {
    val schema = Schema(
      type = "object",
      required = listOf("armLength"),
      properties = mapOf("armLength" to Schema(type = "number")),
    )

    When("the generator produces a data class with an implemented interface") {
      val code = generator.generateFromSchema("Tyrannosaur", schema, listOf("Dinosaur")).toString()

      Then("it should implement the interface") {
        code shouldContain ": Dinosaur"
      }

      Then("it should still be a data class") {
        code shouldContain "data class Tyrannosaur"
      }
    }
  }

  Given("a data class schema with a discriminator override") {
    val schema = Schema(
      type = "object",
      required = listOf("type", "armLength"),
      properties = mapOf(
        "type" to Schema(type = "string", enum = listOf("tyrannosaur")),
        "armLength" to Schema(type = "number"),
      ),
    )
    val override = DiscriminatorOverride("Dinosaur", "type", "TYRANNOSAUR", "tyrannosaur")

    When("the generator produces a data class with a discriminator override") {
      val code = generator.generateFromSchema("Tyrannosaur", schema, listOf("Dinosaur"), discriminatorOverride = override).toString()

      Then("the discriminator property is an override with a default value") {
        code shouldContain "override val type: Dinosaur.Type"
        code shouldContain "Dinosaur.Type.TYRANNOSAUR"
      }

      Then("no inline enum class is generated for the discriminator property") {
        code shouldNotContain "enum class Type"
      }

      Then("other properties are generated normally") {
        code shouldContain "val armLength: Double"
      }
    }
  }

  Given("a schema with an array of uuid items") {
    val schema = Schema(
      type = "object",
      properties = mapOf(
        "ids" to Schema(type = "array", items = Schema(type = "string", format = "uuid")),
      )
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Dinosaur", schema).toString()

      Then("it should generate List<Uuid>") {
        code shouldContain "val ids: List<Uuid>?"
      }

      Then("it should add the OptIn annotation") {
        code shouldContain "@file:OptIn(ExperimentalUuidApi::class)"
      }
    }
  }

  Given("a schema with a date-time property and the KMP type mapping strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("discoveredAt"),
      properties = mapOf("discoveredAt" to Schema(type = "string", format = "date-time")),
    )
    val kmpGenerator = KotlinClassGenerator(GeneratorConfig(typeMappingStrategy = KotlinMultiplatformTypeMappingStrategy()))

    When("the generator produces a data class") {
      val code = kmpGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("it should use kotlinx.datetime.Instant") {
        code shouldContain "kotlinx.datetime.Instant"
        code shouldNotContain "java.time"
      }
    }
  }

  Given("a schema with a date-time property and the Java type mapping strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("discoveredAt"),
      properties = mapOf("discoveredAt" to Schema(type = "string", format = "date-time")),
    )
    val javaGenerator = KotlinClassGenerator(GeneratorConfig(typeMappingStrategy = JavaTypeMappingStrategy()))

    When("the generator produces a data class") {
      val code = javaGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("it should use java.time.Instant") {
        code shouldContain "java.time.Instant"
        code shouldNotContain "kotlinx.datetime"
      }
    }
  }

  Given("a schema with a uuid property and the Java type mapping strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("id"),
      properties = mapOf("id" to Schema(type = "string", format = "uuid")),
    )
    val javaGenerator = KotlinClassGenerator(GeneratorConfig(typeMappingStrategy = JavaTypeMappingStrategy()))

    When("the generator produces a data class") {
      val code = javaGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("it should use java.util.UUID") {
        code shouldContain "java.util.UUID"
      }

      Then("it should not add the kotlin.uuid OptIn annotation") {
        code shouldNotContain "@file:OptIn(ExperimentalUuidApi::class)"
      }
    }
  }

  Given("a schema with a date-time property and the KMP strategy with a format override") {
    val schema = Schema(
      type = "object",
      required = listOf("discoveredAt"),
      properties = mapOf("discoveredAt" to Schema(type = "string", format = "date-time")),
    )
    val overrideStrategy = KotlinMultiplatformTypeMappingStrategy()
        .withOverrides(formatOverrides = mapOf("date-time" to com.squareup.kotlinpoet.ClassName("java.time", "Instant")))
    val overrideGenerator = KotlinClassGenerator(GeneratorConfig(typeMappingStrategy = overrideStrategy))

    When("the generator produces a data class") {
      val code = overrideGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("the override type wins over the strategy default") {
        code shouldContain "java.time.Instant"
        code shouldNotContain "kotlinx.datetime"
      }
    }
  }

  Given("a schema with required and optional properties and the AllNullable strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("name"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "weight" to Schema(type = "number"),
      )
    )
    val allNullableGenerator = KotlinClassGenerator(GeneratorConfig(nullabilityStrategy = AllNullableStrategy()))

    When("the generator produces a data class") {
      val code = allNullableGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("required properties are also nullable") {
        code shouldContain "val name: String?"
      }

      Then("optional properties are nullable") {
        code shouldContain "val weight: Double?"
      }
    }
  }

  Given("a schema with required and optional properties and the AllNonNullable strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("name"),
      properties = mapOf(
        "name" to Schema(type = "string"),
        "weight" to Schema(type = "number"),
      )
    )
    val allNonNullableGenerator = KotlinClassGenerator(GeneratorConfig(nullabilityStrategy = AllNonNullableStrategy()))

    When("the generator produces a data class") {
      val code = allNonNullableGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("required properties are non-nullable") {
        code shouldContain "val name: String"
        code shouldNotContain "val name: String?"
      }

      Then("optional properties are also non-nullable") {
        code shouldContain "val weight: Double"
        code shouldNotContain "val weight: Double?"
      }
    }
  }

  Given("a schema with mixed-case and separator names using the Preserve naming strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("SpeciesName"),
      properties = mapOf(
        "SpeciesName" to Schema(type = "string"),
        "content-type" to Schema(type = "string"),
        "diet_type" to Schema(type = "string", enum = listOf("carnivore", "herbivore")),
      )
    )
    val preserveGenerator = KotlinClassGenerator(GeneratorConfig(namingStrategy = PreserveNamingStrategy()))

    When("the generator produces a data class") {
      val code = preserveGenerator.generateFromSchema("tyrannosaur", schema).toString()

      Then("the class name is preserved as-is") {
        code shouldContain "data class tyrannosaur"
      }

      Then("property names are preserved as-is") {
        code shouldContain "val SpeciesName: String"
      }

      Then("hyphenated property names are backtick-escaped by KotlinPoet") {
        code shouldContain "`content-type`"
      }

      Then("inline enum constants preserve their original case") {
        code shouldContain "carnivore"
        code shouldNotContain "CARNIVORE"
      }
    }
  }

  Given("a schema with a separator-containing property name using the Default naming strategy") {
    val schema = Schema(
      type = "object",
      required = listOf("content-type"),
      properties = mapOf("content-type" to Schema(type = "string")),
    )

    When("the generator produces a data class") {
      val code = generator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("the property name is converted to camelCase") {
        code shouldContain "val contentType: String"
        code shouldNotContain "`content-type`"
      }
    }
  }

  Given("a kotlinx serialisation strategy") {
    val serialisationGenerator = KotlinClassGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))

    When("the generator produces a data class") {
      val schema = Schema(
        type = "object",
        required = listOf("name"),
        properties = mapOf("name" to Schema(type = "string")),
      )
      val code = serialisationGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("the data class is annotated with @Serializable") {
        code shouldContain "@Serializable"
      }

      Then("the import for Serializable is present") {
        code shouldContain "import kotlinx.serialization.Serializable"
      }
    }

    When("the generator produces a top-level enum class") {
      val schema = Schema(enum = listOf("tyrannosaur", "triceratops"))
      val code = serialisationGenerator.generateFromSchema("DinosaurType", schema).toString()

      Then("the enum class is annotated with @Serializable") {
        code shouldContain "@Serializable"
      }

      Then("the import for Serializable is present") {
        code shouldContain "import kotlinx.serialization.Serializable"
      }

      Then("each constant has a @SerialName matching the spec value") {
        code shouldContain """@SerialName("tyrannosaur")"""
        code shouldContain """@SerialName("triceratops")"""
      }

      Then("the Kotlin constant names are still uppercased") {
        code shouldContain "TYRANNOSAUR"
        code shouldContain "TRICERATOPS"
      }
    }

    When("the generator produces an inline enum companion class") {
      val schema = Schema(
        type = "object",
        required = listOf("diet"),
        properties = mapOf("diet" to Schema(type = "string", enum = listOf("carnivore", "herbivore"))),
      )
      val code = serialisationGenerator.generateFromSchema("Dinosaur", schema).toString()

      Then("the inline enum class is annotated with @Serializable") {
        code shouldContain "@Serializable"
      }

      Then("each constant has a @SerialName matching the spec value") {
        code shouldContain """@SerialName("carnivore")"""
        code shouldContain """@SerialName("herbivore")"""
      }
    }

    When("the generator produces a sealed interface with a discriminator enum") {
      val schema = Schema(
        oneOf = listOf(
          Schema(ref = "#/components/schemas/Tyrannosaur"),
          Schema(ref = "#/components/schemas/Triceratops"),
        ),
        discriminator = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator("type"),
      )
      val discriminatorEnum = DiscriminatorEnum("type", listOf("TYRANNOSAUR", "TRICERATOPS"), listOf("tyrannosaur", "triceratops"))
      val code = serialisationGenerator.generateFromSchema("Dinosaur", schema, discriminatorEnum = discriminatorEnum).toString()

      Then("the sealed interface is annotated with @Serializable") {
        code shouldContain "@Serializable"
      }

      Then("the sealed interface is annotated with @JsonClassDiscriminator") {
        code shouldContain """@JsonClassDiscriminator("type")"""
      }

      Then("no nested Type enum is generated") {
        code shouldNotContain "enum class Type"
      }

      Then("no abstract discriminator property is generated") {
        code shouldNotContain "val type:"
      }
    }

    When("the generator produces a variant data class with a discriminator override") {
      val schema = Schema(
        type = "object",
        required = listOf("type", "armLength"),
        properties = mapOf(
          "type" to Schema(type = "string", enum = listOf("tyrannosaur")),
          "armLength" to Schema(type = "number"),
        ),
      )
      val override = DiscriminatorOverride("Dinosaur", "type", "TYRANNOSAUR", "tyrannosaur")
      val code = serialisationGenerator.generateFromSchema("Tyrannosaur", schema, listOf("Dinosaur"), discriminatorOverride = override).toString()

      Then("the data class is annotated with @SerialName for the discriminator value") {
        code shouldContain """@SerialName("tyrannosaur")"""
      }

      Then("the discriminator property is omitted from the data class") {
        code shouldNotContain "val type:"
        code shouldNotContain "override val type"
      }

      Then("other properties are generated normally") {
        code shouldContain "val armLength: Double"
      }
    }
  }

  Given("no serialisation strategy") {
    val noSerializationGenerator = KotlinClassGenerator(GeneratorConfig())

    When("the generator produces a data class") {
      val schema = Schema(
        type = "object",
        required = listOf("name"),
        properties = mapOf("name" to Schema(type = "string")),
      )
      val code = noSerializationGenerator.generateFromSchema("Tyrannosaur", schema).toString()

      Then("no @Serializable annotation is present") {
        code shouldNotContain "@Serializable"
        code shouldNotContain "import kotlinx.serialization"
      }
    }

    When("the generator produces an enum class") {
      val schema = Schema(enum = listOf("tyrannosaur", "triceratops"))
      val code = noSerializationGenerator.generateFromSchema("DinosaurType", schema).toString()

      Then("no @Serializable annotation is present") {
        code shouldNotContain "@Serializable"
        code shouldNotContain "import kotlinx.serialization"
      }

      Then("no @SerialName annotations are present") {
        code shouldNotContain "@SerialName"
      }
    }
  }
})
