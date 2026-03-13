package uk.co.developmentanddinosaurs.diplodokode.generator

enum class PolymorphismStrategy {
  /** Emit a discriminator annotation on the sealed interface (e.g. `@JsonClassDiscriminator`). */
  ANNOTATION,

  /**
   * Generate a `diplodokodeModule: SerializersModule` file that registers sealed hierarchies
   * polymorphically. Works with any kotlinx.serialization format (JSON, YAML, etc.).
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
