plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlinx.kover) apply false
  alias(libs.plugins.axion.release)
  alias(libs.plugins.sonarqube)
  alias(libs.plugins.spotless)
}

group = "uk.co.developmentanddinosaurs.diplodokode"
version = scmVersion.version

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
