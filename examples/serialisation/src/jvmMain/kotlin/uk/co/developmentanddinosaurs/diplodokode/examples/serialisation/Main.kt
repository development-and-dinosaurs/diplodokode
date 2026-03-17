package uk.co.developmentanddinosaurs.diplodokode.examples.serialisation

import kotlinx.serialization.json.Json
import uk.co.developmentanddinosaurs.diplodokode.generated.Dinosaur
import uk.co.developmentanddinosaurs.diplodokode.generated.Diet
import uk.co.developmentanddinosaurs.diplodokode.generated.StringOrDouble
import uk.co.developmentanddinosaurs.diplodokode.generated.Era
import uk.co.developmentanddinosaurs.diplodokode.generated.Fossil
import uk.co.developmentanddinosaurs.diplodokode.generated.Sauropod
import uk.co.developmentanddinosaurs.diplodokode.generated.Theropod
import uk.co.developmentanddinosaurs.diplodokode.generated.diplodokodeModule

// The diplodokodeModule is generated alongside the models by diplodokode.
// It registers the sealed hierarchy so any kotlinx.serialization format can
// decode polymorphic types without format-specific annotations on the models.
val json = Json {
    explicitNulls = false
    // classDiscriminator must match the OpenAPI spec's discriminator.propertyName
    classDiscriminator = "classification"
    serializersModule = diplodokodeModule
}

fun main() {
    println("=== Diplodokode Serialisation JSON Example ===")
    println()

    println("--- Fossils from JSON files ---")

    val sue = json.decodeFromString<Fossil>(resource("specimens/sue.json"))
    println("Deserialized: $sue")
    check(sue.id == "NHM-001") { "Unexpected id: ${sue.id}" }
    check(sue.name == "Sue") { "Unexpected name: ${sue.name}" }
    check(sue.era == Era.CRETACEOUS) { "Unexpected era: ${sue.era}" }
    check(sue.diet == Diet.CARNIVORE) { "Unexpected diet: ${sue.diet}" }
    check(sue.discoveryYear == 1990) { "Unexpected year: ${sue.discoveryYear}" }
    check(sue.discoveryLocation == "South Dakota, USA") { "Unexpected location: ${sue.discoveryLocation}" }
    check(sue.completenessPercent == 90.0) { "Unexpected completeness: ${sue.completenessPercent}" }
    check(sue.lengthEstimate == StringOrDouble.DoubleValue(12.3)) { "Unexpected length: ${sue.lengthEstimate}" }
    println("✅ All assertions passed for Sue")
    println()

    val dippy = json.decodeFromString<Fossil>(resource("specimens/dippy.json"))
    println("Deserialized: $dippy")
    check(dippy.id == "NHM-002") { "Unexpected id: ${dippy.id}" }
    check(dippy.name == "Dippy") { "Unexpected name: ${dippy.name}" }
    check(dippy.era == Era.JURASSIC) { "Unexpected era: ${dippy.era}" }
    check(dippy.diet == Diet.HERBIVORE) { "Unexpected diet: ${dippy.diet}" }
    check(dippy.discoveryYear == null) { "Expected null discoveryYear, got: ${dippy.discoveryYear}" }
    check(dippy.discoveryLocation == null) { "Expected null discoveryLocation, got: ${dippy.discoveryLocation}" }
    check(dippy.lengthEstimate == StringOrDouble.StringValue("approximately 26 metres")) { "Unexpected length: ${dippy.lengthEstimate}" }
    println("✅ All assertions passed for Dippy")
    println()

    println("--- Polymorphic Dinosaurs from JSON files (via diplodokodeModule) ---")

    val theropod = json.decodeFromString(Dinosaur.serializer(), resource("specimens/theropod.json"))
    println("Deserialized: $theropod (${theropod::class.simpleName})")
    check(theropod is Theropod) { "Expected Theropod, got ${theropod::class.simpleName}" }
    check((theropod as Theropod).name == "Tyrannosaurus rex") { "Unexpected name: ${theropod.name}" }
    println("✅ Correct variant resolved for Theropod")
    println()

    val sauropod = json.decodeFromString(Dinosaur.serializer(), resource("specimens/sauropod.json"))
    println("Deserialized: $sauropod (${sauropod::class.simpleName})")
    check(sauropod is Sauropod) { "Expected Sauropod, got ${sauropod::class.simpleName}" }
    check((sauropod as Sauropod).name == "Diplodocus carnegii") { "Unexpected name: ${(sauropod as Sauropod).name}" }
    println("✅ Correct variant resolved for Sauropod")
    println()

    println("=== All checks passed ===")
}

private fun resource(path: String): String =
    object {}.javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()
