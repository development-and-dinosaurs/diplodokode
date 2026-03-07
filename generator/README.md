# Diplodokode Generator

The core library for Diplodokode. 

Parses an OpenAPI specification file and generates Kotlin data classes from the schemas defined in `components.schemas`.

## Usage

```kotlin
val generator = DiplodokodeGenerator()
val files = generator.generateFromSpec(File("openapi.yaml"))

files.forEach { fileSpec ->
  fileSpec.writeTo(File("src/main/kotlin"))
}
```

Each `FileSpec` is a [KotlinPoet](https://square.github.io/kotlinpoet/) file ready to be written to disk.

## What gets generated

Given the following schema:

```yaml
components:
  schemas:
    Dinosaur:
      type: object
      required:
        - name
        - age
      properties:
        name:
          type: string
          description: The name of the dinosaur
        age:
          type: integer
          description: The age of the dinosaur in years
        weight:
          type: number
          description: The weight of the dinosaur in kilograms
```

The generator produces:

```kotlin
public data class Dinosaur(
  /** The name of the dinosaur */
  val name: String,
  /** The age of the dinosaur in years */
  val age: Int,
  /** The weight of the dinosaur in kilograms */
  val weight: Double?,
)
```

Fields listed under `required` are generated as non-nullable types. All other fields are nullable. Property descriptions
are emitted as KDoc.

## Type mapping

| OpenAPI type | Kotlin type |
|--------------|-------------|
| `string`     | `String`    |
| `integer`    | `Int`       |
| `number`     | `Double`    |
| `boolean`    | `Boolean`   |
| `array`      | `List`      |
| `object`     | `Any`       |
