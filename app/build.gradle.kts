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
        warningsAsErrors = true
        // Phase 1 targets the verified Android 16 device. API 37 changes are
        // evaluated separately before raising targetSdk, per technical design.
        disable += setOf("GradleDependency", "OldTargetApi")
        // AGP 9.3.0 officially defaults to Gradle 9.5.0.
        disable += "AndroidGradlePluginVersion"
    }
}
