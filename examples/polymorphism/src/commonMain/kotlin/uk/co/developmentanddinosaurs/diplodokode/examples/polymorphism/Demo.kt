package uk.co.developmentanddinosaurs.diplodokode.examples.polymorphism

import uk.co.developmentanddinosaurs.diplodokode.generated.ActiveDefence
import uk.co.developmentanddinosaurs.diplodokode.generated.DefenceStrategy
import uk.co.developmentanddinosaurs.diplodokode.generated.Dinosaur
import uk.co.developmentanddinosaurs.diplodokode.generated.DinosaurProfile
import uk.co.developmentanddinosaurs.diplodokode.generated.FlatFootprint
import uk.co.developmentanddinosaurs.diplodokode.generated.Footprint
import uk.co.developmentanddinosaurs.diplodokode.generated.ForestHabitat
import uk.co.developmentanddinosaurs.diplodokode.generated.Habitat
import uk.co.developmentanddinosaurs.diplodokode.generated.PassiveDefence
import uk.co.developmentanddinosaurs.diplodokode.generated.SwampHabitat
import uk.co.developmentanddinosaurs.diplodokode.generated.ThreeToeFootprint
import uk.co.developmentanddinosaurs.diplodokode.generated.Triceratops
import uk.co.developmentanddinosaurs.diplodokode.generated.Tyrannosaur

class Demo {

  fun runDemo() {
    demoOneOfWithDiscriminator()
    demoOneOfWithDiscriminatorCyclic()
    demoAnyOfWithDiscriminator()
    demoOneOfWithoutDiscriminator()
    demoAllOfMixin()
  }

  // Pattern 1: oneOf + discriminator (recommended)
  // Variants are plain type: object schemas that pin their discriminator value
  // via an inline enum. No allOf back-reference, no cyclic references.
  private fun demoOneOfWithDiscriminator() {
    println("--- Pattern 1: oneOf + discriminator (recommended) ---")

    val dinosaurs: List<Dinosaur> = listOf(
      Tyrannosaur(name = "Rexy", armLength = 0.9),
      Tyrannosaur(name = "Sue", armLength = 1.0),
      Triceratops(name = "Tops", hornCount = 3),
    )

    // The when expression is exhaustive because Dinosaur is a sealed interface.
    // The type property defaults to the correct value — no need to pass it explicitly.
    dinosaurs.forEach { dinosaur ->
      when (dinosaur) {
        is Tyrannosaur -> println("  ${dinosaur.name}: tyrannosaur, arm length ${dinosaur.armLength}m (type=${dinosaur.type})")
        is Triceratops -> println("  ${dinosaur.name}: triceratops, ${dinosaur.hornCount} horns (type=${dinosaur.type})")
      }
    }
    println()
  }

  // Pattern 2: oneOf + discriminator (cyclic allOf back-reference)
  // The allOf back-reference lets variants inherit shared properties from the
  // parent (name, areaSquareKm) without redeclaring them. Because those properties
  // are declared on the parent schema they appear as abstract vals on the sealed
  // interface — accessible directly on a Habitat typed variable, no downcast needed.
  // Only variant-specific properties require a when expression.
  private fun demoOneOfWithDiscriminatorCyclic() {
    println("--- Pattern 2: oneOf + discriminator (cyclic allOf back-reference) ---")

    val habitats: List<Habitat> = listOf(
      ForestHabitat(name = "Cretaceous Canopy", areaSquareKm = 120.0, canopyHeightMetres = 30.0),
      SwampHabitat(name = "Jurassic Marsh", areaSquareKm = 45.0, waterDepthMetres = 1.5),
    )

    // name and areaSquareKm are on the sealed interface — no downcast required.
    println("  All habitats:")
    habitats.forEach { habitat ->
      println("    ${habitat.name}: ${habitat.areaSquareKm}km² (habitatType=${habitat.habitatType})")
    }

    // Variant-specific properties require a when expression.
    println("  Variant details:")
    habitats.forEach { habitat ->
      when (habitat) {
        is ForestHabitat -> println("    ${habitat.name}: forest canopy at ${habitat.canopyHeightMetres}m")
        is SwampHabitat  -> println("    ${habitat.name}: swamp water depth ${habitat.waterDepthMetres}m")
      }
    }
    println()
  }

  // Pattern 3: anyOf + discriminator
  // anyOf means one or more variants may apply. The sealed interface is
  // structurally identical to oneOf — the semantic difference is in KDoc only.
  private fun demoAnyOfWithDiscriminator() {
    println("--- Pattern 3: anyOf + discriminator ---")

    val strategies: List<DefenceStrategy> = listOf(
      ActiveDefence(weaponType = "tail club"),
      PassiveDefence(armorThicknessMm = 50.0),
      ActiveDefence(weaponType = "horns"),
    )

    strategies.forEach { strategy ->
      when (strategy) {
        is ActiveDefence  -> println("  Active defence: ${strategy.weaponType} (strategyType=${strategy.strategyType})")
        is PassiveDefence -> println("  Passive defence: ${strategy.armorThicknessMm}mm armour (strategyType=${strategy.strategyType})")
      }
    }
    println()
  }

  // Pattern 4: oneOf without discriminator
  // No discriminator declared — the sealed interface has no abstract type
  // property. Variants are distinguished by shape alone in a when expression.
  private fun demoOneOfWithoutDiscriminator() {
    println("--- Pattern 4: oneOf without discriminator ---")

    val footprints: List<Footprint> = listOf(
      ThreeToeFootprint(lengthCm = 45.0, toeSpreadDegrees = 120.0),
      FlatFootprint(lengthCm = 80.0, widthCm = 55.0),
      ThreeToeFootprint(lengthCm = 38.0, toeSpreadDegrees = 95.0),
    )

    footprints.forEach { footprint ->
      when (footprint) {
        is ThreeToeFootprint -> println("  Three-toe: ${footprint.lengthCm}cm long, ${footprint.toeSpreadDegrees}° spread")
        is FlatFootprint     -> println("  Flat: ${footprint.lengthCm}cm × ${footprint.widthCm}cm")
      }
    }
    println()
  }

  // Pattern 5: allOf flat mixin (composition via $ref)
  // allOf merges all referenced schemas into a single flat data class.
  // DinosaurProfile is not "an AuditInfo" or "a DinosaurMeasurements" —
  // it composes both. Neither mixin schema is a sealed interface.
  private fun demoAllOfMixin() {
    println("--- Pattern 5: allOf flat mixin ---")

    val profile = DinosaurProfile(
      recordedBy = "Dr. Grant",
      recordedAt = "1993-06-11T09:00:00Z",
      heightMetres = 6.1,
      weightKg = 8400.0,
      topSpeedKph = 45.0,
    )

    println("  Recorded by: ${profile.recordedBy} at ${profile.recordedAt}")
    println("  Height: ${profile.heightMetres}m, Weight: ${profile.weightKg}kg, Top speed: ${profile.topSpeedKph}kph")
    println()
  }
}
