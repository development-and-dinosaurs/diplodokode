plugins {
  kotlin("multiplatform") version "2.3.10"
  kotlin("plugin.serialization") version "2.3.10"
  id("uk.co.developmentanddinosaurs.diplodokode")
}

kotlin {
  jvm()

  sourceSets {
    commonMain {
      dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
      }
      kotlin.srcDir("build/generated/kotlin")
    }
  }
}

diplodokode {
  inputFile.set("src/commonMain/resources/fossil-registry-api.yaml")
  outputDir.set("build/generated/kotlin")
  serialisation {
    useKotlinx()
  }
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn("generateDiplodokode") }

val jvmRuntimeClasspath by configurations

tasks.register<JavaExec>("run") {
  group = "application"
  mainClass.set("uk.co.developmentanddinosaurs.diplodokode.examples.serialisation.MainKt")
  classpath = jvmRuntimeClasspath + files(tasks.named("compileKotlinJvm").map { (it as org.jetbrains.kotlin.gradle.tasks.KotlinCompile).destinationDirectory })
  dependsOn("compileKotlinJvm")
}

repositories {
  mavenCentral()
}
