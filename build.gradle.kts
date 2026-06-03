import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

sourceSets {
    main {
        java {
            srcDirs("src/main/gen")
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.1.3")

        testFramework(TestFrameworkType.Platform)
    }

    // DAP debugger support (Platform module, present in all JetBrains IDEs 2025.1+)
    compileOnly(
        fileTree("${gradle.gradleUserHomeDir}/caches") {
            include("**/transformed/ideaIU-2025.1.3/lib/modules/intellij.platform.dap.jar")
        }
    )

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "de.magynhard.crystal"
        name = "Crystal Language"
        version = project.version.toString()
        description = "Crystal language support for JetBrains IDEs"
        vendor {
            name = "magynhard"
            url = "https://github.com/magynhard"
        }
        ideaVersion {
            sinceBuild = "251"
        }
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }
}

tasks {
    runIde {
        systemProperty("idea.trust.all.projects", "true")
    }

    generateLexer {
        sourceFile.set(file("src/main/kotlin/de/magynhard/crystal/lexer/Crystal.flex"))
        targetOutputDir.set(file("src/main/gen/de/magynhard/crystal/lexer"))
    }

    generateParser {
        sourceFile.set(file("src/main/kotlin/de/magynhard/crystal/parser/Crystal.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        pathToParser.set("de/magynhard/crystal/parser/CrystalParser.java")
        pathToPsiRoot.set("de/magynhard/crystal/psi")
    }

    compileKotlin {
        dependsOn(generateLexer, generateParser)
    }
}

kotlin {
    jvmToolchain(21)
}
