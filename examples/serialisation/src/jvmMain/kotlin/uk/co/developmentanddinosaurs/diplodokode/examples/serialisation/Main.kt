package uk.co.developmentanddinosaurs.diplodokode.examples.serialisation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.developmentanddinosaurs.diplodokode.generated.Diet
import uk.co.developmentanddinosaurs.diplodokode.generated.Era
import uk.co.developmentanddinosaurs.diplodokode.generated.Fossil

val json = Json { prettyPrint = true }

fun main() {
    println("=== Diplodokode Serialisation Example ===")
    println()

    val specimens = listOf(
        Fossil(
            id = "NHM-001",
            name = "Sue",
            era = Era.CRETACEOUS,
            diet = Diet.CARNIVORE,
            discoveryYear = 1990,
            discoveryLocation = "South Dakota, USA",
            completeness = 90.0,
        ),
        Fossil(
            id = "NHM-002",
            name = "Dippy",
            era = Era.JURASSIC,
            diet = Diet.HERBIVORE,
            discoveryYear = 1899,
            discoveryLocation = "Wyoming, USA",
            completeness = 70.0,
        ),
        Fossil(
            id = "NHM-003",
            name = "Stan",
            era = Era.CRETACEOUS,
            diet = Diet.CARNIVORE,
            discoveryYear = 1987,
            discoveryLocation = null,
            completeness = null,
        ),
    )

    println("--- Encoding to JSON ---")
    specimens.forEach { fossil ->
        val encoded = json.encodeToString(fossil)
        println(encoded)
        println()
    }

    println("--- Round-trip encode → decode ---")
    val sue = specimens.first()
    val encodedSue = json.encodeToString(sue)
    val decodedSue = json.decodeFromString<Fossil>(encodedSue)
    println("Original:  $sue")
    println("Re-decoded: $decodedSue")
    println("Match: ${sue == decodedSue}")
    println()
    Era.entries.forEach { era ->
        println("  ${era.name} -> ${json.encodeToString(era)}")
    }
}
