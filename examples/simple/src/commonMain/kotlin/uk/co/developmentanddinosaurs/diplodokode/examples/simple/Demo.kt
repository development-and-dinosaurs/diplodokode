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
      Dinosaur(
        name = "T-Rex",
        species = "Tyrannosaurus rex",
        age = 30,
        weight = 8000.0,
        isCarnivore = true
      ),
      Dinosaur(
        name = "Triceratops",
        species = "Triceratops horridus",
        age = 25,
        weight = 12000.0,
        isCarnivore = false
      ),
      Dinosaur(
        name = "Brachio",
        species = "Brachiosaurus altithorax",
        age = 40,
        weight = 56000.0,
        isCarnivore = false
      )
    )
    
    dinosaurs.forEach { displayDinosaurInfo(it) }
  }

  private fun displayDinosaurInfo(dinosaur: Dinosaur) {
    println("🦕 Dinosaur Information:")
    println("  Name: ${dinosaur.name}")
    println("  Species: ${dinosaur.species}")
    println("  Age: ${dinosaur.age} years")
    println("  Weight: ${dinosaur.weight ?: "Unknown"} kg")
    println("  Carnivore: ${dinosaur.isCarnivore ?: "Unknown"}")
    println()
  }
}
