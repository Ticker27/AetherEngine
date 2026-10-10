plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val fixtureApk = project(":fixture-guest").layout.buildDirectory.file(
    "outputs/apk/debug/fixture-guest-debug.apk",
)
val stagedFixtureApkDir = layout.buildDirectory.dir("generated/androidTestAssets").get().asFile
val stageFixtureApk by tasks.registering(Copy::class) {
    dependsOn(project(":fixture-guest").tasks.named("assembleDebug"))
    from(fixtureApk)
    into(stagedFixtureApkDir)
    rename { "fixture-guest.apk" }
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

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
            assets.srcDir(stagedFixtureApkDir)
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

tasks.configureEach {
    if (name == "mergeDebugAndroidTestAssets" || name == "assembleDebugAndroidTest") {
        dependsOn(stageFixtureApk)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")

    // settings.gradle.kts includes the generated Flutter module after `flutter pub get`.
    implementation(project(":flutter"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("androidx.test:core:1.5.0")

    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
