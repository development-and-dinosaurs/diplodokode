package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

internal class EnumClassGenerator(private val config: GeneratorConfig) {

  fun generateEnumClass(name: String, values: List<String>, title: String? = null, description: String? = null, deprecated: Boolean? = null): TypeSpec {
    val enumBuilder = TypeSpec.enumBuilder(name)
    listOfNotNull(title, description).joinToString("\n\n")
        .takeIf { it.isNotEmpty() }?.let { enumBuilder.addKdoc("$it\n") }
    if (deprecated == true) {
      enumBuilder.addAnnotation(
          AnnotationSpec.builder(Deprecated::class)
              .addMember("%S", "Deprecated in the OpenAPI spec.")
              .addMember("level = %T.WARNING", DeprecationLevel::class)
              .build()
      )
    }
    config.serialisationStrategy?.let { enumBuilder.addAnnotation(it.classAnnotation) }
    values.forEach { rawValue ->
      val kotlinName = config.namingStrategy.enumConstant(rawValue)
      val constantAnnotation = config.serialisationStrategy?.enumConstantAnnotation(rawValue)
      if (constantAnnotation != null) {
        enumBuilder.addEnumConstant(kotlinName, TypeSpec.anonymousClassBuilder().addAnnotation(constantAnnotation).build())
      } else {
        enumBuilder.addEnumConstant(kotlinName)
      }
    }
    return enumBuilder.build()
  }

  fun generateTopLevelEnum(name: String, schema: Schema): FileSpec {
    val enumName = config.namingStrategy.className(name)
    return FileSpec.builder(config.packageName, enumName)
        .addType(generateEnumClass(enumName, schema.enum ?: emptyList(), schema.title, schema.description, schema.deprecated))
        .build()
  }
}
