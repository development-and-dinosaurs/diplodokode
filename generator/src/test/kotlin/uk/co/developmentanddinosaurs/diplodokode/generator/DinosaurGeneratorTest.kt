package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class DinosaurGeneratorTest : BehaviorSpec({
  
  val generator = DiplodokodeGenerator(GeneratorConfig())

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

  Given("an OpenAPI spec with typed array properties") {
    val openApiSpec = File("src/test/resources/array-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("it should generate a file for each schema") {
        generatedFiles shouldHaveSize 2
      }

      Then("required array of strings should be List<String>") {
        val dinosaurFile = generatedFiles.find { it.name == "Dinosaur" }!!
        dinosaurFile.toString() shouldContain "val tags: List<String>"
      }

      Then("optional array of refs should be nullable List<Tag>") {
        val dinosaurFile = generatedFiles.find { it.name == "Dinosaur" }!!
        dinosaurFile.toString() shouldContain "val relatedDinosaurs: List<Tag>?"
      }
    }
  }

  Given("an OpenAPI spec with format-mapped properties") {
    val openApiSpec = File("src/test/resources/format-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)
      val code = generatedFiles.find { it.name == "Dinosaur" }!!.toString()

      Then("uuid format maps to Uuid") {
        code shouldContain "val id: Uuid"
      }

      Then("date-time format maps to Instant") {
        code shouldContain "val createdAt: Instant"
      }

      Then("date format maps to LocalDate") {
        code shouldContain "val birthDate: LocalDate"
      }

      Then("int64 format maps to Long") {
        code shouldContain "val populationCount: Long"
      }
    }
  }

  Given("an OpenAPI spec with allOf schemas") {
    val openApiSpec = File("src/test/resources/allof-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("it should generate a file for each schema") {
        generatedFiles shouldHaveSize 2
      }

      Then("the extended schema should include properties from the referenced schema") {
        val extendedFile = generatedFiles.find { it.name == "ExtendedDinosaur" }!!
        val code = extendedFile.toString()
        code shouldContain "val name: String"
        code shouldContain "val age: Int"
      }

      Then("the extended schema should include its own properties") {
        val extendedFile = generatedFiles.find { it.name == "ExtendedDinosaur" }!!
        val code = extendedFile.toString()
        code shouldContain "val armLength: Double"
        code shouldContain "val favouriteFood: String?"
      }

      Then("required fields from the referenced schema should be non-nullable") {
        val extendedFile = generatedFiles.find { it.name == "ExtendedDinosaur" }!!
        extendedFile.toString() shouldContain "val name: String"
      }

      Then("the extended schema does not inherit from the base schema") {
        val extendedFile = generatedFiles.find { it.name == "ExtendedDinosaur" }!!
        extendedFile.toString() shouldNotContain ": Dinosaur"
      }
    }
  }

  Given("an OpenAPI spec with oneOf schemas") {
    val openApiSpec = File("src/test/resources/oneof-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("it should generate a file for each schema") {
        generatedFiles shouldHaveSize 3
      }

      Then("the parent schema should be a sealed interface") {
        val dinosaurFile = generatedFiles.find { it.name == "Dinosaur" }!!
        dinosaurFile.toString() shouldContain "sealed interface Dinosaur"
      }

      Then("the sealed interface should have a typed discriminator property and nested enum") {
        val code = generatedFiles.find { it.name == "Dinosaur" }!!.toString()
        code shouldContain "val type: Type"
        code shouldContain "enum class Type"
        code shouldContain "TYRANNOSAUR"
        code shouldContain "TRICERATOPS"
      }

      Then("each variant should be a data class implementing the sealed interface") {
        val tyrannosaurCode = generatedFiles.find { it.name == "Tyrannosaur" }!!.toString()
        tyrannosaurCode shouldContain "data class Tyrannosaur"
        tyrannosaurCode shouldContain ": Dinosaur"

        val triceratopsCode = generatedFiles.find { it.name == "Triceratops" }!!.toString()
        triceratopsCode shouldContain "data class Triceratops"
        triceratopsCode shouldContain ": Dinosaur"
      }

      Then("each variant should override the discriminator property with a typed default") {
        val tyrannosaurCode = generatedFiles.find { it.name == "Tyrannosaur" }!!.toString()
        tyrannosaurCode shouldContain "override val type: Dinosaur.Type"
        tyrannosaurCode shouldContain "Dinosaur.Type.TYRANNOSAUR"

        val triceratopsCode = generatedFiles.find { it.name == "Triceratops" }!!.toString()
        triceratopsCode shouldContain "override val type: Dinosaur.Type"
        triceratopsCode shouldContain "Dinosaur.Type.TRICERATOPS"
      }
    }
  }

  Given("an OpenAPI spec with anyOf schemas") {
    val openApiSpec = File("src/test/resources/anyof-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("the parent schema should be a sealed interface") {
        val dinosaurFile = generatedFiles.find { it.name == "Dinosaur" }!!
        dinosaurFile.toString() shouldContain "sealed interface Dinosaur"
      }

      Then("the sealed interface should note that one or more variants may be used") {
        val dinosaurFile = generatedFiles.find { it.name == "Dinosaur" }!!
        dinosaurFile.toString() shouldContain "One or more of the following variants may be used"
      }

      Then("each variant should be a data class implementing the sealed interface") {
        val tyrannosaurFile = generatedFiles.find { it.name == "Tyrannosaur" }!!
        tyrannosaurFile.toString() shouldContain ": Dinosaur"

        val triceratopsFile = generatedFiles.find { it.name == "Triceratops" }!!
        triceratopsFile.toString() shouldContain ": Dinosaur"
      }
    }
  }

  Given("an OpenAPI spec with the cyclic allOf + oneOf inheritance-simulation pattern") {
    val openApiSpec = File("src/test/resources/allof-cyclic-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("it should generate a file for each schema") {
        generatedFiles shouldHaveSize 3
      }

      Then("the parent schema should be a sealed interface") {
        generatedFiles.find { it.name == "Dinosaur" }!!.toString() shouldContain "sealed interface Dinosaur"
      }

      Then("the sealed interface should declare shared properties as abstract vals") {
        val dinosaurCode = generatedFiles.find { it.name == "Dinosaur" }!!.toString()
        dinosaurCode shouldContain "val name: String"
      }

      Then("each variant should be a data class implementing the sealed interface") {
        generatedFiles.find { it.name == "Tyrannosaur" }!!.toString() shouldContain ": Dinosaur"
        generatedFiles.find { it.name == "Triceratops" }!!.toString() shouldContain ": Dinosaur"
      }

      Then("each variant should override the discriminator property with a typed default") {
        val tyrannosaurCode = generatedFiles.find { it.name == "Tyrannosaur" }!!.toString()
        tyrannosaurCode shouldContain "override val type: Dinosaur.Type"
        tyrannosaurCode shouldContain "Dinosaur.Type.TYRANNOSAUR"

        val triceratopsCode = generatedFiles.find { it.name == "Triceratops" }!!.toString()
        triceratopsCode shouldContain "override val type: Dinosaur.Type"
        triceratopsCode shouldContain "Dinosaur.Type.TRICERATOPS"
      }

      Then("each variant should override the shared interface properties") {
        generatedFiles.find { it.name == "Tyrannosaur" }!!.toString() shouldContain "override val name: String"
        generatedFiles.find { it.name == "Triceratops" }!!.toString() shouldContain "override val name: String"
      }

      Then("each variant should have its own properties") {
        generatedFiles.find { it.name == "Tyrannosaur" }!!.toString() shouldContain "val armLength: Double"
        generatedFiles.find { it.name == "Triceratops" }!!.toString() shouldContain "val hornCount: Int"
      }
    }
  }

  Given("an OpenAPI spec with a shared enum-constrained property on the oneOf parent schema") {
    val openApiSpec = File("src/test/resources/shared-enum-prop-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("the sealed interface declares the shared enum property as String with a KDoc warning") {
        val dinosaurCode = generatedFiles.find { it.name == "Dinosaur" }!!.toString()
        dinosaurCode shouldContain "val era: String"
        dinosaurCode shouldNotContain "enum class Era"
        dinosaurCode shouldContain "NOTE:"
        dinosaurCode shouldContain "\$ref"
      }

      Then("implementing data classes override the shared property as String, not a local enum type") {
        val tyrannosaurCode = generatedFiles.find { it.name == "Tyrannosaur" }!!.toString()
        tyrannosaurCode shouldContain "override val era: String"
        tyrannosaurCode shouldNotContain "enum class Era"

        val triceratopsCode = generatedFiles.find { it.name == "Triceratops" }!!.toString()
        triceratopsCode shouldContain "override val era: String"
        triceratopsCode shouldNotContain "enum class Era"
      }
    }
  }

  Given("an OpenAPI spec with a oneOf discriminator where only some variants carry the discriminator property") {
    val openApiSpec = File("src/test/resources/partial-discriminator-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)
      val dinosaurCode = generatedFiles.find { it.name == "Dinosaur" }!!.toString()

      Then("it should still generate a sealed interface") {
        dinosaurCode shouldContain "sealed interface Dinosaur"
      }

      Then("the discriminator falls back to abstract val type: String") {
        dinosaurCode shouldContain "val type: String"
      }

      Then("a KDoc warning is emitted on the sealed interface") {
        dinosaurCode shouldContain "Warning:"
        dinosaurCode shouldContain "type"
        dinosaurCode shouldContain "not all variants"
      }

      Then("both variants still implement the sealed interface") {
        generatedFiles.find { it.name == "Tyrannosaur" }!!.toString() shouldContain ": Dinosaur"
        generatedFiles.find { it.name == "Triceratops" }!!.toString() shouldContain ": Dinosaur"
      }
    }
  }

  Given("an OpenAPI spec with separator-containing schema names") {
    val openApiSpec = File("src/test/resources/separator-names-api.yaml")

    When("the generator processes the spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)

      Then("hyphenated schema names are transformed to PascalCase class names") {
        generatedFiles.find { it.name == "FossilRecord" } shouldNotBe null
        generatedFiles.find { it.name == "BoneSpecimen" } shouldNotBe null
      }

      Then("\$ref to a hyphenated schema resolves to the transformed Kotlin type") {
        val code = generatedFiles.find { it.name == "FossilRecord" }!!.toString()
        code shouldContain "val specimen: BoneSpecimen"
        code shouldNotContain "bone-specimen"
      }

      Then("hyphenated oneOf parent becomes a sealed interface with a PascalCase name") {
        generatedFiles.find { it.name == "PackHunter" }!!.toString() shouldContain "sealed interface PackHunter"
      }

      Then("hyphenated variants implement the sealed interface using its transformed name") {
        val velociRaptorCode = generatedFiles.find { it.name == "VelociRaptor" }!!.toString()
        velociRaptorCode shouldContain "data class VelociRaptor"
        velociRaptorCode shouldContain ": PackHunter"
        velociRaptorCode shouldNotContain "pack-hunter"

        val trooDonCode = generatedFiles.find { it.name == "TrooDon" }!!.toString()
        trooDonCode shouldContain "data class TrooDon"
        trooDonCode shouldContain ": PackHunter"
      }

      Then("the discriminator enum type on a variant uses the transformed interface name") {
        val velociRaptorCode = generatedFiles.find { it.name == "VelociRaptor" }!!.toString()
        velociRaptorCode shouldContain "override val type: PackHunter.Type"
        velociRaptorCode shouldNotContain "pack-hunter"
      }
    }
  }

  Given("an OpenAPI spec with an empty schema") {
    val openApiSpec = File("src/test/resources/empty-class-api.yaml")

    When("the generator processes the empty spec") {
      val generatedFiles = generator.generateFromSpec(openApiSpec)
      
      Then("it should generate one empty data class") {
        generatedFiles shouldHaveSize 1
      }
      
      Then("it should generate an empty dinosaur class") {
        val emptyClassFile = generatedFiles.find { it.name.contains("Dinosaur") }
        emptyClassFile shouldNotBe null
        
        val generatedCode = emptyClassFile.toString()
        generatedCode shouldContain "data class Dinosaur"

        generatedCode shouldNotContain "val "
        generatedCode shouldNotContain "constructor"
      }
    }
  }
})
