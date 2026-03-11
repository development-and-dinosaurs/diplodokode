package uk.co.developmentanddinosaurs.diplodokode.generator

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Diet
import uk.co.developmentanddinosaurs.diplodokode.generator.fixtures.Dinosaur
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

    val json = Json { explicitNulls = false }

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
})
