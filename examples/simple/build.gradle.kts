plugins {
  kotlin("multiplatform") version "2.3.10"
  id("uk.co.developmentanddinosaurs.diplodokode")
}

kotlin {
  jvm()
  js(IR) {
    browser()
  }
}

// Configure the Diplodokode plugin
diplodokode {
  inputFile.set("src/commonMain/resources/dinosaur-api.yaml")
  outputDir.set("build/generated/kotlin")
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn("generateDiplodokode") }

repositories {
  mavenCentral()
}
