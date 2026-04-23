package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

internal class MapTypealiasGenerator(
    private val config: GeneratorConfig,
    private val typeResolver: TypeResolver,
) {

  fun generate(name: String, schema: Schema): FileSpec {
    val className = config.namingStrategy.className(name)
    val mapType = typeResolver.resolveMapType(schema.additionalProperties!!)
    val aliasBuilder = TypeAliasSpec.builder(className, mapType)

    listOfNotNull(schema.title, schema.description).joinToString("\n\n")
        .takeIf { it.isNotEmpty() }?.let { aliasBuilder.addKdoc("$it\n") }

    if (schema.deprecated == true) {
      aliasBuilder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", "Deprecated in the OpenAPI spec.")
              .addMember("level = %T.WARNING", DeprecationLevel::class)
              .build()
      )
    }

    return FileSpec.builder(config.packageName, className)
        .addTypeAlias(aliasBuilder.build())
        .build()
  }
}
