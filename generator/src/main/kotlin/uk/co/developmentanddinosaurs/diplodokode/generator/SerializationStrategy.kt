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

    /**
     * Returns an [AnnotationSpec] to place on a sealed interface to declare which JSON field acts
     * as the polymorphic discriminator, or `null` if the library handles this differently.
     *
     * @param propertyName the discriminator property name from the OpenAPI spec (e.g. `"type"`)
     */
    fun discriminatorAnnotation(propertyName: String): AnnotationSpec? = null

    /**
     * Returns an [AnnotationSpec] to place on a variant data class to declare its discriminator
     * wire value, or `null` if the library does not support this.
     *
     * @param rawValue the discriminator value from the OpenAPI spec (e.g. `"tyrannosaur"`)
     */
    fun variantAnnotation(rawValue: String): AnnotationSpec? = null

    /**
     * Returns a file-level [AnnotationSpec] required when [discriminatorAnnotation] is used, or
     * `null` if no opt-in or file annotation is needed.
     *
     * For example, `@JsonClassDiscriminator` is `@ExperimentalSerializationApi`-gated, so this
     * returns `@OptIn(ExperimentalSerializationApi::class)`.
     */
    fun discriminatorFileAnnotation(): AnnotationSpec? = null
}

private const val KOTLINX_SERIALIZATION = "kotlinx.serialization"

/**
 * Emits `@Serializable` and `@SerialName` from `kotlinx.serialization`.
 *
 * Consumers must apply `kotlin("plugin.serialization")` to their project and add
 * `kotlinx-serialization-core` to their dependencies so that the generated annotations compile.
 *
 * Polymorphic sealed hierarchies are registered in the generated `diplodokodeModule`
 * (`SerializersModule`). Pass this module to whichever serializer format you use:
 * ```kotlin
 * val json = Json { serializersModule = diplodokodeModule }
 * val yaml = Yaml { serializersModule = diplodokodeModule }
 * ```
 */
private val KOTLINX_SERIAL_NAME = ClassName(KOTLINX_SERIALIZATION, "SerialName")

data object KotlinxSerialisationStrategy : SerializationStrategy {
    override val classAnnotation: ClassName = ClassName(KOTLINX_SERIALIZATION, "Serializable")

    override fun enumConstantAnnotation(rawValue: String): AnnotationSpec =
        AnnotationSpec.builder(KOTLINX_SERIAL_NAME).addMember("%S", rawValue).build()

    override fun propertyAnnotation(specName: String): AnnotationSpec =
        AnnotationSpec.builder(KOTLINX_SERIAL_NAME).addMember("%S", specName).build()

    override fun variantAnnotation(rawValue: String): AnnotationSpec =
        AnnotationSpec.builder(KOTLINX_SERIAL_NAME).addMember("%S", rawValue).build()
}
