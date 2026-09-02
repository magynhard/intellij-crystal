import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.grammarkit.tasks.GenerateLexerTask

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
        intellijIdea("262.10315.19")
        bundledModule("intellij.platform.dap")
        bundledModule("intellij.platform.smRunner")
        bundledModule("intellij.platform.testRunner")
        bundledModule("intellij.platform.structureView")
        bundledModule("intellij.platform.langInjection")
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
            sinceBuild = "262"
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
        // Optional: open a project directly instead of the welcome wizard
        // (skips the trust/first-run dialogs): RUN_IDE_PROJECT=/path ./gradlew runIde
        System.getenv("RUN_IDE_PROJECT")?.let { args(it) }
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

    register<GenerateLexerTask>("generateEcrLexer") {
        sourceFile.set(file("src/main/kotlin/de/magynhard/crystal/ecr/lexer/EmbeddedCrystal.flex"))
        targetOutputDir.set(file("src/main/gen/de/magynhard/crystal/ecr/lexer"))
    }

    register<GenerateParserTask>("generateEcrParser") {
        sourceFile.set(file("src/main/kotlin/de/magynhard/crystal/ecr/parser/EmbeddedCrystal.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        pathToParser.set("de/magynhard/crystal/ecr/parser/EmbeddedCrystalParser.java")
        pathToPsiRoot.set("de/magynhard/crystal/ecr/psi")
    }

    compileKotlin {
        dependsOn(generateLexer, generateParser, "generateEcrLexer", "generateEcrParser")
    }

    // compileJava compiles the generated parser/lexer sources — it must never
    // run against stale generation output (flip-flopping parse results).
    compileJava {
        dependsOn(generateLexer, generateParser, "generateEcrLexer", "generateEcrParser")
    }

    test {
        // The stdlib library provider legitimately enumerates the real Crystal
        // distribution (/usr/lib/crystal, including compiler/crystal/macros.cr for
        // the builtin macro-method API). Background index activities hit the VFS
        // root check at racy points — depending on test-class order the check runs
        // before any test registered the allowance and the error is attributed to
        // whichever test is currently running, flaking the suite. The platform
        // provides this switch to disable the check when VFS roots are legitimately
        // accessed (IDE test infrastructure guidance); per-class allowances
        // (CrystalTestVfsRoots) remain for individually executed test classes.
        systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
    }

    withType<JavaCompile>().configureEach {
        options.isFork = false
    }
}

kotlin {
    jvmToolchain(25)
}

val crystalCorpus = providers.gradleProperty("crystalCorpus").orElse("indexed")
val crystalStdlibRoot = providers.gradleProperty("crystalStdlibRoot")

val stdlibParseAudit by intellijPlatformTesting.testIde.registering {
    val scope = crystalCorpus.get()
    task {
        group = "verification"
        description = "Parses the pinned Crystal 1.21.0 source corpus without recovery or exclusions."
        classpath += files(
            sourceSets.test.get().runtimeClasspath,
            configurations["intellijPlatformTestClasspath"]
        )
        useJUnit()
        filter {
            includeTestsMatching(
                "de.magynhard.crystal.parser.CrystalStdlibParseAuditTest.testCorpusParsesWithoutErrors"
            )
            isFailOnNoMatchingTests = true
        }
        systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
        systemProperty("crystal.stdlib.parse.audit.enabled", "true")
        systemProperty("crystal.stdlib.parse.audit.root", crystalStdlibRoot.orElse("").get())
        systemProperty("crystal.stdlib.parse.audit.scope", scope)
        systemProperty(
            "crystal.stdlib.parse.audit.report",
            layout.buildDirectory.file("reports/stdlib-parse-audit/$scope/report.txt").get().asFile.absolutePath
        )
        systemProperty(
            "crystal.stdlib.parse.audit.tsv",
            layout.buildDirectory.file("reports/stdlib-parse-audit/$scope/errors.tsv").get().asFile.absolutePath
        )
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/stdlibParseAudit/$scope"))
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/stdlibParseAudit/$scope"))
        maxHeapSize = "4g"
        maxParallelForks = 1
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}
