plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlinx.kover) apply false
  alias(libs.plugins.sonarqube)
  alias(libs.plugins.spotless)
}

group = "uk.co.developmentanddinosaurs.diplodokode"

val releaseType = project.findProperty("releaseType") as String?
val releaseVersion = project.findProperty("releaseVersion") as String?

version =
    when (releaseType) {
      "release" -> releaseVersion ?: "0.0.1"
      "snapshot" -> "${releaseVersion ?: "0.0.1"}-SNAPSHOT"
      else -> "0.0.1-SNAPSHOT"
    }

subprojects {
  group = rootProject.group
  version = rootProject.version
}

sonar {
  properties {
    property("sonar.organization", "development-and-dinosaurs")
    property("sonar.projectKey", "development-and-dinosaurs_diplodokode")
    property("sonar.coverage.jacoco.xmlReportPaths", "${rootDir}/**/build/reports/kover/report.xml")
  }
}
