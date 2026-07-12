import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit") version "2023.3.0.3"
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
        intellijIdea("2026.1.3")
        bundledModule("intellij.platform.dap")
        bundledPlugin("HtmlTools")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "de.magynhard.crystal"
        name = "Crystal Language"
        version = project.version.toString()
        vendor {
            name = "magynhard"
            url = "https://github.com/magynhard"
        }
        ideaVersion {
            sinceBuild = "261"
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

    generateLexer {
        sourceFile.set(file("src/main/kotlin/de/magynhard/crystal/ecr/lexer/EmbeddedCrystal.flex"))
        targetOutputDir.set(file("src/main/gen/de/magynhard/crystal/ecr/lexer"))
    }

    generateParser {
        sourceFile.set(file("src/main/kotlin/de/magynhard/crystal/ecr/parser/EmbeddedCrystal.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        pathToParser.set("de/magynhard/crystal/ecr/parser/EmbeddedCrystalParser.java")
        pathToPsiRoot.set("de/magynhard/crystal/ecr/psi")
    }

    compileKotlin {
        dependsOn(generateLexer, generateParser)
    }

    withType<JavaCompile>().configureEach {
        options.isFork = false
    }
}

kotlin {
    jvmToolchain(21)
}
