package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName

/**
 * Controls which serialisation annotations are emitted on generated classes.
 *
 * Implement this interface to add support for a serialisation library not provided out of the box.
 * Set [GeneratorConfig.serialisationStrategy] to `null` (the default) to emit no annotations.
 */
interface SerializationStrategy {
    /** The annotation to place on every generated class (data class, enum class, sealed interface). */
    val classAnnotation: ClassName
}

/**
 * Emits `@Serializable` from `kotlinx.serialization`.
 *
 * Consumers must apply `kotlin("plugin.serialization")` to their project and add
 * `kotlinx-serialization-core` to their dependencies so that the generated annotations compile.
 */
data object KotlinxSerialisationStrategy : SerializationStrategy {
    override val classAnnotation: ClassName = ClassName("kotlinx.serialization", "Serializable")
}
