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

internal class SerializersModuleGenerator(private val config: GeneratorConfig) {

  fun generate(interfaceVariants: Map<String, List<String>>): FileSpec? {
    if (interfaceVariants.isEmpty()) return null

    val initializer = CodeBlock.builder()
        .beginControlFlow("%T", SERIALIZERS_MODULE_CLASS)
        .apply {
          interfaceVariants.entries.sortedBy { it.key }.forEach { (interfaceName, variants) ->
            val interfaceClass = ClassName(config.packageName, config.namingStrategy.className(interfaceName))
            beginControlFlow("%M(%T::class)", POLYMORPHIC_FN, interfaceClass)
            variants.sorted().forEach { variantName ->
              val variantClass = ClassName(config.packageName, config.namingStrategy.className(variantName))
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
