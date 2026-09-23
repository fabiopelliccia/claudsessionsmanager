import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.changelog") version "2.4.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }

    // Gson's only dependency, Error Prone annotations, is needed to compile Gson itself and never at
    // runtime: leaving it out keeps the distribution down to what the plugin actually loads.
    implementation("com.google.code.gson:gson:2.11.0") {
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())

    compilerOptions {
        // Keep the API level aligned with the Kotlin stdlib bundled in the IDE.
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(providers.gradleProperty("javaVersion").get())
    targetCompatibility = JavaVersion.toVersion(providers.gradleProperty("javaVersion").get())
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                val item = getOrNull(pluginVersion) ?: getUnreleased()
                val date = Regex("\\d{4}-\\d{2}-\\d{2}").find(item.header)?.value
                val heading = "<h3>[${item.version}]${date?.let { " - $it" } ?: ""}</h3>"
                heading + renderItem(
                    item.withHeader(false).withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        // `since-build` is 261 and `until-build` stays open, as JetBrains recommends; these are the
        // builds the plugin is verified on, from the 2026.1.5 update to the 2026.3 EAP.
        ides {
            create(IntelliJPlatformType.IntellijIdea, "2026.1.5")
            create(IntelliJPlatformType.IntellijIdea, "2026.2.3")
            create(IntelliJPlatformType.IntellijIdea, "263.5153.40")
        }

        // Every finding fails the build - internal, experimental and deprecated API usages included,
        // as well as a plugin that could not be loaded without a restart - because the Marketplace
        // expects none of them and nothing in this plugin needs any.
        failureLevel = VerifyPluginTask.FailureLevel.ALL.toList()
    }

    // Marketplace credentials are never stored in the repository: both tasks read the environment,
    // and stay unconfigured (and therefore fail with an explicit message) when it is not set.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")

        channels = providers.gradleProperty("pluginVersion").map { version ->
            listOf(version.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }
}

changelog {
    version = providers.gradleProperty("pluginVersion")
    groups = listOf("Added", "Changed", "Fixed", "Removed")
}

tasks {
    test {
        useJUnit()
    }

    // The distribution bundles Gson (Apache License 2.0), which has to travel with its license: the
    // plugin jar carries it, the plugin's own license and the third-party notice.
    jar {
        from(layout.projectDirectory) {
            include("LICENSE", "THIRD_PARTY_NOTICES.md")
            into("META-INF/licenses")
        }
        from(layout.projectDirectory.dir("licenses")) {
            into("META-INF/licenses")
        }
    }

    // The sandbox IDE reads and writes an isolated Claude Code home instead of the real ~/.claude,
    // so trying out export/import never touches the developer's actual sessions.
    runIde {
        jvmArgs("-Dclaude.home=${layout.buildDirectory.dir("claude-home-test").get().asFile}")
    }
}
