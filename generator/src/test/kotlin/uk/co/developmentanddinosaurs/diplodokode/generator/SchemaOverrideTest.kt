package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema
import java.io.File

class SchemaOverrideTest : BehaviorSpec({

  val dnaClassName = ClassName("com.example.biology", "DinosaurDna")
  val config = GeneratorConfig(
    schemaOverrides = mapOf("DinosaurDna" to dnaClassName),
  )
  val generator = DiplodokodeGenerator(config)

  Given("a spec with a schema that has a \$ref to an overridden schema") {
    val specFile = File("src/test/resources/schema-override-api.yaml")

    When("the generator runs") {
      val files = generator.generateFromSpec(specFile)

      Then("no file is generated for the overridden schema") {
        files.none { it.name == "DinosaurDna" } shouldBe true
      }

      Then("a file is generated for the referencing schema") {
        files.any { it.name == "Tyrannosaur" } shouldBe true
      }

      Then("the referencing schema uses the override class for the \$ref property") {
        val code = files.find { it.name == "Tyrannosaur" }!!.toString()
        code shouldContain "import com.example.biology.DinosaurDna"
      }

      Then("the referencing schema does not import from the default generated package for the overridden type") {
        val code = files.find { it.name == "Tyrannosaur" }!!.toString()
        code shouldNotContain "import uk.co.developmentanddinosaurs.diplodokode.generated.DinosaurDna"
      }
    }
  }

  Given("a spec where the overridden schema is used in an array items \$ref") {
    val arrayConfig = GeneratorConfig(
      schemaOverrides = mapOf("DinosaurDna" to dnaClassName),
    )

    val schema = Schema(
      type = "object",
      properties = mapOf(
        "dnaSequences" to Schema(
          type = "array",
          items = Schema(
            ref = "#/components/schemas/DinosaurDna",
          ),
        ),
      ),
    )
    val typeResolver = TypeResolver(arrayConfig)
    val enumClassGen = EnumClassGenerator(arrayConfig)
    val dataClassGen = DataClassGenerator(arrayConfig, typeResolver, enumClassGen)

    When("the data class is generated") {
      val code = dataClassGen.generate("Tyrannosaur", schema).toString()

      Then("the array property uses the override class as the element type") {
        code shouldContain "import com.example.biology.DinosaurDna"
        code shouldContain "List<DinosaurDna>"
      }
    }
  }

  Given("a spec where an overridden schema is a sealed interface that variants implement") {
    val dinosaurOverride = ClassName("com.example.zoo", "Dinosaur")
    val sealedConfig = GeneratorConfig(
      schemaOverrides = mapOf("Dinosaur" to dinosaurOverride),
    )
    val moduleConfig = GeneratorConfig(
      schemaOverrides = mapOf("Dinosaur" to dinosaurOverride),
      serialisationStrategy = KotlinxSerialisationStrategy,
      polymorphismStrategy = PolymorphismStrategy.MODULE,
    )
    val sealedGenerator = DiplodokodeGenerator(sealedConfig)
    val specFile = File("src/test/resources/oneof-api.yaml")

    When("the generator runs") {
      val files = sealedGenerator.generateFromSpec(specFile)

      Then("variant data classes implement the override class instead of the default-package one") {
        val tyrannosaurCode = files.find { it.name == "Tyrannosaur" }!!.toString()
        tyrannosaurCode shouldContain "import com.example.zoo.Dinosaur"
        tyrannosaurCode shouldContain ": Dinosaur"
        tyrannosaurCode shouldNotContain "import uk.co.developmentanddinosaurs.diplodokode.generated.Dinosaur"
      }

      Then("the discriminator override property uses the override's nested Type") {
        val tyrannosaurCode = files.find { it.name == "Tyrannosaur" }!!.toString()
        tyrannosaurCode shouldContain "Dinosaur.Type."
      }

      Then("no file is generated for the overridden sealed interface") {
        files.none { it.name == "Dinosaur" } shouldBe true
      }
    }

    When("the same spec is generated with a kotlinx-serialisation MODULE strategy") {
      val moduleFiles = DiplodokodeGenerator(moduleConfig).generateFromSpec(specFile)

      Then("the serializers module skips the overridden interface so no dangling reference is registered") {
        val moduleFile = moduleFiles.find { it.name == "DiplodokodeModule" }
        if (moduleFile != null) {
          val code = moduleFile.toString()
          code shouldNotContain "polymorphic(Dinosaur::class)"
          code shouldNotContain "import uk.co.developmentanddinosaurs.diplodokode.generated.Dinosaur"
        }
      }
    }
  }

  Given("a spec where the overridden schema is not defined in the spec at all") {
    val externalConfig = GeneratorConfig(
      schemaOverrides = mapOf("DinosaurDna" to dnaClassName),
    )
    val validator = SpecValidator()
    val schemas = mapOf(
      "Tyrannosaur" to Schema(
        type = "object",
        properties = mapOf(
          "dna" to Schema(
            ref = "#/components/schemas/DinosaurDna",
          ),
        ),
      ),
    )

    When("the validator runs with the override names provided") {
      val diagnostics = validator.validate(schemas, externalConfig.schemaOverrides.keys)

      Then("no error is produced for the ref to the externally-provided schema") {
        diagnostics.none { it.severity == DiagnosticSeverity.ERROR } shouldBe true
      }
    }
  }
})
