package uk.co.developmentanddinosaurs.diplodokode.generator

enum class PolymorphismStrategy {
  /**
   * Emit a discriminator annotation on the sealed interface (e.g. `@JsonClassDiscriminator`).
   * The Type enum is suppressed only when the configured [SerializationStrategy] returns a
   * non-null [SerializationStrategy.discriminatorAnnotation]; otherwise the Kotlin-typed Type
   * enum is generated as a fallback so the hierarchy always has a discrimination mechanism.
   */
  ANNOTATION,

  /**
   * Generate a `diplodokodeModule: SerializersModule` file that registers sealed hierarchies
   * polymorphically. Works with any kotlinx.serialization format (JSON, YAML, etc.).
   * The Type enum is always suppressed when a serialisation strategy is configured.
   */
  MODULE,
}

data class GeneratorConfig(
    val namingStrategy: NamingStrategy = DefaultNamingStrategy(),
    val nullabilityStrategy: NullabilityStrategy = SpecDrivenNullabilityStrategy(),
    val packageName: String = "uk.co.developmentanddinosaurs.diplodokode.generated",
    val typeMappingStrategy: TypeMappingStrategy = KotlinMultiplatformTypeMappingStrategy(),
    val serialisationStrategy: SerializationStrategy? = null,
    val polymorphismStrategy: PolymorphismStrategy = PolymorphismStrategy.MODULE,
    /** Package for the generated `DiplodokodeModule` file. Defaults to [packageName] when null. */
    val modulePackage: String? = null,
)
