plugins {
    id("com.android.application")
}

// Host owns the API classes; the fixture references them without bundling a second copy.
dependencies {
    compileOnly(project(":guest-api"))
}

android {
    namespace = "com.aether.fixture"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.aether.fixture"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            // AGP's debug signer is sufficient for the isolated fixture trust test.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
