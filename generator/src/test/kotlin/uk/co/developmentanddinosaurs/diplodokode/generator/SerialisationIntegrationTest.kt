package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Brachiosaurus
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Diet
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Diplodocus
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Dinosaur
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Sauropod
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Pterodactyl
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Stegosaurus
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.StringOrDouble
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.TagValue
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Tyrannosaur
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.sauropodModule
import java.io.File

/**
 * Integration tests verifying that the serialisation strategy produces annotations that result in
 * correct kotlinx.serialization behaviour.
 *
 * The [Dinosaur] and [Diet] fixture classes in [uk.co.developmentanddinosaurs.diplodokode.generator.fixtures]
 * mirror the exact output the generator produces from ref-api.yaml when [KotlinxSerialisationStrategy]
 * is configured. Tests assert both that the generator emits the expected annotations and that those
 * annotations produce correct encode/decode behaviour at runtime.
 */
class SerialisationIntegrationTest : BehaviorSpec({

    val json = Json { explicitNulls = false; serializersModule = sauropodModule }

    val generator = DiplodokodeGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))

    Given("an OpenAPI spec with a data class and a ref enum, with serialisation enabled") {
        val spec = File("src/test/resources/ref-api.yaml")
        val generatedFiles = generator.generateFromSpec(spec)

        Then("the generated data class is annotated with @Serializable") {
            val dinosaurCode = generatedFiles.find { it.name == "Dinosaur" }!!.toString()
            dinosaurCode shouldContain "@Serializable"
        }

        Then("the generated enum class is annotated with @Serializable") {
            val dietCode = generatedFiles.find { it.name == "Diet" }!!.toString()
            dietCode shouldContain "@Serializable"
        }

        Then("each generated enum constant has a @SerialName matching its spec value") {
            val dietCode = generatedFiles.find { it.name == "Diet" }!!.toString()
            dietCode shouldContain """@SerialName("carnivore")"""
            dietCode shouldContain """@SerialName("herbivore")"""
        }

        When("a model instance is encoded to JSON") {
            val trex = Dinosaur(name = "T-Rex", diet = Diet.CARNIVORE, favouritePrey = "Triceratops")
            val encoded = json.encodeToString(trex)

            Then("enum values appear as spec-defined lowercase strings") {
                encoded shouldContain """"diet":"carnivore""""
            }

            Then("string fields appear with their values") {
                encoded shouldContain """"name":"T-Rex""""
                encoded shouldContain """"favouritePrey":"Triceratops""""
            }

            Then("the decoded instance is equal to the original") {
                val decoded = json.decodeFromString<Dinosaur>(encoded)
                decoded shouldBe trex
            }
        }

        When("a model with a null optional field is encoded") {
            val unknown = Dinosaur(name = "Unknown", diet = Diet.HERBIVORE, favouritePrey = null)
            val encoded = json.encodeToString(unknown)

            Then("the null field is omitted from the JSON output") {
                encoded shouldNotContain "favouritePrey"
            }

            Then("the decoded instance is equal to the original") {
                val decoded = json.decodeFromString<Dinosaur>(encoded)
                decoded shouldBe unknown
            }
        }

        When("a JSON payload using spec-value enum names is decoded") {
            val specJson = """{"name":"Dippy","diet":"herbivore"}"""
            val decoded = json.decodeFromString<Dinosaur>(specJson)

            Then("the enum field maps to the correct Kotlin constant") {
                decoded.diet shouldBe Diet.HERBIVORE
            }

            Then("the null optional field is absent") {
                decoded.favouritePrey shouldBe null
            }
        }
    }

    Given("an OpenAPI spec with snake_case property names and serialisation enabled") {
        val spec = File("src/test/resources/snake-case-api.yaml")
        val generatedFiles = generator.generateFromSpec(spec)

        Then("the generated class has @SerialName on each transformed property") {
            val code = generatedFiles.find { it.name == "Tyrannosaur" }!!.toString()
            code shouldContain """@SerialName("arm_length")"""
            code shouldContain """@SerialName("hunting_territory")"""
            code shouldContain """@SerialName("favourite_prey")"""
        }

        When("a model instance is encoded to JSON") {
            val tyrannosaur = Tyrannosaur(armLength = 0.8, huntingTerritory = "Montana", favouritePrey = null)
            val encoded = json.encodeToString(tyrannosaur)

            Then("property names in JSON match the spec's snake_case keys") {
                encoded shouldContain """"arm_length":0.8"""
                encoded shouldContain """"hunting_territory":"Montana""""
            }

            Then("camelCase Kotlin names are absent from the JSON") {
                encoded shouldNotContain "armLength"
                encoded shouldNotContain "huntingTerritory"
            }

            Then("the decoded instance is equal to the original") {
                val decoded = json.decodeFromString<Tyrannosaur>(encoded)
                decoded shouldBe tyrannosaur
            }
        }

        When("a JSON payload with snake_case keys is decoded") {
            val specJson = """{"arm_length":1.2,"hunting_territory":"Alberta","favourite_prey":"Edmontosaurus"}"""
            val decoded = json.decodeFromString<Tyrannosaur>(specJson)

            Then("fields map to the correct Kotlin properties") {
                decoded.armLength shouldBe 1.2
                decoded.huntingTerritory shouldBe "Alberta"
                decoded.favouritePrey shouldBe "Edmontosaurus"
            }
        }
    }

    Given("an OpenAPI spec with oneOf and a discriminator, with serialisation enabled") {
        val spec = File("src/test/resources/discriminator-serialisation-api.yaml")
        val generatedFiles = generator.generateFromSpec(spec)

        Then("the sealed interface is annotated with @Serializable") {
            val code = generatedFiles.find { it.name == "Sauropod" }!!.toString()
            code shouldContain "@Serializable"
        }

        Then("a DiplodokodeModule file is generated registering the sealed hierarchy") {
            val moduleCode = generatedFiles.find { it.name == "DiplodokodeModule" }!!.toString()
            moduleCode shouldContain "diplodokodeModule"
            moduleCode shouldContain "SerializersModule"
            moduleCode shouldContain "polymorphic(Sauropod::class)"
            moduleCode shouldContain "subclass(Diplodocus::class)"
            moduleCode shouldContain "subclass(Brachiosaurus::class)"
        }

        Then("no nested Type enum is generated on the sealed interface") {
            val code = generatedFiles.find { it.name == "Sauropod" }!!.toString()
            code shouldNotContain "enum class Type"
        }

        Then("each variant is annotated with @SerialName matching its discriminator value") {
            val diplodocusCode = generatedFiles.find { it.name == "Diplodocus" }!!.toString()
            val brachiosaurusCode = generatedFiles.find { it.name == "Brachiosaurus" }!!.toString()
            diplodocusCode shouldContain """@SerialName("diplodocus")"""
            brachiosaurusCode shouldContain """@SerialName("brachiosaurus")"""
        }

        Then("variant data classes do not contain the discriminator property") {
            val diplodocusCode = generatedFiles.find { it.name == "Diplodocus" }!!.toString()
            diplodocusCode shouldNotContain "val type:"
            diplodocusCode shouldNotContain "override val type"
        }

        When("a variant is encoded as the sealed interface type") {
            val diplodocus = Diplodocus(neckLength = 8.5)
            val encoded = json.encodeToString(Sauropod.serializer(), diplodocus)

            Then("the discriminator field appears in the JSON with the spec value") {
                encoded shouldContain """"type":"diplodocus""""
            }

            Then("variant-specific fields are present") {
                encoded shouldContain """"neckLength":8.5"""
            }

            Then("the decoded instance is equal to the original") {
                val decoded = json.decodeFromString(Sauropod.serializer(), encoded)
                decoded shouldBe diplodocus
            }
        }

        When("a JSON payload with the discriminator field is decoded") {
            val specJson = """{"type":"brachiosaurus","foreLegLength":3.2}"""
            val decoded = json.decodeFromString(Sauropod.serializer(), specJson)

            Then("the correct variant type is produced") {
                decoded shouldBe Brachiosaurus(foreLegLength = 3.2)
            }
        }
    }

    Given("an OpenAPI spec with a primitive union property and serialisation enabled") {
        val spec = File("src/test/resources/primitive-union-property-api.yaml")
        val generatedFiles = DiplodokodeGenerator(GeneratorConfig(serialisationStrategy = KotlinxSerialisationStrategy))
            .generateFromSpec(spec)
        val unionCode = generatedFiles.find { it.name == "StringOrDouble" }!!.toString()

        Then("the sealed interface is annotated with @Serializable(with = ...)") {
            unionCode shouldContain "@Serializable(with = StringOrDoubleSerializer::class)"
        }

        Then("the sealed interface contains @JvmInline value class wrappers") {
            unionCode shouldContain "value class StringValue"
            unionCode shouldContain "value class DoubleValue"
        }

        Then("the serializer dispatches each variant to the correct encode call") {
            unionCode shouldContain "is StringOrDouble.StringValue -> encoder.encodeString(value.value)"
            unionCode shouldContain "is StringOrDouble.DoubleValue -> encoder.encodeDouble(value.value)"
        }

        Then("the deserializer guards against non-JSON decoders") {
            unionCode shouldContain "if (decoder !is JsonDecoder) throw SerializationException"
            unionCode shouldContain "Primitive union types only support JSON deserialization"
        }

        Then("the deserializer checks each variant condition and throws for unexpected values") {
            unionCode shouldContain "element.isString -> StringOrDouble.StringValue(element.content)"
            unionCode shouldContain "!element.isString && element.content.toDoubleOrNull() != null -> StringOrDouble.DoubleValue(element.content.toDouble())"
            unionCode shouldContain "else -> throw SerializationException"
        }

        Then("only one StringOrDouble file is generated when two schemas share the union type") {
            generatedFiles.count { it.name == "StringOrDouble" } shouldBe 1
        }

        When("a Stegosaurus with a string score is encoded") {
            val stegosaurus = Stegosaurus(name = "Steggy", score = StringOrDouble.StringValue("excellent"))
            val encoded = json.encodeToString(stegosaurus)

            Then("the score field appears as a JSON string") {
                encoded shouldContain """"score":"excellent""""
            }

            Then("the decoded instance is equal to the original") {
                json.decodeFromString<Stegosaurus>(encoded) shouldBe stegosaurus
            }
        }

        When("a Stegosaurus with a numeric score is encoded") {
            val stegosaurus = Stegosaurus(name = "Steggy", score = StringOrDouble.DoubleValue(9.5))
            val encoded = json.encodeToString(stegosaurus)

            Then("the score field appears as a JSON number") {
                encoded shouldContain """"score":9.5"""
            }

            Then("the decoded instance is equal to the original") {
                json.decodeFromString<Stegosaurus>(encoded) shouldBe stegosaurus
            }
        }

        When("a JSON payload with a string score is decoded") {
            val decoded = json.decodeFromString<Stegosaurus>("""{"name":"Steggy","score":"excellent"}""")

            Then("the score is a StringValue") {
                decoded.score shouldBe StringOrDouble.StringValue("excellent")
            }
        }

        When("a JSON payload with a numeric score is decoded") {
            val decoded = json.decodeFromString<Stegosaurus>("""{"name":"Steggy","score":9.5}""")

            Then("the score is a DoubleValue") {
                decoded.score shouldBe StringOrDouble.DoubleValue(9.5)
            }
        }

        When("a JSON payload with no score field is decoded") {
            val decoded = json.decodeFromString<Stegosaurus>("""{"name":"Steggy"}""")

            Then("the score is null") {
                decoded.score shouldBe null
            }
        }

        When("a JSON payload with a value that matches no variant is decoded") {
            val result = runCatching { json.decodeFromString<Stegosaurus>("""{"name":"Steggy","score":true}""") }

            Then("a SerializationException is thrown rather than NumberFormatException") {
                (result.exceptionOrNull() is SerializationException) shouldBe true
            }
        }

        Then("a Union2 file is generated") {
            generatedFiles.any { it.name == "Union2" } shouldBe true
        }

        Then("Union2 declares an abstract fold function") {
            val code = generatedFiles.find { it.name == "Union2" }!!.toString()
            code shouldContain "interface Union2<A, B>"
            code shouldContain "fun <R> fold("
        }

        Then("Union2 has default firstOrNull and secondOrNull accessors") {
            val code = generatedFiles.find { it.name == "Union2" }!!.toString()
            code shouldContain "fun firstOrNull()"
            code shouldContain "fun secondOrNull()"
        }

        Then("Union2 has default first and second throwing accessors") {
            val code = generatedFiles.find { it.name == "Union2" }!!.toString()
            code shouldContain "fun first()"
            code shouldContain "fun second()"
        }
    }

    Given("a Pterodactyl with a TagValue (string|boolean|integer) fixture") {
        When("a Pterodactyl with a string tag is encoded") {
            val pterodactyl = Pterodactyl(name = "Pterry", tag = TagValue("herbivore"))
            val encoded = json.encodeToString(pterodactyl)

            Then("the tag field appears as a JSON string") {
                encoded shouldContain """"tag":"herbivore""""
            }

            Then("the decoded instance is equal to the original") {
                json.decodeFromString<Pterodactyl>(encoded) shouldBe pterodactyl
            }
        }

        When("a Pterodactyl with a boolean tag is encoded") {
            val pterodactyl = Pterodactyl(name = "Pterry", tag = TagValue(true))
            val encoded = json.encodeToString(pterodactyl)

            Then("the tag field appears as a JSON boolean") {
                encoded shouldContain """"tag":true"""
            }

            Then("the decoded instance is equal to the original") {
                json.decodeFromString<Pterodactyl>(encoded) shouldBe pterodactyl
            }
        }

        When("a Pterodactyl with an integer tag is encoded") {
            val pterodactyl = Pterodactyl(name = "Pterry", tag = TagValue(42))
            val encoded = json.encodeToString(pterodactyl)

            Then("the tag field appears as a JSON number") {
                encoded shouldContain """"tag":42"""
            }

            Then("the decoded instance is equal to the original") {
                json.decodeFromString<Pterodactyl>(encoded) shouldBe pterodactyl
            }
        }

        When("a JSON payload with a string tag is decoded") {
            val decoded = json.decodeFromString<Pterodactyl>("""{"name":"Pterry","tag":"herbivore"}""")

            Then("the tag is a StringValue") {
                decoded.tag shouldBe TagValue.StringValue("herbivore")
            }
        }

        When("a JSON payload with a boolean tag is decoded") {
            val decoded = json.decodeFromString<Pterodactyl>("""{"name":"Pterry","tag":true}""")

            Then("the tag is a BooleanValue") {
                decoded.tag shouldBe TagValue.BooleanValue(true)
            }
        }

        When("a JSON payload with an integer tag is decoded") {
            val decoded = json.decodeFromString<Pterodactyl>("""{"name":"Pterry","tag":42}""")

            Then("the tag is an IntValue") {
                decoded.tag shouldBe TagValue.IntValue(42)
            }
        }
    }

    Given("an OpenAPI spec with Any-typed and Map-typed properties and serialisation enabled") {
        val spec = File("src/test/resources/contextual-api.yaml")
        val generatedFiles = generator.generateFromSpec(spec)
        val code = generatedFiles.find { it.name == "Triceratops" }!!.toString()

        Then("a bare type: object property is annotated with @Contextual") {
            code shouldContain "val metadata: Any?"
        }

        Then("an array-without-items property is annotated with @Contextual") {
            code shouldContain "val rawList: List<Any>?"
        }

        Then("additionalProperties: true generates Map<String, Any> annotated with @Contextual") {
            code shouldContain "val extensions: Map<String, Any>?"
        }

        Then("additionalProperties typed as string generates Map<String, String> without @Contextual") {
            code shouldContain "val attributes: Map<String, String>?"
        }

        Then("additionalProperties typed as integer generates Map<String, Int> without @Contextual") {
            code shouldContain "val scores: Map<String, Int>?"
        }

        Then("@Contextual appears exactly on the Any-containing properties") {
            // attributes (Map<String,String>) and scores (Map<String,Int>) must NOT be preceded by @Contextual
            code shouldNotContain "@Contextual\n  val attributes"
            code shouldNotContain "@Contextual\n  val scores"
        }
    }
})
