# Diplodokode

Generate roarsome Kotlin models from an OpenAPI specification file with ease.

Diplodokode reads an OpenAPI 3.x YAML spec and generates Kotlin data classes, enums, and sealed interfaces — with full support for Kotlin Multiplatform, sealed-interface polymorphism, and kotlinx.serialization.

---

## What it does

Given an OpenAPI spec like this:

```yaml
components:
  schemas:
    Dinosaur:
      type: object
      required: [name, species, age]
      properties:
        name:
          type: string
        species:
          type: string
        age:
          type: integer
        weight:
          type: number
```

Diplodokode generates:

```kotlin
public data class Dinosaur(
    val name: String,
    val species: String,
    val age: Int,
    val weight: Double?,
)
```

---

## Features

- **Data classes** from `object` schemas, with required/optional fields mapped to non-nullable/nullable Kotlin types
- **Enum classes** from `string` schemas with `enum` values, with constants uppercased from spec values
- **Sealed interfaces** from `oneOf` and `anyOf` schemas, with typed discriminator support
- **Flat composition** from `allOf` schemas (all sub-schemas merged into a single data class)
- **KMP-safe type mapping** — `date-time` → `kotlinx.datetime.Instant`, `uuid` → `kotlin.uuid.Uuid`, etc.
- **kotlinx.serialization** support — `@Serializable`, `@SerialName`, and a generated `SerializersModule` for polymorphism
- **Configurable** — naming strategy, nullability strategy, type mappings, package name, serialisation library

---

## Artifacts

Two artifacts are published to Maven Central:

| Artifact | Coordinates | Use when |
|---|---|---|
| Gradle plugin | `uk.co.developmentanddinosaurs.diplodokode:diplodokode-plugin` | Generating code as part of a Gradle build |
| Generator library | `uk.co.developmentanddinosaurs.diplodokode:diplodokode-generator` | Embedding the generator in your own tooling |

---

## Next steps

- [Quick start](quick-start.md) — get up and running in a few minutes
- [Type mapping](type-mapping.md) — understand how OpenAPI types map to Kotlin
- [Polymorphism](polymorphism.md) — `oneOf`, `anyOf`, `allOf`, and discriminator patterns
- [Serialisation](serialisation.md) — kotlinx.serialization integration
