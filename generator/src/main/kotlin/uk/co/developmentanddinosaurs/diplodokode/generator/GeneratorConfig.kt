package uk.co.developmentanddinosaurs.diplodokode.generator

data class GeneratorConfig(
    val namingStrategy: NamingStrategy = DefaultNamingStrategy(),
    val nullabilityStrategy: NullabilityStrategy = SpecDrivenNullabilityStrategy(),
    val packageName: String = "uk.co.developmentanddinosaurs.diplodokode.generated",
    val typeMappingStrategy: TypeMappingStrategy = KotlinMultiplatformTypeMappingStrategy(),
    val serialisationStrategy: SerializationStrategy? = null,
)
