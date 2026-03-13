# Diplodokode

Generate roarsome Kotlin models from an OpenAPI specification file with ease.

Diplodokode reads an OpenAPI 3.x YAML spec and generates Kotlin data classes, enums, and interfaces — with full support for Kotlin Multiplatform, sealed-interface polymorphism, and kotlinx.serialization.

---

## Contents

- [Quick start](#quick-start)
- [Type mapping](#type-mapping)
- [Polymorphism](#polymorphism)
- [Serialisation](#serialisation)
- [Plugin configuration reference](#plugin-configuration-reference)
- [Library usage](#library-usage)
- [Building](#building)

---

## Quick start

### 1. Apply the plugin

```kotlin
plugins {
    id("uk.co.developmentanddinosaurs.diplodokode") version "<version>"
}
```

### 2. Configure the plugin

```kotlin
diplodokode {
    inputFile.set("src/main/resources/openapi.yaml")
    outputDir.set("build/generated/kotlin")
    packageName.set("uk.co.developmentanddinosaurs.diplodokode.generated")
}
```

### 3. Wire the output into your build

The generated sources must be added to a source set and compilation must depend on the task. Diplodokode does not do this automatically — it keeps the plugin simple and works with any project layout.

**JVM project:**

```kotlin
kotlin {
    sourceSets["main"].kotlin.srcDir("build/generated/kotlin")
}

tasks.named("compileKotlin") {
    dependsOn("generateDiplodokode")
}
```

**Kotlin Multiplatform project:**

```kotlin
kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/kotlin")
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn("generateDiplodokode") }
```

### 4. Run

```bash
./gradlew generateDiplodokode
```

---

## Type mapping

Given this spec:

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
        isCarnivore:
          type: boolean
```

Diplodokode generates:

```kotlin
public data class Dinosaur(
    val name: String,
    val species: String,
    val age: Int,
    val weight: Double?,
    val isCarnivore: Boolean?,
)
```

Required fields are non-nullable. Optional fields are nullable. `nullable: true` on a required field forces it nullable regardless.

### Base type mapping

| OpenAPI type | Kotlin type |
|--------------|-------------|
| `string`     | `String`    |
| `integer`    | `Int`       |
| `number`     | `Double`    |
| `boolean`    | `Boolean`   |
| `array`      | `List<T>`   |
| `object`     | `Any`       |

### Format mapping (KMP-safe defaults)

| OpenAPI format    | Kotlin type                  |
|-------------------|------------------------------|
| `date-time`       | `kotlinx.datetime.Instant`   |
| `date`            | `kotlinx.datetime.LocalDate` |
| `time`            | `kotlinx.datetime.LocalTime` |
| `duration`        | `kotlin.time.Duration`       |
| `uuid`            | `kotlin.uuid.Uuid`           |
| `uri`             | `String`                     |
| `byte` / `binary` | `ByteArray`                  |
| `int64`           | `Long`                       |
| `float`           | `Float`                      |

To use Java types instead, or to override specific mappings, see [Type mappings](#type-mappings).

### Arrays

- `items.$ref` resolves to the referenced type: `List<Tyrannosaur>`
- `items.type` maps to a Kotlin type: `List<String>`, `List<Int>`
- Nested arrays are recursive: `List<List<String>>`
- Missing `items` falls back to `List<Any>`

### $ref properties

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

### Enums

Top-level enum schemas generate a standalone `enum class`:

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

Inline enum properties on a data class generate a nested `enum class` in the same file.

---

## Polymorphism

### oneOf — exactly one variant

```yaml
Dinosaur:
  oneOf:
    - $ref: '#/components/schemas/Tyrannosaur'
    - $ref: '#/components/schemas/Triceratops'
```

Generates a `sealed interface` with each `$ref` variant implementing it:

```kotlin
public sealed interface Dinosaur {
    // Exactly one of the following variants must be used.
}

public data class Tyrannosaur() : Dinosaur
public data class Triceratops() : Dinosaur
```

### anyOf — one or more variants

Same structure as `oneOf`. The KDoc on the sealed interface reads "One or more of the following variants may be used."

### allOf — flat composition

```yaml
PackHunter:
  allOf:
    - $ref: '#/components/schemas/Tyrannosaur'
    - type: object
      properties:
        packSize: { type: integer }
```

All sub-schemas are merged into a single flat `data class`. There is no IS-A relationship — `allOf` is always composition.

### Discriminator

When a `oneOf`/`anyOf` schema has a `discriminator` and every variant carries the discriminator property, Diplodokode generates a typed discrimination mechanism.

**Recommended pattern:** each variant pins its discriminator value via an inline `enum` on the property:

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
      enum: [tyrannosaur]
    name:
      type: string

Triceratops:
  type: object
  required: [classification, name]
  properties:
    classification:
      type: string
      enum: [triceratops]
    name:
      type: string
```

**Without serialisation**, the sealed interface gets a nested `Type` enum and an abstract property, and each variant overrides it with a default value:

```kotlin
public sealed interface Dinosaur {
    public enum class Type { TYRANNOSAUR, TRICERATOPS }
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

**With serialisation** (see [Serialisation](#serialisation)), the `Type` enum is suppressed and a `SerializersModule` handles polymorphic dispatch instead.

### Shared properties on sealed interfaces

Properties declared directly on the `oneOf`/`anyOf` parent schema become `abstract val` declarations on the sealed interface. Variants that carry those properties emit `override val`.

---

## Serialisation

Diplodokode has opt-in support for [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization).

### Setup

Add the serialization plugin and dependency to your project, then enable it in the Diplodokode configuration:

```kotlin
// build.gradle.kts
plugins {
    kotlin("plugin.serialization") version "<kotlin-version>"
}

dependencies {
    // or commonMainImplementation for KMP
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:<version>")
}

diplodokode {
    serialisation {
        useKotlinx()
    }
}
```

### What gets annotated

- Every generated class (data class, enum class, sealed interface) is annotated with `@Serializable`.
- Enum constants get `@SerialName` matching the original spec value (e.g. `"carnivore"`, not `"CARNIVORE"`).
- Properties whose Kotlin name differs from the spec name (e.g. `discovery_year` → `discoveryYear`) get `@SerialName("discovery_year")`.
- Discriminator variant classes get `@SerialName` matching their discriminator value.

### Polymorphic serialisation

Diplodokode supports two strategies for serialising sealed hierarchies, controlled by `polymorphismStrategy` in `GeneratorConfig` (or configured via the plugin DSL — see [Plugin configuration reference](#plugin-configuration-reference)).

#### Module (default)

Diplodokode generates a `diplodokodeModule: SerializersModule` file that registers each sealed hierarchy using `polymorphic`/`subclass`. This works with any kotlinx.serialization format — JSON, YAML, CBOR, etc. — because it uses the format-agnostic serialization module mechanism.

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

```kotlin
// JSON
val json = Json {
    serializersModule = diplodokodeModule
}

// YAML (e.g. yamlkt)
val yaml = Yaml {
    serializersModule = diplodokodeModule
}
```

The sealed interface itself has no discriminator annotation — the module and the `@SerialName` on each subclass are sufficient for encode/decode.

#### Annotation

When a `SerializationStrategy` is configured that returns a non-null `discriminatorAnnotation`, Diplodokode emits that annotation directly on the sealed interface (e.g. `@JsonClassDiscriminator` from `kotlinx.serialization.json`). This ties generated code to a specific serialisation format but avoids the need to register a module at runtime.

If the configured strategy does not provide a `discriminatorAnnotation` (as is the case for the built-in `KotlinxSerialisationStrategy`), Diplodokode falls back to generating a Kotlin-typed `Type` enum on the sealed interface, so the hierarchy always has a discrimination mechanism regardless.

#### Choosing between them

|                   | MODULE                                        | ANNOTATION                                                    |
|-------------------|-----------------------------------------------|---------------------------------------------------------------|
| Format support    | Any kotlinx.serialization format              | Format-specific (annotation must be understood by the format) |
| Runtime setup     | Register `diplodokodeModule` in format config | Nothing extra required                                        |
| Generated code    | Separate module file                          | Annotation on the sealed interface                            |
| KMP compatibility | Yes                                           | Depends on annotation; `@JsonClassDiscriminator` is JSON-only |

`Module` is the right default for most projects, especially those targeting multiple formats or KMP. Use `Annotation` only if you have a custom `SerializationStrategy` that provides a format-appropriate discriminator annotation.

#### Configuration

You can configure the module package and module name in the Diplodokode extension.

```kotlin
diplodokode {
    serialisation {
        useKotlinx()
        moduleName("MySerializersModule")
        modulePackage("com.example.serialisation")
    }
}
```

---

## Plugin configuration reference

```kotlin
diplodokode {
    inputFile.set("src/main/resources/openapi.yaml")
    outputDir.set("build/generated/kotlin")
    packageName.set("com.example.api.models")

    naming {
        useDefault()     // PascalCase classes, camelCase properties (default)
        // usePreserve() // preserve names exactly as written in the spec
    }

    nullability {
        useSpecDriven()     // required → non-nullable, optional → nullable (default)
        // useAllNullable()
        // useAllNonNullable()
    }

    typeMappings {
        useMultiplatform()  // KMP-safe types (default)
        // useJava()        // java.time.*, java.util.UUID, java.net.URI

        // override individual format mappings
        format("date-time", "java.time.Instant")
        format("uuid", "java.util.UUID")

        // override base type mappings
        base("integer", "kotlin.Long")
    }

    serialisation {
        useKotlinx()
        // useNone()  // no serialisation annotations (default)

        // rename the generated module file/property to avoid collisions
        moduleName("MySerializersModule")

        // place the module file in a different package
        modulePackage("com.example.serialisation")
    }
}
```

### Defaults

| Property                      | Default                                               |
|-------------------------------|-------------------------------------------------------|
| `inputFile`                   | `src/main/resources/openapi.yaml`                     |
| `outputDir`                   | `build/generated/kotlin`                              |
| `packageName`                 | `uk.co.developmentanddinosaurs.diplodokode.generated` |
| `naming`                      | `default`                                             |
| `nullability`                 | `spec-driven`                                         |
| `typeMappings`                | `multiplatform`                                       |
| `serialisation`               | `none`                                                |
| `serialisation.moduleName`    | `DiplodokodeModule`                                   |
| `serialisation.modulePackage` | same as `packageName`                                 |

### Type mappings

#### `useMultiplatform()` (default)

Requires `org.jetbrains.kotlinx:kotlinx-datetime` on the classpath for date/time types.

| Format            | Kotlin type                  |
|-------------------|------------------------------|
| `date-time`       | `kotlinx.datetime.Instant`   |
| `date`            | `kotlinx.datetime.LocalDate` |
| `time`            | `kotlinx.datetime.LocalTime` |
| `duration`        | `kotlin.time.Duration`       |
| `uuid`            | `kotlin.uuid.Uuid`           |
| `uri`             | `String`                     |
| `byte` / `binary` | `ByteArray`                  |
| `int64`           | `Long`                       |
| `float`           | `Float`                      |

#### `useJava()`

| Format      | Kotlin type           |
|-------------|-----------------------|
| `date-time` | `java.time.Instant`   |
| `date`      | `java.time.LocalDate` |
| `time`      | `java.time.LocalTime` |
| `duration`  | `java.time.Duration`  |
| `uuid`      | `java.util.UUID`      |
| `uri`       | `java.net.URI`        |

---

## Library usage

The generator can be used directly without the Gradle plugin:

```kotlin
// build.gradle.kts
dependencies {
    implementation("uk.co.developmentanddinosaurs.diplodokode:diplodokode-generator:<version>")
}
```

```kotlin
val config = GeneratorConfig(
    packageName = "com.example.api.models",
    serialisationStrategy = KotlinxSerialisationStrategy,
)

val generator = DiplodokodeGenerator(config)
val files = generator.generateFromSpec(File("openapi.yaml"))

files.forEach { fileSpec ->
    fileSpec.writeTo(File("src/main/kotlin"))
}
```

### Custom SerializationStrategy

Implement `SerializationStrategy` to support a serialisation library not provided out of the box:

```kotlin
object MySerializationStrategy : SerializationStrategy {
    override val classAnnotation = ClassName("com.example.serialization", "Serializable")

    override fun enumConstantAnnotation(rawValue: String): AnnotationSpec? =
        AnnotationSpec.builder(ClassName("com.example.serialization", "SerialName"))
            .addMember("%S", rawValue)
            .build()

    override fun propertyAnnotation(specName: String): AnnotationSpec? =
        AnnotationSpec.builder(ClassName("com.example.serialization", "SerialName"))
            .addMember("%S", specName)
            .build()
}
```

---

## Building

```bash
# Build everything
./gradlew build

# Run all tests
./gradlew test

# Run tests for a single subproject
./gradlew :diplodokode-generator:test
./gradlew :diplodokode-plugin:test

# Run the example projects
./gradlew -p examples/simple generateDiplodokode
./gradlew -p examples/polymorphism generateDiplodokode
./gradlew -p examples/serialisation generateDiplodokode

# Show the current version (derived from git tags)
./gradlew currentVersion
```

### Project layout

| Module                                    | Directory                      | Published artifact                                                |
|-------------------------------------------|--------------------------------|-------------------------------------------------------------------|
| `diplodokode-generator`                   | `generator/`                   | `uk.co.developmentanddinosaurs.diplodokode:diplodokode-generator` |
| `diplodokode-plugin`                      | `plugin/`                      | `uk.co.developmentanddinosaurs.diplodokode:diplodokode-plugin`    |
| `diplodokode-examples-simple`             | `examples/simple/`             | —                                                                 |
| `diplodokode-examples-polymorphism`       | `examples/polymorphism/`       | —                                                                 |
| `diplodokode-examples-serialisation`      | `examples/serialisation/`      | —                                                                 |
| `diplodokode-examples-serialisation-yaml` | `examples/serialisation-yaml/` | —                                                                 |

Both example projects are standalone composite builds — they use `includeBuild("../..") ` in their `settings.gradle.kts`.
