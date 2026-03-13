package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SerializersModuleGeneratorTest : BehaviorSpec({

  fun generator(config: GeneratorConfig = GeneratorConfig()) = SerializersModuleGenerator(config)

  Given("an empty interface-variants map") {
    When("the generator is called") {
      val result = generator().generate(emptyMap())

      Then("it returns null") {
        result shouldBe null
      }
    }
  }

  Given("a single sealed interface with two variants") {
    val interfaceVariants = mapOf("Sauropod" to listOf("Diplodocus", "Brachiosaurus"))

    When("the generator produces the module file") {
      val file = generator().generate(interfaceVariants)!!
      val code = file.toString()

      Then("the file is named DiplodokodeModule") {
        file.name shouldBe "DiplodokodeModule"
      }

      Then("a diplodokodeModule property is declared") {
        code shouldContain "val diplodokodeModule"
      }

      Then("SerializersModule is used as the type") {
        code shouldContain "SerializersModule"
      }

      Then("the interface is registered polymorphically") {
        code shouldContain "polymorphic(Sauropod::class)"
      }

      Then("each variant is registered as a subclass") {
        code shouldContain "subclass(Diplodocus::class)"
        code shouldContain "subclass(Brachiosaurus::class)"
      }
    }
  }

  Given("multiple sealed interfaces") {
    val interfaceVariants = mapOf(
      "Dinosaur" to listOf("Tyrannosaur", "Triceratops"),
      "Sauropod" to listOf("Diplodocus", "Brachiosaurus"),
    )

    When("the generator produces the module file") {
      val code = generator().generate(interfaceVariants)!!.toString()

      Then("all interfaces and their variants are registered") {
        code shouldContain "polymorphic(Dinosaur::class)"
        code shouldContain "subclass(Tyrannosaur::class)"
        code shouldContain "subclass(Triceratops::class)"
        code shouldContain "polymorphic(Sauropod::class)"
        code shouldContain "subclass(Diplodocus::class)"
        code shouldContain "subclass(Brachiosaurus::class)"
      }
    }
  }

  Given("lowercase schema names") {
    val interfaceVariants = mapOf("sauropod" to listOf("diplodocus", "brachiosaurus"))

    When("the generator produces the module file") {
      val code = generator().generate(interfaceVariants)!!.toString()

      Then("class names are pascal-cased by the default naming strategy") {
        code shouldContain "polymorphic(Sauropod::class)"
        code shouldContain "subclass(Diplodocus::class)"
        code shouldContain "subclass(Brachiosaurus::class)"
      }

      Then("lowercase names are not present") {
        code shouldNotContain "polymorphic(sauropod::class)"
      }
    }
  }

  Given("a spec file with a oneOf sealed interface and serialisation enabled") {
    val specGenerator = DiplodokodeGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))

    When("the generator processes discriminator-serialisation-api.yaml") {
      val files = specGenerator.generateFromSpec(java.io.File("src/test/resources/discriminator-serialisation-api.yaml"))

      Then("a DiplodokodeModule file is generated") {
        files.find { it.name == "DiplodokodeModule" } shouldNotBe null
      }

      Then("the module registers Sauropod with its variants") {
        val code = files.find { it.name == "DiplodokodeModule" }!!.toString()
        code shouldContain "polymorphic(Sauropod::class)"
        code shouldContain "subclass(Diplodocus::class)"
        code shouldContain "subclass(Brachiosaurus::class)"
      }
    }
  }

  Given("a spec file with no sealed interfaces and serialisation enabled") {
    val specGenerator = DiplodokodeGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))

    When("the generator processes a plain data class spec") {
      val files = specGenerator.generateFromSpec(java.io.File("src/test/resources/dinosaur-api.yaml"))

      Then("no DiplodokodeModule file is generated") {
        files.find { it.name == "DiplodokodeModule" } shouldBe null
      }
    }
  }

  Given("a spec file with a oneOf sealed interface but no serialisation strategy") {
    val specGenerator = DiplodokodeGenerator(GeneratorConfig())

    When("the generator processes a polymorphic spec") {
      val files = specGenerator.generateFromSpec(java.io.File("src/test/resources/discriminator-serialisation-api.yaml"))

      Then("no DiplodokodeModule file is generated") {
        files.find { it.name == "DiplodokodeModule" } shouldBe null
      }
    }
  }
})
