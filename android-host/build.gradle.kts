plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aether.host"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.aether.host"
        minSdk = 24
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

    sourceSets {
        getByName("main") {
            // Flutter-dependent variant (FlutterActivity + AetherRuntimeChannel + MainActivity)
            // ถูกรวมเมื่อ :flutter project มีจริง (bootstrap ด้วย flutter pub get)
            if (findProject(":flutter") != null) {
                kotlin.srcDirs("src/flutterHost/kotlin")
                manifest.srcFile("src/flutterHost/AndroidManifest.xml")
            } else {
                // host-only: launcher = MainActivityHostOnly (stub ไม่ผูก flutter)
                manifest.srcFile("src/main/AndroidManifest.xml")
            }
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")

    // Host-only builds (no Flutter SDK) omit this dependency; the engine is
    // then provided at runtime by HostInitializer's reflection path.
    if (findProject(":flutter") != null) {
        implementation(project(":flutter"))
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("androidx.test:core:1.5.0")
}
