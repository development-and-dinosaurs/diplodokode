import com.vanniktech.maven.publish.DeploymentValidation
import java.util.Base64

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlinx.kover)
  alias(libs.plugins.vanniktech.maven.publish)
  `java-library`
  `maven-publish`
  signing
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
    name.set("Diplodokode")
    description.set("Generate roarsome Kotlin models from an OpenAPI specification file with ease")
    inceptionYear.set("2026")
    url.set("https://github.com/development-and-dinosaurs/diplodokode/")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    developers {
      developer {
        id.set("tyrannoseanus")
        name.set("Tyrannoseanus")
        url.set("https://github.com/Tyrannoseanus/")
        email.set("tyrannoseanus@developmentanddinosaurs.co.uk")
      }
    }
    scm {
      url.set("https://github.com/development-and-dinosaurs/diplodokode/")
      connection.set("scm:git:git://github.com/development-and-dinosaurs/diplodokode.git")
      developerConnection.set(
          "scm:git:ssh://git@github.com/development-and-dinosaurs/diplodokode.git"
      )
    }
  }
}

signing {
  val signingKeyBase64: String? by project
  val signingKey = decode(signingKeyBase64)
  val signingPassword: String? by project
  useInMemoryPgpKeys(signingKey, signingPassword)
}

fun decode(base64Key: String?): String {
  return if (base64Key == null) "" else String(Base64.getDecoder().decode(base64Key))
}
