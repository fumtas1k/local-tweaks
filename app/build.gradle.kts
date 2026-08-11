import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.fumtas1k.localtweaks"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.fumtas1k.localtweaks"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".probe"
            versionNameSuffix = "-probe"
        }

        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
        warningsAsErrors = true
        // Phase 1 targets the verified Android 16 device. API 37 changes are
        // evaluated separately before raising targetSdk, per technical design.
        disable += setOf("GradleDependency", "OldTargetApi")
        // AGP 9.3.0 officially defaults to Gradle 9.5.0.
        disable += "AndroidGradlePluginVersion"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}

private val requiredReleaseArtifacts = setOf(
    "libadb-android",
    "spake2-android",
    "bcprov-jdk15to18",
    "bcpkix-jdk15to18",
    "bcutil-jdk15to18",
    "conscrypt-android",
)

abstract class VerifyReleaseRuntimeClasspathTask : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val requiredArtifacts: SetProperty<String>

    @TaskAction
    fun verify() {
        val resolved = runtimeClasspath.files
        val missing = requiredArtifacts.get().filter { artifact ->
            resolved.none { file ->
                file.nameWithoutExtension == artifact || file.nameWithoutExtension.startsWith("$artifact-")
            }
        }
        check(missing.isEmpty()) { "Release runtime classpath is missing production ADB artifacts: $missing" }
    }
}

abstract class VerifyReleaseManifestTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifests: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val manifestFiles = manifests.files.filter { it.isFile && it.name == "AndroidManifest.xml" }
        check(manifestFiles.isNotEmpty()) {
            "Release merged manifest was not produced"
        }
        val forbiddenMarkers = listOf(
            "android.permission.WRITE_SETTINGS",
            "LocalAdbProbeActivity",
            "SettingsProbeActivity",
            ".probe",
        )
        val violations = manifestFiles.flatMap { manifest ->
            val content = manifest.readText()
            forbiddenMarkers.filter(content::contains).map { marker ->
                "${manifest.path}: $marker"
            }
        }
        check(violations.isEmpty()) {
            "Release manifest contains debug-only entries: ${violations.joinToString()}"
        }
        val permissionPattern = Regex("""<uses-permission\b[^>]*android:name="([^"]+)""" )
        val permissions = manifestFiles.flatMap { manifest ->
            permissionPattern.findAll(manifest.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        check(permissions == setOf("android.permission.INTERNET")) {
            "Release manifest permissions are not least privilege: $permissions"
        }
        check(manifestFiles.none { it.readText().contains("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }) {
            "Release manifest contains an unexpected dynamic receiver permission"
        }
    }
}

abstract class VerifyReleaseArtifactTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseApks: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val apks = releaseApks.files.filter { it.isFile && it.extension == "apk" }
        check(apks.isNotEmpty()) { "No release APK was produced for artifact inspection" }
        val forbiddenSourceMarkers = listOf(
            "echo hello",
            "shell:echo",
            "SettingsProbeActivity",
            "LocalAdbProbeActivity",
            "android.permission.WRITE_SETTINGS",
        )
        val sourceViolations = productionSources.files
            .filter { it.isFile }
            .flatMap { source ->
                val content = source.readText()
                forbiddenSourceMarkers.filter(content::contains).map { marker -> "${source.path}: $marker" }
            }
        check(sourceViolations.isEmpty()) {
            "Production source contains removed probe markers: ${sourceViolations.joinToString()}"
        }
        val forbiddenEntries = apks.flatMap { apk ->
            ZipFile(apk).use { zip ->
                val removedProbeMarkers = listOf(
                    "LocalAdbProbeActivity",
                    "SettingsProbeActivity",
                    "localtweaks/probe",
                )
                zip.entries().asSequence()
                    .filter { entry -> removedProbeMarkers.any { marker -> entry.name.contains(marker) } }
                    .map { entry -> "${apk.name}: ${entry.name}" }
                    .toList()
            }
        }
        check(forbiddenEntries.isEmpty()) {
            "Release APK contains debug-only probe entries: ${forbiddenEntries.joinToString()}"
        }
    }
}

val verifyReleaseRuntimeClasspath = tasks.register<VerifyReleaseRuntimeClasspathTask>("verifyReleaseRuntimeClasspath") {
    runtimeClasspath.from(configurations.named("releaseRuntimeClasspath"))
    requiredArtifacts.set(requiredReleaseArtifacts)
}

val verifyReleaseManifest = tasks.register<VerifyReleaseManifestTask>("verifyReleaseManifest") {
    dependsOn("processReleaseMainManifest")
    manifests.from(
        layout.buildDirectory.dir("intermediates/merged_manifest/release").map { directory ->
            directory.asFileTree.matching { include("**/AndroidManifest.xml") }
        },
    )
}

val verifyReleaseArtifact = tasks.register<VerifyReleaseArtifactTask>("verifyReleaseArtifact") {
    dependsOn("assembleRelease")
    releaseApks.from(
        layout.buildDirectory.dir("outputs/apk/release").map { directory ->
            directory.asFileTree.matching { include("**/*.apk") }
        },
    )
    productionSources.from(layout.projectDirectory.dir("src/main").asFileTree)
}

tasks.named("check") {
    dependsOn(verifyReleaseRuntimeClasspath)
    dependsOn(verifyReleaseManifest)
    dependsOn(verifyReleaseArtifact)
}
