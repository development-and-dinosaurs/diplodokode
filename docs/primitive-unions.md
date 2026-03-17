# Primitive union types

OpenAPI allows a field to declare `oneOf` a set of primitive types — for example, a measurement that could be either a number or a descriptive string. Kotlin has no native union type, so Diplodokode generates a sealed interface with `@JvmInline value class` wrappers for each variant.

---

## Why they exist

Consider a spec where a fossil's length estimate might be recorded as a number (metres) or a freeform string when an exact figure isn't known:

```yaml
components:
  schemas:
    Fossil:
      type: object
      properties:
        name:
          type: string
        lengthEstimate:
          $ref: '#/components/schemas/LengthEstimate'

    LengthEstimate:
      oneOf:
        - type: number
        - type: string
```

The closest Kotlin equivalent is `Any?`, but that loses all type information and plays poorly with kotlinx.serialization. Diplodokode generates a proper sealed type instead.

---

## What gets generated

For the `LengthEstimate` schema above, Diplodokode produces:

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

And when serialisation is enabled:

```kotlin
@Serializable(with = LengthEstimateSerializer::class)
sealed interface LengthEstimate : Union2<Double, String> { ... }

object LengthEstimateSerializer : KSerializer<LengthEstimate> { ... }
```

The serializer uses `JsonDecoder` to inspect the raw JSON element and dispatch to the correct variant. Serialisation must be configured to generate the serializer — see [Serialisation](serialisation.md).

!!! warning "JSON only"
    The generated serializer depends on `JsonDecoder` and only works with `kotlinx.serialization.json.Json`. Other formats (CBOR, Protobuf) are not supported for primitive union types.

!!! note "@JvmInline and boxing"
    Value class wrappers are erased on the JVM when used as their concrete type — `DoubleValue` compiles down to a plain `Double` with no extra allocation. However, when held as the sealed interface type (which is the common case here), the JVM requires a real object reference and boxing occurs at that boundary. The `@JvmInline` annotation is still the right choice — it signals intent, avoids redundant object creation where the concrete type is used directly, and is the idiomatic Kotlin pattern — but it does not eliminate allocations entirely in this use case.

---

## Using the generated type

### Construction

The `companion object` on each union type has `invoke` overloads for each variant, so you can construct values without knowing the wrapper class names:

```kotlin
val exact: LengthEstimate = LengthEstimate(12.3)       // DoubleValue
val estimate: LengthEstimate = LengthEstimate("approximately 26 metres")  // StringValue
```

You can also construct wrapper instances directly when the intent is clearer:

```kotlin
val exact: LengthEstimate = LengthEstimate.DoubleValue(12.3)
```

### Folding over the value

`fold` is the primary way to consume a union value. It takes one lambda per variant and returns a result, forcing you to handle every case:

```kotlin
val display = fossil.lengthEstimate?.fold(
    onFirst = { "$it metres" },
    onSecond = { it },
) ?: "unknown"
```

This is exhaustive by construction — the compiler enforces that both branches are provided.

### Extracting a specific variant

The `Union` interfaces provide `firstOrNull()` / `secondOrNull()` (and so on for higher arities) for when you only care about one specific variant:

```kotlin
val numeric: Double? = fossil.lengthEstimate?.firstOrNull()   // null if it's a string
val text: String? = fossil.lengthEstimate?.secondOrNull()     // null if it's a number
```

The non-nullable variants `first()` / `second()` throw if the value is not that variant:

```kotlin
val numeric: Double = fossil.lengthEstimate!!.first()   // throws if it's a string
```

### The Union interfaces

Every generated primitive union extends one of the generic `Union` interfaces, depending on how many variants it has:

| Variants | Interface |
|---|---|
| 2 | `Union2<A, B>` |
| 3 | `Union3<A, B, C>` |
| 4 | `Union4<A, B, C, D>` |

These interfaces are generated alongside your models. Because `LengthEstimate` extends `Union2<Double, String>`, utility code can accept the abstract interface without knowing the concrete type name:

```kotlin
fun formatMeasurement(m: Union2<Double, String>): String =
    m.fold(onFirst = { "$it metres" }, onSecond = { it })
```

---

## Supported primitive types

| OpenAPI type | Kotlin wrapper |
|---|---|
| `string` | `StringValue(val value: String)` |
| `integer` | `IntValue(val value: Int)` |
| `number` | `DoubleValue(val value: Double)` |
| `boolean` | `BooleanValue(val value: Boolean)` |

When multiple types appear in the same `oneOf`, the variants are always ordered and named in canonical order — string → boolean → integer → number — regardless of the order they appear in the spec. This means `oneOf: [number, string]` produces `StringOrDouble`, not `DoubleOrString`.

---

## Naming: named types vs inline properties

Diplodokode supports two ways to introduce a primitive union.

### Named schema component (recommended)

Define the union as a named schema and `$ref` it from your properties:

```yaml
components:
  schemas:
    LengthEstimate:
      oneOf:
        - type: number
        - type: string

    Fossil:
      type: object
      properties:
        lengthEstimate:
          $ref: '#/components/schemas/LengthEstimate'
```

This is the recommended approach because:

- The name carries domain meaning — `val lengthEstimate: LengthEstimate?` is self-documenting
- Multiple schemas can share the same union type — only one file is generated
- Evolving the union (adding a variant, adding a description) is a single change in one place

### Inline `oneOf` on a property (convenience fallback)

If you don't control the spec, or prefer inline definitions, Diplodokode handles inline `oneOf` on properties automatically:

```yaml
Fossil:
  type: object
  properties:
    lengthEstimate:
      oneOf:
        - type: number
        - type: string
```

Diplodokode synthesises a name from the variant types in canonical order — here, `StringOrDouble`. The generated type is identical to the named approach; only the name differs. If multiple schemas have the same combination of primitive types, only one file is generated.

The trade-off: `val lengthEstimate: StringOrDouble?` is less meaningful than `val lengthEstimate: LengthEstimate?`. For specs you own, prefer the named approach.

---

## Alternatives to consider

Before reaching for a primitive union, it's worth considering whether a simpler design achieves the same goal.

**Always use a string.** If the value is usually a string anyway and numeric values can be represented as strings without loss, the simplest approach is to declare the field as `type: string` and convert on the consumer side. This avoids the union entirely.

**Always use the more specific type.** If a measurement is always a number conceptually, consider whether the freeform string case is an API design smell that should be fixed upstream. A field that is sometimes a number and sometimes a prose description may indicate two separate concerns that would be cleaner as two separate fields.

**Use a sealed interface with a discriminator.** If the variants are objects rather than primitives, or if the semantics are richer than "this or that value", [discriminator-based sealed interfaces](polymorphism.md) are the appropriate tool.

Primitive unions are the right choice when you genuinely have a field in a spec you can't change, or when the "string or number" distinction carries real domain meaning that consumers need to handle explicitly.
