# Quick start

This guide walks through adding Diplodokode to a Gradle project from scratch.

---

## 1. Apply the plugin

=== "settings.gradle.kts"

    ```kotlin
    pluginManagement {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
    }
    ```

=== "build.gradle.kts"

    ```kotlin
    plugins {
        id("uk.co.developmentanddinosaurs.diplodokode") version "<version>"
    }
    ```

---

## 2. Configure the plugin

```kotlin
diplodokode {
    inputFile.set("src/main/resources/openapi.yaml")  // default
    outputDir.set("build/generated/kotlin")            // default
    packageName.set("com.example.api.models")
}
```

---

## 3. Wire the output into your build

The generated sources must be added to a source set and compilation must depend on the task. Diplodokode does not do this automatically — it keeps the plugin simple and works with any project layout.

=== "JVM"

    ```kotlin
    kotlin {
        sourceSets["main"].kotlin.srcDir("build/generated/kotlin")
    }

    tasks.named("compileKotlin") {
        dependsOn("generateDiplodokode")
    }
    ```

=== "Kotlin Multiplatform"

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

---

## 4. Run

```bash
./gradlew generateDiplodokode
```

The task is incremental — it only re-runs when the input spec changes.

---

## Next steps

- [Type mapping](type-mapping.md) — understand how OpenAPI types map to Kotlin
- [Polymorphism](polymorphism.md) — `oneOf`, `anyOf`, `allOf`, and discriminator patterns
- [Serialisation](serialisation.md) — add `@Serializable` and generate a `SerializersModule`
- [Plugin configuration](configuration.md) — full reference for all plugin options
