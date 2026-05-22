import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "intellij-crystal"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}


