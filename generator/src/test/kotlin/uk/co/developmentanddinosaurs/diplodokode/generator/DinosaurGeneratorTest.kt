package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class DinosaurGeneratorTest : BehaviorSpec({
  
  val generator = DiplodokodeGenerator()

  Given("an OpenAPI spec with a dinosaur schema") {
    val openApiSpec = File("src/test/resources/dinosaur-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)
      
      Then("it should generate a dinosaur data class") {
        val dinosaurFile = generatedFiles.find { it.name.contains("Dinosaur") }
        dinosaurFile shouldNotBe null
      }
      
      Then("it should handle required vs optional fields correctly") {
        val dinosaurFile = generatedFiles.find { it.name.contains("Dinosaur") }!!
        val generatedCode = dinosaurFile.toString()

        generatedCode shouldContain "val name: String"
        generatedCode shouldContain "val species: String"
        generatedCode shouldContain "val age: Int"

        generatedCode shouldContain "val weight: Double?"
        generatedCode shouldContain "val isCarnivore: Boolean?"
      }
      
      Then("it should include proper KDoc documentation") {
        val generatedCode = generatedFiles.first().toString()
        generatedCode shouldContain "/**"
        generatedCode shouldContain "The name of the dinosaur"
        generatedCode shouldContain "The species of the dinosaur"
        generatedCode shouldContain "The age of the dinosaur in years"
        generatedCode shouldContain "The weight of the dinosaur in kilograms"
        generatedCode shouldContain "Whether the dinosaur is carnivore"
      }
    }
  }
  
  Given("an OpenAPI spec with a top-level enum and a \$ref property") {
    val openApiSpec = File("src/test/resources/ref-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("it should generate a file for each schema") {
        generatedFiles shouldHaveSize 2
      }

      Then("it should generate the top-level enum as an enum class") {
        val dietFile = generatedFiles.find { it.name == "Diet" }
        dietFile shouldNotBe null
        dietFile.toString() shouldContain "enum class Diet"
      }

      Then("it should resolve the \$ref to the correct Kotlin type") {
        val dinosaurFile = generatedFiles.find { it.name == "Dinosaur" }!!
        dinosaurFile.toString() shouldContain "val diet: Diet"
      }
    }
  }

  Given("an OpenAPI spec with an empty schema") {
    val openApiSpec = File("src/test/resources/empty-class-api.yaml")
    val generator = DiplodokodeGenerator()
    
    When("the generator processes the empty spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)
      
      Then("it should generate one empty data class") {
        generatedFiles shouldHaveSize 1
      }
      
      Then("it should generate an empty dinosaur class") {
        val emptyClassFile = generatedFiles.find { it.name.contains("EmptyDinosaur") }
        emptyClassFile shouldNotBe null
        
        val generatedCode = emptyClassFile.toString()
        generatedCode shouldContain "data class EmptyDinosaur"

        // Should not contain any properties
        generatedCode shouldNotContain "val "
        generatedCode shouldNotContain "constructor"
      }
    }
  }
})
