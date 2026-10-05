plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aether"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aether"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake { cppFlags += "-std=c++17" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
    }

    sourceSets {
        getByName("androidTest") {
            kotlin.srcDirs("src/androidTest/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// D-C1.2: keep libaether.so's symbol table in the DEBUG build only, so the D-A1.2 fnPtr
// table (n_ac .. n_update) is statically readable from the packaged library.
// Scoped through the variant API on purpose: buildTypes.<name>.packaging does not exist,
// so a `packaging { jniLibs { ... } }` call written inside a buildTypes block silently
// resolves against the outer android {} extension and widens the glob to every variant,
// release included (docs/decisions/unstripped-debug.md).
// If the variant-scoped API ever becomes unavailable, stop and report UNABLE instead of
// falling back to a global `packaging {}` block.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.packaging.jniLibs.keepDebugSymbols.add("**/libaether.so")
    }
}

dependencies {
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
