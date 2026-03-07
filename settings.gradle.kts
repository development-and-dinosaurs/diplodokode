rootProject.name = "diplodokode"

dependencyResolutionManagement { @Suppress("UnstableApiUsage") repositories { mavenCentral() } }

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

include(":generator")
project(":generator").name = "diplodokode-generator"

include(":plugin")
project(":plugin").name = "diplodokode-plugin"

