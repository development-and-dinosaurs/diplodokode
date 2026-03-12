package uk.co.developmentanddinosaurs.diplodokode.examples.serialisation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.developmentanddinosaurs.diplodokode.generated.Diet
import uk.co.developmentanddinosaurs.diplodokode.generated.Era
import uk.co.developmentanddinosaurs.diplodokode.generated.Fossil

val json = Json {
    prettyPrint = true
    explicitNulls = false  // nullable fields may be omitted from JSON input
}

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
            completenessPercent = 90.0,
        ),
        Fossil(
            id = "NHM-002",
            name = "Dippy",
            era = Era.JURASSIC,
            diet = Diet.HERBIVORE,
            discoveryYear = 1899,
            discoveryLocation = "Wyoming, USA",
            completenessPercent = 70.0,
        ),
        Fossil(
            id = "NHM-003",
            name = "Stan",
            era = Era.CRETACEOUS,
            diet = Diet.CARNIVORE,
            discoveryYear = 1987,
            discoveryLocation = null,
            completenessPercent = null,
        ),
    )

    println("--- Encoding to JSON ---")
    println("Spec property names are snake_case; Kotlin properties are camelCase.")
    println("@SerialName maps them back to the spec wire format automatically.")
    println()
    specimens.forEach { fossil ->
        println(json.encodeToString(fossil))
        println()
    }

    println("--- Round-trip encode → decode ---")
    val sue = specimens.first()
    val encodedSue = json.encodeToString(sue)
    val decodedSue = json.decodeFromString<Fossil>(encodedSue)
    println("Original:   $sue")
    println("Re-decoded: $decodedSue")
    println("Match: ${sue == decodedSue}")
    println()

    println("--- Decode from spec-value JSON (snake_case keys, lowercase enums) ---")
    val specJson = """
        {
            "id": "NHM-004",
            "name": "Matilda",
            "era": "cretaceous",
            "diet": "herbivore",
            "discovery_year": 2004,
            "discovery_location": "Queensland, Australia",
            "completeness_percent": 65.0
        }
    """.trimIndent()
    val matilda = json.decodeFromString<Fossil>(specJson)
    println("Decoded: $matilda")
    println("  discoveryYear=${matilda.discoveryYear}, discoveryLocation=${matilda.discoveryLocation}, completenessPercent=${matilda.completenessPercent}")
    println()

    println("--- Enum wire values ---")
    Era.entries.forEach { era ->
        println("  ${era.name} -> ${json.encodeToString(era)}")
    }
}
