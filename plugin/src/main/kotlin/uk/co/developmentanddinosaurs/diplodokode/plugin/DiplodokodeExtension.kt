package uk.co.developmentanddinosaurs.diplodokode.plugin

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class DiplodokodeExtension @Inject constructor(objects: ObjectFactory) {
  
  val inputFile: Property<String> = objects.property(String::class.java).convention("src/main/resources/openapi.yaml")

  val outputDir: Property<String> = objects.property(String::class.java).convention("build/generated/kotlin")

  val packageName: Property<String> = objects.property(String::class.java).convention("uk.co.developmentanddinosaurs.diplodokode.generated")

  fun inputFile(path: String) {
    inputFile.set(path)
  }

  fun outputDir(path: String) {
    outputDir.set(path)
  }

  fun packageName(name: String) {
    packageName.set(name)
  }
}
