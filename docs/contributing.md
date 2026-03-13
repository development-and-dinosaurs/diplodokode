# Contributing

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

# Run a single test class
./gradlew :diplodokode-generator:test --tests "uk.co.developmentanddinosaurs.diplodokode.generator.KotlinClassGeneratorTest"

# Show the current version (derived from git tags)
./gradlew currentVersion
```

---

## Running the examples

The example projects are standalone composite builds. They use `includeBuild("../..") ` in their `settings.gradle.kts`, so no `publishToMavenLocal` step is needed.

```bash
./gradlew -p examples/simple generateDiplodokode
./gradlew -p examples/polymorphism generateDiplodokode
./gradlew -p examples/serialisation generateDiplodokode
./gradlew -p examples/serialisation-yaml generateDiplodokode
```

---

## Project layout

| Module | Directory | Published artifact |
|---|---|---|
| `diplodokode-generator` | `generator/` | `uk.co.developmentanddinosaurs.diplodokode:diplodokode-generator` |
| `diplodokode-plugin` | `plugin/` | `uk.co.developmentanddinosaurs.diplodokode:diplodokode-plugin` |
| `diplodokode-examples-simple` | `examples/simple/` | — |
| `diplodokode-examples-polymorphism` | `examples/polymorphism/` | — |
| `diplodokode-examples-serialisation` | `examples/serialisation/` | — |
| `diplodokode-examples-serialisation-yaml` | `examples/serialisation-yaml/` | — |

---

## Architecture

### Code generation flow

1. `DiplodokodeGenerator.generateFromSpec(File)` reads a YAML OpenAPI spec.
2. `OpenApiSpecParser` deserialises it into `OpenApiSpec` → `Components` → `Map<String, Schema>`.
3. `SchemaResolver.resolve(schemas)` pre-processes the schema map and returns a `ResolvedSpec` containing:
    - `schemas` — flat schemas with `allOf` merged into plain `type: object`
    - `implementedInterfaces` — map of variant name → sealed interfaces it implements
    - `discriminatorEnums` — map of sealed interface name → `DiscriminatorEnum`
    - `discriminatorOverrides` — map of variant name → `DiscriminatorOverride`
    - `interfacePropertyNames` — map of variant name → non-discriminator property names from its interfaces
4. For each resolved schema, `KotlinClassGenerator.generateFromSchema(...)` dispatches to the correct generator:
    - Top-level enum schema → `enum class` via `EnumClassGenerator`
    - Schema with `oneOf` → `sealed interface` via `SealedInterfaceGenerator`
    - Schema with `anyOf` → `sealed interface` via `SealedInterfaceGenerator`
    - Everything else → `data class` via `DataClassGenerator`
5. When `serialisationStrategy` is set and `polymorphismStrategy` is `MODULE`, `SerializersModuleGenerator` produces a `DiplodokodeModule.kt` file.

### Testing

Tests use [Kotest](https://kotest.io/) `BehaviorSpec` (`Given/When/Then`) throughout. All test fixtures, schema names, and example data use dinosaur-themed names.

Coverage is measured with [Kover](https://github.com/Kotlin/kotlinx-kover); reports are written to `build/reports/kover/`.

### Versioning

Versions are managed by the [axion-release](https://axion-release-plugin.readthedocs.io/) plugin, applied to the root project. The version is derived automatically from git tags — no manual version properties are needed.
