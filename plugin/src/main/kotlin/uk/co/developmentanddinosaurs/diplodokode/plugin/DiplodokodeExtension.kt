package uk.co.developmentanddinosaurs.diplodokode.plugin

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class DiplodokodeExtension @Inject constructor(objects: ObjectFactory) {

  val inputFile: Property<String> = objects.property(String::class.java).convention("src/main/resources/openapi.yaml")

  val outputDir: Property<String> = objects.property(String::class.java).convention("build/generated/kotlin")

  val packageName: Property<String> = objects.property(String::class.java).convention("uk.co.developmentanddinosaurs.diplodokode.generated")

  internal val typeMappingConfig: TypeMappingsExtension = objects.newInstance(TypeMappingsExtension::class.java)

  fun inputFile(path: String) {
    inputFile.set(path)
  }

  fun outputDir(path: String) {
    outputDir.set(path)
  }

  fun packageName(name: String) {
    packageName.set(name)
  }

  fun typeMappings(action: TypeMappingsExtension.() -> Unit) {
    action(typeMappingConfig)
  }
}

abstract class TypeMappingsExtension @Inject constructor(objects: ObjectFactory) {

  internal val preset: Property<String> = objects.property(String::class.java).convention("kmp")

  internal val formatOverrides: MapProperty<String, String> =
      objects.mapProperty(String::class.java, String::class.java)

  internal val baseOverrides: MapProperty<String, String> =
      objects.mapProperty(String::class.java, String::class.java)

  fun useMultiplatform() {
    preset.set("kmp")
  }

  fun useJava() {
    preset.set("java")
  }

  fun format(format: String, fqcn: String) {
    formatOverrides.put(format, fqcn)
  }

  fun base(type: String, fqcn: String) {
    baseOverrides.put(type, fqcn)
  }
}
