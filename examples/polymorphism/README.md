# Diplodokode Polymorphism Example

This example project demonstrates every composition and polymorphism pattern supported by Diplodokode, using a Kotlin Multiplatform project targeting JVM and JS.

The patterns that are supported are:

1. `oneOf` + discriminator (recommended)
2. `oneOf` + discriminator with `allOf` back-reference
3. `anyOf` + discriminator
4. `oneOf` without discriminator
5. `allOf` flat mixin (composition via `$ref`)

## Running

From the repository root:

```bash
# Generate sources only
./gradlew -p examples/polymorphism generateDiplodokode

# Compile all targets
./gradlew -p examples/polymorphism build
```

---

## Patterns

### Pattern 1 — `oneOf` + discriminator (recommended)

The recommended pattern for Diplodokode consumers. Each variant is a plain `type: object` schema. The discriminator property is pinned to a single value via an inline `enum` on each variant. No `allOf` back-reference is needed, so there are no cyclic references.

```yaml
Dinosaur:
  oneOf:
    - $ref: '#/components/schemas/Tyrannosaur'
    - $ref: '#/components/schemas/Triceratops'
  discriminator:
    propertyName: type

Tyrannosaur:
  type: object
  properties:
    type:
      type: string
      enum: [tyrannosaur]   # pins the discriminator value
    armLengthCm:
      type: number
```

**Generated Kotlin:**

```kotlin
sealed interface Dinosaur {
  val type: Dinosaur.Type
  enum class Type { TYRANNOSAUR, TRICERATOPS }
}

data class Tyrannosaur(
  override val type: Dinosaur.Type = Dinosaur.Type.TYRANNOSAUR,  // default from enum
  val name: String,
  val armLength: Double,
) : Dinosaur
```

The `type` property defaults to the correct constant — consumers never need to pass it explicitly. Dinosaur is a sealed interface so a `when` expression over a `Dinosaur` is exhaustive at compile time.

---

### Pattern 2 — `oneOf` + discriminator (cyclic `allOf` back-reference)

The main motivation for using the `allOf` back-reference pattern is **shared properties**. The parent schema declares properties common to all variants (`name`, `areaSquareKm`). 

Each variant `allOf` includes the parent to inherit those properties, then adds only its own variant-specific fields. Without this, every variant would have to redeclare the shared properties.

The discriminator property is **not** declared on the parent — only the shared business properties are. Declaring the discriminator enum on the parent would cause every variant to inherit all enum values rather than a single pinned value, which is not what we want and would not work as expected.

```yaml
Habitat:
  oneOf:
    - $ref: '#/components/schemas/ForestHabitat'
    - $ref: '#/components/schemas/SwampHabitat'
  discriminator:
    propertyName: habitatType
  type: object
  properties:
    name: 
      type: string # shared — inherited by both variants
    areaSquareKm: 
      type: number # shared — inherited by both variants

ForestHabitat:
  allOf:
    - $ref: '#/components/schemas/Habitat' # inherits name + areaSquareKm
    - type: object
      properties:
        habitatType:
          type: string
          enum: [forest] # pins this variant's discriminator value
        canopyHeightMetres: # specific data only for this variant
          type: number
      required:
        - habitatType
SwampHabitat:
  description: A waterlogged swamp habitat
  allOf:
    - $ref: '#/components/schemas/Habitat' # inherits name + areaSquareKm
    - type: object
      properties:
        habitatType:
          type: string
          enum: [swamp] # pins this variant's discriminator value
        waterDepthMetres: # specific data only for this variant
          type: number
      required:
        - habitatType
```

**Generated Kotlin:**

```kotlin
sealed interface Habitat {
  val habitatType: Habitat.Type
  val name: String          // abstract — declared on the interface
  val areaSquareKm: Double  // abstract — declared on the interface
  enum class Type { FOREST, SWAMP }
}

data class ForestHabitat(
  override val name: String,
  override val areaSquareKm: Double,
  override val habitatType: Habitat.Type = Habitat.Type.FOREST,
  val canopyHeightMetres: Double,
) : Habitat

data class SwampHabitat(
  override val name: String,
  override val areaSquareKm: Double,
  override val habitatType: Habitat.Type = Habitat.Type.SWAMP,
  val waterDepthMetres: Double,
) : Habitat
```

Shared properties declared on the parent schema appear as abstract vals on the sealed interface and as `override val` on each variant.

> **Note**  
> Pattern 1 is preferred for new specs. Use Pattern 2 when working with existing specs that already use the cyclic `allOf` style, or when centralising shared properties on the parent is worthwhile.

---

### Pattern 3 — `anyOf` + discriminator

Pattern 3 is difficult to represent meaningfully in Kotlin — `anyOf` means one or more variants may apply simultaneously, rather than exactly one. This isn't something that we can readily apply to the result code. The generated sealed interface is structurally identical to `oneOf`; the semantic difference is captured in KDoc only.

```yaml
DefenceStrategy:
  anyOf:
    - $ref: '#/components/schemas/ActiveDefence'
    - $ref: '#/components/schemas/PassiveDefence'
  discriminator:
    propertyName: strategyType
```

**Generated Kotlin:**

```kotlin
/**
 * One or more of the following variants may be used.
 * ...
 */
sealed interface DefenceStrategy {
  val strategyType: DefenceStrategy.Type
  enum class Type { ACTIVE, PASSIVE }
}
```

Use `anyOf` when multiple variants can legitimately apply to the same instance, and `oneOf` when exactly one applies.

---

### Pattern 4 — `oneOf` without discriminator

When no `discriminator` is declared the sealed interface has no abstract type property. Consumers distinguish variants by shape at runtime using an exhaustive `when` expression.

```yaml
Footprint:
  oneOf:
    - $ref: '#/components/schemas/ThreeToeFootprint'
    - $ref: '#/components/schemas/FlatFootprint'

ThreeToeFootprint:
  type: object
  properties:
    lengthCm:
      type: number
    toeSpreadDegrees:
      type: number

FlatFootprint:
  type: object
  properties:
    lengthCm:
      type: number
    widthCm:
      type: number
```

**Generated Kotlin:**

```kotlin
sealed interface Footprint // no abstract discriminator property

data class ThreeToeFootprint(
  val lengthCm: Double,
  val toeSpreadDegrees: Double,
) : Footprint

data class FlatFootprint(
  val lengthCm: Double,
  val widthCm: Double,
) : Footprint
```

Use this pattern when variants have no natural type field, or when type discrimination is handled out-of-band (e.g. by a wrapping envelope field).

---

### Pattern 5 — `allOf` flat mixin (composition via `$ref`)

By itself `allOf` is always flat composition — never inheritance. Referenced schemas are merged into a single flat `data class`. The composed schema does not implement any sealed interface.

Referenced schemas can be reused independently across multiple composed schemas.

```yaml
AuditInfo: # reusable mixin — who recorded it and when
  type: object
  properties:
    recordedBy:
      type: string
    recordedAt:
      type: string

DinosaurMeasurements: # reusable mixin — physical stats
  type: object
  properties:
    heightMetres:
      type: number
    weightKg:
      type: number
    topSpeedKph:
      type: number

DinosaurProfile: # Contains the properties from both mixins but no relationship is implied
  allOf:
    - $ref: '#/components/schemas/AuditInfo'
    - $ref: '#/components/schemas/DinosaurMeasurements'
```

**Generated Kotlin:**

```kotlin
data class AuditInfo(
  val recordedBy: String,
  val recordedAt: String,
)

data class DinosaurMeasurements(
  val heightMetres: Double,
  val weightKg: Double,
  val topSpeedKph: Double,
)

data class DinosaurProfile(
  val recordedBy: String,
  val recordedAt: String,
  val heightMetres: Double,
  val weightKg: Double,
  val topSpeedKph: Double,
)
```

`DinosaurProfile` is not *"an `AuditInfo`"* or *"a `DinosaurMeasurements`"* — `allOf` merges properties, not types. `AuditInfo` could equally be composed into an `EnclosureRecord` or a `FeedingLog`.

---

## Summary

| OpenAPI construct                           | Kotlin output                                                                      | When to use                                                                      |
|---------------------------------------------|------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `oneOf` + `discriminator` (enum pins value) | `sealed interface` + `data class` per variant; `override val type = Type.CONSTANT` | **Recommended.** Variants have a type field with a fixed value.                  |
| `oneOf` + `discriminator` (cyclic `allOf`)  | Identical to above                                                                 | Existing specs that use `allOf` back-references. Prefer Pattern 1 for new specs. |
| `anyOf` + `discriminator`                   | `sealed interface` + `data class` per variant; KDoc notes one or more may apply    | Multiple variants can apply simultaneously.                                      |
| `oneOf` (no `discriminator`)                | `sealed interface` + `data class` per variant; no abstract type property           | Variants distinguished by shape, no type field needed.                           |
| `allOf` (flat mixin via `$ref`)             | Single flat `data class` with all properties merged                                | Composing reusable property groups without an IS-A relationship.                 |
