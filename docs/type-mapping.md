# Type mapping

Diplodokode maps OpenAPI schema types and formats to Kotlin types. This page describes all mappings and how to customise them.

---

## Nullability

Required fields are non-nullable. Optional fields are nullable. `nullable: true` on a required field forces it nullable regardless.

```yaml
Dinosaur:
  type: object
  required: [name, age]
  properties:
    name:
      type: string         # required → String
    age:
      type: integer        # required → Int
    weight:
      type: number         # optional → Double?
    nickname:
      type: string
      nullable: true       # required but nullable: true → String?
```

```kotlin
public data class Dinosaur(
    val name: String,
    val age: Int,
    val weight: Double?,
    val nickname: String?,
)
```

---

## Base types

| OpenAPI type | Kotlin type |
|---|---|
| `string` | `String` |
| `integer` | `Int` |
| `number` | `Double` |
| `boolean` | `Boolean` |
| `array` | `List<T>` |
| `object` | `Any` |

---

## Format mapping

The `format` keyword on a property refines the Kotlin type. Two presets are available.

### Multiplatform (default)

All types are safe for use in Kotlin Multiplatform projects.

| Format | Kotlin type | Notes |
|---|---|---|
| `date-time` | `kotlinx.datetime.Instant` | Requires `kotlinx-datetime` |
| `date` | `kotlinx.datetime.LocalDate` | Requires `kotlinx-datetime` |
| `time` | `kotlinx.datetime.LocalTime` | Requires `kotlinx-datetime` |
| `duration` | `kotlin.time.Duration` | Built into Kotlin stdlib |
| `uuid` | `kotlin.uuid.Uuid` | Adds `@file:OptIn(ExperimentalUuidApi::class)` |
| `uri` | `String` | No KMP-safe URI type available |
| `byte` / `binary` | `ByteArray` | |
| `int64` | `Long` | |
| `float` | `Float` | |

### Java

JVM-only types from `java.time` and `java.util`.

| Format | Kotlin type |
|---|---|
| `date-time` | `java.time.Instant` |
| `date` | `java.time.LocalDate` |
| `time` | `java.time.LocalTime` |
| `duration` | `java.time.Duration` |
| `uuid` | `java.util.UUID` |
| `uri` | `java.net.URI` |

Switch to Java types:

```kotlin
diplodokode {
    typeMappings {
        useJava()
    }
}
```

### Overriding individual mappings

```kotlin
diplodokode {
    typeMappings {
        // override a format mapping
        format("date-time", "java.time.Instant")
        format("uuid", "java.util.UUID")

        // override a base type mapping
        base("integer", "kotlin.Long")
    }
}
```

---

## Arrays

Array items resolve recursively:

| Spec | Kotlin |
|---|---|
| `items.$ref: Tyrannosaur` | `List<Tyrannosaur>` |
| `items.type: string` | `List<String>` |
| `items.type: array, items.type: integer` | `List<List<Int>>` |
| No `items` | `List<Any>` |

---

## $ref properties

```yaml
Diet:
  type: string
  enum: [carnivore, herbivore, omnivore]

Dinosaur:
  type: object
  properties:
    diet:
      $ref: '#/components/schemas/Diet'
```

Resolves to `val diet: Diet?`.

---

## Enums

### Top-level enum schemas

A schema with `enum` values generates a standalone `enum class`. Constants are uppercased from the spec values.

```yaml
Diet:
  type: string
  enum: [carnivore, herbivore, omnivore]
```

```kotlin
public enum class Diet {
    CARNIVORE,
    HERBIVORE,
    OMNIVORE,
}
```

### Inline enum properties

An inline enum on a data class property generates a nested `enum class` in the same file.

```yaml
Dinosaur:
  type: object
  properties:
    diet:
      type: string
      enum: [carnivore, herbivore]
```

```kotlin
public data class Dinosaur(
    val diet: DinosaurDiet?,
)

public enum class DinosaurDiet {
    CARNIVORE,
    HERBIVORE,
}
```

---

## Property naming

By default, Diplodokode uses PascalCase for class names and camelCase for property names. `snake_case` spec properties are converted automatically.

```yaml
properties:
  discovery_year:
    type: integer
  discoveryLocation:
    type: string
```

```kotlin
val discoveryYear: Int?
val discoveryLocation: String?
```

To preserve names exactly as written in the spec:

```kotlin
diplodokode {
    naming {
        usePreserve()
    }
}
```

---

## KDoc from descriptions

Property `description` values are emitted as KDoc comments.

```yaml
properties:
  name:
    type: string
    description: The display name of the dinosaur
```

```kotlin
/**
 * The display name of the dinosaur
 */
val name: String?
```
