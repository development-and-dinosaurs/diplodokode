package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class PropertyUnionGeneratorTest : BehaviorSpec({

  val generator = DiplodokodeGenerator(GeneratorConfig())

  Given("a spec with property-level oneOf, anyOf, and allOf") {
    val openApiSpec = File("src/test/resources/property-union-api.yaml")
    val generatedFiles = generator.generateFromSpec(openApiSpec)
    val herd = generatedFiles.find { it.name == "Herd" }
    herd shouldNotBe null
    val herdCode = herd!!.toString()

    Then("a property-level oneOf with variants sharing a common sealed interface resolves to that interface") {
      herdCode shouldContain "val alpha: Dinosaur"
      herdCode shouldNotContain "val alpha: String"
      herdCode shouldNotContain "val alpha: Any"
    }

    Then("an array of oneOf variants sharing a common parent resolves to List<CommonInterface>") {
      herdCode shouldContain "val members: List<Dinosaur>"
    }

    Then("a property-level allOf with a single \$ref resolves to the referenced type") {
      herdCode shouldContain "val escort: Pack"
    }
  }
})
