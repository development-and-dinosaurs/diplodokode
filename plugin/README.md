# Diplodokode Plugin

A Gradle plugin that generates Kotlin data classes from an OpenAPI specification file as part of your build.

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

The task is incremental — it is skipped if neither the spec file nor the output directory has changed since the last build.

## Wiring into your build

The plugin registers the `generateDiplodokode` task but does not wire it into compilation automatically — build setups vary too much across languages and plugins. You are responsible for:

1. Adding the output directory to your source set
2. Making compilation depend on `generateDiplodokode`

### Kotlin JVM

```kotlin
kotlin {
  sourceSets["main"].kotlin.srcDir("build/generated/kotlin")
}

tasks.named("compileKotlin") { dependsOn("generateDiplodokode") }
```

### Kotlin Multiplatform

```kotlin
kotlin {
  sourceSets["commonMain"].kotlin.srcDir("build/generated/kotlin")
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn("generateDiplodokode") }
```

## Generated output

See the [diplodokode-generator README](../generator/README.md) for details on how OpenAPI schemas are mapped to Kotlin data classes.
