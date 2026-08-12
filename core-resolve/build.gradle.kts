plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Toolchain, а не sourceCompatibility: иначе Kotlin берёт таргет от той JDK,
// на которой запущен Gradle, и сборка разъезжается между CLI (17) и
// Android Studio (embedded JBR 25).
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
}
