package uk.co.developmentanddinosaurs.diplodokode.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class DiplodokodePlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val extension = project.extensions.create("diplodokode", DiplodokodeExtension::class.java)

    val generateTask = project.tasks.register("generateDiplodokode", GenerateDiplodokodeTask::class.java) { task ->
      task.group = "diplodokode"
      task.description = "Generate Kotlin models from OpenAPI specifications"
      task.inputFile.set(extension.inputFile.map { project.layout.projectDirectory.file(it) })
      task.outputDir.set(extension.outputDir.map { project.layout.projectDirectory.dir(it) })
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
      val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
      kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateTask.flatMap { it.outputDir })
    }
  }
}
