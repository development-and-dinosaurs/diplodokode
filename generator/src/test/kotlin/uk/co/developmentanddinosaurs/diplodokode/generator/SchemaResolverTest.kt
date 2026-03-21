package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

class SchemaResolverTest : BehaviorSpec({

  val resolver = SchemaResolver(GeneratorConfig())

  Given("a schema without allOf") {
    val schema = Schema(type = "object", properties = mapOf("name" to Schema(type = "string")))
    val schemas = mapOf("Dinosaur" to schema)

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("it returns the schema unchanged") {
        resolved.schemas["Dinosaur"] shouldBe schema
      }
    }
  }

  Given("a schema with allOf containing only inline schemas") {
    val schemas = mapOf(
        "ExtendedDinosaur" to Schema(
            allOf = listOf(
                Schema(
                    type = "object",
                    properties = mapOf("name" to Schema(type = "string")),
                    required = listOf("name"),
                ),
                Schema(
                    type = "object",
                    properties = mapOf("armLength" to Schema(type = "number")),
                    required = listOf("armLength"),
                ),
            )
        )
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val schema = resolved.schemas["ExtendedDinosaur"].shouldNotBeNull()

      Then("it produces an object schema") {
        schema.type shouldBe "object"
      }

      Then("it merges properties from all allOf items") {
        schema.properties!! shouldContainKey "name"
        schema.properties shouldContainKey "armLength"
      }

      Then("it merges required fields from all allOf items") {
        schema.required shouldBe listOf("name", "armLength")
      }

      Then("allOf is no longer present") {
        schema.allOf shouldBe null
      }
    }
  }

  Given("a schema with allOf referencing another schema") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            type = "object",
            properties = mapOf("name" to Schema(type = "string")),
            required = listOf("name"),
        ),
        "ExtendedDinosaur" to Schema(
            allOf = listOf(
                Schema(ref = "#/components/schemas/Dinosaur"),
                Schema(
                    type = "object",
                    properties = mapOf("armLength" to Schema(type = "number")),
                    required = listOf("armLength"),
                ),
            )
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val schema = resolved.schemas["ExtendedDinosaur"].shouldNotBeNull()

      Then("it resolves the ref and merges its properties") {
        schema.properties!! shouldContainKey "name"
        schema.properties shouldContainKey "armLength"
      }

      Then("it merges required fields from both the ref and inline schema") {
        schema.required shouldBe listOf("name", "armLength")
      }
    }
  }

  Given("a schema with allOf and additional properties declared directly on the wrapper") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            type = "object",
            properties = mapOf("name" to Schema(type = "string")),
            required = listOf("name"),
        ),
        "ExtendedDinosaur" to Schema(
            properties = mapOf("favouriteFood" to Schema(type = "string")),
            required = listOf("favouriteFood"),
            allOf = listOf(
                Schema(ref = "#/components/schemas/Dinosaur"),
                Schema(
                    type = "object",
                    properties = mapOf("armLength" to Schema(type = "number")),
                    required = listOf("armLength"),
                ),
            ),
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val schema = resolved.schemas["ExtendedDinosaur"].shouldNotBeNull()

      Then("it includes properties from the allOf entries") {
        schema.properties!! shouldContainKey "name"
        schema.properties shouldContainKey "armLength"
      }

      Then("it includes properties declared directly on the wrapper schema") {
        schema.properties!! shouldContainKey "favouriteFood"
      }

      Then("it merges required fields from the wrapper and all allOf entries") {
        schema.required!! shouldContain "name"
        schema.required shouldContain "armLength"
        schema.required shouldContain "favouriteFood"
      }
    }
  }

  Given("a schema with allOf where the same required field appears in multiple items") {
    val schemas = mapOf(
        "Thing" to Schema(
            allOf = listOf(
                Schema(type = "object", properties = mapOf("id" to Schema(type = "string")), required = listOf("id")),
                Schema(type = "object", properties = mapOf("id" to Schema(type = "string"), "name" to Schema(type = "string")), required = listOf("id", "name")),
            )
        )
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val schema = resolved.schemas["Thing"].shouldNotBeNull()

      Then("it deduplicates required fields") {
        schema.required shouldBe listOf("id", "name")
      }
    }
  }

  Given("a schema with allOf containing an unresolvable ref") {
    val schemas = mapOf(
        "Thing" to Schema(
            allOf = listOf(
                Schema(ref = "#/components/schemas/Missing"),
                Schema(type = "object", properties = mapOf("name" to Schema(type = "string"))),
            )
        )
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val schema = resolved.schemas["Thing"].shouldNotBeNull()

      Then("it skips the unresolvable ref and merges what it can") {
        schema.properties!! shouldContainKey "name"
      }
    }
  }

  Given("a schema with a cyclic allOf reference") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            oneOf = listOf(
                Schema(ref = "#/components/schemas/Tyrannosaur"),
            )
        ),
        "Tyrannosaur" to Schema(
            allOf = listOf(
                Schema(ref = "#/components/schemas/Dinosaur"),
                Schema(type = "object", properties = mapOf("armLength" to Schema(type = "number"))),
            )
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("it does not loop infinitely") {
        resolved.schemas shouldContainKey "Tyrannosaur"
      }

      Then("it merges what it can from the non-cyclic parts") {
        resolved.schemas["Tyrannosaur"]?.properties!! shouldContainKey "armLength"
      }
    }
  }

  Given("a schema with oneOf variants") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            oneOf = listOf(
                Schema(ref = "#/components/schemas/Tyrannosaur"),
                Schema(ref = "#/components/schemas/Triceratops"),
            )
        ),
        "Tyrannosaur" to Schema(type = "object", properties = mapOf("armLength" to Schema(type = "number"))),
        "Triceratops" to Schema(type = "object", properties = mapOf("hornCount" to Schema(type = "integer"))),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("Tyrannosaur is mapped to implement Dinosaur") {
        resolved.implementedInterfaces["Tyrannosaur"]!! shouldContain "Dinosaur"
      }

      Then("Triceratops is mapped to implement Dinosaur") {
        resolved.implementedInterfaces["Triceratops"]!! shouldContain "Dinosaur"
      }

      Then("Dinosaur itself has no implemented interfaces") {
        resolved.implementedInterfaces["Dinosaur"] shouldBe null
      }
    }
  }

  Given("a schema with anyOf variants") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            anyOf = listOf(
                Schema(ref = "#/components/schemas/Tyrannosaur"),
                Schema(ref = "#/components/schemas/Triceratops"),
            )
        ),
        "Tyrannosaur" to Schema(type = "object", properties = mapOf("armLength" to Schema(type = "number"))),
        "Triceratops" to Schema(type = "object", properties = mapOf("hornCount" to Schema(type = "integer"))),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("Tyrannosaur is mapped to implement Dinosaur") {
        resolved.implementedInterfaces["Tyrannosaur"]!! shouldContain "Dinosaur"
      }

      Then("Triceratops is mapped to implement Dinosaur") {
        resolved.implementedInterfaces["Triceratops"]!! shouldContain "Dinosaur"
      }
    }
  }

  Given("a oneOf schema with discriminator where only some variants have the discriminator property") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            oneOf = listOf(
                Schema(ref = "#/components/schemas/Tyrannosaur"),
                Schema(ref = "#/components/schemas/Triceratops"),
            ),
            discriminator = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator("type"),
        ),
        "Tyrannosaur" to Schema(
            type = "object",
            properties = mapOf(
                "type" to Schema(type = "string", enum = listOf("tyrannosaur")),
                "armLength" to Schema(type = "number"),
            ),
        ),
        "Triceratops" to Schema(
            type = "object",
            properties = mapOf(
                "hornCount" to Schema(type = "integer"),
                // no 'type' property
            ),
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("no discriminator enum is produced") {
        resolved.discriminatorEnums["Dinosaur"] shouldBe null
      }

      Then("no discriminator overrides are produced for any variant") {
        resolved.discriminatorOverrides["Tyrannosaur"] shouldBe null
        resolved.discriminatorOverrides["Triceratops"] shouldBe null
      }

      Then("both variants still implement the interface") {
        resolved.implementedInterfaces["Tyrannosaur"]!! shouldContain "Dinosaur"
        resolved.implementedInterfaces["Triceratops"]!! shouldContain "Dinosaur"
      }
    }
  }

  Given("a oneOf schema with discriminator values containing hyphens") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            oneOf = listOf(
                Schema(ref = "#/components/schemas/ForestDweller"),
                Schema(ref = "#/components/schemas/SwampDweller"),
            ),
            discriminator = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator("habitatType"),
        ),
        "ForestDweller" to Schema(
            type = "object",
            properties = mapOf(
                "habitatType" to Schema(type = "string", enum = listOf("forest-habitat")),
            ),
        ),
        "SwampDweller" to Schema(
            type = "object",
            properties = mapOf(
                "habitatType" to Schema(type = "string", enum = listOf("swamp-habitat")),
            ),
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val enum = resolved.discriminatorEnums["Dinosaur"].shouldNotBeNull()

      Then("hyphens are replaced with underscores in enum constants") {
        enum.constants shouldContain "FOREST_HABITAT"
        enum.constants shouldContain "SWAMP_HABITAT"
      }

      Then("discriminator overrides use the sanitized constant") {
        resolved.discriminatorOverrides["ForestDweller"]!!.single().constant shouldBe "FOREST_HABITAT"
        resolved.discriminatorOverrides["SwampDweller"]!!.single().constant shouldBe "SWAMP_HABITAT"
      }
    }
  }

  Given("a oneOf schema with a discriminator value starting with a digit") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            oneOf = listOf(
                Schema(ref = "#/components/schemas/Biped"),
                Schema(ref = "#/components/schemas/Quadruped"),
            ),
            discriminator = uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Discriminator("locomotion"),
        ),
        "Biped" to Schema(
            type = "object",
            properties = mapOf(
                "locomotion" to Schema(type = "string", enum = listOf("2-legged")),
            ),
        ),
        "Quadruped" to Schema(
            type = "object",
            properties = mapOf(
                "locomotion" to Schema(type = "string", enum = listOf("4-legged")),
            ),
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)
      val enum = resolved.discriminatorEnums["Dinosaur"].shouldNotBeNull()

      Then("digit-leading constants are prefixed with an underscore") {
        enum.constants shouldContain "_2_LEGGED"
        enum.constants shouldContain "_4_LEGGED"
      }
    }
  }

  Given("a variant that participates in two discriminated oneOf hierarchies") {
    val tyrannosaur = Schema(
      type = "object",
      properties = mapOf(
        "dinosaurType" to Schema(type = "string", enum = listOf("tyrannosaur")),
        "predatorType" to Schema(type = "string", enum = listOf("biped")),
      ),
    )
    val schemas = mapOf(
      "Tyrannosaur" to tyrannosaur,
      "Dinosaur" to Schema(
        oneOf = listOf(Schema(ref = "#/components/schemas/Tyrannosaur")),
        discriminator = Discriminator("dinosaurType"),
      ),
      "Predator" to Schema(
        oneOf = listOf(Schema(ref = "#/components/schemas/Tyrannosaur")),
        discriminator = Discriminator("predatorType"),
      ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("both discriminator overrides are preserved for the variant") {
        val overrides = resolved.discriminatorOverrides["Tyrannosaur"]
        overrides.shouldNotBeNull()
        overrides.size shouldBe 2
        overrides.map { it.interfaceName } shouldContainExactlyInAnyOrder listOf("Dinosaur", "Predator")
      }
    }
  }

  Given("a schema with oneOf inline variants") {
    val schemas = mapOf(
        "Dinosaur" to Schema(
            oneOf = listOf(
                Schema(type = "object", properties = mapOf("armLength" to Schema(type = "number"))),
            )
        ),
    )

    When("the resolver processes the schemas") {
      val resolved = resolver.resolve(schemas)

      Then("no interface mappings are produced for inline variants") {
        resolved.implementedInterfaces.isEmpty() shouldBe true
      }
    }
  }
})
