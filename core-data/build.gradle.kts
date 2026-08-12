plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    // Kotlin включён встроенно в AGP 9 — org.jetbrains.kotlin.android не применяется.
}

android {
    namespace = "tv.anion.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 23 // Android TV 6.0 — нижняя планка дешёвых боксов
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core-source"))

    implementation(libs.kotlinx.coroutines.android)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}
