plugins {
    id("com.android.application")
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
    // Local ADB is a debug-only probe dependency; the release app must not
    // package its transport, crypto providers, or transitive SPAKE2 library.
    debugImplementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // Bouncy Castle supplies the X.509 builder used by the probe certificate.
    debugImplementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    // Conscrypt is required by libadb for the Android TLS pairing handshake.
    debugImplementation("org.conscrypt:conscrypt-android:2.5.3")
}

private val forbiddenReleaseArtifacts = setOf(
    "libadb-android",
    "spake2-android",
    "bcprov-jdk15to18",
    "bcpkix-jdk15to18",
    "bcutil-jdk15to18",
    "conscrypt-android",
)

// Keep the source-set boundary executable: an accidental implementation or
// dependency move must fail CI before a release artifact is produced.
tasks.register("verifyReleaseRuntimeClasspath") {
    doLast {
        val releaseRuntimeClasspath = configurations.getByName("releaseRuntimeClasspath")
        val violations = releaseRuntimeClasspath.resolve().filter { file ->
            forbiddenReleaseArtifacts.any { artifact ->
                file.nameWithoutExtension == artifact ||
                    file.nameWithoutExtension.startsWith("$artifact-")
            }
        }
        check(violations.isEmpty()) {
            "Release runtime classpath contains debug-only ADB artifacts: " +
                violations.joinToString { it.name }
        }
    }
}

tasks.register("verifyReleaseManifest") {
    dependsOn("processReleaseMainManifest")
    doLast {
        val manifests = mutableListOf(file("src/main/AndroidManifest.xml"))
        val mergedManifestDirectory = layout.buildDirectory.dir("intermediates/merged_manifest/release").get().asFile
        if (mergedManifestDirectory.isDirectory) {
            manifests += fileTree(mergedManifestDirectory) {
                include("**/AndroidManifest.xml")
            }.files
        }
        val forbiddenMarkers = listOf(
            "android.permission.WRITE_SETTINGS",
            "LocalAdbProbeActivity",
            ".probe",
        )
        val violations = manifests.flatMap { manifest ->
            val content = manifest.readText()
            forbiddenMarkers.filter(content::contains).map { marker ->
                "${manifest.path}: $marker"
            }
        }
        check(violations.isEmpty()) {
            "Release manifest contains debug-only entries: ${violations.joinToString()}"
        }
    }
}

tasks.register("verifyReleaseArtifact") {
    dependsOn("assembleRelease")
    doLast {
        val releaseApks = fileTree(layout.buildDirectory.dir("outputs/apk/release").get().asFile) {
            include("*.apk")
        }.files
        check(releaseApks.isNotEmpty()) { "No release APK was produced for artifact inspection" }
        val forbiddenEntries = releaseApks.flatMap { apk ->
            zipTree(apk).files.filter { entry ->
                val path = entry.name
                path.contains("probe", ignoreCase = true) ||
                    path.contains("libadb", ignoreCase = true) ||
                    path.contains("conscrypt", ignoreCase = true) ||
                    path.contains("bouncycastle", ignoreCase = true)
            }.map { entry -> "${apk.name}: ${entry.name}" }
        }
        check(forbiddenEntries.isEmpty()) {
            "Release APK contains debug-only probe entries: ${forbiddenEntries.joinToString()}"
        }
    }
}

tasks.named("check") {
    dependsOn("verifyReleaseRuntimeClasspath")
    dependsOn("verifyReleaseManifest")
    dependsOn("verifyReleaseArtifact")
}
