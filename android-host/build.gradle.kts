plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val testAbi = providers.gradleProperty("aetherTestAbi").getOrElse("arm64-v8a")
require(testAbi in setOf("arm64-v8a", "x86_64")) { "Unsupported test ABI" }
// Release artifacts must retain the production ARM64 ABI even when tests use an emulator.
gradle.taskGraph.whenReady {
    if (testAbi != "arm64-v8a" && allTasks.any { it.name.contains("Release") }) {
        throw GradleException("aetherTestAbi must not change release artifacts")
    }
}

val fixtureApk = project(":fixture-guest").layout.buildDirectory.file(
    "outputs/apk/debug/fixture-guest-debug.apk",
)
val stagedFixtureApkDir = layout.buildDirectory.dir("generated/androidTestAssets").get().asFile
val configSplitDir = layout.buildDirectory.dir("generated/fixtureSplit")
// AGP's disposable debug signing config is generated at this path by the fixture build.
// CI may override it when the runner uses a non-default Gradle user home.
val fixtureDebugKeystore = providers.gradleProperty("aetherFixtureKeystore")
    .orElse(providers.provider { "${System.getProperty("user.home")}/.android/debug.keystore" })
    .map { file(it) }
val createFixtureConfig by tasks.registering(Exec::class) {
    dependsOn(project(":fixture-guest").tasks.named("assembleDebug"))
    outputs.file(configSplitDir.map { it.file("fixture-config-arm64.apk") })
    outputs.file(configSplitDir.map { it.file("fixture-signer.sha256") })
    inputs.file(rootProject.file("tools/create_fixture_config_split.py"))
    inputs.file(fixtureApk)
    // AGP creates the debug keystore while signing the fixture, so it may not exist at
    // configuration time on a fresh runner. Mark it optional; doFirst verifies it at execution.
    inputs.file(fixtureDebugKeystore).optional(true)
    inputs.property("buildToolsVersion", android.buildToolsVersion ?: "36.0.0")
    doFirst {
        val keystore = fixtureDebugKeystore.get()
        check(keystore.isFile) {
            "Fixture debug keystore is missing: ${keystore.absolutePath}; assemble fixture first or set -PaetherFixtureKeystore"
        }
        commandLine("python3", rootProject.file("tools/create_fixture_config_split.py").absolutePath,
            "--sdk", android.sdkDirectory.absolutePath,
            "--keystore", keystore.absolutePath,
            "--output-dir", configSplitDir.get().asFile.absolutePath)
    }
}
val stageFixtureApk by tasks.registering(Copy::class) {
    dependsOn(createFixtureConfig)
    from(fixtureApk) { rename { "fixture-guest.apk" } }
    from(configSplitDir.map { it.file("fixture-config-arm64.apk") })
    from(configSplitDir.map { it.file("fixture-signer.sha256") })
    into(stagedFixtureApkDir)
    doFirst { check(fixtureApk.get().asFile.isFile) { "Fixture APK was not built" } }
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
            abiFilters += listOf(testAbi)
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
    implementation(project(":guest-api"))
    implementation("com.android.tools.build:apksig:9.1.0")
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
