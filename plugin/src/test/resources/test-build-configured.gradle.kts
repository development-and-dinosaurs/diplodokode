plugins {
  kotlin("jvm") version "2.3.10"
  id("uk.co.developmentanddinosaurs.diplodokode")
}

repositories {
  mavenLocal()
  mavenCentral()
}

diplodokode {
  inputFile.set("src/main/resources/dinosaur-api.yaml")
  outputDir.set("build/generated/kotlin")
  packageName.set("com.example.dinosaurs.models")
  typeMappings {
    useJava()
  }
  nullability {
    useAllNonNullable()
  }
}
