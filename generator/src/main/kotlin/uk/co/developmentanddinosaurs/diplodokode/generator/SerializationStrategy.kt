package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
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

    /**
     * Returns an [AnnotationSpec] to place on an enum constant to declare its serialised name,
     * or `null` if the library does not support per-constant name overrides.
     *
     * @param rawValue the original value from the OpenAPI spec (e.g. `"tyrannosaur"`)
     */
    fun enumConstantAnnotation(rawValue: String): AnnotationSpec?

    /**
     * Returns an [AnnotationSpec] to place on a property to declare its serialised name,
     * or `null` if the library does not support per-property name overrides.
     *
     * Only called when the Kotlin property name differs from the original spec property name.
     *
     * @param specName the original property key from the OpenAPI spec (e.g. `"arm_length"`)
     */
    fun propertyAnnotation(specName: String): AnnotationSpec?
}

/**
 * Emits `@Serializable` from `kotlinx.serialization`.
 *
 * Consumers must apply `kotlin("plugin.serialization")` to their project and add
 * `kotlinx-serialization-core` to their dependencies so that the generated annotations compile.
 */
private val KOTLINX_SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")

data object KotlinxSerialisationStrategy : SerializationStrategy {
    override val classAnnotation: ClassName = ClassName("kotlinx.serialization", "Serializable")

    override fun enumConstantAnnotation(rawValue: String): AnnotationSpec =
        AnnotationSpec.builder(KOTLINX_SERIAL_NAME).addMember("%S", rawValue).build()

    override fun propertyAnnotation(specName: String): AnnotationSpec =
        AnnotationSpec.builder(KOTLINX_SERIAL_NAME).addMember("%S", specName).build()
}
