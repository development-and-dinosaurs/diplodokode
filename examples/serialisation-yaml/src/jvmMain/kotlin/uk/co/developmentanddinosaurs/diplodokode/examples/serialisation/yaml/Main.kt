package uk.co.developmentanddinosaurs.diplodokode.examples.serialisation.yaml

import net.mamoe.yamlkt.Yaml
import uk.co.developmentanddinosaurs.diplodokode.generated.Diet
import uk.co.developmentanddinosaurs.diplodokode.generated.Era
import uk.co.developmentanddinosaurs.diplodokode.generated.Fossil
import uk.co.developmentanddinosaurs.diplodokode.generated.diplodokodeModule

// The diplodokodeModule is generated alongside the models by diplodokode.
// Passing it to Yaml enables polymorphic decode/encode of sealed interfaces
// without any format-specific annotations on the generated models.
val yaml = Yaml {
    serializersModule = diplodokodeModule
    // Note: unlike kotlinx-serialization-json (explicitNulls = false), yamlkt requires
    // nullable fields to be present in the YAML, using '~' to represent null.
}

fun main() {
    println("=== Diplodokode Serialisation YAML Example ===")
    println()

    println("--- Fossils from YAML files ---")

    val sue = yaml.decodeFromString(Fossil.serializer(), resource("specimens/sue.yaml"))
    println("Deserialized: $sue")
    check(sue.id == "NHM-001") { "Unexpected id: ${sue.id}" }
    check(sue.name == "Sue") { "Unexpected name: ${sue.name}" }
    check(sue.era == Era.CRETACEOUS) { "Unexpected era: ${sue.era}" }
    check(sue.diet == Diet.CARNIVORE) { "Unexpected diet: ${sue.diet}" }
    check(sue.discoveryYear == 1990) { "Unexpected year: ${sue.discoveryYear}" }
    check(sue.discoveryLocation == "South Dakota, USA") { "Unexpected location: ${sue.discoveryLocation}" }
    check(sue.completenessPercent == 90.0) { "Unexpected completeness: ${sue.completenessPercent}" }
    println("✅ All assertions passed for Sue")
    println()

    val dippy = yaml.decodeFromString(Fossil.serializer(), resource("specimens/dippy.yaml"))
    println("Deserialized: $dippy")
    check(dippy.id == "NHM-002") { "Unexpected id: ${dippy.id}" }
    check(dippy.name == "Dippy") { "Unexpected name: ${dippy.name}" }
    check(dippy.era == Era.JURASSIC) { "Unexpected era: ${dippy.era}" }
    check(dippy.diet == Diet.HERBIVORE) { "Unexpected diet: ${dippy.diet}" }
    check(dippy.discoveryYear == null) { "Expected null discoveryYear, got: ${dippy.discoveryYear}" }
    check(dippy.discoveryLocation == null) { "Expected null discoveryLocation, got: ${dippy.discoveryLocation}" }
    println("✅ All assertions passed for Dippy")
    println()

    val stan = yaml.decodeFromString(Fossil.serializer(), resource("specimens/stan.yaml"))
    println("Deserialized: $stan")
    check(stan.id == "NHM-003") { "Unexpected id: ${stan.id}" }
    check(stan.name == "Stan") { "Unexpected name: ${stan.name}" }
    check(stan.era == Era.CRETACEOUS) { "Unexpected era: ${stan.era}" }
    check(stan.discoveryYear == 1987) { "Unexpected year: ${stan.discoveryYear}" }
    check(stan.discoveryLocation == null) { "Expected null discoveryLocation, got: ${stan.discoveryLocation}" }
    check(stan.completenessPercent == 70.0) { "Unexpected completeness: ${stan.completenessPercent}" }
    println("✅ All assertions passed for Stan")
    println()

    println("=== All checks passed ===")
}

private fun resource(path: String): String =
    object {}.javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()
