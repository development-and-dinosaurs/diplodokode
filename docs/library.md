# Library usage

The generator can be used directly as a library, without the Gradle plugin. This is useful for embedding code generation in your own tooling or build pipeline.

---

## Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("uk.co.developmentanddinosaurs.diplodokode:diplodokode-generator:<version>")
}
```

---

## Basic usage

```kotlin
val generator = DiplodokodeGenerator(GeneratorConfig(
    packageName = "com.example.api.models",
))

val files = generator.generateFromSpec(File("openapi.yaml"))

files.forEach { fileSpec ->
    fileSpec.writeTo(File("src/main/kotlin"))
}
```

`generateFromSpec` returns a list of [KotlinPoet](https://square.github.io/kotlinpoet/) `FileSpec` objects. Call `writeTo(directory)` to write them to disk, or `toString()` to get the source text directly.

---

## GeneratorConfig

```kotlin
data class GeneratorConfig(
    val namingStrategy: NamingStrategy = DefaultNamingStrategy(),
    val nullabilityStrategy: NullabilityStrategy = SpecDrivenNullabilityStrategy(),
    val packageName: String = "uk.co.developmentanddinosaurs.diplodokode.generated",
    val typeMappingStrategy: TypeMappingStrategy = KotlinMultiplatformTypeMappingStrategy(),
    val serialisationStrategy: SerializationStrategy? = null,
    val polymorphismStrategy: PolymorphismStrategy = PolymorphismStrategy.MODULE,
    val modulePackage: String? = null,
    val moduleName: String = "DiplodokodeModule",
)
```

### With serialisation

```kotlin
val config = GeneratorConfig(
    packageName = "com.example.api.models",
    serialisationStrategy = KotlinxSerialisationStrategy,
)
```

### With Java type mappings

```kotlin
val config = GeneratorConfig(
    packageName = "com.example.api.models",
    typeMappingStrategy = JavaTypeMappingStrategy(),
)
```

---

## NamingStrategy

Controls how schema names and property names are converted to Kotlin identifiers.

| Class | Behaviour |
|---|---|
| `DefaultNamingStrategy()` | PascalCase class names, camelCase property names |
| `PreserveNamingStrategy()` | Names are used exactly as written in the spec |

Implement `NamingStrategy` to provide custom logic:

```kotlin
class MyNamingStrategy : NamingStrategy {
    override fun className(name: String): String = name.uppercase()
    override fun propertyName(name: String): String = name.lowercase()
    override fun enumConstant(name: String): String = name.uppercase()
}
```

---

## NullabilityStrategy

Controls which properties are nullable.

| Class | Behaviour |
|---|---|
| `SpecDrivenNullabilityStrategy()` | Uses `required` and `nullable` from the spec |
| `AllNullableStrategy()` | Every property is nullable |
| `AllNonNullableStrategy()` | Every property is non-nullable |

Implement `NullabilityStrategy` to provide custom logic:

```kotlin
class MyNullabilityStrategy : NullabilityStrategy {
    override fun isNullable(
        propertyName: String,
        schema: Schema,
        required: Set<String>,
    ): Boolean = propertyName !in required
}
```

---

## TypeMappingStrategy

Controls how OpenAPI types and formats map to Kotlin `ClassName`s.

| Class | Behaviour |
|---|---|
| `KotlinMultiplatformTypeMappingStrategy()` | KMP-safe types (`kotlinx.datetime.*`, `kotlin.uuid.Uuid`) |
| `JavaTypeMappingStrategy()` | JVM types (`java.time.*`, `java.util.UUID`) |

Both classes support `withOverrides(formatOverrides, baseOverrides)` to apply per-entry overrides on top of the preset:

```kotlin
val strategy = KotlinMultiplatformTypeMappingStrategy().withOverrides(
    formatOverrides = mapOf("date-time" to ClassName("java.time", "Instant")),
    baseOverrides = mapOf("integer" to ClassName("kotlin", "Long")),
)
```

---

## SerializationStrategy

Implement `SerializationStrategy` to support a serialisation library not provided out of the box.

```kotlin
interface SerializationStrategy {
    /** Annotation placed on every generated class. */
    val classAnnotation: ClassName

    /** Annotation placed on an enum constant to declare its serialised name. */
    fun enumConstantAnnotation(rawValue: String): AnnotationSpec?

    /** Annotation placed on a property to declare its serialised name. */
    fun propertyAnnotation(specName: String): AnnotationSpec?

    /** Annotation placed on a sealed interface to declare the discriminator field. */
    fun discriminatorAnnotation(propertyName: String): AnnotationSpec? = null

    /** Annotation placed on a variant to declare its discriminator wire value. */
    fun variantAnnotation(rawValue: String): AnnotationSpec? = null

    /** File-level annotation required alongside discriminatorAnnotation (e.g. @OptIn). */
    fun discriminatorFileAnnotation(): AnnotationSpec? = null
}
```

Example:

```kotlin
object MySerializationStrategy : SerializationStrategy {
    override val classAnnotation = ClassName("com.example.serialization", "Serializable")

    override fun enumConstantAnnotation(rawValue: String): AnnotationSpec =
        AnnotationSpec.builder(ClassName("com.example.serialization", "SerialName"))
            .addMember("%S", rawValue)
            .build()

    override fun propertyAnnotation(specName: String): AnnotationSpec =
        AnnotationSpec.builder(ClassName("com.example.serialization", "SerialName"))
            .addMember("%S", specName)
            .build()
}
```
