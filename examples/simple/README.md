# Simple Example

Demonstrates the Diplodokode plugin generating a `Dinosaur` data class from an OpenAPI spec and using it in a runnable application.

## Running

From the repository root:

```bash
./gradlew -p examples/simple run
```

This generates the `Dinosaur` class from `src/main/resources/dinosaur-api.yaml`, compiles everything, and runs the demo.

To generate sources without running:

```bash
./gradlew -p examples/simple generateDiplodokode
```

## How it's configured

```kotlin
// build.gradle.kts
plugins {
  kotlin("jvm") version "2.3.10"
  id("uk.co.developmentanddinosaurs.diplodokode")
  application
}

diplodokode {
  inputFile.set("src/main/resources/dinosaur-api.yaml")
  outputDir.set("build/generated/kotlin")
}
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
