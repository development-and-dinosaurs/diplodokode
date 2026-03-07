# Simple Example

Demonstrates the Diplodokode plugin generating a `Dinosaur` data class from an OpenAPI spec and using it in a Kotlin Multiplatform project targeting JVM and JS.

## Running

From the repository root:

```bash
./gradlew -p examples/simple generateDiplodokode
```

This generates the `Dinosaur` class from `src/commonMain/resources/dinosaur-api.yaml` into `build/generated/kotlin`.

To compile all targets:

```bash
./gradlew -p examples/simple build
```

## How it's configured

```kotlin
// build.gradle.kts
plugins {
  kotlin("multiplatform") version "2.3.10"
  id("uk.co.developmentanddinosaurs.diplodokode")
}

kotlin {
  jvm()
  js(IR) { browser() }
}

diplodokode {
  inputFile.set("src/commonMain/resources/dinosaur-api.yaml")
  outputDir.set("build/generated/kotlin")
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn("generateDiplodokode") }
```

The plugin resolves from the local build via the composite build declared in `settings.gradle.kts`, so no published artifact is needed. Use the latest version of the plugin when consuming Diplodokode for real.

## What gets generated

`dinosaur-api.yaml` defines a single `Dinosaur` schema with three required fields and two optional fields, which produces:

```kotlin
public data class Dinosaur(
  val name: String,
  val species: String,
  val age: Int,
  val weight: Double?,
  val isCarnivore: Boolean?,
)
```

See the [generator README](../../generator/README.md) for the full type mapping reference.
