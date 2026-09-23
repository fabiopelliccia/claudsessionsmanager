import org.jetbrains.changelog.Changelog
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

    implementation("com.google.code.gson:gson:2.11.0")

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
        ides {
            recommended()
        }

        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )
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

    // The sandbox IDE reads and writes an isolated Claude Code home instead of the real ~/.claude,
    // so trying out export/import never touches the developer's actual sessions.
    runIde {
        jvmArgs("-Dclaude.home=${layout.buildDirectory.dir("claude-home-test").get().asFile}")
    }
}
