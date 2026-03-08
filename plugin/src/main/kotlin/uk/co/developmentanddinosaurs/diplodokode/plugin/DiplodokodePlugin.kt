package uk.co.developmentanddinosaurs.diplodokode.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class DiplodokodePlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val extension = project.extensions.create("diplodokode", DiplodokodeExtension::class.java)

    project.tasks.register("generateDiplodokode", GenerateDiplodokodeTask::class.java) { task ->
      task.group = "diplodokode"
      task.description = "Generate Kotlin models from OpenAPI specifications"
      task.inputFile.set(extension.inputFile.map { project.layout.projectDirectory.file(it) })
      task.outputDir.set(extension.outputDir.map { project.layout.projectDirectory.dir(it) })
      task.packageName.set(extension.packageName)
      task.typeMappingPreset.set(extension.typeMappingConfig.preset)
      task.typeMappingFormatOverrides.set(extension.typeMappingConfig.formatOverrides)
      task.typeMappingBaseOverrides.set(extension.typeMappingConfig.baseOverrides)
    }
  }
}
