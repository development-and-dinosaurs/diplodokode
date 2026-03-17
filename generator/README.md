# Diplodokode Generator

The core library for Diplodokode.

Parses an OpenAPI specification file and generates Kotlin data classes from the schemas defined in `components.schemas`.

## Usage

```kotlin
val generator = DiplodokodeGenerator()
val files = generator.generateFromSpec(File("openapi.yaml"))

files.forEach { fileSpec ->
  fileSpec.writeTo(File("src/main/kotlin"))
}
```

Each `FileSpec` is a [KotlinPoet](https://square.github.io/kotlinpoet/) file ready to be written to disk.

## What gets generated

Given the following schema:

```yaml
components:
  schemas:
    Dinosaur:
      type: object
      required:
        - name
        - age
      properties:
        name:
          type: string
          description: The name of the dinosaur
        age:
          type: integer
          description: The age of the dinosaur in years
        weight:
          type: number
          description: The weight of the dinosaur in kilograms
```

The generator produces:

```kotlin
public data class Dinosaur(
  /** The name of the dinosaur */
  val name: String,
  /** The age of the dinosaur in years */
  val age: Int,
  /** The weight of the dinosaur in kilograms */
  val weight: Double?,
)
```

Fields listed under `required` are generated as non-nullable types. All other fields are nullable. Property descriptions
are emitted as KDoc.

## Type mapping

| OpenAPI type | Kotlin type |
|--------------|-------------|
| `string`     | `String`    |
| `integer`    | `Int`       |
| `number`     | `Double`    |
| `boolean`    | `Boolean`   |
| `array`      | `List`      |
| `object`     | `Any`       |

## Format mapping

The `format` field on a property refines the Kotlin type. All mapped types are KMP-compatible.

| OpenAPI type | Format      | Kotlin type                  | Dependency           |
|--------------|-------------|------------------------------|----------------------|
| `string`     | `date-time` | `kotlinx.datetime.Instant`   | `kotlinx-datetime`   |
| `string`     | `date`      | `kotlinx.datetime.LocalDate` | `kotlinx-datetime`   |
| `string`     | `time`      | `kotlinx.datetime.LocalTime` | `kotlinx-datetime`   |
| `string`     | `duration`  | `kotlin.time.Duration`       | stdlib               |
| `string`     | `uuid`      | `kotlin.uuid.Uuid`           | stdlib (Kotlin 2.0+) |
| `string`     | `uri`       | `String`                     | stdlib               |
| `string`     | `byte`      | `ByteArray`                  | stdlib               |
| `string`     | `binary`    | `ByteArray`                  | stdlib               |
| `integer`    | `int64`     | `Long`                       | stdlib               |
| `number`     | `float`     | `Float`                      | stdlib               |

> **Note:** `uri` maps to `String` — there is no KMP-compatible URI type.

> **Note:** Files containing `uuid`-format properties have `@file:OptIn(ExperimentalUuidApi::class)` added automatically.

## Primitive union types

A schema or property with `oneOf` a set of primitive types generates a sealed interface with `@JvmInline value class` wrappers for each variant, a concrete `fold` implementation, and `companion object` `invoke` overloads.

```yaml
LengthEstimate:
  oneOf:
    - type: number
    - type: string
```

```kotlin
sealed interface LengthEstimate : Union2<Double, String> {
    @JvmInline value class DoubleValue(val value: Double) : LengthEstimate
    @JvmInline value class StringValue(val value: String) : LengthEstimate

    override fun <R> fold(onFirst: (Double) -> R, onSecond: (String) -> R): R = when (this) {
        is DoubleValue -> onFirst(value)
        is StringValue -> onSecond(value)
    }

    companion object {
        operator fun invoke(value: Double): LengthEstimate = DoubleValue(value)
        operator fun invoke(value: String): LengthEstimate = StringValue(value)
    }
}
```

`Union2`, `Union3`, and `Union4` interfaces are generated alongside your models and provide default `firstOrNull()` / `secondOrNull()` / ... and `first()` / `second()` / ... accessors built on `fold`.

Variants are always ordered canonically (string → boolean → integer → number), so the generated name and structure are stable regardless of the order types appear in the spec.

When serialisation is enabled, a `KSerializer` is also generated. See the [full primitive unions guide](https://diplodokode.developmentanddinosaurs.co.uk/primitive-unions/) for usage, naming recommendations, and alternatives.
