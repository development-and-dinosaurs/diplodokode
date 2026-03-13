# Polymorphism

Diplodokode supports all three OpenAPI polymorphism keywords — `oneOf`, `anyOf`, and `allOf` — and handles discriminators with a typed Kotlin mechanism.

---

## oneOf — exactly one variant

A `oneOf` schema generates a `sealed interface`. Each `$ref` variant generates a `data class` that implements it.

```yaml
Dinosaur:
  oneOf:
    - $ref: '#/components/schemas/Tyrannosaur'
    - $ref: '#/components/schemas/Triceratops'

Tyrannosaur:
  type: object
  required: [name]
  properties:
    name:
      type: string

Triceratops:
  type: object
  required: [hornCount]
  properties:
    hornCount:
      type: integer
```

```kotlin
/**
 * Exactly one of the following variants must be used.
 */
public sealed interface Dinosaur

public data class Tyrannosaur(
    val name: String,
) : Dinosaur

public data class Triceratops(
    val hornCount: Int,
) : Dinosaur
```

!!! note
    Only `$ref` variants are supported. Inline variants (anonymous objects directly inside `oneOf`) have no natural name and cannot be generated — a KDoc warning is emitted instead.

---

## anyOf — one or more variants

`anyOf` generates the same structure as `oneOf`. The only difference is the KDoc on the sealed interface, which reads "One or more of the following variants may be used."

---

## allOf — flat composition

`allOf` merges all sub-schemas into a single flat `data class`. There is no IS-A relationship — `allOf` is always composition, never inheritance.

```yaml
PackHunter:
  allOf:
    - $ref: '#/components/schemas/Tyrannosaur'
    - type: object
      properties:
        packSize:
          type: integer
```

All properties from `Tyrannosaur` and the inline object are merged:

```kotlin
public data class PackHunter(
    val name: String,
    val packSize: Int?,
)
```

---

## Discriminator

When a `oneOf`/`anyOf` schema has a `discriminator` and every variant carries the discriminator property, Diplodokode generates a typed discrimination mechanism.

### Recommended pattern

Pin each variant's discriminator value using an inline `enum` on the property. This avoids the need for a `discriminator.mapping` and keeps the spec self-contained.

```yaml
Dinosaur:
  oneOf:
    - $ref: '#/components/schemas/Tyrannosaur'
    - $ref: '#/components/schemas/Triceratops'
  discriminator:
    propertyName: classification

Tyrannosaur:
  type: object
  required: [classification, name]
  properties:
    classification:
      type: string
      enum: [tyrannosaur]   # pins the discriminator value
    name:
      type: string

Triceratops:
  type: object
  required: [classification, name]
  properties:
    classification:
      type: string
      enum: [triceratops]   # pins the discriminator value
    name:
      type: string
```

### Generated output — without serialisation

The sealed interface gets a nested `Type` enum and an abstract property. Each variant overrides it with its pinned value as a default.

```kotlin
public sealed interface Dinosaur {
    public enum class Type {
        TYRANNOSAUR,
        TRICERATOPS,
    }
    public abstract val classification: Type
}

public data class Tyrannosaur(
    override val classification: Dinosaur.Type = Dinosaur.Type.TYRANNOSAUR,
    val name: String,
) : Dinosaur

public data class Triceratops(
    override val classification: Dinosaur.Type = Dinosaur.Type.TRICERATOPS,
    val name: String,
) : Dinosaur
```

The `classification` property is available on every instance, and the default value means callers do not need to supply it when constructing a variant.

### Generated output — with serialisation

When serialisation is enabled, the `Type` enum is suppressed and a `SerializersModule` handles polymorphic dispatch instead. See [Serialisation](serialisation.md) for details.

---

## Shared properties on sealed interfaces

Properties declared directly on the `oneOf`/`anyOf` parent schema become `abstract val` declarations on the sealed interface. Variants that also carry those properties emit `override val`.

```yaml
Habitat:
  oneOf:
    - $ref: '#/components/schemas/ForestHabitat'
    - $ref: '#/components/schemas/SwampHabitat'
  properties:
    name:
      type: string
    areaSquareKm:
      type: number
```

```kotlin
public sealed interface Habitat {
    public abstract val name: String?
    public abstract val areaSquareKm: Double?
}

public data class ForestHabitat(
    override val name: String?,
    override val areaSquareKm: Double?,
    val canopyHeightM: Double?,
) : Habitat
```

---

## Cyclic allOf + oneOf

A common OpenAPI pattern has variants use `allOf` to both reference the parent interface and add their own fields:

```yaml
Habitat:
  oneOf:
    - $ref: '#/components/schemas/ForestHabitat'
  discriminator:
    propertyName: type

ForestHabitat:
  allOf:
    - $ref: '#/components/schemas/Habitat'   # back-reference
    - type: object
      properties:
        type:
          type: string
          enum: [forest]
        canopyHeightM:
          type: number
```

Diplodokode resolves this correctly. The cyclic back-reference is detected and skipped during `allOf` flattening. `ForestHabitat` is generated as a `data class` with the discriminator property and its own fields, implementing `Habitat`.
