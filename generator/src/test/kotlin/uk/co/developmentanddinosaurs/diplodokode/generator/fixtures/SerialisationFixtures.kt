package uk.co.developmentanddinosaurs.diplodokode.generator.fixtures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fixtures that mirror the exact output the generator produces from ref-api.yaml when
 * [uk.co.developmentanddinosaurs.diplodokode.generator.KotlinxSerialisationStrategy] is configured.
 *
 * These are used by [uk.co.developmentanddinosaurs.diplodokode.generator.SerialisationIntegrationTest]
 * to exercise real kotlinx.serialization encode/decode without needing to compile generated code at
 * test runtime.
 */

@Serializable
enum class Diet {
    @SerialName("carnivore") CARNIVORE,
    @SerialName("herbivore") HERBIVORE,
}

@Serializable
data class Dinosaur(
    val name: String,
    val diet: Diet,
    val favouritePrey: String?,
)
