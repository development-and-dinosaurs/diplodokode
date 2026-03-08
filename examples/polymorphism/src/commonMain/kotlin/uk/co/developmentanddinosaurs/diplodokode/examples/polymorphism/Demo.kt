package uk.co.developmentanddinosaurs.diplodokode.examples.polymorphism

import uk.co.developmentanddinosaurs.diplodokode.generated.Dinosaur
import uk.co.developmentanddinosaurs.diplodokode.generated.DinosaurProfile
import uk.co.developmentanddinosaurs.diplodokode.generated.Triceratops
import uk.co.developmentanddinosaurs.diplodokode.generated.Tyrannosaur
class Demo {

  fun runDemo() {
    println("=== Diplodokode Polymorphism Demo ===")
    println()

    // oneOf — sealed interface: Tyrannosaur and Triceratops both implement Dinosaur.
    // The discriminator type property defaults to the correct value automatically.
    val dinosaurs: List<Dinosaur> = listOf(
      Tyrannosaur(name = "Rexy", armLength = 0.9),
      Tyrannosaur(name = "Sue", armLength = 1.0),
      Triceratops(name = "Tops", hornCount = 3),
    )

    println("--- oneOf: sealed interface ---")
    dinosaurs.forEach { describeDinosaur(it) }

    // allOf — flat composition: DinosaurProfile merges AuditInfo (provenance metadata)
    // with DinosaurMeasurements (physical stats). Neither referenced schema defines
    // what a profile "is" — they are mixed in. AuditInfo could equally be mixed into
    // an EnclosureRecord or a FeedingLog.
    println("--- allOf: flat composition ---")
    val profile = DinosaurProfile(
      recordedBy = "Dr. Grant",
      recordedAt = "1993-06-11T09:00:00Z",
      heightMetres = 6.1,
      weightKg = 8400.0,
      topSpeedKph = 45.0,
    )
    println("Profile recorded by ${profile.recordedBy} at ${profile.recordedAt}")
    println("  Height: ${profile.heightMetres}m, Weight: ${profile.weightKg}kg, Top speed: ${profile.topSpeedKph}kph")
    println()
  }

  private fun describeDinosaur(dinosaur: Dinosaur) {
    val description = when (dinosaur) {
      is Tyrannosaur -> "${dinosaur.name} is a tyrannosaur with ${dinosaur.armLength}m arms (type=${dinosaur.type})"
      is Triceratops -> "${dinosaur.name} is a triceratops with ${dinosaur.hornCount} horns (type=${dinosaur.type})"
    }
    println(description)
  }
}
