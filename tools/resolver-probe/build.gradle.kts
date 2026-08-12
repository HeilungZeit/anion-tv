plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Toolchain, а не sourceCompatibility: иначе Kotlin берёт таргет от той JDK,
// на которой запущен Gradle, и сборка разъезжается между CLI (17) и
// Android Studio (embedded JBR 25).
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("tv.anion.probe.MainKt")
}

dependencies {
    implementation(project(":core-resolve"))
    implementation(project(":core-source"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}
