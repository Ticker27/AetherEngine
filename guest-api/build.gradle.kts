plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Classes-only API JAR: Android types are compile stubs, never packaged in the guest.
val sdkRoot = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
dependencies {
    compileOnly(files(sdkRoot.map { "$it/platforms/android-36/android.jar" }))
}
