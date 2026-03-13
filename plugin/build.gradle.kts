import com.vanniktech.maven.publish.DeploymentValidation

plugins {
  `java-gradle-plugin`
  alias(libs.plugins.gradle.publish)
  alias(libs.plugins.vanniktech.maven.publish)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  id("diplodokode.publishing")
}

gradlePlugin {
  plugins {
    create("diplodokode") {
      id = "uk.co.developmentanddinosaurs.diplodokode"
      displayName = "Diplodokode"
      description = "Generate roarsome Kotlin models from an OpenAPI specification file with ease"
      implementationClass = "uk.co.developmentanddinosaurs.diplodokode.plugin.DiplodokodePlugin"
    }
  }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":diplodokode-generator"))
  implementation(kotlin("stdlib"))
  implementation(libs.kotlinpoet)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
}

tasks.test { useJUnitPlatform() }

mavenPublishing {
  publishToMavenCentral(
      automaticRelease = true,
      validateDeployment = DeploymentValidation.PUBLISHED,
  )
  signAllPublications()
  pom {
    name.set("Diplodokode Gradle Plugin")
    description.set("Generate roarsome Kotlin models from an OpenAPI specification file with ease")
  }
}
