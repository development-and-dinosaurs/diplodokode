package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec

private const val KOTLINX_SERIALIZATION_MODULES = "kotlinx.serialization.modules"

private val SERIALIZERS_MODULE_CLASS = ClassName(KOTLINX_SERIALIZATION_MODULES, "SerializersModule")
private val POLYMORPHIC_FN = MemberName(KOTLINX_SERIALIZATION_MODULES, "polymorphic")
private val SUBCLASS_FN = MemberName(KOTLINX_SERIALIZATION_MODULES, "subclass")

internal class SerializersModuleGenerator(
    private val config: GeneratorConfig,
    private val typeResolver: TypeResolver,
) {

  fun generate(interfaceVariants: Map<String, List<String>>): FileSpec? {
    val registrable = interfaceVariants
        .filterKeys { it !in config.schemaOverrides }
        .mapValues { (_, variants) -> variants.filter { it !in config.schemaOverrides } }
        .filterValues { it.isNotEmpty() }
    if (registrable.isEmpty()) return null

    val initializer = CodeBlock.builder()
        .beginControlFlow("%T", SERIALIZERS_MODULE_CLASS)
        .apply {
          registrable.entries.sortedBy { it.key }.forEach { (interfaceName, variants) ->
            val interfaceClass = typeResolver.resolveSchemaName(interfaceName)
            beginControlFlow("%M(%T::class)", POLYMORPHIC_FN, interfaceClass)
            variants.sorted().forEach { variantName ->
              val variantClass = typeResolver.resolveSchemaName(variantName)
              addStatement("%M(%T::class)", SUBCLASS_FN, variantClass)
            }
            endControlFlow()
          }
        }
        .endControlFlow()
        .build()

    val propertyName = config.moduleName.replaceFirstChar { it.lowercaseChar() }
    val property = PropertySpec.builder(propertyName, SERIALIZERS_MODULE_CLASS)
        .initializer(initializer)
        .build()

    val filePackage = config.modulePackage ?: config.packageName
    return FileSpec.builder(filePackage, config.moduleName)
        .addProperty(property)
        .build()
  }
}
