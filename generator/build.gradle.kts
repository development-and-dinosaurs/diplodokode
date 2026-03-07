import com.vanniktech.maven.publish.DeploymentValidation

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlinx.kover)
  alias(libs.plugins.vanniktech.maven.publish)
  `java-library`
  id("diplodokode.publishing")
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.yamlkt)

  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.runner.junit5)
}

tasks.test { useJUnitPlatform() }

kover {
  reports {
    total {
      html { onCheck = true }
      xml { onCheck = true }
    }
  }
}

mavenPublishing {
  publishToMavenCentral(
      automaticRelease = true,
      validateDeployment = DeploymentValidation.PUBLISHED,
  )
  signAllPublications()
  pom {
    name.set("Diplodokode Generator")
    description.set("Generate roarsome Kotlin models from an OpenAPI specification file with ease")
  }
}

