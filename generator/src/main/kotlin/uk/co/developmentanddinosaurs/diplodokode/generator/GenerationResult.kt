package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec

/**
 * Describes a single diagnostic (warning or error) produced during generation.
 *
 * @param schemaName The name of the schema that triggered the diagnostic, or empty for file-level issues.
 * @param location A short string identifying where in the schema the issue was found (e.g. `"properties.age"`).
 * @param message A human-readable description of the issue.
 */
data class GenerationDiagnostic(
    val schemaName: String,
    val location: String,
    val message: String,
)

/**
 * The result of a [DiplodokodeGenerator.generateFromSpecWithResult] call.
 *
 * Use this type when you need to inspect warnings or handle failures without exceptions.
 * For simple use cases, [DiplodokodeGenerator.generateFromSpec] throws on failure instead.
 */
sealed class GenerationResult {
    /** Generation completed with no issues. */
    data class Success(val files: List<FileSpec>) : GenerationResult()

    /** Generation completed but produced one or more warnings. Files are still usable. */
    data class PartialSuccess(val files: List<FileSpec>, val warnings: List<GenerationDiagnostic>) : GenerationResult()

    /** Generation failed. No files were produced. */
    data class Failure(val errors: List<GenerationDiagnostic>) : GenerationResult()
}
