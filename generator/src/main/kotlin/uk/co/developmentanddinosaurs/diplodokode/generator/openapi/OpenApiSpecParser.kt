package uk.co.developmentanddinosaurs.diplodokode.generator.openapi

import net.mamoe.yamlkt.Yaml
import java.io.File

class OpenApiSpecParser {
  private val yaml = Yaml()

  fun parse(specFile: File): OpenApiSpec {
    return yaml.decodeFromString(OpenApiSpec.serializer(), specFile.readText())
  }
}
