package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.OpenApiSpecParser
import java.io.File

class DiplodokodeGenerator {
  private val parser = OpenApiSpecParser()
  private val resolver = SchemaResolver()
  private val classGenerator = KotlinClassGenerator()

  fun generateFromSpec(specFile: File): List<FileSpec> {
    val openApiSpec = parser.parse(specFile)
    val schemas = openApiSpec.components?.schemas ?: return emptyList()
    val resolvedSchemas = resolver.resolve(schemas)
    return resolvedSchemas.map { (name, schema) ->
      classGenerator.generateFromSchema(name, schema)
    }
  }
}
