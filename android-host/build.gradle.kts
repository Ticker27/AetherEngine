plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.0.21"
}

android {
    namespace = "com.aether.host"
    compileSdk = 34
    buildToolsVersion = "35.0.0"
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.aether.host"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-frtti", "-fexceptions")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
            isJniDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDir("src/test/kotlin")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../aether-native/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")

    // Phase 3: Flutter embedding will be provided by flutter-app build
    // via `flutter pub get` generating .android/Flutter and adding:
    // implementation(project(":flutter"))
    // For Phase 1, keep buildable without Flutter SDK — no flutter_embedding dep.

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("androidx.test:core:1.5.0")
}
