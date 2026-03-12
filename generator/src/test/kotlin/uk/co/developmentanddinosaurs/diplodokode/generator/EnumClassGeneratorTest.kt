package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class EnumClassGeneratorTest : BehaviorSpec({

  Given("an enum generator without a serialisation strategy") {
    val generator = EnumClassGenerator(GeneratorConfig())

    When("generating an enum class from spec values") {
      val typeSpec = generator.generateEnumClass("Era", listOf("triassic", "jurassic", "cretaceous"))
      val code = typeSpec.toString()

      Then("the enum class has the correct name") {
        code shouldContain "enum class Era"
      }

      Then("constants are uppercased") {
        code shouldContain "TRIASSIC"
        code shouldContain "JURASSIC"
        code shouldContain "CRETACEOUS"
      }

      Then("no serialisation annotations are present") {
        code shouldNotContain "@Serializable"
        code shouldNotContain "@SerialName"
      }
    }

    When("generating a top-level enum file") {
      val schema = Schema(enum = listOf("carnivore", "herbivore"))
      val fileSpec = generator.generateTopLevelEnum("Diet", schema)

      Then("the file is in the configured package") {
        fileSpec.packageName shouldContain "diplodokode.generated"
      }

      Then("the file contains the enum class") {
        fileSpec.toString() shouldContain "enum class Diet"
      }

      Then("constants are present") {
        fileSpec.toString() shouldContain "CARNIVORE"
        fileSpec.toString() shouldContain "HERBIVORE"
      }
    }

    When("generating a top-level enum from an empty schema") {
      val schema = Schema(enum = emptyList())
      val fileSpec = generator.generateTopLevelEnum("EmptyEnum", schema)

      Then("an empty enum class is produced") {
        fileSpec.toString() shouldContain "enum class EmptyEnum"
      }
    }
  }

  Given("an enum generator with a kotlinx serialisation strategy") {
    val generator = EnumClassGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))

    When("generating an enum class") {
      // Use generateTopLevelEnum so imports resolve to short names in the output
      val code = generator.generateTopLevelEnum("Era", Schema(enum = listOf("triassic", "jurassic", "cretaceous"))).toString()

      Then("the enum class is annotated with @Serializable") {
        code shouldContain "@Serializable"
      }

      Then("each constant has a @SerialName matching the spec value") {
        code shouldContain """@SerialName("triassic")"""
        code shouldContain """@SerialName("jurassic")"""
        code shouldContain """@SerialName("cretaceous")"""
      }

      Then("Kotlin constant names are still uppercased") {
        code shouldContain "TRIASSIC"
        code shouldContain "JURASSIC"
        code shouldContain "CRETACEOUS"
      }
    }

    When("generating from spec values with separators") {
      val code = generator.generateTopLevelEnum("PackBehaviour", Schema(enum = listOf("pack-hunter", "solitary"))).toString()

      Then("constant names are converted to UPPER_SNAKE_CASE") {
        code shouldContain "PACK_HUNTER"
        code shouldContain "SOLITARY"
      }

      Then("@SerialName preserves the original spec value") {
        code shouldContain """@SerialName("pack-hunter")"""
        code shouldContain """@SerialName("solitary")"""
      }
    }
  }
})
