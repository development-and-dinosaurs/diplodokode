# Diplodokode Plugin

A Gradle plugin that generates Kotlin data classes from an OpenAPI specification file as part of your build. Generated sources are automatically available at compile time.

## Setup

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
  id("uk.co.developmentanddinosaurs.diplodokode") version "<version>"
}
```

## Configuration

Configure the plugin using the `diplodokode` extension:

```kotlin
diplodokode {
  inputFile.set("src/main/resources/openapi.yaml") // default
  outputDir.set("build/generated/kotlin")          // default
}
```

| Property    | Description                                                                         | Default                           |
|-------------|-------------------------------------------------------------------------------------|-----------------------------------|
| `inputFile` | Path to the OpenAPI spec file, relative to the project directory                    | `src/main/resources/openapi.yaml` |
| `outputDir` | Directory to write generated Kotlin sources into, relative to the project directory | `build/generated/kotlin`          |

## Tasks

| Task                  | Group         | Description                                              |
|-----------------------|---------------|----------------------------------------------------------|
| `generateDiplodokode` | `diplodokode` | Generates Kotlin models from the configured OpenAPI spec |

The `generateDiplodokode` task runs automatically before `compileKotlin`, so generated sources are always up to date before compilation. The task is also incremental — it is skipped if neither the spec file nor the output directory has changed since the last build.

## Generated output

See the [diplodokode-generator README](../generator/README.md) for details on how OpenAPI schemas are mapped to Kotlin data classes.
