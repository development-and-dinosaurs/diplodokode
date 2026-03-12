@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package uk.co.developmentanddinosaurs.diplodokode.generator.fixtures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Fixtures that mirror the exact output the generator produces from discriminator-serialisation-api.yaml
 * when [uk.co.developmentanddinosaurs.diplodokode.generator.KotlinxSerialisationStrategy] is configured.
 *
 * These are used by [uk.co.developmentanddinosaurs.diplodokode.generator.SerialisationIntegrationTest]
 * to exercise real kotlinx.serialization polymorphic encode/decode without needing to compile
 * generated code at test runtime.
 */

@Serializable
@JsonClassDiscriminator("type")
sealed interface Sauropod

@Serializable
@SerialName("diplodocus")
data class Diplodocus(
    val neckLength: Double,
) : Sauropod

@Serializable
@SerialName("brachiosaurus")
data class Brachiosaurus(
    val foreLegLength: Double,
) : Sauropod
