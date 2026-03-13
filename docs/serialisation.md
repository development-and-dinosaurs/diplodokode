# Serialisation

Diplodokode has opt-in support for [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization). When enabled, generated classes are annotated with `@Serializable` and configured for correct round-trip encode/decode.

---

## Setup

=== "build.gradle.kts"

    ```kotlin
    plugins {
        kotlin("plugin.serialization") version "<kotlin-version>"
    }

    dependencies {
        // JVM
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:<version>")
        // or for JSON specifically:
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<version>")

        // KMP — use commonMainImplementation instead
    }

    diplodokode {
        serialisation {
            useKotlinx()
        }
    }
    ```

---

## What gets annotated

Every generated class receives `@Serializable`:

```kotlin
@Serializable
public data class Fossil(
    val id: String,
    val name: String,
)

@Serializable
public enum class Diet { CARNIVORE, HERBIVORE }

@Serializable
public sealed interface Dinosaur
```

### Enum constants

Enum constants get `@SerialName` matching the original spec value, so serialised output uses the spec's lowercase strings rather than the Kotlin constant names.

```yaml
Diet:
  type: string
  enum: [carnivore, herbivore]
```

```kotlin
@Serializable
public enum class Diet {
    @SerialName("carnivore")
    CARNIVORE,

    @SerialName("herbivore")
    HERBIVORE,
}
```

### Property names

Properties whose Kotlin name differs from the spec name (due to snake_case → camelCase conversion) get `@SerialName` to preserve the original wire format.

```yaml
properties:
  discovery_year:
    type: integer
  discovery_location:
    type: string
```

```kotlin
@SerialName("discovery_year")
val discoveryYear: Int?

@SerialName("discovery_location")
val discoveryLocation: String?
```

---

## Polymorphic serialisation

Diplodokode supports two strategies for serialising sealed hierarchies.

### Module (default)

Diplodokode generates a `diplodokodeModule: SerializersModule` file alongside the model classes. This registers each sealed hierarchy using `polymorphic`/`subclass` and works with any kotlinx.serialization format — JSON, YAML, CBOR, etc.

```kotlin
// generated: DiplodokodeModule.kt
public val diplodokodeModule: SerializersModule = SerializersModule {
    polymorphic(Dinosaur::class) {
        subclass(Theropod::class)
        subclass(Sauropod::class)
    }
}
```

Register the module when configuring your format:

=== "JSON"

    ```kotlin
    val json = Json {
        serializersModule = diplodokodeModule
    }
    ```

=== "YAML (yamlkt)"

    ```kotlin
    val yaml = Yaml {
        serializersModule = diplodokodeModule
    }
    ```

The sealed interface itself has no discriminator annotation — the module and the `@SerialName` on each subclass are sufficient for encode/decode.

Each variant's `@SerialName` value matches its discriminator value from the spec:

```kotlin
@Serializable
@SerialName("theropod")
public data class Theropod(val name: String) : Dinosaur

@Serializable
@SerialName("sauropod")
public data class Sauropod(val name: String) : Dinosaur
```

Round-trip example:

```kotlin
val dinosaur: Dinosaur = Theropod(name = "Tyrannosaurus rex")

val encoded = json.encodeToString(Dinosaur.serializer(), dinosaur)
// {"type":"theropod","name":"Tyrannosaurus rex"}

val decoded = json.decodeFromString(Dinosaur.serializer(), encoded)
// Theropod(name="Tyrannosaurus rex")
```

### Annotation

When a custom `SerializationStrategy` returns a non-null `discriminatorAnnotation`, Diplodokode emits that annotation directly on the sealed interface. This is format-specific but requires no runtime module registration.

```kotlin
// Example: custom strategy returning @JsonClassDiscriminator
@Serializable
@JsonClassDiscriminator("type")
public sealed interface Dinosaur
```

!!! note
    The built-in `KotlinxSerialisationStrategy` does **not** provide a `discriminatorAnnotation`. It uses Module mode. To use Annotation mode you need a custom strategy that returns the appropriate annotation for your format.

If the configured strategy does not provide a `discriminatorAnnotation`, Diplodokode falls back to generating a Kotlin-typed `Type` enum on the sealed interface. The hierarchy will always have a discrimination mechanism.

### Choosing between them

|  | Module | Annotation |
|---|---|---|
| Format support | Any kotlinx.serialization format | Format-specific |
| Runtime setup | Register module in format config | Nothing extra |
| Generated code | Separate module file | Annotation on sealed interface |
| KMP compatibility | Yes | Depends on the annotation |

Module is the right default for most projects, especially KMP or those targeting multiple serialisation formats. Use Annotation only with a custom strategy that provides a format-appropriate discriminator annotation.

---

## Module name and package

If the default name `DiplodokodeModule` would collide with a schema in your spec, or if you want the module in a separate package:

```kotlin
diplodokode {
    serialisation {
        useKotlinx()
        moduleName("MySerializersModule")        // renames file and property
        modulePackage("com.example.serial")      // separate package (optional)
    }
}
```

The property name is derived from the file name with the first character lowercased:
`"MySerializersModule"` → `val mySerializersModule: SerializersModule`.
