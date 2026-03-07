plugins {
  kotlin("jvm") version "2.3.10"
  id("uk.co.developmentanddinosaurs.diplodokode")
  application
}

kotlin { jvmToolchain(21) }

// Configure the Diplodokode plugin
diplodokode {
  inputFile.set("src/main/resources/dinosaur-api.yaml")
  outputDir.set("build/generated/kotlin")
}

repositories {
  mavenCentral()
}

application {
  mainClass.set("uk.co.developmentanddinosaurs.diplodokode.examples.simple.MainKt")
}
