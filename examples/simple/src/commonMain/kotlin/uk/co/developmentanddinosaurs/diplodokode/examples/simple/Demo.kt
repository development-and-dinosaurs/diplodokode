package uk.co.developmentanddinosaurs.diplodokode.examples.simple

import uk.co.developmentanddinosaurs.diplodokode.generated.Dinosaur


/**
 * Demo class showcasing the usage of generated Dinosaur data class.
 * This demonstrates how the generated models can be used in practice.
 */
class Demo {
  
  fun runDemo() {
    println("=== Diplodokode Generated Models Demo ===")
    println()
    
    val dinosaurs = listOf(
      // weight and isCarnivore have schema defaults (0.0 and false) — override them here
      Dinosaur(
        name = "T-Rex",
        species = "Tyrannosaurus rex",
        age = 30,
        weight = 8000.0,
        isCarnivore = true,
        traits = mapOf("diet" to "carnivore", "habitat" to "forest"),
        metadata = mapOf("discovered" to 1902, "confidence" to "high"),
      ),
      Dinosaur(
        name = "Triceratops",
        species = "Triceratops horridus",
        age = 25,
        weight = 12000.0,
        traits = mapOf("diet" to "herbivore", "defence" to "horns"),
        metadata = null,
      ),
      // weight and isCarnivore omitted — schema defaults (0.0, false) apply
      Dinosaur(
        name = "Brachio",
        species = "Brachiosaurus altithorax",
        age = 40,
        traits = null,
        metadata = null,
      )
    )

    dinosaurs.forEach { displayDinosaurInfo(it) }
  }

  private fun displayDinosaurInfo(dinosaur: Dinosaur) {
    println("Dinosaur Information:")
    println("  Name: ${dinosaur.name}")
    println("  Species: ${dinosaur.species}")
    println("  Age: ${dinosaur.age} years")
    println("  Weight: ${dinosaur.weight ?: "Unknown"} kg")
    println("  Carnivore: ${dinosaur.isCarnivore ?: "Unknown"}")
    if (!dinosaur.traits.isNullOrEmpty()) {
      println("  Traits: ${dinosaur.traits.entries.joinToString { "${it.key}=${it.value}" }}")
    }
    if (!dinosaur.metadata.isNullOrEmpty()) {
      println("  Metadata: ${dinosaur.metadata.entries.joinToString { "${it.key}=${it.value}" }}")
    }
    println()
  }
}
