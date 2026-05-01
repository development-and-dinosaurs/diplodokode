package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class MapTypealiasGeneratorTest : BehaviorSpec({

  val generator = DiplodokodeGenerator(GeneratorConfig())

  Given("a spec with map-only schemas at the top level") {
    val openApiSpec = File("src/test/resources/map-schema-api.yaml")
    val generatedFiles = generator.generateFromSpec(openApiSpec)

    Then("a schema with additionalProperties: \$ref generates a typealias to Map<String, RefType>") {
      val lookup = generatedFiles.find { it.name == "DinosaurLookup" }.shouldNotBeNull()
      val code = lookup.toString()
      code shouldContain "typealias DinosaurLookup = Map<String, Dinosaur>"
      code shouldNotContain "data object DinosaurLookup"
    }

    Then("the KDoc description is preserved on the typealias") {
      val lookup = generatedFiles.find { it.name == "DinosaurLookup" }.shouldNotBeNull()
      lookup.toString() shouldContain "A lookup of dinosaurs by identifier."
    }

    Then("a schema with additionalProperties: primitive generates a typealias to Map<String, Primitive>") {
      val scores = generatedFiles.find { it.name == "StringScores" }.shouldNotBeNull()
      scores.toString() shouldContain "typealias StringScores = Map<String, Int>"
    }

    Then("a schema with additionalProperties: true generates a typealias to Map<String, Any>") {
      val freeform = generatedFiles.find { it.name == "FreeformMap" }.shouldNotBeNull()
      freeform.toString() shouldContain "typealias FreeformMap = Map<String, Any>"
    }

    Then("a \$ref to a map schema resolves correctly from a consumer") {
      val herd = generatedFiles.find { it.name == "Herd" }.shouldNotBeNull()
      herd.toString() shouldContain "val members: DinosaurLookup"
    }
  }

  Given("a map schema whose values use formats that require a Kotlin opt-in") {
    val schema = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema(
      type = "object",
      additionalProperties = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties.Typed(
          uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema(type = "string", format = "uuid")
      ),
    )
    val typeResolver = TypeResolver(GeneratorConfig())
    val mapGenerator = MapTypealiasGenerator(GeneratorConfig(), typeResolver)

    When("the typealias is generated") {
      val code = mapGenerator.generate("DinosaurIds", schema).toString()

      Then("a file-level @OptIn marker is emitted so the typealias compiles on Kotlin 2.0–2.1.x") {
        code shouldContain "@file:OptIn"
        code shouldContain "ExperimentalUuidApi::class"
      }
    }
  }

  Given("a map schema whose values are date-time") {
    val schema = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema(
      type = "object",
      additionalProperties = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.AdditionalProperties.Typed(
          uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema(type = "string", format = "date-time")
      ),
    )
    val typeResolver = TypeResolver(GeneratorConfig())
    val mapGenerator = MapTypealiasGenerator(GeneratorConfig(), typeResolver)

    When("the typealias is generated") {
      val code = mapGenerator.generate("DiscoveryTimes", schema).toString()

      Then("a file-level @OptIn(ExperimentalTime::class) is emitted") {
        code shouldContain "ExperimentalTime::class"
      }
    }
  }
})
