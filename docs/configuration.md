# Plugin configuration reference

All Diplodokode plugin configuration lives in the `diplodokode` extension block in `build.gradle.kts`.

---

## Full example

```kotlin
diplodokode {
    inputFile.set("src/main/resources/openapi.yaml")
    outputDir.set("build/generated/kotlin")
    packageName.set("com.example.api.models")

    naming {
        useDefault()        // PascalCase classes, camelCase properties (default)
        // usePreserve()    // preserve names exactly as written in the spec
    }

    nullability {
        useSpecDriven()        // required → non-nullable, optional → nullable (default)
        // useAllNullable()
        // useAllNonNullable()
    }

    typeMappings {
        useMultiplatform()     // KMP-safe types (default)
        // useJava()           // java.time.*, java.util.UUID, java.net.URI

        format("date-time", "java.time.Instant")   // override a format mapping
        base("integer", "kotlin.Long")             // override a base type mapping
    }

    serialisation {
        useKotlinx()
        // useNone()           // no serialisation annotations (default)

        moduleName("MySerializersModule")          // rename the module file/property
        modulePackage("com.example.serialisation") // separate package for the module
    }
}
```

---

## Defaults

| Property | Default |
|---|---|
| `inputFile` | `src/main/resources/openapi.yaml` |
| `outputDir` | `build/generated/kotlin` |
| `packageName` | `uk.co.developmentanddinosaurs.diplodokode.generated` |
| `naming` | `default` |
| `nullability` | `spec-driven` |
| `typeMappings` | `multiplatform` |
| `serialisation` | `none` |
| `serialisation.moduleName` | `DiplodokodeModule` |
| `serialisation.modulePackage` | same as `packageName` |

---

## inputFile

Path to the OpenAPI specification YAML file, relative to the project directory.

```kotlin
diplodokode {
    inputFile.set("src/commonMain/resources/api.yaml")
}
```

---

## outputDir

Directory where generated Kotlin files are written, relative to the project directory. Files are placed in subdirectories matching the package structure.

```kotlin
diplodokode {
    outputDir.set("build/generated/kotlin")
}
```

---

## packageName

Kotlin package for all generated classes.

```kotlin
diplodokode {
    packageName.set("com.example.api.models")
}
```

---

## naming

Controls how schema names and property names are converted to Kotlin identifiers.

| Option | Class names | Property names |
|---|---|---|
| `useDefault()` | PascalCase (`MyDinosaur`) | camelCase (`myProperty`) |
| `usePreserve()` | Unchanged from spec | Unchanged from spec |

```kotlin
diplodokode {
    naming {
        useDefault()
    }
}
```

---

## nullability

Controls which properties are nullable.

| Option | Behaviour |
|---|---|
| `useSpecDriven()` | Fields in `required` are non-nullable; others are nullable. `nullable: true` on a required field forces it nullable. |
| `useAllNullable()` | Every property is nullable, regardless of `required`. |
| `useAllNonNullable()` | Every property is non-nullable, regardless of `required`. |

```kotlin
diplodokode {
    nullability {
        useSpecDriven()
    }
}
```

---

## typeMappings

Controls how OpenAPI types and formats are mapped to Kotlin types.

### Presets

```kotlin
diplodokode {
    typeMappings {
        useMultiplatform()  // default — kotlinx.datetime.*, kotlin.uuid.Uuid
        // useJava()        // java.time.*, java.util.UUID, java.net.URI
    }
}
```

See [Type mapping](type-mapping.md) for the full mapping tables.

### Overrides

Override individual format or base type mappings without switching the entire preset:

```kotlin
diplodokode {
    typeMappings {
        // use KMP preset but override date-time and uuid with Java types
        format("date-time", "java.time.Instant")
        format("uuid", "java.util.UUID")

        // map all integers to Long instead of Int
        base("integer", "kotlin.Long")
    }
}
```

---

## serialisation

Opt-in serialisation annotation support.

```kotlin
diplodokode {
    serialisation {
        useKotlinx()
    }
}
```

| Option | Effect |
|---|---|
| `useNone()` | No serialisation annotations emitted (default). |
| `useKotlinx()` | Emits `@Serializable`, `@SerialName` using `kotlinx.serialization`. Generates a `SerializersModule` for sealed hierarchies. |

### moduleName

Renames the generated `SerializersModule` file and property. Use this to avoid a collision with a schema in your spec that happens to be named `DiplodokodeModule`.

```kotlin
diplodokode {
    serialisation {
        useKotlinx()
        moduleName("FossilSerializers")
        // generates: val fossilSerializers: SerializersModule = ...
    }
}
```

### modulePackage

Places the generated module file in a different package from the model classes.

```kotlin
diplodokode {
    serialisation {
        useKotlinx()
        modulePackage("com.example.serialisation")
    }
}
```
