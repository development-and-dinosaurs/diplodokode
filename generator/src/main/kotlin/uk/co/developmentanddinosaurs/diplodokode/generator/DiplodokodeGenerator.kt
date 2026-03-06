package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.OpenApiSpecParser
import java.io.File

class DiplodokodeGenerator {
  private val parser = OpenApiSpecParser()
  private val classGenerator = KotlinClassGenerator()

  fun generateFromSpec(specFile: File): List<FileSpec> {
    val openApiSpec = parser.parse(specFile)
    return openApiSpec.components?.schemas?.map { classGenerator.generateDataClass(it.key, it.value) } ?: emptyList()
  }
}
